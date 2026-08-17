# Arrowz Browser — brief for the store and launcher assets

Six frames, in `docs/store/frames/`. Drag the SVGs into Figma and each arrives as an empty frame at exactly the right size, with its safe area drawn as a dashed guide in a group named so it is obvious the guide goes before export.

## The thing to change first: no corner radius

Every one of these is drawn to the frame edge with square corners.

The current exports carry their own rounded card, and that is what is going wrong. Google rounds the icon itself, with a mask that changes shape between devices and launchers, and it animates that mask. Artwork that is already rounded gets rounded a second time, so the corners come out either doubled or clipped depending on whose launcher is drawing it, and there is a dark rim where the artwork's own corner falls inside the platform's.

The same applies to the television tile. It sits in a row where the system draws the shape, so a mark with its own radius reads as a card floating inside a card.

So: fill the frame, square corners, and let the platform do the rounding. What we lose is control of the corner, which was never ours; what we gain is the mark reaching the edges on every device instead of shrinking away from them.

## The frames

| Frame | Size | Where it appears | Safe area |
|---|---|---|---|
| `play-icon-512` | 512 × 512 | Play listing, and the launcher | Inside the circle |
| `play-feature-graphic-1024x500` | 1024 × 500 | Top of the Play listing | Inside the dashed box |
| `play-tv-banner-1280x720` | 1280 × 720 | Play television listing | Inside the dashed box |
| `launcher-banner-640x360` | 640 × 360 | The home row of the television | Inside the dashed box |
| `adaptive-icon-foreground-432` | 432 × 432 | Launcher icon, foreground layer | Inside the circle |
| `screenshot-1920x1080` | 1920 × 1080 | Play listing, three needed | Inside the dashed box |

## What the safe areas actually mean

**The two circular ones are masks, not suggestions.** A launcher may draw the icon as a circle, a squircle, a rounded square or a teardrop, and it picks, not us. Anything outside the circle is not guaranteed to survive on any given device. The adaptive foreground's circle is deliberately tight — 66% of the frame — because the launcher also scales the layer during its open animation, so the outer third moves in and out of view.

**The rectangular ones are crops.** The feature graphic is re-cropped for different placements in the store, and the television banner and tile sit in rows where the surrounding chrome overlaps the edges. Keep the wordmark inside the guide and the composition can bleed past it.

**The screenshot guide is overscan.** A television may cut up to 5% off each edge, and older panels genuinely do.

## Transparency

`adaptive-icon-foreground-432` is the only one that keeps a transparent background — its colour comes from a separate background layer, which is currently the mark's darkest ground, `#090104`.

Every other frame must export fully opaque. Play rejects an icon with transparency, and a transparent television tile is drawn over whatever wallpaper the viewer has set.

## The palette, as sampled from the current logos

| Colour | Role |
|---|---|
| `#FF0055` | The accent, and the focus ring on dark |
| `#FE2970` | The lighter partner in the gradients |
| `#990033` | The deep shade, and the focus ring on light |
| `#1F000A` | The raised ground |
| `#090104` | The darkest ground |

Two notes from putting these into the app, in case they change what you would draw.

`#FF0055` cannot be used on a light background: it reaches 2.55:1 where a focus indicator needs 3. Light mode uses `#990033` instead, which reaches 5.71:1. And text sitting on top of `#FF0055` has to be the dark `#1F000A` rather than white — 5.07:1 against 3.90:1, which is the opposite of what a red fill usually wants.

## The wordmark

The lockup is "Arrowz" over "browser" in blackletter. At the tile size on a television, "browser" is around 14 real pixels tall and blackletter is the hardest face to read small. Worth checking that second line at 100% on a screen across the room, and dropping it if it turns to texture — the square mark already does without it.
