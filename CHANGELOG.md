# Changelog

All notable changes to this project are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Project scaffold: Gradle 9.2.1, AGP 9.0.1, Kotlin 2.3.20, Compose, `minSdk` 28, `targetSdk` 36.
- Leanback manifest with the launcher intent filter, touchscreen declared not required, and a banner.
- Light and dark palettes resolved from the system setting, with the window theme themed separately so the launch transition does not flash the wrong colour.
- CI gates for ABI coverage, leanback launchability, and the absence of analytics dependencies.
- `deploy.sh` and `deploy.ps1` for build, install, launch, screenshot and logcat across one or more devices, with `--settle` for content that paints after the first frame.
- WebView host with the platform surface a browser needs turned on, including DOM storage, wide viewport and multiple windows.
- User agent derived from the system's own, with the WebView markers removed and a TV token added.
- `KeyDispatcher`, a pure state machine deciding what each of the six remote keys means, covered by unit tests.
- Page rendering follows the system light and dark setting through algorithmic darkening.
- Tab eviction policy: least recently used first, with tabs that are playing media or holding typed input never released.
- Media session: playback publishes a real Android session with the page's title and artwork, and the remote's transport keys are routed back into the page, including on sites that never call the media session API.
- Fullscreen video: the view WebView hands over is displayed, system bars hide, the screen is kept awake while it plays, and BACK exits it ahead of history.
- Cleartext pages load, so http:// sites on a local network are reachable; https pages still cannot pull http subresources.
- Nav bar revealed by pressing UP with the pointer against the top edge, with an address field that tells an address from a search query, reload and home, and a load progress indicator.
- Cursor: an accelerating pointer driven by the D-pad, with synthesised taps so every site's existing tap handling applies, and edge scrolling so a long page is readable without a seventh key.
