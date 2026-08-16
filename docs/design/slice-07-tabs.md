# Slice 7 — Tabs, memory pressure, and surviving a dead renderer

## Two things measured first, which changed what this slice contains

**Background audio already works.** The plan scheduled a foreground service to make audio survive leaving the app. It already does: after pressing HOME with the test tone playing, the session stayed `PLAYING` and the position advanced from 2184ms to 6435ms. The reason is an omission rather than a feature — nothing calls `WebView.onPause()` or `pauseTimers()`, which is the default-path mistake that silently kills playback.

So the service is not what makes audio continue. Its only remaining job is process priority under memory pressure, which is this slice's subject, so it belongs here rather than as its own step.

**Picture-in-picture is not available.** Neither box reports `android.software.picture_in_picture`. Building it would mean shipping a feature that cannot be exercised on any hardware here, and unverified code is the thing this project has been avoiding on purpose. It stays in the plan for devices that support it and is not written now.

## Why tabs are a memory problem before they are a feature

Measured on the two boxes: 3.5GB on the 8010, 2GB on the 8000, and `webview_zygote` running on both, so **page memory lives in sandboxed renderer processes rather than in our heap**. Tab pressure therefore appears as system pressure and renderer kills, not as heap exhaustion. That changes both the signal to watch and the failure to survive.

## The eviction rule

Tabs stay live until memory says otherwise. `TabPressureManager` watches `onTrimMemory`, `ActivityManager.MemoryInfo` and renderer deaths, and evicts **least recently used first**, with two exemptions that are absolute:

- **A tab holding a media session is never evicted.** Audio stopping because a newer tab was opened is indistinguishable from a crash.
- **A tab with a dirty form is never evicted.** `WebView.saveState` does not reliably carry unsaved input, and silently losing what somebody typed on a television keyboard is unforgivable.

There is no fixed tab cap. The cap is whatever the device can hold, which is the only number that is right on both a 2GB box and a modern Google TV.

## `onRenderProcessGone` is the routine case

With multiprocess WebView, a renderer killed under pressure takes its WebView with it, and an unhandled callback crashes the whole browser. On a 2GB box this is routine rather than exceptional, and it is the single most likely cause of "the browser just closed".

Slice 2 already returns `true` from that callback so the app survives. This slice makes it a real recovery: the dead WebView is destroyed and the tab is rebuilt from its saved state.

**State must therefore be captured continuously, not at eviction time.** A process that died without warning never gave us a chance to save, so a suspend-time capture is worthless to exactly the case it exists for. State is captured on navigation and periodically.

## Failure modes this slice must not ship

1. **Evicting the tab that is playing**, or the one holding typed input.
2. **A crash on renderer death**, which looks like the app closing for no reason.
3. **Restoring a tab without its history**, so BACK stops working after a recovery.
4. **A thumbnail captured from a protected surface**, which is black. Those fall back to the page's poster or favicon.
5. **Eviction that never happens** because the only signal watched is one the device never sends.

## Provoking a renderer death without root

`adb shell kill` cannot touch another app's process, so the sandboxed renderer cannot be killed from the outside on a normal device. The debug test page therefore carries an "exhaust the renderer" button that allocates until the process dies, which reproduces the real condition rather than simulating it.

It reproduced it thoroughly: `aw_browser_terminator` reported the renderer crash, and the low-memory killer took down three unrelated apps on the device in the process. Our app survived with the same pid, stayed foreground, and rebuilt the tab from the state captured before the crash.

## Acceptance

Unit tests for the eviction order, including both exemptions and the case where every tab is exempt.

On the **8000 at `192.168.2.80`**, not the 8010: it has 2GB against the 8010's 3.5GB, so it is the box where pressure actually arrives. Open tabs until `onTrimMemory` fires, confirm via `dumpsys media_session` that the playing tab survived while an older idle tab was evicted, then kill a sandboxed renderer with `adb shell kill` and confirm the tab rebuilds from saved state and the app stays alive.
