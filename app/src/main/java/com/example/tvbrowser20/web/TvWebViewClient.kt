package com.example.tvbrowser20.web

import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.util.Log

class TvWebViewClient(
    private val allowedDomains: Set<String>,
    private val onPageStarted: () -> Unit,
    private val onPageFinished: (url: String) -> Unit,
    private val onError: (message: String) -> Unit
) : WebViewClient() {

    private val tag = "TvWebViewClient"

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url  = request.url?.toString() ?: return true
        val host = request.url?.host       ?: return true

        // Always allow data URIs and blob URLs (used by video players internally)
        if (url.startsWith("data:") || url.startsWith("blob:")) return false

        val allowed = allowedDomains.any { domain ->
            host == domain || host.endsWith(".$domain")
        }

        if (!allowed) {
            Log.w(tag, "Blocked navigation to: $url")
            return true  // block
        }
        Log.d(tag, "Allowing navigation to: $url")
        return false     // allow
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        onPageStarted()
    }

    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)
        onPageFinished(url)
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError
    ) {
        super.onReceivedError(view, request, error)
        // Only report errors for the main frame load, not sub-resources
        if (request.isForMainFrame) {
            val msg = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                "错误 ${error.errorCode}: ${error.description}"
            } else {
                "页面加载失败"
            }
            Log.e(tag, "Main frame error: $msg for ${request.url}")
            onError(msg)
        }
    }
}
