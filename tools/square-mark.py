"""Builds the square mark the launcher needs, from the wide lockup we have.

The delivered square assets are a crop of the wide composition. The crop lands
mid-wordmark, so they show a lone "A" in the top left with the petals cut off at
the right and bottom: 31% of that glyph falls outside the adaptive icon's 66%
safe circle, and the foreground layer is fully opaque where it has to be
transparent. Both sizes are the same image, so they are one problem.

Nothing here is invented. The mark is a four-fold petal cluster and exactly one
petal survives the wide lockup uncut, so the other three are that same petal
rotated about the centre. The result is the designer's own shape, arranged the
way the artwork already arranges it.

Regenerate with:  python tools/square-mark.py
"""

from __future__ import annotations

import os

from PIL import Image, ImageChops, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SOURCE = os.path.join(ROOT, "docs", "store", "tv-logo.png")

# The one uncut petal in the wide lockup, tip up and base down.
PETAL_BOX = (1320, 150, 1640, 600)

GROUND = (9, 1, 4, 255)

# Softens the mask edge so the petal does not end on a hard ellipse.
EDGE_FEATHER = 9

# 66% of the frame, which is all a launcher guarantees to draw. The cluster is
# sized to this rather than to the frame, so no petal is masked away.
SAFE_FRACTION = 0.66


def cluster(size: int) -> Image.Image:
    """The four-petal mark on black, at `size` square."""
    petal = Image.open(SOURCE).convert("RGB").crop(PETAL_BOX)

    # An ellipse, not a luminance threshold. The petal sits on #160007 rather
    # than black, so lightening four overlapping crops stamps their rectangles
    # across the mark - but the petal's own body contains darks as deep as that
    # ground, and keying on brightness punched holes through the middle of it.
    # The corners of the crop are the only part that is certainly not petal, so
    # the shape is what gets masked.
    mask = Image.new("L", petal.size, 0)
    ImageDraw.Draw(mask).ellipse((0, 0, petal.width - 1, petal.height - 1), fill=255)
    mask = mask.filter(ImageFilter.GaussianBlur(EDGE_FEATHER))
    petal = Image.composite(petal, Image.new("RGB", petal.size, (0, 0, 0)), mask)

    # Worked at 4x and reduced once at the end, so the rotations do not soften
    # the edges four separate times.
    work = size * 4
    canvas = Image.new("RGB", (work, work), (0, 0, 0))

    reach = int(work * SAFE_FRACTION / 2)
    scaled = petal.resize((int(reach * 0.72), reach), Image.LANCZOS)

    upright = Image.new("RGB", (work, work), (0, 0, 0))
    upright.paste(scaled, ((work - scaled.width) // 2, work // 2 - scaled.height))

    for turn in range(4):
        # Lighten, not paste: every petal carries the near-black ground it was
        # cut from, and compositing that ground normally would stamp four
        # visible boxes across the mark.
        canvas = ImageChops.lighter(canvas, upright.rotate(turn * 90, resample=Image.BICUBIC))

    return canvas.resize((size, size), Image.LANCZOS)


def write_adaptive_foreground(path: str, size: int = 432) -> None:
    """Transparent where the background layer should show through."""
    mark = cluster(size)
    # The ground the petals were cut from becomes the transparency, so the
    # launcher's own background layer shows and the parallax has nothing to
    # reveal.
    alpha = mark.convert("L").point(lambda level: min(255, level * 3))
    out = mark.convert("RGBA")
    out.putalpha(alpha)
    out.save(path, optimize=True)


def write_store_icon(path: str, size: int = 512) -> None:
    """Opaque, square-cornered. Play rejects transparency here."""
    out = Image.new("RGBA", (size, size), GROUND)
    out.alpha_composite(cluster(size).convert("RGBA"))
    out.convert("RGB").save(path, optimize=True)


if __name__ == "__main__":
    store = os.path.join(ROOT, "docs", "store")
    res = os.path.join(ROOT, "app", "src", "main", "res")

    write_store_icon(os.path.join(store, "icon-512.png"))
    write_adaptive_foreground(
        os.path.join(res, "mipmap-xxxhdpi", "ic_launcher_foreground.png")
    )
    print("wrote icon-512.png and ic_launcher_foreground.png")
