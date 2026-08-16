# Slice 10 — address suggestions

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

## Still to do, in the D-pad slice

BACK while a text field holds focus should release that focus and nothing else.
It must not walk history, and it must not exit the app. That is what every
native television interface does, and it is the rung this ladder is still
missing: today BACK ends editing only while the keyboard is up, and a field
that has focus without the keyboard falls through to the history rung.

Belongs with slice 14, where the whole D-pad model is built.
