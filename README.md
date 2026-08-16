# NoMercy Browser

A web browser for Android TV that is actually operable from a remote, plays media the way a real browser does, and sends nothing about your browsing anywhere.

Most TV browsers fail in the same two places. They fake a mouse badly, so navigating any page is a fight. And they treat video as whatever the system WebView does by default, which means no fullscreen, no now playing, no audio focus and no background audio. This project treats input and media as the product rather than as details.

## What it does

Every function is reachable from six keys: the four directions, OK, and BACK. That is what a plain Chromecast voice remote gives you, and it is the floor the whole interface is designed against. Remotes with more buttons are welcome to have them, but nothing is ever hidden behind a key your remote may not have.

Media works the way you expect from a browser on a phone. Video goes fullscreen, playback publishes a real media session so transport controls and headset buttons work, audio focus is respected so the browser does not talk over other apps, and audio keeps playing when you leave the app.

Privacy is the default rather than a setting. There is no analytics code in the project, WebView's Safe Browsing reporting is turned off, trackers are blocked from filter lists fetched straight from their public upstream sources, and the app never contacts a server belonging to us.

## Status

Early. The scaffold builds and runs on Android TV. Features land slice by slice, and each slice is accepted only when everything it added can be reached using six keycodes and nothing else.

## Building

You need JDK 21 and an Android SDK with platform 36.

```
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The app ships no native code of its own. One artifact runs on every processor Android TV uses, so there are no ABI splits to pick between.

## What it cannot do

Netflix, Prime Video and Disney+ refuse to serve browsers, and that is their policy rather than a limitation here. The same is true of Brave on a phone. Other services that use encrypted media play normally.

## Legal

- [Privacy policy](docs/legal/privacy-policy.md)
- [Terms of service](docs/legal/terms-of-service.md)
- [Security policy](SECURITY.md)

## Licence

MIT. Copyright 2026 NoMercy Labs.
