#!/bin/bash
# Upscale extracted submap pack PNGs with RealESRGAN.
#
# For each <bin>/<fileIndex> variant: the background is upscaled directly;
# foreground cutouts run the alpha pipeline from BinarizeAlpha.java —
# rgb-fill under transparency BEFORE upscaling (alpha bleed so the AI
# doesn't drag matte-edge black into content), then post-upscale alpha
# binarize + matte dilation (the engine's shader discards on binary alpha
# and needs rgb=0 under a==0).
#
# usage: upscale.sh <extractDir> <outDir> <bin/index> [<bin/index> ...]
#
# env overrides:
#   REALESRGAN   path to realesrgan-ncnn-vulkan (default /tmp/realesrgan/…)
#   REALESRGAN_MODELS  model dir (default $REALESRGAN's sibling models/)
#   MODEL        model name (default realesrgan-x4plus — the general/real-
#                istic model; realesr-animevideov3 is softer/painterly)
#   SCALE        upscale factor (default 4)
#   THRESHOLD    alpha binarize threshold 0-255 (default 128)
#   DILATE       matte dilation px at output scale (default 2)

set -euo pipefail

SRC=$1; DST=$2; shift 2

REALESRGAN=${REALESRGAN:-/tmp/realesrgan/realesrgan-ncnn-vulkan}
REALESRGAN_MODELS=${REALESRGAN_MODELS:-$(dirname "$REALESRGAN")/models}
MODEL=${MODEL:-realesrgan-x4plus}
SCALE=${SCALE:-4}
THRESHOLD=${THRESHOLD:-128}
DILATE=${DILATE:-2}
TOOL="$(cd "$(dirname "$0")" && pwd)/BinarizeAlpha.java"

for spec in "$@"; do
  bin=${spec%/*}; idx=${spec#*/}

  mkdir -p "$DST/bg/$bin"
  "$REALESRGAN" -i "$SRC/bg/$bin/$idx.png" -o "$DST/bg/$bin/$idx.png" \
    -n "$MODEL" -m "$REALESRGAN_MODELS" -s "$SCALE" >/dev/null

  if [ -d "$SRC/fg/$bin/$idx" ]; then
    work=$(mktemp -d)
    cp "$SRC/fg/$bin/$idx/"*.png "$work/"
    java "$TOOL" fill "$work" >/dev/null
    mkdir -p "$DST/fg/$bin/$idx"
    for f in "$work/"*.png; do
      "$REALESRGAN" -i "$f" -o "$DST/fg/$bin/$idx/$(basename "$f")" \
        -n "$MODEL" -m "$REALESRGAN_MODELS" -s "$SCALE" >/dev/null
    done
    java "$TOOL" bin "$DST/fg/$bin/$idx" "$THRESHOLD" "$DILATE" >/dev/null
    rm -rf "$work"
  fi

  echo "done $spec"
done
echo "ALL DONE"
