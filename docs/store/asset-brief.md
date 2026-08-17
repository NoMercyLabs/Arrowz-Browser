# Arrowz Browser — brief for the store and launcher assets

## Where the set stands

All six slots are filled with the designer's own artwork. Nothing here is
generated or reconstructed any more.

| Asset | Status |
|---|---|
| `launcher-banner-640x360` | In, as `app/src/main/res/drawable-xhdpi/banner.png` |
| `play-tv-banner-1280x720` | In, as `docs/store/tv-banner-1280x720.png` |
| `play-feature-graphic-1024x500` | In, as `docs/store/feature-graphic-1024x500.png` |
| `play-icon-512` | The delivered `store-logo.png`, reduced once from 1024 |
| `adaptive-icon-foreground-432` | In, as delivered, at five launcher densities |
| `screenshot-1920x1080` | Slot filled by three real captures; the delivered art is kept as `key-art-1920x1080.png` |

The wide artwork is right. The accent samples as `#FF0055` exactly, which is the
value already in the app, so nothing in the palette had to move.

The 1920×1080 is key art, not a screenshot, so it is kept as
`docs/store/key-art-1920x1080.png`. Play wants frames of the app actually
running for that slot, and those already exist in `docs/store/`. The key art is
worth having anyway.

### The square icon

The delivered artwork is used as delivered. `docs/store/icon-512.png` is
`store-logo.png` reduced once from 1024 with its transparent corners flattened
onto `#090104`, because Play rejects any alpha in that slot. The adaptive
foreground ships as delivered at five launcher densities, so no device
downscales a 432 image at draw time.

One measured consequence, recorded because it decides what a phone launcher
draws rather than what the file looks like. The foreground is fully opaque, all
186,624 pixels, so 65.8% of it lies outside the 66% safe circle that a circular
mask keeps. On a launcher drawing a circle, the corners go, and the "A" is in
one of them. Televisions use the banner rather than this icon, so the effect is
confined to phones and tablets.

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

## Phone screenshots, for an app phones cannot install

Play requires two phone screenshots on the base listing even though this app
declares `android.software.leanback` and its device catalogue excludes phones.
`docs/store/phone-1-home.png` and `phone-2-page.png` are those two.

They are real captures of the app running on a real phone, a Samsung at
1080x2408. That is possible for exactly the reason a television app once
installed itself on a phone: `uses-feature leanback` is Play distribution
metadata and the platform installer never reads it, so `adb install` works.

Two things were decided while taking them, both visible in the files:

Landscape, not portrait. A fresh install in portrait shows an address bar
squeezed past its own buttons and an empty grid, which is a fair picture of a
layout nobody targeted and an unfair picture of the app. Landscape is the
geometry the app is built for and the one a television uses.

Composed onto 16:9. Play wants a ratio between 16:9 and 9:16, and the phone's
landscape frame is 2.28:1, so the app's own area is cut from the capture and
centred on the mark's darkest ground. The pixels are the app's; only the
surround is added. The phone's status bar and gesture bar are cropped out, since
they are the system rather than us.
