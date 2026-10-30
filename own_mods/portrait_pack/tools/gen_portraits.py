#!/usr/bin/env python3
"""Generate HD portraits from official Sony promotional media references.

Produces generated (not cropped/upscaled) 384px portraits in the late-90s
pre-rendered CG style using low-denoise img2img. Eight characters use the
official Sony CG profile crops from the legendofdragoon.org fan kit as
structural references; Miranda is absent from that kit and uses her correct
official portrait (blonde, red/copper forehead guard) from wikia, restored 4x
with Real-ESRGAN before img2img so the generator sees real edges instead of
pixel blocks. The RPGFan "Artwork-022" image is Lenus, not Miranda, and is
never a source.

Setup:
  python3 -m venv /tmp/pgen
  /tmp/pgen/bin/pip install torch diffusers transformers accelerate safetensors pillow spandrel
  /tmp/pgen/bin/python gen_portraits.py [character ...]   # default: all 9

The DreamShaper 8 checkpoint downloads on first run. Exact winning
strengths/seeds are recorded in CONFIG/MIRANDA below so outputs reproduce.
"""
import io
import os
import sys
import urllib.request
import zipfile
from pathlib import Path

os.environ.setdefault("PYTORCH_ENABLE_MPS_FALLBACK", "1")

import torch
from PIL import Image
from diffusers import StableDiffusionImg2ImgPipeline

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "mods" / "portraitpack"

MODEL = "Lykon/dreamshaper-8"
FAN_KIT_URL = "https://legendofdragoon.org/download/fankits/discord-profile-pics.zip"
MIRANDA_URL = "https://static.wikia.nocookie.net/legendofdragoon/images/5/5f/Miranda_portrait.png/revision/latest?cb=20150605035748"

SOURCE_SIZE = 512
OUTPUT_SIZE = 384

PROMPT = ("late 1990s Sony PlayStation pre-rendered CGI portrait, The Legend of Dragoon, "
          "faithful official character design, head and shoulders, painted low-poly CGI textures, "
          "dramatic studio lighting, dark neutral background")
MIRANDA_PROMPT = ("late 1990s Sony PlayStation pre-rendered CGI portrait, The Legend of Dragoon, "
                  "faithful official Miranda design, mature woman, long golden-blonde hair, blue eyes, "
                  "red copper forehead guard, red copper armor, white sleeves, stern, head and shoulders, "
                  "dark studio background")
NEGATIVE = ("generic anime, cel shading, manga, modern photo, redesign, different face, "
            "different hairstyle, different costume, text, watermark, duplicate face, deformed")
MIRANDA_NEGATIVE = ("Lenus, Wingly, silver hair, blue hair, tiara, crown, generic blonde, "
                    "modern photo, modern anime, cel shading, redesign, different face, "
                    "different costume, missing headband, child, smile, text, "
                    "duplicate face, deformed")

# name -> (fan-kit member, strength, seed, traits)
CONFIG = {
    "dart": ("Char_Dart.png", 0.22, 727, "Dart Feld, spiky blond hair, red headband, blue-green eyes, red armor"),
    "lavitz": ("Char_Lavitz.png", 0.22, 727, "Lavitz Slambert, sandy-blond swept hair, blue eyes, green knight armor, stern loyal knight"),
    "shana": ("Char_Shana.png", 0.22, 404, "Shana, young woman, long chestnut-brown hair, brown eyes, white and pink clothing, gentle expression"),
    "rose": ("Char_Rose.png", 0.22, 1900, "Rose, mature woman, long black-purple hair, violet eyes, dark red-black armor, stern expression"),
    "haschel": ("Char_Haschel.png", 0.22, 404, "Haschel, elderly martial artist, swept white hair, white moustache and beard, violet clothing, sharp eyes"),
    "albert": ("Char_Albert.png", 0.22, 1900, "King Albert, young man, swept sandy-blond hair, blue eyes, green armor, composed royal expression"),
    "meru": ("Char_Meru.png", 0.22, 727, "Meru, young Wingly woman, pale blue-white hair, red eyes, red clothing, cheerful expression"),
    "kongol": ("Char_Kongol.png", 0.22, 1900, "Kongol, enormous dark-skinned Giganto warrior, black mohawk, red markings over both eyes, white irises, heavy brow, gold shoulder armor"),
}
# Miranda ships as strength 0.26 / seed 1900 from the restored-reference
# pipeline; the cpu generator is part of the shipped selection, don't change it.
MIRANDA = (0.26, 1900, "Miranda, mature woman, long golden-blonde hair, blue eyes, red copper forehead guard, red copper armor, white sleeves, stern")

REALESR_URL = "https://github.com/xinntao/Real-ESRGAN/releases/download/v0.1.0/RealESRGAN_x4plus.pth"
MODEL_CACHE = Path(os.environ.get("PORTRAIT_MODEL_CACHE", Path.home() / ".cache" / "gen_portraits"))

UA = {"User-Agent": "Severed-Chains-Android portrait asset builder"}


def fetch(url: str) -> bytes:
    req = urllib.request.Request(url, headers=UA)
    with urllib.request.urlopen(req) as resp:
        return resp.read()


