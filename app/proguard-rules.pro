# Bridge objects exposed to page JavaScript are reached only by reflection from
# the WebView, so R8 cannot see the call sites and would strip them.
#
# The pattern reaches nested classes as well, which matters: the media bridge's
# page interface is MediaSessionBridge$PageInterface, and the seeds report in
# build/outputs/mapping confirms its methods survive alongside FormBridge's.
-keepclassmembers class com.nomercylabs.arrowz.**.*Bridge {
    @android.webkit.JavascriptInterface <methods>;
}

# R8's optimizer draws nothing.
#
# Measured on the 8000, on the artifact the release pipeline actually produces:
# the minified build reported "Displayed" in 1.1s, added its window, registered
# its back callback, loaded WebView, and rendered a black screen. No crash, no
# exception, nothing in logcat from our process. It is the worst shape a bug can
# have, because every signal says the app is running.
#
# Bisected in three builds: shrinking and obfuscation are both fine -- the same
# APK renders correctly with only this line added -- so the fault is in the
# optimizer, not in anything being stripped or renamed. It costs nothing
# measurable: the bundle is 2,978,486 bytes with this line and 2,984,484 without
# it, so the optimizer was making the artifact slightly larger while breaking it.
#
# This is a blunt instrument and it is deliberate. Narrowing it to a specific
# -optimizations exclusion means shipping a build whose failure mode is a black
# screen if the guess is wrong, and no size saving is worth that.
-dontoptimize
