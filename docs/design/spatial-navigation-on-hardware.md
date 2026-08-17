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

## Committing a field with the D-pad, which is open

With the app now reporting when the sheet opens and when a commit finishes, the
sequence is measurable end to end, and it stops in the same place every time.

Watched on screen for the plain text field: OK opens the sheet with the field's
label, OK again starts editing and the keyboard comes up, typing lands in the
box, and BACK puts the keyboard away with the text kept and the sheet still
open. Then DOWN does not reach the Done row, and OK goes back into the field.
The only press that leaves the sheet is BACK, which discards.

Routing DOWN to the Done row by name rather than by geometry — the override the
plan calls for wherever a search across a container boundary is ambiguous — did
not change it. So the ambiguity is not the cause and the next suspect is the
field's own focus handling, which asks for focus back when editing ends.

This is the highest-value thing outstanding in the browser: a form can be typed
into and not submitted.

## Real sites, and the six things that were stopping them

Everything above was measured against one page written for the purpose. Driven
against the open web the same harness stopped after three header links on a
Wikipedia article, with the body of the article never reached.

The obvious suspect was the beam group — a sticky header and the body under it
are deliberately separate, and starting inside the header that rule would be a
trap. It was not the cause: that page reports **zero** fixed elements, so both
were in one group already. There were six separate causes, and none of them was
the search's geometry.

**A sentinel used as a map key.** Everything outside a row, grid or list reports
the section name `document`, which is the absence of a section rather than the
name of one. Section memory took it as a section, so the whole body of a page
became one row with one remembered child: leaving the header nav resolved
straight back to the first link on the page, and the next press went back into
the header. Three links and a wall, from a sentinel standing in a map it was
never meant to be a key in.

**The mode probe re-entering a page somebody was already reading.** The probe
asks a page what shape it is for three seconds after it loads, and settling on
focus mode applied focus mode — including when focus mode was already running.
Entering a page is now only entering it when nothing of ours is standing
anywhere on it.

**A load event read as a navigation.** `onPageFinished` was announcing a
committed navigation, so every deferred subresource cleared the spatial state
mid-read and recorded the visit in history a second time. What a page does is
now three signals rather than one: a document starting, a routed navigation, and
a page reporting on itself. Only the first two reset anything — and a routed
navigation reset nothing at all before, so a single-page app kept the previous
page's focus positions and filtering origin for as long as it was open.

**A retry on a timer instead of on the page.** `scrollBy` reaches the page some
frames after it is asked for, and the retry waited two of them. That is plenty
on a form and nowhere near enough on an article, so the second look read the
position from before the scroll. It now waits for the page to report the scroll
it was asked for, polling one number rather than re-walking the document.

**A scroll of a whole screenful.** One press past the last visible link moved
the page an entire screen, and everything between the fold and the next
candidate went by unread. The scroll is now measured against the candidate it is
going to and capped at a screenful, and the retry runs up to six times so a gap
wider than the screen does not dead-end. On the article the page now travels 69,
41, 36, 59 and 39 pixels across successive presses.

**Half a second per press.** One snapshot cost 500ms on that article: it walked
all 1,668 focusables, computing a style per element and another per ancestor up
every chain. It now rejects on geometry first, keeps only what is within a
screen of the viewport, caches the ancestor chain and keeps the elements it
found for the lookup that follows. **59 candidates, 77ms.**

## Pinned elements, which the article could not have shown

Wikipedia reports zero fixed elements, so nothing above tested the group split
at all. `developer.android.com` has a sticky header, a pinned cookie bar and
content between them, and on that page focus began in the cookie bar and
**fourteen presses moved nothing** while the page scrolled to its end
underneath.

A bar glued to the bottom of the screen has nothing below it, ever, however far
the page scrolls. Two rules follow, and they are not symmetric:

- **A page is entered at its content**, not at the first thing in document
  order, which on a great many sites is a consent bar, a skip link or a pinned
  toolbar.
- **A scroll asked for from a pinned element is not an answer**, because
  scrolling cannot change what a pinned group contains, so that press crosses
  into the content and resumes from where the content walk left off. From the
  content it is the opposite: a scroll *is* the answer, and crossing instead put
  focus in the cookie bar on every second press the whole way down.

So the content walks and scrolls among its own, and the pinned groups are
reached where somebody would reach for them — the header by pressing up at the
top, the bar by pressing down at the end. Both were then checked for the
opposite failure, that a reachable thing is a trap: pressing up out of the
cookie bar walks the bar and then returns to the article.

## Sites where there is nothing to navigate

BBC News and Stack Overflow report **zero** reachable focusables, or only the
handful in a consent banner. Their consent dialog puts its controls in a
third-party iframe, and an iframe's contents are not in this document, so
nothing in the page can see them.

This is not a navigation defect and it should not be fixed in the search. The
mode probe counts what is reachable, finds nothing, and hands the page to the
pointer — which is the right answer for a page whose only control is in a frame
we cannot walk. It is written down here because "zero focusables" looks
identical to a collector bug and is not one.

## Where the harness was measuring itself

Two of its own faults were found while the browser's were being fixed.

It slept a fixed 1000ms after each press and called anything that had not moved
a dead press. Answering one press costs a snapshot, and on a long article that
is longer than the sleep, so working presses were counted dead. It now waits for
focus to move and only calls a press dead when it has not moved by the deadline
— which is both honest and faster.

And its raster is quadratic: reaching row *n* costs a reload and *n* presses.
That was invisible while the column died after three elements, and the moment
the column ran a whole article the sweep stopped finishing at all. Rows are now
sampled across the column, and the sample size is reported beside the coverage
number, because a coverage figure that quietly skipped most of a page reads
exactly like a page that was fully covered.
