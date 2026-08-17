# Changelog

All notable changes to this project are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Project scaffold: Gradle 9.2.1, AGP 9.0.1, Kotlin 2.3.20, Compose, `minSdk` 28, `targetSdk` 36.
- Leanback manifest with the launcher intent filter, touchscreen declared not required, and a banner.
- Light and dark palettes resolved from the system setting, with the window theme themed separately so the launch transition does not flash the wrong color.
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
- Tabs: a registry that keeps every live page attached and toggles visibility, so switching tabs never repaints a page black, and closing the last tab opens a fresh one rather than leaving a blank screen.
- Home screen: a grid of favorites and most visited, with a star in the address bar that keeps the page you are on. Backed by SQLite with stable ids, `updatedAt` and tombstones, so a sync can arrive later without a migration on somebody's television.
- Menu on a long press of BACK, and a button at the end of the address bar, because a shortcut nobody is told about cannot be the only way in.
- Appearance follows the TV by default and can be set to light or dark, and the choice survives a restart.
- Address suggestions: favorites and visited origins ranked under the field while you type, with a search for what you typed always reachable on the last row.
- Voice input from the remote's microphone, straight into an address or a search.
- Find in page, with the match count beside the field.
- Kept pages and recently visited, as browsable screens.
- Site permissions for camera, microphone and location: asked once per origin, remembered, and defaulting to the answer a stray press produces.
- Downloads through the system downloader, and a file chooser for pages that ask for one.
- Desktop or TV site per origin, remembered.
- The designer's Moooom icons throughout, as vector drawables tinted at draw time.
- Spatial navigation inside page content: Android's own beam-and-weight rules applied to the DOM, so a press moves to the element beside the one you are on rather than to whichever rectangle happens to be nearest. Scrolls before it jumps, remembers the last item in each row or grid, and hands focus to the chrome rather than doing nothing.
- The focus ring inside web content is drawn by us, from the same token the native chrome uses, because a site's own focus style is frequently invisible at three metres or removed outright.
- Input mode is chosen per page and re-asked as the page renders, since a page that has drawn nothing focusable at navigation time usually has by the time it settles. A long press of OK overrules it, per site.
- Native form editing: a web field opens a real television keyboard with the layout its type asks for, a `<select>` becomes a list a D-pad can operate, and voice dictation goes straight into the field.
- Links from other apps: the browser is a system web handler for http and https, opening a cold start in its single empty tab and a warm one in a new tab, so a page being read is never discarded.
- Screen-reader support: with TalkBack running the pointer, the injected ring and our own spatial search all stand down, so one focus system walks the chrome and the page as a single tree.
- Announcements for page load, load failure and tab changes, spoken only when a reader is listening and never twice for the same event.
- The television's caption preferences applied to web video through an injected `::cue` rule, and the system font scale carried into page text.
- Tracker blocking, on by default, using the public EasyList and uBlock Origin lists fetched from their own publishers, with a smaller seed list inside the app so the first page is already protected. Rules are indexed by token rather than compiled per rule, and element hiding is a separate CSS injection.
- Cookies are erased when the browser closes, with a per-site choice to stay signed in.
- Brand assets generated from one description of the mark, and a Dutch translation alongside the English strings.

### Fixed

- Long-press BACK opened the menu and the system immediately closed it again, because a press whose key-down was lost reached a path that handed the key on. BACK is never handed to the system now.
- Focus was dropped whenever a surface closed, leaving every direction doing nothing on a screen that looked fine. Each change of surface hands focus back to the address field, and screens with nothing in them still have something to focus.
- Pressing DOWN out of the address bar landed on no control at all. The bar is one focus group naming where DOWN goes, rather than relying on a geometric search across the gap between two sections.
- Typing in the address bar reached the page underneath, because the WebView kept the input connection while a surface was over it.
- The keyboard closing never ended editing, since the leanback IME reports no window inset. Regaining window focus is the signal that does arrive.
- Pressing HOME released a tab: `TRIM_MEMORY_UI_HIDDEN` is a lifecycle notice rather than memory pressure.
- Debug and release builds shared a launcher name, so both appeared in the launcher as the same app.
- The release build rendered a black screen. R8's optimizer, on the artifact the pipeline produces and nothing else; shrinking and obfuscation were both innocent. CI now builds the release variant on every push, since the debug APK is not the thing anybody installs.
- A television that had already run the browser crashed on launch after an upgrade, because a table added to the database's create path never reached an existing device. Create and upgrade are now the same list of statements.
- Focus could land on elements nobody could see, including a closed off-canvas drawer, and could not reach a label acting as a control.
- BACK from the first page opened after a launch left the browser entirely rather than returning to the home screen.
- A search page that focused its own box opened a suggestion list over the results that no press could dismiss.
- The focus ring leaked: its class and its attribute were removed separately, so a re-rendered element kept the attribute and a second ring was drawn.

### Changed

- Palette: dark greys with a desaturated steel accent. The violet inherited from the NoMercy media ecosystem, and the cyan that briefly replaced it, were both brighter than anything else on screen, so focus arrived as a flare rather than as emphasis.
- Focus is unmistakable at three metres: a 3dp ring, a real lift, and an accent label, rather than a hairline outline.
- The address bar and find bar are headers with their own surface and a shadow, rather than strips of page color with controls floating in them.
- User-facing text is American English.
