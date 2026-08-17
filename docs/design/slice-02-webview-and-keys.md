# Slice 2 — WebView host, platform settings, and the key dispatcher

## Goal

Load and browse real pages, with BACK behaving correctly and every decision about a keypress made in one testable place.

No cursor yet, that is slice 3. In this slice the D-pad reaches the page through WebView's own focus handling, which is exactly as poor as every other TV browser. That is deliberate: it establishes the baseline the cursor has to beat.

## The key dispatcher is a pure function

`KeyDispatcher.dispatch(key, phase, state) -> Command?`

- `RemoteKey` is the six-key vocabulary and nothing else: `Up`, `Down`, `Left`, `Right`, `Center`, `Back`.
- `KeyPhase` is `Down`, `Up`, or `LongPress`.
- `BrowserState` is the small set of facts a decision needs: is fullscreen active, is chrome open, can the page go back, is the page scrolled to the top, and the current `InputMode`.
- `Command` is what should happen, named rather than performed.

Nothing in the dispatcher touches Android. It has no `WebView`, no `Context`, no clock. That is the whole point: every BACK branch and every mode transition is a table row in a unit test that runs in milliseconds with no device, and the parts that are genuinely hard to test on a TV become the parts that need no device at all.

`InputMode` already has three values rather than two — `Cursor`, `Focus`, `ScreenReader` — because a screen reader must consume no D-pad at all. Adding it later would mean revisiting every branch.

## BACK, and a correction to the plan

The plan said to use `OnBackPressedCallback` and opt into predictive back. Working through it, that is wrong here.

BACK carries four behaviors in this app, and one of them is a long press. The predictive back APIs deliver an invocation, not a duration, so there is no way to distinguish a short press from a long one through `OnBackInvokedCallback`. Opting in would make the menu unreachable.

So this app deliberately does **not** enable `android:enableOnBackInvokedCallback`, and handles BACK through the documented long-press path instead: `KeyEvent.startTracking()` on the initial `ACTION_DOWN`, then `onKeyLongPress` for the long press and the tracked `ACTION_UP` for the short one.

This is a real trade-off rather than an oversight. Predictive back's benefit is the animated preview of the destination during a back *gesture*, and a television has no back gesture — only a key. We give up nothing users can see, and we keep a binding the interface depends on. Revisit only if Android makes back-invoked callbacks mandatory, at which point the long-press menu needs a different home.

Short BACK resolves in a fixed order, first match wins: exit fullscreen, close chrome, go back in history, then exit the app.

## The user agent is derived, never hardcoded

`WebSettings.getDefaultUserAgent(context)` is read at runtime, the `; wv` token is removed, and a TV token is appended.

Hardcoding a Chrome version string is the obvious approach and it rots: the string is frozen at whatever Chrome existed when it was typed, while the engine underneath keeps updating, so sites are told a lie that grows over time. Deriving keeps the version honest forever and costs one string operation.

`wv` is removed because it is the marker sites use to detect an embedded WebView, and many respond by serving a degraded page or refusing outright.

The TV token is what a site with a ten-foot layout looks for. There is no standard signal — the `TV` form factor was proposed for client hints and removed — so a UA token is the only mechanism that exists.

## Web platform surface

WebView's defaults are conservative in ways that make embedded browsers feel broken. `WebSettingsFactory` turns on JavaScript, DOM storage (off by default, which silently breaks any site using `localStorage`), database storage, wide viewport and overview mode, and multiple windows.

It leaves off, deliberately: file access, content access, and geolocation. It forces `MIXED_CONTENT_NEVER_ALLOW`, and SSL errors are cancelled rather than proceeded through, with no override path in this slice.

Cleartext pages are allowed. A browser that cannot open `http://` cannot reach a router admin page, a NAS or a printer, and refusing them makes the browser useless on a home network rather than safer. The half that matters is kept: an `https` page still cannot pull `http` subresources, because that is the case where someone believes they are on a secure page.

## Dark mode reaches the page

`WebSettingsCompat.setAlgorithmicDarkeningAllowed` is enabled, and combined with `android:isLightTheme` from slice 1 it makes `prefers-color-scheme` report the system setting to the page. A site with a dark stylesheet then follows the television's theme, and a site without one gets Chromium's algorithmic darkening rather than a white rectangle in a dark room.

This is why the theme attribute was declared in `-v29` variants in slice 1 rather than left out.

## Failure modes this slice must not ship

1. **A blank screen with no explanation.** Load failures render an in-app error state, not an empty WebView.
2. **SSL errors proceeded through.** `onReceivedSslError` cancels. Always, in this slice.
3. **A page that traps BACK forever.** Short BACK reaches app exit once history is exhausted, from any state.
4. **`setLayerType(LAYER_TYPE_SOFTWARE)` anywhere.** It disables hardware and secure decode, which matters from slice 4 onward and is impossible to notice until then.

## Consequences carried forward

- The dispatcher's `Command` set is the contract slice 3 attaches the cursor to, and slice 14 attaches spatial navigation to. Neither of those slices should need to modify the dispatcher's shape, only add commands.
- Not opting into predictive back is a decision the manifest now depends on. If anything sets `enableOnBackInvokedCallback` to true, the long-press menu silently stops working, and it will look like a menu bug rather than a manifest one.
- Deriving the UA means the string differs per device. Anything that parses our own UA is therefore wrong by construction.

## Acceptance

Unit tests cover every BACK branch and every mode transition, on the JVM, with no device.

On the 8010 at `192.168.2.21`: load a real page, confirm it renders, confirm short BACK walks history and then exits, confirm long BACK is distinguishable from short. Driven with keycodes `19 20 21 22 23 4` only.
