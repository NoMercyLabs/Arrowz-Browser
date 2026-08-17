# Releasing

## Signing

The upload key is generated once and never regenerated. Play App Signing means Google holds the app signing key; the upload key only proves that a build came from us. Losing it is recoverable by contacting Play support, but losing it is still avoidable.

It lives **outside this repository** so that no `git add -f` can ever capture it:

```
C:/Projects/NoMercyLabs/.keys/nomercy-browser-upload.jks
C:/Projects/NoMercyLabs/.keys/nomercy-browser-upload.password
```

RSA 4096, alias `upload`, valid 10950 days. Put the password in a password manager and treat the file beside the key as a convenience copy, not the record of truth.

## Local release build

```
export NM_KEYSTORE_PATH=C:/Projects/NoMercyLabs/.keys/nomercy-browser-upload.jks
export NM_KEYSTORE_PASSWORD=...
export NM_KEY_ALIAS=upload
export NM_KEY_PASSWORD=...
./gradlew bundleRelease
```

Without `NM_KEYSTORE_PATH` set, the release build is unsigned rather than failing, so that anyone can build the project without possessing the key.

## CI secrets

Set these on the GitHub repository once it exists:

| Secret | Value |
|---|---|
| `NM_KEYSTORE_BASE64` | `base64 -w0` of the `.jks` file |
| `NM_KEYSTORE_PASSWORD` | keystore password |
| `NM_KEY_ALIAS` | `upload` |
| `NM_KEY_PASSWORD` | key password, same as the keystore password |
| `PLAY_SERVICE_ACCOUNT_JSON` | Play Console service account JSON, once the listing exists |

## What a tag produces

Pushing a `v*` tag builds both artifacts from the same signed build and attaches
them to a GitHub release, along with their SHA-256 sums:

- `nomercy-browser-<version>.aab` — what Play takes.
- `nomercy-browser-<version>.apk` — what somebody sideloads onto a television
  Play has not reached. A release that ships only the bundle leaves those people
  with nothing.
- `SHA256SUMS.txt` — so a download can be checked against what the pipeline
  built rather than trusted because it came from the right page.

They are release assets rather than workflow artifacts on purpose: a workflow
artifact expires and needs a GitHub login to fetch, which is not what
downloadable means.

## Track policy

Tag pushes build an AAB and publish to the **internal** track only. Promotion to production is a manual decision in the Play Console.

That is deliberate. A browser reaching every Android TV device is not something a tag push should be able to do by itself, and the Android TV quality review is worth passing before a wide audience sees the app.

## The release build is a different program

R8 runs only on the release variant, and it has already broken this app once in
a way nothing else would have caught: the minified build reported `Displayed` in
1.1 seconds, added its window, loaded WebView and rendered a black screen, with
no crash and nothing in logcat. `-dontoptimize` in `app/proguard-rules.pro` is
what makes it render, and the comment there records the bisect that found it.

So a release build is never assumed to work because the debug build does. CI
builds `assembleRelease` on every push, which catches the R8 failures that fail
loudly. The one that does not fail loudly needs a screen:

```
keytool -genkeypair -keystore verify.jks -alias verify -keyalg RSA     -validity 30 -dname "CN=Release Verification"
NM_KEYSTORE_PATH=$PWD/verify.jks NM_KEYSTORE_PASSWORD=... NM_KEY_ALIAS=verify     NM_KEY_PASSWORD=... ./gradlew assembleRelease
./deploy.sh -d 192.168.2.80:5555 --release
```

That keystore is a throwaway for driving the app on hardware and is never the
upload key. Open a page, enter focus mode, and check the tracker count in the
menu: those three together prove the injected scripts, the JavaScript bridges
and the request filter all survived minification.

Then drive it, because "it rendered" and "it navigates" are different claims and
the black screen passed the first one for a while:

```
node tools/release-walk.mjs 192.168.2.80:5555 8 /tmp/release-frames \
  https://en.wikipedia.org/wiki/Television
```

The release variant is not debuggable, so it carries no devtools socket and the
harness that reads focus over CDP cannot see it at all. This presses a key, takes
a screenshot and compares the frames: a press that changed nothing is a frame
identical to the one before it, and a black screen is a run of identical frames
from the first press. It reports how many frames matched their predecessor,
which must be zero, and leaves the images behind so the focus ring can be looked
at rather than inferred.

This is a hardware check and cannot move into CI. CI has no screen and no
television, and the failure it exists to catch is one that builds cleanly, exits
zero and shows nothing.

## Before promoting to production

- Every function reachable with six keycodes on real hardware
- `tools/release-walk.mjs` run against the **release** build, reporting no frame identical to the one before it
- Media checks pass: media session published, audio focus taken, fullscreen entered and exited, background audio surviving HOME
- Accessibility pass with TalkBack enabled
- Data safety declaration still matches reality, which the CI analytics gate is what keeps honest
- Privacy policy URL live and matching `docs/legal/privacy-policy.md`
