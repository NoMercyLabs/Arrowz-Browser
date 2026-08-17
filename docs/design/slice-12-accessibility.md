# Slice 12 — accessibility

## Goal

A screen reader user reaches everything this browser does, using the same six keys as everybody else, with one focus system on screen at a time.

## The page is already accessible, and that is the problem

Chromium's WebView publishes the page's accessibility tree through `AccessibilityNodeProvider`, so TalkBack can already read a form's labels, roles, values and states and act on them. Nothing in this slice makes web content accessible; web content arrives accessible and a TV browser is unusually good at destroying it.

It destroys it twice over. A synthesised tap at a pointer position means nothing to a screen reader, which has no idea where that pointer is and never moved it. And our spatial search consumes the same directional keys TalkBack navigates with, so one press drives two systems and focus lands in two places at once.

So the slice is subtractive. When a screen reader is running, the pointer goes away, the injected ring goes away, our search stops consuming D-pad, and accessibility focus walks the chrome and the page as one tree — which is what it does natively when nothing fights it.

## Three modes, not two with a flag

`InputMode` already carries `ScreenReader` alongside `Cursor` and `Focus`, and `KeyDispatcher` already consumes nothing directional in that state. What was missing was anything that ever selected it.

`A11yMode` is the decision, and it is pure: given whether accessibility is enabled, whether touch exploration is on, and how many spoken-feedback services are running, it says whether a screen reader is active. Two signals rather than one, because they disagree. Touch exploration is TalkBack's own switch and is the most reliable indicator when it is on; the spoken-service list catches readers that never enable it. Either is enough.

The mode is not sticky the way a per-site override is. Turning a reader off returns the browser to the pointer rather than leaving it in a mode with no owner, and the per-site memory is deliberately not consulted while a reader is on: a site remembered as "focus mode" must not drag our search back over the top of TalkBack.

`chooseInputMode` therefore has a rung above everything it used to decide, including the remembered override, and the probe's late upgrade is refused for the same reason. A probe that reports two seconds after a page loads must not be able to take the page away from a screen reader.

## Announcements

A visual change nobody announces is a change a screen reader user does not receive. Four of them matter here and none produce a natural announcement: a page finished loading, a navigation failed, a tab was opened or switched, and content was blocked.

`Announcer` holds the policy rather than the text. It refuses to speak when no reader is listening, refuses to repeat what it just said, and falls back from a page's title to its host when the title is empty — which is every page that fails to load, and exactly when an announcement matters most. The text itself comes from `strings.xml` like all other user-facing text.

The repeat guard is not politeness. A progress callback fires several times per load and each one carries the same title, so without it a single page load says the same sentence four times.

## Captions

`CaptioningManager` holds a system-wide preference for caption size, colour and edge, and web video ignores it completely, because a `<track>` is styled by the page and the page has never heard of Android.

`CaptionStyles` turns those preferences into a `::cue` rule, and the rule is injected through the CSSOM at document start like everything else we inject — a strict-CSP site would drop an appended `<style>` tag, and captions would then work on exactly the well-built sites where they should have worked. The user's font scale multiplies the caption size on top of the system caption scale, because both are their stated preference and a person who has raised both meant it.

Colours are emitted only when the user actually set a preference. A default caption style is the page's own, and overriding it with a colour nobody chose makes captions worse on every site that styled them properly.

## Font scale

The chrome needs nothing: dimensions are declared in `sp` and Compose applies the system scale by construction. The page needs `textZoom`, which is the WebView setting that carries the same intent into web content, clamped so an extreme system scale cannot render a site as three words per line.

This is deliberately text zoom rather than page zoom. Page zoom scales images and layout with the text and produces horizontal scrolling on a television, which is the one axis a D-pad handles worst.

## Chrome semantics

Every primitive already carried a content description. What it lacked was role and state, so TalkBack read a tab row as text rather than as a selected button, and the difference between "the tab you are on" and "a tab" was conveyed by a coloured bar and nothing else. Roles are declared on all four primitives, and selection is declared as state rather than left to colour.

## Failure modes this slice must not ship

1. **Two focus systems on screen.** A pointer and an accessibility highlight at once means every press does two things.
2. **A remembered mode overruling a live screen reader.** Per-site memory is a convenience; a running reader is not.
3. **A late probe stealing the page back.** The mode ladder is checked at the moment the probe answers, not only when it was scheduled.
4. **Announcing to nobody.** Speaking when no reader is active does nothing visible in testing and wastes work on every page load.
5. **Overriding caption colours the user never set.**

## Consequences carried forward

- The dispatcher gained no new commands, which is the shape slice 2 promised: modes change what is consumed, not what exists.
- Anything added later that consumes a directional key must ask the mode first. This is now the third system that does.
- Slice 13's blocked-content notice has an announcement to attach to rather than needing its own.

## Acceptance

Unit tests for the mode decision, the announcement policy including the repeat guard and title fallback, the caption CSS for a set preference and an unset one, and the text-zoom clamp.

On the 8010 at `192.168.2.21` with TalkBack running: the pointer is absent, our ring is absent, the D-pad walks chrome and page as one tree, and BACK still exits. With TalkBack off again, the pointer returns without a relaunch.
