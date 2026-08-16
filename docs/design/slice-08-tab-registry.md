# Slice 8 — The tab registry, and the pressure signal that actually arrives

The previous slice wrote the eviction policy and the renderer-death recovery. Both had zero callers: there was one page, no registry, and nothing watching memory. This slice is the machinery around them.

## Suspension is the recovery path, on purpose

A suspended tab and a tab whose renderer was killed end up in the same state: no live `WebView`, a saved state bundle, a URL. So suspension reuses the mechanism recovery already proved rather than adding a second one.

`WebViewHost` gains `suspendTab()` — capture, detach, destroy — and `resumeTab()`, which calls the same rebuild factory a dead renderer calls. If restoring can be broken by an eviction, it is broken by a renderer death too, and it is better to have one path that is exercised constantly than two that are each exercised half the time.

## Which tab is playing has to be a fact, not a guess

One `MediaSession` is published for the app, never one per tab: two sessions from one process make the system pick, and the picked one is not reliably the one the user is listening to.

But with tabs, "the page" is no longer unambiguous, and two things break if the session does not know which tab owns it:

- A transport key routed to the *foreground* tab pauses a video the user is looking at instead of the audio they are hearing.
- A second tab loading a page that stops its own media clears the now-playing state of the tab that is actually playing.

So the page bridge is created per tab and carries its tab id. The session records the owning tab on every report, routes transport back to that tab whatever is on screen, and ignores a stop from any other tab. That ownership is also what `TabPressureManager` reads for its media exemption, so "is anything playing" has exactly one owner, as slice 6 required.

## Three pressure signals, because one of them is going away

`onTrimMemory` is the obvious signal and it is not sufficient. The `TRIM_MEMORY_*` levels below `UI_HIDDEN` are deprecated, and on newer Android versions they are no longer dispatched at all — a browser that watches only that callback evicts nothing on the devices most likely to need it. That is the fifth failure mode named in slice 7, arriving as a platform change rather than as a bug.

So pressure is read from three places:

1. `onTrimMemory`, which is what the API 34 boxes here actually send.
2. `ActivityManager.MemoryInfo`, sampled when a tab is opened or activated — `lowMemory`, and available memory against the system's own threshold. This is the signal that still exists everywhere.
3. **A renderer death is itself evidence of pressure.** The system killed a process to reclaim memory; opening more pages at that moment is how a browser gets into a kill loop. One recovery, one eviction pass.

## Closing the last tab

The registry never empties. Closing the last tab replaces it with a fresh home tab rather than finishing the activity: an accidental exit from a remote is indistinguishable from a crash, and the browser closing itself is the exact complaint that started this project.

## `hasDirtyForm` stays false, and stays declared

The eviction exemption for unsaved input is written and tested, and nothing sets it yet because form introspection is slice 11. It is left declared rather than removed, because the alternative is discovering the exemption is missing after somebody loses what they typed.

## Failure modes this slice must not ship

1. **A transport key reaching the wrong tab**, so the remote pauses the wrong thing.
2. **A background tab clearing the playing tab's session.**
3. **Eviction that never fires** because the only signal watched is one the device no longer sends.
4. **A suspended tab resumed without its history**, so BACK stops working after a switch.
5. **An empty registry**, which has no valid rendering and would show a black screen.
6. **Evicting into a kill loop**: reacting to a renderer death by rebuilding the tab *and* opening pressure everywhere at once.

## Acceptance

Unit tests for the registry: open, activate, close, close-the-last, eviction ordering through the real policy, and the media exemption driven through the session's tab ownership rather than through a flag set by the test.

On the **8000 at `192.168.2.80`**, which has 2GB against the 8010's 3.5GB and is therefore the box where pressure arrives: open tabs from the tab list until an eviction happens, confirm with `dumpsys media_session` that the playing tab still holds the session, and confirm the evicted tab resumes with its history intact. Driven with the six keycodes only.
