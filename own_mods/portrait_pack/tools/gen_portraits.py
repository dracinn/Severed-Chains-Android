#!/usr/bin/env python3
"""Regenerate the 90s-anime HD portraits in ../mods/portraitpack/.

Locally generated with an SD 1.5 anime checkpoint (gsdf/Counterfeit-V2.5)
running on Apple Silicon via PyTorch MPS — no external services.

Characters with a usable 384px source go through img2img at low denoise so
the likeness survives the style transfer; Kongol (only a 48px source exists)
is txt2img since the pixel art anchors nothing but skin-tone/hair artefacts.

Setup:
  python3 -m venv /tmp/pgen
  /tmp/pgen/bin/pip install torch diffusers transformers accelerate safetensors pillow numpy

Usage:
  /tmp/pgen/bin/python gen_portraits.py [char ...]   # default: all
Outputs land in ../mods/portraitpack/ at 384x384.
"""

import os
import sys

os.environ.setdefault("PYTORCH_ENABLE_MPS_FALLBACK", "1")

import torch
from diffusers import StableDiffusionImg2ImgPipeline, StableDiffusionPipeline
from PIL import Image

MODEL = "gsdf/Counterfeit-V2.5"
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "mods", "portraitpack")

STYLE = (
    "1990s anime style, retro cel shaded anime, vintage ova aesthetic, "
    "hand painted, thick lineart, muted colors, slight grain, portrait, "
    "head and shoulders, dark gradient background"
)
NEG = (
    "3d, realistic, photo, cgi, modern anime style, moe, glossy, shiny, "
    "blurry, extra limbs, bad anatomy, deformed, text, watermark, "
    "signature, lowres"
)
NEG_HAIR = NEG + ", hair, long hair, two people, multiple heads"

CHARS = {
    "dart":    "young man with spiky blonde hair and red headband, red armor, determined green eyes",
    "lavitz":  "young knight with short brown hair, red and white armor, kind brown eyes",
    "shana":   "young woman with long brown hair, white and pink dress, gentle brown eyes",
    "rose":    "stern woman with long dark purple hair, dark armor, cold violet eyes",
    "haschel": "elderly martial artist man with grey hair and mustache, orange robe, sharp eyes",
    "albert":  "young royal knight with wavy blonde hair, gold circlet, green and white armor",
    "meru":    "cheerful young woman with long silver hair, red dress and headband, playful smile",
    "miranda": "young noblewoman with blonde hair and gold tiara, white outfit, stern blue eyes",
}
DENOISE = 0.55
SEED = 4242

# Kongol: 48px source only -> txt2img. Trait-critical words go first so they
# survive the 77-token CLIP truncation.
KONGOL_PROMPT = (
    "bald man, dark skin, huge muscular warrior, (completely bald head:1.3), "
    "fierce scowl, scar, heavy brow, square jaw, gold earring, red armor, "
    "1990s anime style, retro cel shaded, hand painted, thick lineart, "
    "portrait, dark background"
)
KONGOL_SEED = 73  # seed that produced the shipped portrait


def load_i2i():
    pipe = StableDiffusionImg2ImgPipeline.from_pretrained(
        MODEL, dtype=torch.float16, safety_checker=None, requires_safety_checker=False,
    ).to("mps")
    pipe.enable_attention_slicing()
    pipe.set_progress_bar_config(disable=True)
    return pipe


def load_t2i():
    pipe = StableDiffusionPipeline.from_pretrained(
        MODEL, dtype=torch.float16, safety_checker=None, requires_safety_checker=False,
    ).to("mps")
    pipe.enable_attention_slicing()
    pipe.set_progress_bar_config(disable=True)
    return pipe


def main():
    names = sys.argv[1:] or [*CHARS, "kongol"]
    i2i = t2i = None
    for name in names:
        out = os.path.join(OUT, f"{name}.png")
        if name == "kongol":
            t2i = t2i or load_t2i()
            img = t2i(
                prompt=KONGOL_PROMPT, negative_prompt=NEG_HAIR,
                height=512, width=512, guidance_scale=7.5,
                num_inference_steps=35,
                generator=torch.Generator("mps").manual_seed(KONGOL_SEED),
            ).images[0]
        else:
            i2i = i2i or load_i2i()
            src = Image.open(out).convert("RGB").resize((512, 512), Image.LANCZOS)
            img = i2i(
                prompt=f"{STYLE}, {CHARS[name]}", negative_prompt=NEG,
                image=src, strength=DENOISE, guidance_scale=7.0,
                num_inference_steps=30,
                generator=torch.Generator("mps").manual_seed(SEED),
            ).images[0]
        img.resize((384, 384), Image.LANCZOS).save(out)
        print(f"{name}: done", flush=True)


if __name__ == "__main__":
    main()
