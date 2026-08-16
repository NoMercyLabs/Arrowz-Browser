# Security policy

## Reporting a vulnerability

Report security issues privately through GitHub's security advisory form on this
repository. Please do not open a public issue for anything exploitable.

Include what you did, what happened, and what you expected. If the issue
involves a specific site, name it, because browser bugs are often only
reproducible against a particular page.

## Scope

This is a browser, so the interesting boundaries are the ones between a page and
the device. Findings in these areas are especially welcome:

- Anything that lets page content reach a `@JavascriptInterface` bridge it should
  not have access to, or reach one from an origin that should not have it.
- Anything that causes injected scripts or styles to run in a page they were not
  intended for.
- Anything that leaks browsing activity off the device. The app is built to make
  no requests to servers belonging to us at all, so any such request is a bug by
  definition.
- Anything that weakens the TLS posture, including certificate errors being
  proceeded through.

## Out of scope

Reports that a site refuses to play protected video are not security issues.
Streaming services decline browser playback by policy.
