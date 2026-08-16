# Slice 10 — address suggestions and the rest of the chrome

Entering a URL on a remote is the worst thing a television browser asks of
anyone. The goal of this slice is not autocomplete for its own sake: it is that
four or five presses reach a site that has been visited before, so the typing
gets abandoned rather than finished.

## What it offers

Ranking lives in `data/Suggestions.kt`, pure and tested without a device,
because the ordering rules are where this succeeds or fails.

A favourite outranks a visit that matches equally well, a host the query is a
prefix of outranks a title that merely contains the same letters, and the same
site kept and visited is one row rather than two. Whatever was typed always
stays reachable as a search on the last row, even when everything above it is a
near miss. When the typed text is itself an address, it leads — unless something
remembered already points there, in which case the remembered row stands alone.

The list only appears once the address has actually been touched. Opening the
bar to check where you are should not bury the page under a list.

## What was learned on the 8010

Two behaviours here are platform facts rather than choices, and both cost a
build to find.

**The leanback IME reports no IME inset.** `WindowInsets.isImeVisible` never
flips, so a field that ends editing when the keyboard goes away never ends it.
Measured: the keyboard was down, the field still held the caret, and RIGHT moved
through the text instead of reaching the buttons beside it. What does arrive is
window focus — the keyboard is its own window and takes focus while it is up —
so regaining window focus while editing is the signal that the keyboard closed.

**The IME swallows the BACK that dismisses it, but not always.** When that press
does reach the activity it must not also be read as a plain BACK, or dropping
the keyboard closes the browser. The dispatcher therefore treats a visible
keyboard as "editing" regardless of what the composition believes.

**A geometric focus search cannot find a list that was not there.** Moving down
out of the field landed on nothing at all — no ring anywhere, and the next press
lost. The suggestion rows appear underneath the control the viewer is standing
on, so the field names its `down` target rather than searching for one.

## The rest of the chrome

**Voice.** A mic beside the address field, `RecognizerIntent`, result navigated
directly. The recogniser is part of the Google app and a stripped television
build may not carry it, so its absence is reported rather than crashing on a
missing activity. This is the fastest way into the browser on a remote and it
sidesteps the leanback keyboard entirely.

**Find in page.** `findAllAsync` with the match count beside the field. The
count is not decoration: the highlighted match is usually off screen when a
search starts, and "3 of 12" is the only thing telling the viewer that pressing
next is worth doing. Leaving the surface clears the highlights.

**Kept pages and recently visited.** One screen with different rows, because a
list is what a search by eye needs; tiles are for the handful of places worth
recognising by colour. History is now real pages rather than the per-origin
counters the home grid uses, which meant a second table and a schema version.
That upgrade adds tables and rebuilds nothing — somebody's television already
holds their favourites.

**Site permissions.** Camera, microphone and location are asked once per origin
and the answer is remembered. Blocking is the first row and the one focus lands
on: this device sits in a living room, the person holding the remote may not
have asked for the page that is asking, and the safe answer has to be the one a
stray OK produces. Walking away from the prompt is a refusal rather than
silence, or the page waits forever.

**Downloads** go to `DownloadManager` rather than being read through the
WebView, because it survives the app being killed, which a television will do.
**The file chooser** always answers its callback even when nothing was chosen —
a file input whose callback never fires stays dead until reload, which reads as
the site being broken.

**Desktop or television site**, per origin and remembered, because the reason to
switch is one site's layout rather than a preference about the web.

## Still to do, in the D-pad slice

BACK while a text field holds focus should release that focus and nothing else.
It must not walk history, and it must not exit the app. That is what every
native television interface does, and it is the rung this ladder is still
missing: today BACK ends editing only while the keyboard is up, and a field
that has focus without the keyboard falls through to the history rung.

Belongs with slice 14, where the whole D-pad model is built.
