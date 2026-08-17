# Slice 11 — native form overlay

Typing into a web page from a couch. A `<select>` that a D-pad can actually
operate. Dictation into a field rather than only into the address bar. This is
the slice that decides whether someone can sign in to a site, which is the line
between a browser and a demo.

## Why the page's own field is not good enough

A web text input on a television fails in three separate ways at once. The
leanback keyboard covers the field it is editing, so the viewer types blind. The
caret is a few pixels tall at three metres. And a `<select>` renders a dropdown
that the D-pad frequently cannot reach at all, because the page's own key
handling assumes a mouse.

So the page's field is not edited in place. Focusing it opens a native sheet
carrying the field's label, its current value, a real `TvTextField` and a
microphone, and the value is written back when the sheet closes. The viewer
edits a native control that is drawn for the room they are sitting in, and the
page receives an ordinary `input` and `change` pair and cannot tell the
difference.

That the result is accessible is a side effect rather than the goal: a real
native control with real semantics is something a screen reader can read, and a
synthesized tap into a box is not.

## What the page reports, and what it never decides

`formbridge.js` reports one thing — that a field took focus — and describes it.
Every decision about whether that opens anything is made in Kotlin, matching how
spatial navigation is split, and for the same reason: a decision made inside a
document we do not control is a decision the document can change.

The description resolves the field's label the way an accessibility tree does,
in order: `<label for>`, a wrapping `<label>`, `aria-label`, `aria-labelledby`,
`placeholder`, `title`, then `name`. A field whose label resolves to nothing is
still editable, labelled generically, because refusing to edit an unlabelled
field would refuse most login forms on the web.

Injection goes through `evaluateJavascript` at document start, never an appended
tag, for the reason slice 14 records: a strict content security policy blocks
both, and those are the sites this works best on.

### Writing a value back is not `element.value = x`

A framework that controls its own inputs — React most visibly — replaces the
value setter on the element instance and listens for its own synthetic events.
Assigning `.value` directly updates what is on screen and leaves the framework's
state untouched, so the page reverts the field on the next render and the viewer
watches what they typed disappear.

The write therefore goes through the prototype's setter, which is the path the
framework's own instrumentation is wrapped around, followed by `input` and
`change` dispatched as bubbling events. Getting this wrong produces a defect
that looks like nothing at all on a plain HTML form and is total on a modern
one, which is why it is written down here rather than discovered later.

## Which focus opens a sheet

Not every one. A page that focuses its search box on load — Google, DuckDuckGo,
most of the web — would otherwise open a keyboard over itself on every visit,
which is exactly the behavior that makes existing TV browsers unpleasant.

So the sheet opens only when the focus follows an activation the viewer
performed: a cursor tap within a short window, which is a pure function tested
without a device. Focus arriving on its own is still recorded — the page has a
focused field, which BACK needs to know — it just does not interrupt.

That window alone turned out to answer only half of it, which the 8010 said
immediately: in focus mode nothing opened at all. The spatial search focuses an
element as it arrives, so the page reported the field on the way *in*, long
before any press, and `click()` on an element that already holds focus fires no
new focus event. There was never a second report to wait for.

So activation reads what is focused rather than waiting for a focus. A field
already holding it opens directly; only when nothing does are we in the tap case
the window was written for. That also settles `<select>`: OK opens our list
instead of clicking through to the page's own dropdown, which is the widget a
D-pad most often cannot operate at all.

The reverse trap is the loop. Committing a value refocuses the element so
spatial navigation can carry on from where it was, and that refocus is itself a
`focusin`. The bridge suppresses reports around its own programmatic focus, or
closing the sheet opens it again forever.

## BACK releases the field

The behavior recorded as a todo during the D-pad work, implemented here.

With a page field focused and no chrome open, BACK blurs that field and does
nothing else. It does not walk history and it does not exit the browser. Every
native app on the platform behaves this way, and the alternative — a press that
leaves the caret where it was and closes the app instead — is the single most
alarming thing an input can do.

It is a new rung on the BACK ladder rather than a special case, so it is ordered
against the other four meanings once and tested there.

## `<select>` is a list, not a dropdown

The options come across as data and render as `ListRow`s, the same primitive the
menu and settings use. The selected one is marked. This removes the worst class
of television browser defect outright — the dropdown or date picker that simply
cannot be reached — because there is no page-drawn widget left to reach.

## The dirty-form exemption, finally wired

Slice 7 declared `TabSnapshot.hasDirtyForm` and tested the exemption it feeds,
with nothing able to set it yet. The bridge sets it now: any `input` or `change`
on the page marks that tab dirty, and a dirty tab is never suspended under
memory pressure.

The consequence traced in the plan holds here too — `WebView.saveState` does not
reliably carry unsaved input, so the exemption is not merely a preference for
keeping the tab, it is the only thing standing between memory pressure and
silently losing what someone typed on a television keyboard.

Switching a site to the desktop user agent reloads, and a reload discards form
state the same way. That switch now warns when the page is dirty rather than
discovering the cost afterward.

## Voice leaves the app and comes back

Dictation launches a system activity, so our window loses focus and the sheet's
composition is torn down around it. The target has to be remembered across that
gap or the result lands in the address bar, which is where it went before this
slice, because the address bar was the only thing that ever asked.

The launcher therefore carries what asked for it, and the result is routed back
to the field that was open rather than to whichever control happens to be on
screen when the recognizer returns.

## Known cost

Opening the sheet takes Android focus away from the `WebView`, which the chrome
already does for every surface and for a good reason: without it the keyboard
our own field raises types into the page. A page that closes a menu on blur will
close it. The commit path looks the element up by its stamped id rather than by
`activeElement`, so the write itself survives that blur — but a page that
dismantles the field on losing focus will lose the value, and there is no
version of a native overlay that avoids this. Named rather than hidden.
