# Privacy policy

**NoMercy Browser** for Android TV
Published by NoMercy Labs. Last updated 16 August 2026.

## The short version

We collect nothing. The app contains no analytics, no crash reporting, no advertising identifiers and no accounts, and it never sends a request to any server belonging to NoMercy Labs. Your browsing stays on your device.

That claim is only worth something if it is checkable, so the app is open source and the build fails automatically if an analytics dependency is ever added to it.

The rest of this document explains the parts that are less obvious, because a browser inevitably causes network traffic that we do not control.

## What is stored on your device

Bookmarks, browsing history, open tabs, your settings, cookies and site data from pages you visit, and per site permissions such as camera or location decisions.

All of it is stored locally. None of it is transmitted anywhere by the app. Uninstalling removes it. Clearing browsing data in settings removes it sooner.

## Network requests the app itself makes

There is one, and it does not go to us.

**Tracker and ad filter lists.** The app downloads filter lists from their public upstream sources, currently the uBlock Origin uAssets repository and the EasyList project. Those providers can see your device's IP address and the fact that a filter list was requested, the same as any other download from them. They receive nothing else, and they receive nothing about the pages you visit. You can turn list updating off in settings, in which case the app uses the copy bundled with the version you installed and makes no requests at all.

## Network requests caused by browsing

These are the ordinary consequences of using a browser, listed because they are easy to forget.

**Sites you visit** receive whatever a website normally receives, including your IP address and any information you enter. Third party requests made by those sites are blocked according to your filter list settings, but blocking is never total.

**Your chosen search engine** receives what you type into the address bar when it is a search rather than a URL. You pick that search engine when you first open the app and can change it in settings. We have no involvement in that exchange.

**Voice input**, if you use it, is handled by the speech recognition service built into your device, which on most Android TV hardware is provided by Google. The audio goes to that service under its own privacy policy, not to us. The app receives only the resulting text.

**Protected video.** Playing DRM protected content requires your device to obtain a licence. That process is performed by Android's own media DRM component together with the licence server operated by the site you are watching, and it can involve a device specific identifier. This happens below the app and we neither see nor control it. It only occurs when you play protected content.

## Safe Browsing is turned off

Android's WebView normally checks pages against Google's Safe Browsing service, which involves sending information about the pages being loaded. We disable it, because reporting your browsing to a third party is exactly what this browser exists to avoid.

The trade-off is real and you should know about it: the app does not warn you about known phishing or malware sites. Filter list blocking is not a substitute for that.

## What we never do

We do not sell data, because we do not have any. We do not share data with third parties, for the same reason. We do not build profiles, we do not use advertising identifiers, and we do not require or offer an account.

## Children

The app is a general purpose web browser and is not directed at children. It can reach any content on the web.

## Your rights

Because we hold no personal data about you, there is nothing for us to disclose, correct, export or erase on request. The data the app creates is on your device and entirely under your control, which is the strongest form of that right we can offer.

## Changes

If this policy changes, the updated version is published in this repository and the change is recorded in the changelog. Material changes will be noted in the app's release notes.

## Contact

Open an issue on the project repository, or use the security advisory form for anything sensitive.
