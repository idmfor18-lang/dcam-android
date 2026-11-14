-keepattributes *Annotation*
-dontwarn android.webkit.**
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
