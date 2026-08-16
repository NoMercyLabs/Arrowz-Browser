# Slice 1 — Repo scaffold and app shell

## Goal

A repository that builds a leanback TV app shell, installs on the dev box, appears in the Android TV launcher, opens, and exits on BACK. CI green. No analytics anywhere.

Nothing in this slice is user-facing product. Its whole job is to make every later slice cheap and to fail early on the environment rather than late on the feature.

## Toolchain, and why these versions

Measured on this machine rather than assumed:

| Component | Version | Why |
|---|---|---|
| JDK | 21 | `JAVA_HOME` already points at `jdk-21` |
| Gradle | 9.2.1 | unpacked in the local wrapper cache, so the build runs offline |
| AGP | 9.0.1 | present in the local module cache |
| Kotlin | 2.3.20 | present in the local module cache |
| Compose BOM | 2025.10.01 | present in the local module cache |
| `compileSdk` / `targetSdk` | 36 | `platforms/android-36` and `build-tools/36.0.0` installed |
| `minSdk` | 28 | plan decision: unconditional `AudioFocusRequest`, PiP, notification channels |

Every one of these is already in the Gradle caches, so a first build needs no network. That is a deliberate choice: a scaffold that cannot build offline is a scaffold that fails on the day the network does.

## What this slice creates

- Gradle build with a version catalog, one `app` module, no ABI filters and no splits.

  **Corrected during the build:** the plan said the app ships zero native code. It does not. `androidx.graphics:graphics-path` arrives transitively through Compose and contributes `libandroidx.graphics.path.so`. The important property still holds — the APK carries that library for `arm64-v8a`, `armeabi-v7a`, `x86` and `x86_64`, so one artifact runs everywhere — but it holds because no `abiFilters` narrows it, not because there is nothing to narrow.

  That makes the acceptance check stronger rather than weaker: the gate is no longer "no native code exists", which would now fail, but "`native-code` lists all four ABIs", which catches an accidental `abiFilters` the original check would have missed once any native dependency appeared.
- `MainActivity` hosting a Compose surface, `TvTheme`, and `Tokens` carrying the single accent and the focus-ring definition that both Compose and later injected CSS will read.
- Leanback manifest.
- Repo documents matching the `shield` layout.
- `ci.yml` building, unit-testing, and failing on a forbidden dependency.
- `release.yml` skeleton producing an AAB for the internal track.
- A signing config that reads from environment variables, so local and CI both work and no secret is ever committed.

## Failure modes this slice must not ship

These are the ways a TV app silently disappears, and each has a manifest line that prevents it:

1. **No `LEANBACK_LAUNCHER` intent filter** — the app installs and is invisible. There is no error; it simply is not on the home screen.
2. **`android.hardware.touchscreen` not declared `required="false"`** — Play filters the app off every TV device. The build is fine, the store listing is empty.
3. **`android.software.leanback` not declared** — Play will not accept it as a TV app at all.
4. **No `android:banner`** — the launcher renders a blank tile. We have seen exactly this on the dev box, where a blank rectangle sat in the app row with only a label underneath.
5. **`abiFilters` set by habit** — silently drops every processor except one, which is the opposite of the plan's central engine decision.

The acceptance check below is written to catch 1, 4 and 5 on hardware rather than trusting the manifest to be read correctly.

## Consequences carried forward

- `Tokens` defines the focus ring **once**. Slice 14 injects the same values into web content as CSS. If the ring is defined a second time anywhere, native and web focus drift — this is consequence 4 in the plan.
- The signing config reads env vars now so slice 16 changes nothing but the values.
- `buildConfig` stays off unless something needs it; AGP 9 defaults it off and turning it on later is one line.

## Acceptance

Mechanical, on the dev box at `192.168.2.80`:

1. `gradlew assembleDebug` succeeds offline.
2. `adb install` succeeds.
3. The app is present in the leanback launcher query, proving the intent filter.
4. The launcher tile shows the banner rather than a blank rectangle, proving `android:banner`.
5. Launch, screenshot the shell, and exit with keycode `4` alone.
6. `aapt dump badging` shows no `native-code` entry, proving the artifact is ABI-neutral.

Step 6 is the one that would otherwise go unchecked for months.
