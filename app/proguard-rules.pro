# Bridge objects exposed to page JavaScript are reached only by reflection from
# the WebView, so R8 cannot see the call sites and would strip them.
-keepclassmembers class com.nomercylabs.browser.**.*Bridge {
    @android.webkit.JavascriptInterface <methods>;
}
