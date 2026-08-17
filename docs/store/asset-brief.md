# Arrowz Browser — brief for the store and launcher assets

## Where the set stands

Five of the six frames came back at exactly the declared size and are in the
repository. One is still open, and it is the square one.

| Asset | Status |
|---|---|
| `launcher-banner-640x360` | In, as `app/src/main/res/drawable-xhdpi/banner.png` |
| `play-tv-banner-1280x720` | In, as `docs/store/tv-banner-1280x720.png` |
| `play-feature-graphic-1024x500` | In, as `docs/store/feature-graphic-1024x500.png` |
| `play-icon-512` | In as a placeholder, and needs redrawing with the one below |
| `adaptive-icon-foreground-432` | Not usable yet — see below |
| `screenshot-1920x1080` | Came back as key art rather than a screenshot |

The wide artwork is right. The accent samples as `#FF0055` exactly, which is the
value already in the app, so nothing in the palette had to move.

The 1920×1080 is key art, not a screenshot, so it is kept as
`docs/store/key-art-1920x1080.png`. Play wants frames of the app actually
running for that slot, and those already exist in `docs/store/`. The key art is
worth having anyway.

### The square icon, which is one problem and not two

`play-icon-512` and `adaptive-icon-foreground-432` are the same image at two
sizes — comparing them pixel for pixel gives a mean difference of 0.8 out of
255. So they need one fix between them, not two.

That image is a crop of the wide composition, and the crop lands mid-wordmark,
so the square assets show a lone "A" up in the top left with the petals cut off
at the right and bottom edges. Two things follow.

**The A does not survive the launcher mask.** Measured against the 66% safe
circle: 31% of the glyph's pixels fall outside it, and its furthest corner
reaches 179px from centre where the limit is 143. On any launcher drawing a
circle, the top left of the A is sliced off.

**The foreground layer is fully opaque**, alpha 255 across all 432×432. An
adaptive foreground has to be transparent where the background should show,
because the launcher composites the two layers and slides them against each
other during the open animation. Opaque means the background layer never
appears and the parallax reveals a hard edge.

So the square mark wants composing square from the start rather than cropped
out of the banner: the petal cluster centred, the wordmark either dropped or
replaced by a single centred letterform, everything inside the circle, and the
432 exported on transparency.

### The tile is darker than the shelf it sits on

The banner is installed and drawn correctly on a real television. Sitting in
the Apps grid, though, it is the least separated tile on the screen. Mean
luminance of each tile against the launcher's black background:

| Tile | Luminance | Separation |
|---|---|---|
| Crunchyroll | 83.7 | +83.7 |
| Twitch | 69.5 | +69.5 |
| NoMercy TV | 23.5 | +23.5 |
| **Arrowz** | **10.0** | **+10.0** |

The launcher draws no border and no card behind a banner, so a tile this dark
reads as a gap in the row rather than as an app. Even NoMercy TV, which is
itself a dark tile, has better than twice the separation.

The wordmark and the petals are fine. It is the ground that disappears: it
samples at roughly `#160007`, which against black is nearly nothing. Lifting
the ground, or carrying the accent further across the composition, would fix it
without changing the design. Worth seeing on a screen across the room before
deciding how far to push it.

## The frames

Six frames, in `docs/store/frames/`. Drag the SVGs into Figma and each arrives as a frame at exactly the right size, filled with the mark's darkest ground and carrying its safe area as a dashed guide in a group named so it is obvious the guide goes before export.

Both the fill and the guide are there to be replaced. The fill exists because Figma sizes an imported frame to the bounds of its content rather than to the declared width and height, so a frame carrying only an inset guide imports at the size of that guide instead of the size it is supposed to be.

## The thing to change first: no corner radius

Every one of these is drawn to the frame edge with square corners.

The current exports carry their own rounded card, and that is what is going wrong. Google rounds the icon itself, with a mask that changes shape between devices and launchers, and it animates that mask. Artwork that is already rounded gets rounded a second time, so the corners come out either doubled or clipped depending on whose launcher is drawing it, and there is a dark rim where the artwork's own corner falls inside the platform's.

The same applies to the television tile. It sits in a row where the system draws the shape, so a mark with its own radius reads as a card floating inside a card.

So: fill the frame, square corners, and let the platform do the rounding. What we lose is control of the corner, which was never ours; what we gain is the mark reaching the edges on every device instead of shrinking away from them.

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
