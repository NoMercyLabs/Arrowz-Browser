# Slice 4 — The nav bar

## Goal

Go somewhere. Until now the browser opens on one page and the only way onward is a link that page happens to offer.

This was slice 8 in the plan, behind media and tabs, on the reasoning that media is most likely to reshape the architecture. That reasoning still holds for media, but it does not justify leaving the browser unable to navigate. Ordering by architectural risk was the wrong ordering for the one feature that decides whether the thing is usable at all.

## Revealing it

`UP` at the top of the page with the pointer against the top edge, which slice 3 already resolves to `RevealNavBar`. `BACK` closes it, ahead of history, which the dispatcher already ranks correctly.

No new key, no chord, no seventh button.

## The cursor stands down while the bar is open

This is the part that needs deciding rather than assuming.

While the nav bar is open, directional keys move **focus between its controls**, not the pointer. Two focus systems live in this app — the pointer for page content, Compose focus for our own chrome — and only one may consume a key at a time. The rule is simple and absolute: chrome open means chrome owns the D-pad.

The dispatcher gains that rule rather than the nav bar policing it, so there is one place where "who owns this key" is answered, and the pointer is hidden while chrome is open so it is never ambiguous on screen either.

## URL or search

What someone types is either an address or a query, and getting this wrong is the most visible possible bug: typing a domain and being sent to a search results page, or typing a sentence and getting a DNS error.

`UrlOrSearch` is a pure function, so every awkward case is a test rather than a guess:

- `example.com` is an address even without a scheme
- `localhost:8080` and bare IP addresses are addresses
- `how tall is the eiffel tower` is a query, because it contains spaces
- `what is a .com domain` is a query despite containing a dot
- `file:` and `javascript:` are neither, and are refused rather than navigated
- an empty or whitespace-only entry does nothing at all

Anything that is not confidently an address becomes a search on the engine in settings, which is DuckDuckGo until the first-run picker lands.

## Text entry

The system leanback IME. `TvTextField` requests focus when the bar opens so the keyboard appears without an extra press, and the field carries `KeyboardType.Uri` and an explicit Go action, so the remote's own confirm key submits.

Voice input lands with the rest of text entry later. The bar is usable without it.

## Failure modes this slice must not ship

1. **A bar that cannot be dismissed.** BACK must close it from any focus position inside it.
2. **Typing that goes nowhere.** Submitting with an empty field must close the bar rather than navigating to a blank page.
3. **A pointer left visible under the bar**, which makes it unclear which of the two focus systems a key will drive.
4. **A stuck cursor direction.** Opening the bar while a direction is held must release the pointer, or it keeps travelling behind the chrome.

## Consequences carried forward

- `isChromeOpen` now has a real producer. Every later overlay — menu, settings, tabs, find-in-page — sets the same flag and inherits the same D-pad ownership rule for free.
- The primitives built here (`tvFocusable`, `IconButton`, `TvTextField`) are three of the four the whole chrome is made from. The grid tile is the only one left.
- The focus ring is drawn from `Tokens.Focus` and the active palette, so slice 14's injected web ring can read the same values rather than inventing its own.

## Observed on hardware

**Closing the bar takes two BACK presses while the keyboard is up**, and that is correct rather than a defect. The IME consumes the first press to dismiss itself, exactly as it does in every Android app, and the second reaches us as `CloseChrome`. Making the bar swallow the first press would mean fighting the platform for a behaviour users already expect.

**The reveal needs the pointer clamped at the top, not merely near it.** Holding UP until the cursor stops at the edge and pressing once more is the gesture; a hold that ends at seventy pixels leaves the pointer outside the band and the press correctly moves the cursor instead.

## Acceptance

Unit tests: every `UrlOrSearch` case above, and that the dispatcher refuses to move the pointer while chrome is open.

On the 8010 at `192.168.2.21`, using only the six keys: reveal the bar with UP, focus lands in the field with the keyboard shown, type an address, submit, and the page navigates. BACK closes the bar without navigating. With the bar open, LEFT and RIGHT move between its controls and the pointer does not move.
