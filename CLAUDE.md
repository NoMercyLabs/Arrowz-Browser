# NoMercy Browser — working notes

A web browser for Android TV. NoMercyLabs product, published under NoMercy Labs. Standalone repository: nothing is shared with or copied from the NoMercy monorepo.

## Non-negotiables

**Six keys.** `DPAD_UP/DOWN/LEFT/RIGHT`, `DPAD_CENTER`, `BACK`. Every function reachable from those alone. `HOME` and `ASSIST` are system consumed and never arrive; volume is unreliable on HDMI ARC. Accelerator keys are deferred and never the only route to anything.

**No analytics, ever.** No analytics, crash reporting or advertising dependency. CI fails if one appears. The privacy policy is written on this basis, so adding one makes the app's published claims false.

**No ABI narrowing.** No `abiFilters`, no splits. One artifact for every processor. AndroidX contributes `libandroidx.graphics.path.so` transitively through Compose, packaged for all four ABIs; the CI gate asserts all four are present.

**Design note before code.** Each slice starts with a note in `docs/design/` naming interfaces, state transitions, failure modes and second order consequences. The note is read before the code is written.

## Toolchain

JDK 21, Gradle 9.2.1, AGP 9.0.1, Kotlin 2.3.20, Compose BOM 2025.10.01, `compileSdk`/`targetSdk` 36, `minSdk` 28.

AGP 9 provides Kotlin support itself. Applying `org.jetbrains.kotlin.android` is an error, and there is no top level `kotlin { }` extension to configure.

## Dev hardware

Nokia Streaming Box 8000 at `192.168.2.80`. Android 14, API 34, `armeabi-v7a` only, roughly 2GB RAM, 1920x1080 at 320dpi, WebView multiprocess, hardware Widevine present.

Drive it with six keycodes only: `19 20 21 22` directions, `23` OK, `4` BACK. Do not drive the launcher blind with keypresses, because it is easy to land in the Play Store on an install page.

## Traps already paid for

Protected video captures black through `screencap`, so DRM playback is never verified by screenshot. Use `dumpsys media.metrics`, the MediaSession state and frame counters.

`WebView` is multiprocess here, so a renderer killed under memory pressure crashes the whole app unless `onRenderProcessGone` is handled. Tab state must be captured continuously rather than at suspend time, because a dead process gives no warning.

Focus ring values live only in `ui/Tokens.kt`, with the colour taken from the active `Palette`. Slice 14 injects the same values into pages as CSS.

Injected scripts and styles go through `evaluateJavascript` at document start and the CSSOM. Appended tags are blocked by strict CSP sites.

## Theming

Light and dark palettes resolve from the system setting through `TvTheme`, with `ThemeMode` allowing an explicit override later. The window theme is themed separately in `values` and `values-night` so the launch transition does not flash the wrong colour, and `android:isLightTheme` is set in the `-v29` variants because WebView reads it to decide what `prefers-color-scheme` reports to a page.
