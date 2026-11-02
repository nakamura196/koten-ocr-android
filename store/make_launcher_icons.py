#!/usr/bin/env python3
"""Make the Android launcher icons from the iOS app icon (the vermilion 典 on washi).

Usage: python3 store/make_launcher_icons.py <iOS AppIcon.png> <app/src/main/res>

Writes, for mdpi..xxxhdpi:
  mipmap-*/ic_launcher_foreground.png  adaptive-icon foreground (108 dp canvas)
  mipmap-*/ic_launcher_background.png  adaptive-icon background (paper gradient)
  mipmap-*/ic_launcher.png             legacy square icon (48 dp)
  mipmap-*/ic_launcher_round.png       legacy round icon (48 dp)

An adaptive icon shows only the middle 72 of its 108 dp and a launcher may cut it to a
circle, so the iOS icon is drawn at ICON_DP in the middle of the foreground with its rim faded
out, over a background of the same paper colours. The corner brackets then stay inside the circle.
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
CANVAS_DP = 108
ICON_DP = 74
LEGACY_DP = 48
# Paper colours at the top and bottom edge of the iOS icon.
TOP = (241, 232, 213)
BOTTOM = (232, 218, 192)


def gradient(size):
    im = Image.new("RGB", (size, size))
    d = ImageDraw.Draw(im)
    for y in range(size):
        t = y / max(1, size - 1)
        d.line([(0, y), (size, y)], fill=tuple(round(a + (b - a) * t) for a, b in zip(TOP, BOTTOM)))
    return im


def faded(icon, size):
    """The icon at `size` px with its outer rim fading to transparent (no seam on the background)."""
    im = icon.resize((size, size), Image.LANCZOS).convert("RGBA")
    rim = max(2, size // 16)
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rectangle([rim, rim, size - rim - 1, size - rim - 1], fill=255)
    mask = mask.filter(ImageFilter.GaussianBlur(rim / 2))
    im.putalpha(mask)
    return im


def main(src, res):
    icon = Image.open(src).convert("RGB")
    res = Path(res)
    for name, k in DENSITIES.items():
        out = res / f"mipmap-{name}"
        out.mkdir(parents=True, exist_ok=True)
        canvas = round(CANVAS_DP * k)
        fg = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
        s = round(ICON_DP * k)
        fg.alpha_composite(faded(icon, s), ((canvas - s) // 2, (canvas - s) // 2))
        fg.save(out / "ic_launcher_foreground.png")
        gradient(canvas).save(out / "ic_launcher_background.png")

        legacy = round(LEGACY_DP * k)
        sq = icon.resize((legacy, legacy), Image.LANCZOS).convert("RGBA")
        corner = Image.new("L", (legacy, legacy), 0)
        ImageDraw.Draw(corner).rounded_rectangle([0, 0, legacy - 1, legacy - 1], radius=legacy // 6, fill=255)
        sq.putalpha(corner)
        sq.save(out / "ic_launcher.png")
        rd = icon.resize((legacy, legacy), Image.LANCZOS).convert("RGBA")
        circle = Image.new("L", (legacy, legacy), 0)
        ImageDraw.Draw(circle).ellipse([0, 0, legacy - 1, legacy - 1], fill=255)
        rd.putalpha(circle)
        rd.save(out / "ic_launcher_round.png")
        print(out, canvas, legacy)


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2])
