# Slice 13 — privacy and filtering

## Goal

Trackers blocked by default, from lists fetched straight from their public upstreams, with no request ever made to us.

## What "no data collected" has to survive

The Play data-safety declaration says the developer collects nothing. That claim is only defensible if it is structurally true rather than a policy nobody checks, so three things are load-bearing:

The filter lists are fetched from [uAssets](https://github.com/uBlockOrigin/uAssets) and EasyList/EasyPrivacy **directly**, never proxied through a NoMercyLabs endpoint. A proxy would be the single most convenient way to build a browsing-history dataset by accident, and its absence is what makes the claim checkable.

A seed list ships inside the APK, so a television is protected on its first page load rather than after its first successful fetch. The seed is authored here and small; the upstream lists are large, their licensing is [contested](https://github.com/uBlockOrigin/uBlock-issues/wiki/Filter-list-licenses), and vendoring them into this repository would relicense work that is not ours. They are fetched and attributed, never bundled.

WebView's Safe Browsing check is already off in the manifest, which is the component that reports browsing activity to Google.

## The matcher, and why it is not a regex per rule

A blocklist is roughly eighty thousand rules and a page is a hundred requests. Compiling a regex per rule and running all of them per request is eight million regex evaluations per page, on a processor that struggles with the page itself.

So rules are indexed by a token. Every rule carries one literal run of at least four characters taken from its pattern — `doubleclick`, `/ads/`, `analytics` — and lives in a bucket keyed by it. A request is tokenised the same way, and only the buckets its own tokens name are tested. A rule with no usable literal goes in a small catch-all bucket that is always tested, and keeping that bucket small is the whole performance story.

The pattern language is Adblock Plus syntax, in the subset that carries the lists' weight: `||` domain anchor, `|` start and end anchors, `*` wildcard, `^` separator, `@@` exception, and the `third-party`, `domain=` and resource-type options. Matching is a hand-written walk rather than a compiled pattern, because the token index has already reduced the candidates to a handful and a walk has no compilation cost at load.

## Resource types are inferred, not given

`shouldInterceptRequest` hands over a URL, whether it is the main frame, and headers. It does **not** say whether the browser asked for a script, an image or a stylesheet, which is a fact every desktop blocker has and this one does not.

So the type is inferred: main frame is a document, and everything else is read from the `Accept` header where it is specific enough, falling back to the file extension. This is honest guesswork and it is recorded as such — a rule scoped to `$script` will occasionally not fire where a desktop blocker would. The alternative, ignoring type options entirely, is worse: it would apply image rules to scripts and break pages.

## Cosmetic rules are a separate mechanism

`shouldInterceptRequest` cannot hide an element that was never requested, so element hiding is a CSS injection at document start, through the CSSOM, for the same CSP reason everything else we inject goes that way.

Injection order matters more than it looks. A cosmetic rule is a selector written by strangers, and our own overlays are in the same document — the focus ring, the caption rule. Ours are applied in their own sheets and carry a reserved `nm-` namespace that no list rule can name, so a rule hiding `.ring` cannot take our focus ring with it.

## Cookies

Third-party cookies were already blocked when the WebView was configured. What this slice adds is the end of a session: cookies are wiped when the browser exits, with a per-site allowlist for sites somebody deliberately stays signed in to.

The allowlist is the important half. A television is shared, and a browser that silently keeps every session is a browser that hands the next person in the room somebody else's mail. One that keeps none is a browser nobody can stay signed in to. The choice is per site and it is theirs.

## Failure modes this slice must not ship

1. **A blocked request that breaks the page.** An exception rule must win over a block rule, always, or well-known sites stop working and the cause is invisible.
2. **A filter rule hiding our own chrome.** Reserved namespace, separate sheet.
3. **A list fetch on the UI thread**, or on every launch. Weekly, off-thread, and a failure leaves the previous list in place rather than leaving the browser unprotected.
4. **Blocking the main frame.** A page the user typed is never blocked, whatever the lists say about it.
5. **A matcher that allocates per request.** A hundred requests per page on a weak processor is where a browser feels slow for reasons nobody can point at.

## Consequences carried forward

- The blocked count is per tab, so it belongs to the tab rather than to the activity, and a suspended tab keeps it.
- Slice 12's announcer is what a blocked-content notice speaks through; it needs nothing new.
- Any future setting that turns filtering off per site shares the site-settings table permissions already use.

## Acceptance

Unit tests for the parser and the matcher: each anchor, the separator, wildcards, exceptions beating blocks, `domain=` includes and excludes, third-party detection, the token index returning the same verdict as a brute-force scan over the same rules, and cosmetic rules selected by host and by parent domain.

On the 8010 at `192.168.2.21`: a page loads with requests blocked and the count visible, the same page loads with filtering off and the count is zero, and a site known to break under aggressive blocking still works.
