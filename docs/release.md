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

## Track policy

Tag pushes build an AAB and publish to the **internal** track only. Promotion to production is a manual decision in the Play Console.

That is deliberate. A browser reaching every Android TV device is not something a tag push should be able to do by itself, and the Android TV quality review is worth passing before a wide audience sees the app.

## Before promoting to production

- Every function reachable with six keycodes on real hardware
- Media checks pass: media session published, audio focus taken, fullscreen entered and exited, background audio surviving HOME
- Accessibility pass with TalkBack enabled
- Data safety declaration still matches reality, which the CI analytics gate is what keeps honest
- Privacy policy URL live and matching `docs/legal/privacy-policy.md`
