package com.example.tvbrowser20.web

import android.util.Log
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient

class TvWebChromeClient(
    private val fullscreenHost: FullscreenHost? = null
) : WebChromeClient() {

    interface FullscreenHost {
        fun onShowFullscreen(view: View, callback: CustomViewCallback)
        fun onHideFullscreen()
    }

    private val tag = "TvWebChromeClient"
    private var customViewCallback: CustomViewCallback? = null

    override fun onConsoleMessage(message: ConsoleMessage): Boolean {
        val text = message.message()
        if (text.contains("[TVBrowser]")) {
            Log.d(tag, "JS: $text (${message.sourceId()}:${message.lineNumber()})")
        }
        return true
    }

    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
        if (fullscreenHost == null) {
            Log.w(tag, "onShowCustomView: no host, ignoring")
            callback.onCustomViewHidden()
            return
        }
        if (customViewCallback != null) {
            callback.onCustomViewHidden()
            return
        }
        customViewCallback = callback
        fullscreenHost.onShowFullscreen(view, callback)
    }

    override fun onHideCustomView() {
        if (customViewCallback == null) return
        fullscreenHost?.onHideFullscreen()
        customViewCallback = null
    }

    fun dismissCustomView() {
        val callback = customViewCallback ?: return
        // Null out first so callback-driven onHideCustomView won't run twice.
        customViewCallback = null
        fullscreenHost?.onHideFullscreen()
        callback.onCustomViewHidden()
    }

    override fun getDefaultVideoPoster(): android.graphics.Bitmap? = null
}
