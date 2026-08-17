# Driving spatial navigation on hardware

## Why the fixture suite was never going to be enough

Spatial navigation has a unit suite of synthetic layouts asserting exactly which element must receive focus for a given press. It has never once caught a defect somebody reported from the sofa. Every one of those was reachability, a ring that leaked, a click the page ignored, a retry that did not retry, or a keyboard that ate the D-pad — none of which a rectangle fixture can express.

So there is a second ruler: `tools/spatial-drive.mjs` drives real key presses at a real television and asks the page where focus actually went. `app/src/debug/assets/formtest.html` is what it drives against — one page carrying every input type a site can put in front of somebody holding a remote, with no focus styling of its own, served over a real https origin by the asset loader so it is subject to the same rules a real site is.

```
node tools/spatial-drive.mjs 192.168.2.80:5555 \
  https://appassets.androidplatform.net/assets/formtest.html
```

## What it found

**The keyboard trap.** Walking onto the first field of a form ended the walk. The ring sat on it and the next six presses moved nothing, which from the sofa is a dead remote. The cause: giving a text control DOM focus makes WebView raise the system keyboard, and the leanback IME consumes every directional key while it is up. A label is part of this — focusing a `<label for>` delegates focus into the control it names, so a caption raised the keyboard for an input nobody had reached.

The fix is that the ring no longer implies DOM focus. A text-shaped control wears the ring without being focused, the keyboard stays down, and OK opens the native overlay, which is the only moment a keyboard should appear.

**Phantom stops.** The page reported 45 focusables where 27 were real controls: every label with a `for` was a second stop beside the control it names, so every field cost two presses and the ring could sit on a caption that does nothing. A label whose control is itself a stop is now dropped. Reported focusables went 45 → 25.

Together those took a page that reached **1 control from six presses** to one where a column walk crosses the whole document, scrolling as it goes: `f-radio-2 → f-time → f-file → (scroll) → f-textarea → f-editable → f-hiddencheck → f-link`.

## What the harness itself taught

Three harness designs were written and thrown away, and the reason matters more than the code:

- A **rotation** of directions oscillates inside one pair of fields and reports four of twenty-five.
- **Placing focus through the page's API** and pressing from there measures the harness. The app owns state the harness cannot set, and the disagreement reads as an interface full of traps when it is not.
- **Resetting with a VIEW intent** opens a *new tab*, because that is what a link arriving at a running browser now does. The devtools target being read was a background tab while the presses went to the one in front, and every direction looked dead.

And one that was not the harness's fault: a sweep once reported six dead presses in a row because the browser had lost the foreground and every press had gone into somebody's media app on the television in the living room. The driver now refuses to send a key unless the browser is the thing in front, which is a courtesy as much as a correctness measure.

## The scroll that outran its own retry

A column sweep stopped at the first `<select>`. The press was not refused and
the control was not a trap: the page scrolled a full screenful and focus stayed
behind, ending up above the viewport with nothing below it that the next press
could find either.

`scrollBy` is applied on the view's next frame. The retry was reading geometry
in the same breath as asking for the scroll, so it got the position from before
it, asked for the same scroll again, and — being the retry — gave up. Waiting two
frames before the second look is the whole fix.

Coverage on the test page went **12 of 25 to 22 of 25**, and the column walk now
runs the full document:

```
f-text f-tel f-search f-select f-range f-file f-textarea f-editable
f-hiddencheck f-link
```

The three the raster does not list are reachable and were checked by hand, since
the sweep only ever steps right from a column stop and they sit left of one:
`f-check` is one LEFT from `f-radio-1`, `f-button` is two LEFT from `f-submit`,
and `f-fakebutton` is one UP from there. Every control on the page can be
reached with the four arrows.

## The form overlay, and where its harness stands

`tools/form-drive.mjs` places focus on each field in turn, presses OK, types,
commits through the sheet's Done row, and reads back what the page received --
the value, and whether the page's own script saw `input` and `change`, because a
framework that never sees those has been handed nothing.

What it established, on the 8000, watched on screen: the sheet opens for every
field type, carrying the field's resolved label, its validity note, the Done row
and the dictation button, and committed values do reach the page -- telephone,
password and the required field all held their text afterwards.

What it does not yet do is report that reliably. Two harness faults are already
fixed and written into the file: typing on a fixed delay put characters into a
sheet before it existed, so a run came out shifted by a field; and sending ENTER
does not commit, because the field's Go action belongs to the IME, so the sheet
stayed open and every later press went into it. Committing through Done is
correct and the run still reports nothing arriving.

The obstacle is specific: from outside the app there is no signal that says the
sheet is up or that a commit has finished. `document.activeElement` answers the
first question only after the tap has already landed, and the second has no
answer at all, so each attempt costs a full device round trip to find out. A
reliable version needs the app to say so — a testing-only report the harness can
wait on — rather than more guessing at delays from the outside.