def gradient(size: int = SOURCE_SIZE) -> Image.Image:
    im = Image.new("RGB", (size, size))
    px = im.load()
    c = size / 2
    for y in range(size):
        for x in range(size):
            d = ((x - c) ** 2 + (y - c) ** 2) ** 0.5 / (size * 0.7)
            v = max(0.0, 1.0 - d)
            k = int(30 + 45 * v)
            px[x, y] = (k, k - 4, k - 8)
    return im


def fan_kit_canvas(ref: Image.Image) -> Image.Image:
    """Official 128 RGBA profile crop scaled to fit 480x480, centered on the
    dark radial canvas, preserving alpha and composition."""
    ref = ref.convert("RGBA")
    w, h = ref.size
    s = 480 / max(w, h)
    nw, nh = round(w * s), round(h * s)
    ref = ref.resize((nw, nh), Image.Resampling.LANCZOS)
    canvas = gradient()
    canvas.paste(ref, ((SOURCE_SIZE - nw) // 2, (SOURCE_SIZE - nh) // 2), ref)
    return canvas


def restore_realesrgan(ref: Image.Image) -> Image.Image:
    """4x neural restoration of a tiny official reference (Miranda's 84x96
    portrait) before img2img. Falls back to LANCZOS if spandrel is missing."""
    try:
        import numpy as np
        from spandrel import ModelLoader
    except ImportError:
        print("spandrel not installed; falling back to LANCZOS upscale "
              "(output will differ from the shipped asset)")
        w, h = ref.size
        return ref.resize((w * 4, h * 4), Image.Resampling.LANCZOS)

    weights = MODEL_CACHE / "RealESRGAN_x4plus.pth"
    if not weights.exists():
        MODEL_CACHE.mkdir(parents=True, exist_ok=True)
        weights.write_bytes(fetch(REALESR_URL))
    sr = ModelLoader().load_from_file(str(weights)).to("mps").eval()
    src = np.asarray(ref.convert("RGB"), dtype=np.float32) / 255.0
    t = torch.from_numpy(src).permute(2, 0, 1).unsqueeze(0).to("mps")
    with torch.no_grad():
        out = sr(t)
    out = out.squeeze(0).permute(1, 2, 0).clamp(0, 1).cpu().numpy()
    return Image.fromarray((out * 255).astype(np.uint8))


def miranda_canvas(ref: Image.Image) -> Image.Image:
    """Restored RGB portrait scaled to max 470x480, centred horizontally
    and bottom-aligned with 16px padding on the dark radial canvas."""
    ref = ref.convert("RGB")
    w, h = ref.size
    s = min(470 / w, 480 / h)
    nw, nh = round(w * s), round(h * s)
    ref = ref.resize((nw, nh), Image.Resampling.LANCZOS)
    canvas = gradient()
    canvas.paste(ref, ((SOURCE_SIZE - nw) // 2, SOURCE_SIZE - 16 - nh))
    return canvas


def main() -> None:
    names = sys.argv[1:] or list(CONFIG) + ["miranda"]
    unknown = [n for n in names if n not in CONFIG and n != "miranda"]
    if unknown:
        sys.exit(f"unknown character(s): {', '.join(unknown)} "
                 f"(valid: {', '.join(list(CONFIG) + ['miranda'])})")

    OUT.mkdir(parents=True, exist_ok=True)
    pipe = StableDiffusionImg2ImgPipeline.from_pretrained(
        MODEL, variant="fp16", torch_dtype=torch.float16,
        safety_checker=None, requires_safety_checker=False)
    pipe.to("mps")
    pipe.enable_attention_slicing()
    pipe.set_progress_bar_config(disable=True)

    kit = None
    for name in names:
        if name == "miranda":
            strength, seed, _ = MIRANDA
            ref = restore_realesrgan(Image.open(io.BytesIO(fetch(MIRANDA_URL))))
            canvas = miranda_canvas(ref)
            prompt, negative = MIRANDA_PROMPT, MIRANDA_NEGATIVE
            steps, guidance, source = 40, 7.0, MIRANDA_URL
            gen = torch.Generator("cpu").manual_seed(seed)
        else:
            member, strength, seed, traits = CONFIG[name]
            if kit is None:
                kit = zipfile.ZipFile(io.BytesIO(fetch(FAN_KIT_URL)))
            ref = Image.open(io.BytesIO(kit.read(member)))
            canvas = fan_kit_canvas(ref)
            prompt, negative = PROMPT + ", " + traits, NEGATIVE
            steps, guidance = 35, 6.5
            source = f"{FAN_KIT_URL}#{member}"
            gen = torch.Generator("mps").manual_seed(seed)
        img = pipe(prompt=prompt, negative_prompt=negative, image=canvas,
                   strength=strength, guidance_scale=guidance,
                   num_inference_steps=steps, generator=gen).images[0]
        img = img.convert("RGB").resize((OUTPUT_SIZE, OUTPUT_SIZE), Image.Resampling.LANCZOS)
        img.save(OUT / f"{name}.png")
        print(f"{name}: source={source} strength={strength} seed={seed} -> {OUT / (name + '.png')}")


if __name__ == "__main__":
    main()
