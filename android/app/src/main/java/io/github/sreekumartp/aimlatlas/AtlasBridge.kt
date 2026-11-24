package io.github.sreekumartp.aimlatlas

import android.content.Intent
import android.webkit.JavascriptInterface

/** Methods the atlas page can call as `window.AtlasNative.*`. */
class AtlasBridge(private val activity: MainActivity) {

    /** Opens the Android share sheet with a topic's syllabus and resource links. */
    @JavascriptInterface
    fun share(title: String, text: String) {
        activity.runOnUiThread {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, text)
            }
            activity.startActivity(Intent.createChooser(send, activity.getString(R.string.share_title)))
        }
    }
}
