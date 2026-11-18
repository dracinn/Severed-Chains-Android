#!/bin/bash
# Batch RealESRGAN upscale of the extracted HD pack on a rented vast.ai
# GPU instance. Creates a cheap single-GPU instance, rsyncs the pack
# source up, runs tools/bgextract/upscale.sh on it, rsyncs the results
# back, then destroys the instance (KEEP_INSTANCE=1 skips the destroy).
#
# Requires: `vastai` CLI + `vastai set api-key <key>` (vast.ai account
# page). rsync/ssh on PATH. Output is deterministic — the same ncnn
# binary + model files as the local pipeline produce identical pixels.
#
# STATUS (2025-11): GPU path currently BLOCKED on vast.ai's nvidia/cuda
# images — they bake NVIDIA_DRIVER_CAPABILITIES=compute,utility into the
# image ENV, and `--env` on `create instance` does NOT override it, so
# the toolkit mounts a libGLX_nvidia stub with no Vulkan entry points
# (vkCreateInstance -9). Verified workarounds that still fail:
#   - apt libnvidia-gl-XXX + extracted ICD: dpkg breaks on bind-mounts,
#     and manual dpkg-deb -x hits undefined Xorg symbols (ErrorF,
#     miCreateDefColormap) in libnvidia-glcore
#   - official runfile extraction: same glcore symbol chain
#   - mesa-vulkan-drivers (lavapipe): vkCreateInstance succeeds but
#     UltraSharp output comes out black, and ~2-3min/img is too slow
# To revive GPU: use an image/template with NVIDIA_DRIVER_CAPABILITIES
# =all baked in at build time (e.g. a desktop/graphics template), or a
# provider that grants graphics caps. CUDA workloads (PyTorch etc.) work
# fine under compute,utility if the pipeline is ever ported to CUDA.
#
# usage:
#   vast_upscale.sh <extractDir> <outDir> [bin/index ...]
#   e.g. vast_upscale.sh build/hd-pack /tmp/pack 21/694 22/1108
#   (no variants = every bg/<bin>/<idx>.png found under extractDir)
#
# env:
#   VASTAI        vastai binary (default: vastai on PATH, else
#                 ~/hd-diffusion-venv/bin/vastai)
#   VAST_QUERY    offers query (default: reliable >=24GB single GPU)
#   VAST_IMAGE    docker image (default: nvidia/cuda:12.4.1-runtime-ubuntu22.04)
#   VAST_DISK     disk GB (default: 30)
#   KEEP_INSTANCE set to 1 to leave the instance running after the run
#   SSH_KEY       path to a pubkey to attach (default: first ~/.ssh/*.pub,
#                 else a generated ~/.ssh/id_ed25519)

set -euo pipefail

SRC=$1; DST=$2; shift 2
SRC=$(cd "$SRC" && pwd)
mkdir -p "$DST"; DST=$(cd "$DST" && pwd)

VASTAI=${VASTAI:-$(command -v vastai || echo "$HOME/hd-diffusion-venv/bin/vastai")}
# NB: gpu_ram/cpu_ram are not queryable fields — select by gpu_name instead.
VAST_QUERY=${VAST_QUERY:-'reliability>0.95 verified=true num_gpus=1 rentable=true inet_down>200 disk_space>20 gpu_name in [RTX_4090, RTX_3090, RTX_5090]'}
VAST_IMAGE=${VAST_IMAGE:-nvidia/cuda:12.4.1-runtime-ubuntu22.04}
VAST_DISK=${VAST_DISK:-30}
KEEP_INSTANCE=${KEEP_INSTANCE:-}
TOOL="$(cd "$(dirname "$0")" && pwd)"

