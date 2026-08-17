#!/usr/bin/env python3
# Copyright (c) 2026 NoMercy Labs
# SPDX-License-Identifier: MIT
"""Writes one correctly-sized SVG per Play and launcher asset, to import into Figma.

Figma turns an imported SVG into a frame named after the file and sized to its
viewport, so these arrive as empty frames at exactly the dimensions Google
accepts, rather than as a written list of numbers somebody has to type in.

Each frame carries its safe area as a dashed guide in a group named so it is
obvious it must go before export. The guides are the whole point: every one of
these assets has something that clips or masks it, and a mark drawn to the frame
edge without knowing where survives the crop by luck.

    python tools/play-frames.py
"""

from __future__ import annotations

import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "docs", "store", "frames")

GUIDE = "#FF0055"

# (filename, width, height, safe, note)
#
# `safe` is (inset_x, inset_y) in pixels, or a radius for the circular mask.
FRAMES = [
    (
        "play-icon-512", 512, 512, ("circle", 512 * 0.5),
        "Play store icon. Play draws its own rounded mask and shadow over this, "
        "so the artwork is a full-bleed square with square corners.",
    ),
    (
        "play-feature-graphic-1024x500", 1024, 500, ("inset", 96, 48),
        "Play feature graphic. Cropped differently across surfaces, so keep the "
        "wordmark inside the guide.",
    ),
    (
        "play-tv-banner-1280x720", 1280, 720, ("inset", 64, 36),
        "Play TV banner for the store listing.",
    ),
    (
        "launcher-banner-640x360", 640, 360, ("inset", 32, 18),
        "The android:banner tile, 320x180dp at xhdpi. This is the one on the "
        "home row of the television.",
    ),
    (
        "adaptive-icon-foreground-432", 432, 432, ("circle", 432 * 0.66),
        "Adaptive launcher icon foreground. The launcher masks this to a shape "
        "of its choosing and animates it, so only the inner circle is "
        "guaranteed visible. Transparent background: the colour comes from the "
        "background layer.",
    ),
    (
        "screenshot-1920x1080", 1920, 1080, ("inset", 96, 54),
        "Store screenshot. The guide is the 5% overscan a television may cut.",
    ),
]


def svg(name: str, width: int, height: int, safe, note: str) -> str:
    if safe[0] == "circle":
        radius = safe[1]
        guide = (
            f'    <circle cx="{width / 2:g}" cy="{height / 2:g}" r="{radius:g}"\n'
            f'            fill="none" stroke="{GUIDE}" stroke-width="2" stroke-dasharray="12 8"/>\n'
        )
    else:
        inset_x, inset_y = safe[1], safe[2]
        guide = (
            f'    <rect x="{inset_x}" y="{inset_y}" '
            f'width="{width - inset_x * 2}" height="{height - inset_y * 2}"\n'
            f'          fill="none" stroke="{GUIDE}" stroke-width="2" stroke-dasharray="12 8"/>\n'
        )

    return f"""<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}"
     viewBox="0 0 {width} {height}">
  <title>{name}</title>
  <desc>{note}</desc>
  <g id="DELETE BEFORE EXPORT - safe area">
{guide}  </g>
</svg>
"""


def main() -> None:
    os.makedirs(OUT, exist_ok=True)
    print("Frames to import into Figma:")
    for name, width, height, safe, note in FRAMES:
        path = os.path.join(OUT, f"{name}.svg")
        with open(path, "w", encoding="utf-8") as handle:
            handle.write(svg(name, width, height, safe, note))
        print(f"  {name}.svg  {width}x{height}")


if __name__ == "__main__":
    main()
