package com.example.tvbrowser20.web

import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView

class TvWebChromeClient : WebChromeClient() {

    private val tag = "TvWebChromeClient"

    override fun onConsoleMessage(message: ConsoleMessage): Boolean {
        // Surface JS logs tagged with [TVBrowser] for easier debugging
        val text = message.message()
        if (text.contains("[TVBrowser]")) {
            Log.d(tag, "JS: $text (${message.sourceId()}:${message.lineNumber()})")
        }
        return true
    }

    // Allow HTML5 media to play fullscreen if the site requests it
    override fun onShowCustomView(view: android.view.View, callback: CustomViewCallback) {
        // Let the system handle it; our JS injection keeps player visible anyway
        callback.onCustomViewHidden()
    }

    override fun onHideCustomView() {
        super.onHideCustomView()
    }

    override fun getDefaultVideoPoster(): android.graphics.Bitmap? = null
}
