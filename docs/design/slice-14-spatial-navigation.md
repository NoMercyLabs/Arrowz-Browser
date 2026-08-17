# Slice 14 — spatial navigation

Walking focus through a page with a D-pad, so a site can be used without aiming
a pointer at it. This is the half of the input model the cursor was always a
fallback for.

## The search is not nearest-rectangle

The WICG polyfill picks the closest rectangle, and that is what produces the
failure everyone recognises: DOWN jumping hundreds of pixels sideways because
that candidate's center happened to be marginally nearer than the one just
below and slightly left. Android solved this years ago in `FocusFinder`, and
these are its rules, applied to the page and to our own chrome so both feel like
one interface.

In order: a candidate must lie strictly beyond the source on both edges, or it
is not in the direction of travel at any distance. Project the source across the
travel axis; anything overlapping that beam beats everything outside it however
near the outsider is. Within a beam class, minimise `13 × major² + minor²` —
Android's own weighting, so travelling far in the intended direction is cheap
and drifting sideways is not. Identical geometry breaks by document order, so a
page focuses the same way twice.

Two rules the web needs that native does not are results rather than moves. A
winner outside the viewport scrolls a screenful and searches again, because
focus disappearing off screen is the worst thing a television browser does. A
direction with no candidate and no room left to scroll hands the key to the
chrome — a press that does nothing is a defect, not a boundary.

The search is pure, and ten synthetic layouts assert the exact winner for each
press. A regression here is invisible in a screenshot and unmistakable in the
hand, which is precisely the kind of defect that needs a mechanical ruler.

## What the page is trusted with

Nothing except geometry. It reports boxes and applies focus; every decision
about where a press goes is made in Kotlin, where it is tested without a device.

Injection goes through `evaluateJavascript` at document start and the CSSOM,
never an appended `<script>` or `<style>` tag. A site with a strict content
security policy blocks both, and those are exactly the well-built sites where
this would otherwise work best. Our ring class carries a reserved name, because
a cosmetic filter rule from a blocklist can hide anything it can name, including
our own overlay.

The ring itself is the chrome's ring, taken from the same token and handed to
the page as a color and two lengths. A second definition drifts within two
releases, and then native focus and page focus stop looking like one interface.

The parser refuses malformed input rather than throwing. The page is the one
input here that cannot be trusted to be well formed — a script can rename
`querySelectorAll`, another can throw halfway through — and a failure in the
middle of a keypress has to end as "no candidates", which falls back to the
pointer.

## Choosing the mode

`NavigabilityProbe` scores the page on load. Dense and navigable starts in focus
mode; sparse starts with the pointer, because on a page with four links in a
screenful most presses would do nothing. A page that moved focus itself on load
keeps it: two systems moving focus is worse than either alone.

A long press on OK overrides, and the override is remembered per site, because
that judgement is about this page and making it again on every visit is the
chore automatic selection exists to remove.

## Measured on the 8010

DuckDuckGo's home page chose focus mode by itself, drew our ring on the Duck.ai
button in the same steel as the chrome, and DOWN stepped from it to the search
field's submit button without the pointer appearing at all.

The `org.json` on the unit-test classpath is a stub that throws from every
method, so the parser's tests run against the real implementation added as a
test dependency. The alternative was rewriting the parser to avoid the stub,
which would have meant testing something other than the code that ships.

## Section memory

Android TV's remembered-child behaviour. A section is the nearest ancestor that
is a list, row, grid, nav, table, toolbar or menu, stamped once so it is
identified the same way on every press. Leaving a grid and coming back returns
to the item that was left; without it, every return costs as many presses as the
original journey did.

It applies only when focus *enters* a section it was not already in. Moving
within one is ordinary geometry, and redirecting those presses would pin focus
to the remembered item and make the row impossible to walk. A remembered element
that has since left the page falls back to the search's winner, because sending
focus at something that no longer exists is indistinguishable from the browser
hanging. A new page forgets every section.

## The plan's fixtures, all seven

The plan named seven layouts this search has to be held to. All seven are now
covered:

1. The 20px-left in-beam candidate versus the 500px-right nearer one.
2. Staggered card grids where rows do not align.
3. A sticky header above a scrolling body, searched apart.
4. A winner below the fold, asserting a scroll rather than a jump.
5. Overlapping and nested focusables.
6. A direction with no candidate, asserting scroll then chrome handoff.
7. Leaving and re-entering a grid, asserting the remembered child.
