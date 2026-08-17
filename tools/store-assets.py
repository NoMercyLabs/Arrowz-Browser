#!/usr/bin/env python3
# Copyright (c) 2026 NoMercy Labs
# SPDX-License-Identifier: MIT
"""Draws every raster brand asset from one description of the mark.

The mark is a globe wearing the app's own focus ring. That is not decoration:
the focus ring is this browser's entire visual language, so an icon showing the
web inside a focus ring says exactly what the product is. Cleverer ideas were
rejected on legibility -- a D-pad silhouette fusing into a pointer reads
beautifully at 512px and turns to mush at three metres in a launcher row, which
is the only size that matters.

Generated rather than hand-drawn so the launcher tile, the store banner and the
icon cannot drift apart, and so a palette change is one edit rather than five
exports. Run it after changing anything here:

    python tools/store-assets.py
"""

from __future__ import annotations

import os
import sys

from PIL import Image, ImageDraw, ImageFont

# The app's own palette, not the plan's earlier guess at one. Tokens.kt is the
# source; these are its dark values, which is what every one of these assets is
# drawn on.
BACKGROUND = (10, 10, 12)
ACCENT = (140, 163, 184)
GLOBE = (242, 242, 245)
MUTED = (154, 154, 166)

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# A wordmark needs a real typeface and Pillow ships none. Failing loudly beats
# falling back to the bitmap default, which renders the product name as
# something nobody would ship.
FONT_CANDIDATES = [
    "C:/Windows/Fonts/segoeuib.ttf",
    "C:/Windows/Fonts/arialbd.ttf",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
    "/System/Library/Fonts/Helvetica.ttc",
]


def font(size: int) -> ImageFont.FreeTypeFont:
    for path in FONT_CANDIDATES:
        if os.path.isfile(path):
            return ImageFont.truetype(path, size)
    sys.exit("No usable font found. Add one to FONT_CANDIDATES.")


def draw_mark(image: ImageDraw.ImageDraw, centre: tuple[int, int], radius: int) -> None:
    """The globe and the ring around it, scaled to one radius.

    Every proportion is derived from that radius so the mark is the same drawing
    at 48px and at 512px, rather than five drawings that happen to resemble each
    other.
    """
    x, y = centre
    ring_padding = int(radius * 0.55)
    ring_stroke = max(2, int(radius * 0.09))
    globe_stroke = max(2, int(radius * 0.07))
    corner = int(radius * 0.42)

    image.rounded_rectangle(
        [x - radius - ring_padding, y - radius - ring_padding,
         x + radius + ring_padding, y + radius + ring_padding],
        radius=corner,
        outline=ACCENT,
        width=ring_stroke,
    )

    image.ellipse([x - radius, y - radius, x + radius, y + radius],
                  outline=GLOBE, width=globe_stroke)
    # The meridian is what makes a circle read as a globe. Without it the mark is
    # a letter O in a box.
    image.ellipse([x - int(radius * 0.44), y - radius, x + int(radius * 0.44), y + radius],
                  outline=GLOBE, width=globe_stroke)
    image.line([x - radius, y, x + radius, y], fill=GLOBE, width=globe_stroke)


def beam(image: Image.Image, height_fraction: float = 0.55) -> None:
    """A soft diagonal lift across the background.

    A flat near-black rectangle reads as a placeholder waiting for artwork. This
    is the same trick the site tiles use, and it is what makes the banner sit
    beside the NoMercy tile in a launcher row rather than beneath it.
    """
    width, height = image.size
    overlay = Image.new("RGB", (width, height), BACKGROUND)
    pixels = overlay.load()
    for column in range(width):
        lift = int(18 * (1 - abs(column / width - 0.62) * 2.2))
        if lift <= 0:
            continue
        for row in range(int(height * (1 - height_fraction)), height):
            depth = (row - height * (1 - height_fraction)) / (height * height_fraction)
            value = int(lift * depth)
            pixels[column, row] = (
                min(255, BACKGROUND[0] + value),
                min(255, BACKGROUND[1] + value),
                min(255, BACKGROUND[2] + int(value * 1.3)),
            )
    image.paste(overlay, (0, 0))


def banner(width: int, height: int) -> Image.Image:
    """Wordmark left, glyph right, matching how the NoMercy TV tile is composed
    so the two sit in one launcher row without clashing."""
    image = Image.new("RGB", (width, height), BACKGROUND)
    beam(image)
    draw = ImageDraw.Draw(image)

    radius = int(height * 0.17)
    draw_mark(draw, (int(width * 0.78), height // 2), radius)

    title = font(int(height * 0.17))
    subtitle = font(int(height * 0.075))
    left = int(width * 0.08)
    draw.text((left, int(height * 0.36)), "NoMercy", font=title, fill=GLOBE)
    draw.text((left, int(height * 0.36) + int(height * 0.19)), "Browser", font=title, fill=ACCENT)
    draw.text((left, int(height * 0.78)), "Private browsing, built for the remote",
              font=subtitle, fill=MUTED)
    return image


def icon(size: int) -> Image.Image:
    image = Image.new("RGB", (size, size), BACKGROUND)
    draw = ImageDraw.Draw(image)
    draw_mark(draw, (size // 2, size // 2), int(size * 0.24))
    return image


def feature_graphic(width: int, height: int) -> Image.Image:
    image = Image.new("RGB", (width, height), BACKGROUND)
    beam(image)
    draw = ImageDraw.Draw(image)
    draw_mark(draw, (int(width * 0.22), height // 2), int(height * 0.21))

    title = font(int(height * 0.15))
    subtitle = font(int(height * 0.07))
    left = int(width * 0.40)
    draw.text((left, int(height * 0.30)), "NoMercy Browser", font=title, fill=GLOBE)
    draw.text((left, int(height * 0.52)), "No trackers. No accounts. Six keys.",
              font=subtitle, fill=ACCENT)
    return image


def write(image: Image.Image, *path: str) -> None:
    target = os.path.join(ROOT, *path)
    os.makedirs(os.path.dirname(target), exist_ok=True)
    image.save(target)
    print(f"{os.path.relpath(target, ROOT)}  {image.size[0]}x{image.size[1]}")


def main() -> None:
    # The launcher tile is declared in dp and served at xhdpi, so the file is
    # twice the 320x180 the manifest asks for.
    write(banner(640, 360), "app", "src", "main", "res", "drawable-xhdpi", "banner.png")
    write(banner(1280, 720), "docs", "store", "tv-banner-1280x720.png")
    write(icon(512), "docs", "store", "icon-512.png")
    write(feature_graphic(1024, 500), "docs", "store", "feature-graphic-1024x500.png")


if __name__ == "__main__":
    main()
