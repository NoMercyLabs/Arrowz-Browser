# Slice 5 — Fullscreen video

## Goal

Video that fills the television.

Without this, YouTube and every video site are unusable: the fullscreen control does nothing, because `WebChromeClient` hands the app a view to display and a WebView that ignores it simply drops the video on the floor. This is not polish. It is the difference between a browser and a webview on a device whose entire purpose is watching things.

## What WebView actually gives us

`onShowCustomView` delivers a `View` that already contains the video, plus a callback to invoke when the user leaves. `onHideCustomView` says the page wants out. That is the whole contract, and everything else is ours:

- putting the view somewhere it covers the screen
- hiding the system bars
- keeping the screen awake
- getting the user back out
- putting the view away again without leaking it

## The screen must stay awake

A page playing video through a custom view does not keep the display on by itself. Without `FLAG_KEEP_SCREEN_ON` the television dims and then sleeps partway through a film, which reads as the app crashing.

The flag is added when fullscreen starts and cleared when it ends. Cleared, not left set: a browser that prevents a TV from ever sleeping is worse than one that dims during video.

## Getting out

`BACK` already ranks `ExitFullscreen` above closing chrome, above history and above exiting the app, and the dispatcher already has `isFullscreen` in its state. This slice supplies a real producer for that flag, and the branch that has been tested since slice 2 finally has something to do.

Exiting also invokes the callback WebView gave us. Skipping it leaves the page believing it is still fullscreen, so its own controls stay in the wrong state and the next fullscreen request is ignored.

## The cursor stays on top

The pointer must draw above the fullscreen view, not below it. Player controls live inside that view, so a cursor hidden behind it makes the video unpausable at the exact moment the pointer matters most.

That is a Compose ordering decision: the fullscreen container is added under the cursor overlay rather than over it.

## No transforms on the view we are handed

The supplied view may be a `SurfaceView` backing a secure decode path. Applying alpha, scale or a cross-fade to it can blank the picture entirely, which looks like a broken video rather than a broken animation. It is reparented and shown, never animated.

`setLayerType(LAYER_TYPE_SOFTWARE)` remains banned for the same reason, one layer up.

## Failure modes this slice must not ship

1. **A fullscreen view that cannot be dismissed.** BACK must exit from any state, and exiting must both remove the view and invoke the callback.
2. **The screen sleeping during playback**, or staying awake forever afterwards.
3. **A leaked view.** The custom view holds page content; keeping a reference after exit keeps a renderer alive that should have been released.
4. **A cursor drawn beneath the video**, making controls unreachable.
5. **System bars reappearing mid-video** because immersive mode was set once rather than re-applied when the system transiently shows them.

## Consequences carried forward

- Slice 6's MediaSession bridge needs to know playback is happening; fullscreen is a strong signal but not the only one, so the bridge must not depend on this slice's state.
- Picture-in-picture in slice 7 enters from the same controller, because both need the same "which view holds the video" answer.
- Protected video renders to a secure surface, so from here on a screenshot is not evidence about playback. Verification uses window flags and the view hierarchy instead.

## The test page

Third-party pages proved unusable as fixtures: consent walls block the click, and a bare media file renders black with no visible control. So the repo now carries `app/src/debug/assets/mediatest.html`, served over a real https origin by `WebViewAssetLoader` and present only in debug builds.

This is infrastructure rather than a workaround. Slice 6's media session and slice 7's picture-in-picture need to drive the same behavior from a page whose markup we control, and no third-party site is a stable fixture.

## Known real-page fixtures

`http://192.168.2.201:4321/` on the LAN, Stoney's Mom design kit. Two reasons it is worth keeping:

- it is a cleartext page, so it exercises the `usesCleartextTraffic` decision rather than only the https path
- its layout is the spatial-navigation problem in miniature: a dense control row at the top right against wide buttons at the bottom center, which is exactly the case where nearest-rect search jumps sideways and the beam test does not

## Acceptance

On the 8010 at `192.168.2.21`, driven with the six keys: click a video's fullscreen control with the cursor, confirm via `dumpsys window` that `FLAG_KEEP_SCREEN_ON` is set on our window and that the custom view is in the hierarchy, then press BACK once and confirm the flag is cleared, the view is gone, and the page did not navigate.