# Default variant list: every extracted background.
if [ $# -eq 0 ]; then
  variants=()
  for f in "$SRC"/bg/*/*.png; do
    [ -e "$f" ] || continue
    rel=${f#"$SRC"/bg/}
    variants+=("${rel%.png}")
  done
  set -- ${variants[@]+"${variants[@]}"}
fi
[ $# -gt 0 ] || { echo "no variants found under $SRC/bg"; exit 1; }
echo "==> $# variants queued (bg + fg under each)"

# JSON field extractor tolerant of top-level array vs {"offers":[...]} /
# {"instances":{...}} wrapper shapes across vastai versions.
jget() { /usr/bin/env python3 -c "
import sys, json
d = json.load(sys.stdin)
if isinstance(d, dict):
    for k in ('offers', 'instances', 'results'):
        if k in d:
            d = d[k]
            break
print($1)"; }

# --- ssh key -----------------------------------------------------------------
SSH_PUB=${SSH_KEY:-}
if [ -z "$SSH_PUB" ]; then
  for k in "$HOME"/.ssh/id_ed25519.pub "$HOME"/.ssh/id_rsa.pub; do
    [ -f "$k" ] && SSH_PUB=$k && break
  done
fi
if [ -z "$SSH_PUB" ]; then
  mkdir -p "$HOME/.ssh"
  ssh-keygen -q -t ed25519 -N '' -f "$HOME/.ssh/id_ed25519"
  SSH_PUB="$HOME/.ssh/id_ed25519.pub"
fi
# Register on the account; per-instance attach happens once it's running.
"$VASTAI" create ssh-key "$SSH_PUB" -y >/dev/null 2>&1 || true

# --- pick + create instance ---------------------------------------------------
echo "==> searching offers"
OFFERS_JSON=$("$VASTAI" search offers "$VAST_QUERY" -o 'dph+' --raw)
OFFER=$(echo "$OFFERS_JSON" | jget '(d[0] if isinstance(d,list) else d)["id"]')
PRICE=$(echo "$OFFERS_JSON" | jget '(d[0] if isinstance(d,list) else d)["dph_total"]')
echo "==> offer $OFFER @ \$$PRICE/hr"
# NVIDIA_DRIVER_CAPABILITIES=all is a no-op on images that bake in
# compute,utility (see STATUS above) but harmless — and it does matter
# on images where caps aren't pinned.
INSTANCE=$("$VASTAI" create instance "$OFFER" --image "$VAST_IMAGE" --disk "$VAST_DISK" --ssh --direct --env '-e NVIDIA_DRIVER_CAPABILITIES=all' --raw | jget 'd["new_contract"]')
echo "==> instance $INSTANCE"

cleanup() {
  if [ -z "$KEEP_INSTANCE" ]; then
    echo "==> destroying instance $INSTANCE"
    "$VASTAI" destroy instance "$INSTANCE" -y || true
  else
    echo "==> KEEP_INSTANCE set — instance $INSTANCE left running"
  fi
}
trap cleanup EXIT

# --- wait for ssh --------------------------------------------------------------
SSH_HOST=""; SSH_PORT=""
for i in $(seq 1 120); do
  sleep 10
  INFO=$("$VASTAI" show instance "$INSTANCE" --raw || true)
  STATUS=$(echo "$INFO" | jget 'd.get("actual_status","")' 2>/dev/null || echo "")
  if [ "$STATUS" = "running" ]; then
    # Prefer direct public_ipaddr + HostPort for 22/tcp — the
    # sshN.vast.ai proxy (ssh_host:ssh_port) intermittently dies during
    # banner exchange.
    SSH_HOST=$(echo "$INFO" | jget 'd.get("public_ipaddr") or d["ssh_host"]')
    SSH_PORT=$(echo "$INFO" | jget '(d.get("ports",{}).get("22/tcp") or [{}])[0].get("HostPort") or d["ssh_port"]')
    break
  fi
done
[ -n "$SSH_HOST" ] || { echo "instance never became running"; exit 1; }
echo "==> running: root@$SSH_HOST:$SSH_PORT"
"$VASTAI" attach ssh "$INSTANCE" "$(cat "$SSH_PUB")" >/dev/null 2>&1 || true

sshc() { ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o ConnectTimeout=10 -p "$SSH_PORT" "root@$SSH_HOST" "$@"; }
# NB: macOS ships rsync 2.6.9 — no --info=progress2.
rscp() { rsync -az -e "ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -p $SSH_PORT" "$@"; }

for i in $(seq 1 60); do
  sleep 5
  sshc true 2>/dev/null && break
done

# --- bootstrap ------------------------------------------------------------------
echo "==> bootstrapping"
# libx11-6/libxext6 are runtime deps of the real libGLX_nvidia (absent in
# the minimal cuda runtime image — without them the ICD lib can't even
# dlopen, which is one of the -9 causes on graphics-capable images).
sshc 'apt-get update -qq && apt-get install -y -qq --no-install-recommends openjdk-17-jdk-headless unzip ca-certificates curl rsync libvulkan1 libgomp1 libx11-6 libxext6 >/dev/null && mkdir -p /usr/share/vulkan/icd.d && { ls /usr/share/vulkan/icd.d/nvidia_icd*.json 2>/dev/null || ls /etc/vulkan/icd.d/nvidia_icd*.json 2>/dev/null || printf "%s\n" "{\"ICD\":{\"api_version\":\"1.3.0\",\"library_path\":\"libGLX_nvidia.so.0\"}}" > /usr/share/vulkan/icd.d/nvidia_icd.json; } && cd /root && curl -fsSL -o resrgan.zip https://github.com/xinntao/Real-ESRGAN/releases/download/v0.2.5.0/realesrgan-ncnn-vulkan-20220424-ubuntu.zip && unzip -oq resrgan.zip -d realesrgan && chmod +x realesrgan/realesrgan-ncnn-vulkan && realesrgan/realesrgan-ncnn-vulkan -i /dev/null -o /dev/null.png 2>&1 | head -6'

# --- ship data + tools ------------------------------------------------------------
echo "==> uploading pack + tools"
rscp "$HOME/realesrgan/realesrgan-bin/models/4x-UltraSharp-fp16.bin" \
     "$HOME/realesrgan/realesrgan-bin/models/4x-UltraSharp-fp16.param" \
     "root@$SSH_HOST:/root/realesrgan/models/"
rscp "$TOOL/upscale.sh" "$TOOL/BinarizeAlpha.java" "root@$SSH_HOST:/root/"
rscp "$SRC/" "root@$SSH_HOST:/root/pack-src/"

# --- run ----------------------------------------------------------------------------
echo "==> running upscale batch on the instance"
sshc "cd /root && REALESRGAN=/root/realesrgan/realesrgan-ncnn-vulkan REALESRGAN_MODELS=/root/realesrgan/models bash upscale.sh /root/pack-src /root/pack-out $*"

# --- fetch --------------------------------------------------------------------------
echo "==> downloading results"
rscp "root@$SSH_HOST:/root/pack-out/" "$DST/"
echo "==> done — results in $DST"
