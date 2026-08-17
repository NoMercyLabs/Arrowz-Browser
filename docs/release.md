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
| `PLAY_SERVICE_ACCOUNT_JSON` | Play Console service account JSON |

All five are set. The credential is a key for
`play-publisher@nomercy-tv.iam.gserviceaccount.com`, an account that already
existed for NoMercy TV and is already linked to the Play developer account: it
authenticates and opens a release edit on `tv.nomercy.app`, so the credential
half of publishing is proven rather than assumed.

What it cannot do yet is reach `com.nomercylabs.arrowz`, which answers
`PERMISSION_DENIED`. There is no app-creation call in the Play Developer API, so
no credential can fix that. The app has to be created once in the Play Console,
by hand, and the upload works from the next tag onward.

### Minting a Play service account

Kept because the current key is long-lived rather than permanent, and this is
what replacing it looks like. Every step is on `console.cloud.google.com` until
it says otherwise. The first four are `gcloud` commands if it is installed and
signed in, which is faster and leaves no key in a downloads folder:

```
gcloud services enable androidpublisher.googleapis.com --project <project>
gcloud iam service-accounts keys create key.json \
  --iam-account play-publisher@<project>.iam.gserviceaccount.com
```

1. Create or pick a Google Cloud project. It exists only to own the key; nothing
   is billed and nothing runs in it.
2. Enable the **Google Play Android Developer API** for that project. Without
   this the credential authenticates and then fails every call, which reads as a
   permissions problem and is not one.
3. **IAM & Admin → Service Accounts → Create.** Give it a name that says what it
   is, like `play-publisher`. Grant it no project roles — the permissions that
   matter are granted in the Play Console, not here, and a project role only
   widens what the key can reach if it leaks.
4. On the new account, **Keys → Add key → Create new key → JSON.** The file
   downloads once and cannot be re-downloaded. This is the secret.
5. Move to `play.google.com/console`. **Users and permissions → Invite new
   user**, and invite the service account by its email address, which looks like
   `play-publisher@<project>.iam.gserviceaccount.com`.
6. Grant it **Release to testing tracks** on the Arrowz Browser app, and nothing
   else. It does not need account-level permissions, and it must not have
   production release rights: promotion is a decision made by a person.

Then, from this repository:

```
gh secret set PLAY_SERVICE_ACCOUNT_JSON < path/to/downloaded-key.json
```

Delete the downloaded file afterwards. It is in the repository secrets now, and
a second copy in a downloads folder is a second thing that can leak.

### The first Arrowz tag

`v0.1.0` predates the rename. It builds `com.nomercylabs.browser` under the old
name, and re-running a tag replays the workflow as it stood at that tag rather
than as it stands now, so it would also name its artifacts `nomercy-browser-*`.

The rename therefore needs a new tag rather than a re-run. `v0.1.1` is cut, and
publishes to GitHub whether or not Play accepts the bundle: a rejected upload
warns on the run page and in the job summary, and never retracts artifacts that
are already built, signed and attached.

## What a tag produces

Pushing a `v*` tag builds both artifacts from the same signed build and attaches
them to a GitHub release, along with their SHA-256 sums:

- `arrowz-browser-<version>.aab` — what Play takes.
- `arrowz-browser-<version>.apk` — what somebody sideloads onto a television
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
