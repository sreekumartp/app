# Keep the JavaScript bridge methods called from the web page.
-keepclassmembers class io.github.sreekumartp.aimlatlas.AtlasBridge {
    @android.webkit.JavascriptInterface <methods>;
}
