# Slice 3 — The cursor

## Goal

Make every page on the open web operable from the D-pad.

Slice 2 left directional keys to WebView's own focus handling, which is the baseline every TV browser ships and the reason they are unpleasant. This slice replaces it with a pointer, because a pointer is the only mechanism that works on 100% of pages regardless of how the site was built.

## Why the cursor is the default rather than the fallback

Spatial navigation is the better experience where it works, and it lands in slice 14. It cannot be the baseline, because it depends on the page having a coherent, reachable focusable graph, and most of the web does not: click handlers on `div`s, focus traps, offscreen menus, elements that reset focus on every render.

A pointer depends on nothing. It works on a page built in 1998 and a page built this morning. So the pointer is what guarantees the browser is never unusable, and spatial navigation is the upgrade offered where the page earns it.

## Acceleration is the whole feel

A constant-speed pointer is unusable on a 1920-wide screen: fast enough to cross the screen is too fast to hit a link, and slow enough to hit a link takes seconds to cross.

So velocity ramps while a direction is held: it starts slow enough for precision, holds that speed briefly so a tap is a small nudge, then accelerates to a capped maximum. Releasing resets it, so every fresh press starts precise again.

The curve is defined by four numbers in `CursorConfig` — start speed, ramp delay, acceleration, maximum speed — and `CursorState` is a pure function of elapsed time and held direction. No Android types, no view, no real clock: the clock is passed in. That makes the acceleration curve testable at exact millisecond offsets, which is the only way to check "one tap moves a small amount, a two-second hold crosses the screen" without a stopwatch and a television.

What tests cannot tell us is whether the numbers feel right at three metres. They are one config object precisely so they can be tuned from feedback rather than by editing logic.

## Clicks are synthesised touches

`OK` dispatches a `MotionEvent` `ACTION_DOWN` followed by `ACTION_UP` at the pointer's coordinates, through `WebView.dispatchTouchEvent`. That is the same input a finger produces, so every site's existing tap handling applies with no cooperation from the page.

The down and up carry the same coordinates and a realistic interval. A zero-duration touch is discarded by some frameworks as a stray event, and a moving one reads as a swipe.

## The pointer must not scroll the page by accident

Moving the pointer is not a drag: no `ACTION_MOVE` is ever dispatched. The pointer is drawn by us and the page is told nothing until a click happens. Otherwise every cursor movement would scroll or select text.

## Scrolling comes from the edges

With no `ACTION_MOVE`, the page needs another way to scroll. Holding the pointer against a screen edge scrolls the page in that direction, at a speed that ramps the same way the pointer does.

This is what removes the need for `CHANNEL_UP`/`DOWN` or any seventh key, and it is why the six-key baseline survives contact with a long page.

## Failure modes this slice must not ship

1. **A pointer that leaves the screen.** Position is clamped to the viewport every frame, or the cursor becomes invisible and the browser appears frozen.
2. **A click that lands somewhere other than the drawn pointer.** The overlay and the synthesised event must read the same position, so both take it from one state object rather than each tracking their own.
3. **A pointer invisible against page content.** The cursor is drawn with a contrasting outline so it survives both a white page and a black video.
4. **Held keys that keep moving after release.** Movement is driven by held state, and every key-up clears it.

## Consequences carried forward

- The cursor overlay must stay above fullscreen video from slice 4, or player controls become unclickable exactly when the pointer matters most.
- Slice 12 disables the cursor when a screen reader is active. `InputMode.ScreenReader` already exists and the dispatcher already returns nothing for directional keys in that mode, so this slice must not move that decision into the cursor.
- Tap synthesis is the same mechanism the form overlay in slice 11 uses to focus a field, so its coordinates-to-event mapping is shared rather than duplicated.

## Corrections found on hardware

Two things in this design were wrong, and both were only visible on a device.

**Input cannot be taken in `Activity.onKeyDown`.** A focused WebView consumes every directional key for its own focus walking, so the activity callbacks never ran and the browser ignored the remote completely, with nothing logged anywhere. Input is now taken in `dispatchKeyEvent`, which sits above the view hierarchy. That path provides no long-press callback and no tracking flags, so `KeyGestureTracker` computes both, and being pure it is testable without a device.

That tracker also fixed a separate defect: a stray key-up arriving during a window transition was read as a real BACK, and the browser exited two seconds after launching for a button nobody pressed. A release with no matching press is now ignored.

**`isPageAtTop` alone cannot trigger the nav bar.** Every freshly loaded page is at scroll zero, so keying the reveal on that made UP stop moving the pointer entirely: the cursor could never travel upward on any unscrolled page. The reveal now also requires the pointer to be against the top edge, which is what "one more UP" actually means once a pointer exists.

## The frame loop must not outlive the movement

A `withFrameMillis` loop inside `while (true)` requests a frame every frame for as long as the app is open. Compose therefore never goes idle, the display is held at full refresh, and the app sits at measurable CPU doing nothing at all. On the 8010 that was a steady 12.5% with the pointer stationary, and it presents as the page feeling stuck rather than as anything obviously wrong with the app.

The loop is now keyed on whether a direction is held, so it is created when movement starts and cancelled when it stops. Measured on the same device afterwards: 0.0% at idle.

This is the general rule for anything animated here. Nothing may hold a frame callback open while there is nothing to animate.

## Acceptance

Unit tests: acceleration at exact time offsets, clamping at all four edges, and that release resets velocity.

On the 8010 at `192.168.2.21`, driven with keycodes `19 20 21 22 23` only: the pointer is visible, moves in all four directions, accelerates while held, stops on release, clicks a real link and navigates, and scrolls a long page from the bottom edge.
