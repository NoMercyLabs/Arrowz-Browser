# Contributing

## The rule that matters most

Every function must be reachable using six keys: the four directions, OK, and BACK. That is what a plain Chromecast voice remote sends to an app. `HOME` and the assistant button never arrive, and volume is unreliable on setups using HDMI ARC.

A change that puts any capability behind a seventh key will not be accepted, however convenient that key is on the remote you happen to own. Richer remotes may add shortcuts to things that are already reachable, never the only route to them.

## How work is organised

Work lands in slices. Each slice opens with a design note in `docs/design/` covering the interfaces, the state transitions, the failure modes, and what the decision forces elsewhere in the app. The note is written and read before the code is.

That last part is the point. On this project the cost of a choice usually lands in a different module from the one that made it, so a decision recorded without its consequences is only half recorded.

## Before you open a pull request

Run the build and the tests. Verify the change on real Android TV hardware or an Android TV emulator image, driving only the six keycodes: `19`, `20`, `21`, `22` for the directions, `23` for OK, and `4` for BACK.

If your change touches spatial navigation, add or update the geometry fixtures. A navigation regression is invisible in a screenshot and immediately obvious in the hand, so the fixtures are the only honest ruler we have.

If your change touches media, prove it with `dumpsys` output rather than a screenshot. Protected video renders to a secure surface and captures black whether it is playing perfectly or not at all.

## Things that will be rejected

Adding an analytics, crash reporting or advertising dependency. CI fails on these automatically, and the privacy policy is written on the assumption that they do not exist.

Setting `abiFilters` or configuring ABI splits. One artifact runs on every processor Android TV uses, and that is deliberate.

Calling `setLayerType(LAYER_TYPE_SOFTWARE)` anywhere in the view tree. It disables hardware and secure video decode.

Injecting scripts or styles into pages by appending tags. Sites with a strict content security policy block them, which loses spatial navigation and focus rings on exactly the well built sites where they work best. Use `evaluateJavascript` at document start and the CSSOM.

Defining focus ring values anywhere other than `ui/Tokens.kt`. A second definition makes native and web focus drift apart.

## Style

Small single purpose files, grouped by feature. Explicit types where the reader would otherwise have to infer them. Comments are for constraints that the code cannot express, not for narration, and the reasoning behind a change belongs in the commit message.

Commits follow Conventional Commits.
