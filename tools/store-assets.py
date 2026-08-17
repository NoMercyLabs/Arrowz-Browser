#!/usr/bin/env python3
# Copyright (c) 2026 NoMercy Labs
# SPDX-License-Identifier: MIT
"""Renders every raster brand asset from the two Arrowz logos.

The logos are the source, not a description of them. An earlier version of this
file drew the mark itself from a written description, which was right while the
mark was ours to describe and is wrong now that a designer owns it: the only way
the launcher tile, the store banner and the icon cannot drift apart is if all
three come out of the same export.

    docs/store/tv-logo.png      1792x1024, the wide lockup, used for banners
    docs/store/store-logo.png   1024x1024, the square mark, used for icons

Both arrive as a rounded card on transparency, so anything that must be opaque
is composited onto the mark's own darkest ground rather than left with a
transparent corner. Play rejects a launcher icon with see-through corners, and a
television row draws one over whatever the launcher's wallpaper happens to be.

    python tools/store-assets.py
"""

from __future__ import annotations

import os
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

TV_LOGO = os.path.join(ROOT, "docs", "store", "tv-logo.png")
STORE_LOGO = os.path.join(ROOT, "docs", "store", "store-logo.png")

# The mark's own darkest ground, sampled from the exports themselves. Tokens.kt
# carries the same value as Palettes.Dark.surface.
GROUND = (9, 1, 4, 255)

# (logo, width, height, fill, opaque, path)
#
# `fill` is the fraction of the frame the logo may occupy. The wide lockup is
# 1.75:1 and a television banner is 16:9, so filling leaves only a hairline of
# ground at the sides. The feature graphic is the one place a margin is wanted:
# it sits at the top of a store page rather than in a row of tiles.
ASSETS = [
    (TV_LOGO, 1280, 720, 1.0, True, "docs/store/tv-banner-1280x720.png"),
    (TV_LOGO, 640, 360, 1.0, True, "app/src/main/res/drawable-xhdpi/banner.png"),
    (TV_LOGO, 1024, 500, 0.92, True, "docs/store/feature-graphic-1024x500.png"),
    (STORE_LOGO, 512, 512, 1.0, True, "docs/store/icon-512.png"),
    # The adaptive foreground keeps its transparency and sits inside the safe
    # circle, because the launcher applies its own mask and would otherwise cut
    # the corners off the mark's card. The background comes from
    # @color/icon_background.
    (STORE_LOGO, 432, 432, 0.66, False, "app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.png"),
]


def render(logo_path: str, width: int, height: int, fill: float, opaque: bool, out: str) -> None:
    logo = Image.open(logo_path).convert("RGBA")

    # Contain, never cover: the lockup carries its own padding and cropping it
    # to fill a differently-shaped frame cuts the wordmark.
    scale = min(width * fill / logo.width, height * fill / logo.height)
    scaled = logo.resize(
        (max(1, round(logo.width * scale)), max(1, round(logo.height * scale))),
        Image.LANCZOS,
    )

    canvas = Image.new("RGBA", (width, height), GROUND if opaque else (0, 0, 0, 0))
    canvas.alpha_composite(scaled, ((width - scaled.width) // 2, (height - scaled.height) // 2))
    if opaque:
        canvas = canvas.convert("RGB")

    os.makedirs(os.path.dirname(out), exist_ok=True)
    canvas.save(out)
    print(f"  {os.path.relpath(out, ROOT)}  {width}x{height}  {'opaque' if opaque else 'alpha'}")


def main() -> None:
    for path in (TV_LOGO, STORE_LOGO):
        if not os.path.exists(path):
            sys.exit(f"Missing {os.path.relpath(path, ROOT)} — the logos are the source for everything here.")

    print("Rendering from the Arrowz logos:")
    for logo, width, height, fill, opaque, target in ASSETS:
        render(logo, width, height, fill, opaque, os.path.join(ROOT, target))


if __name__ == "__main__":
    main()
