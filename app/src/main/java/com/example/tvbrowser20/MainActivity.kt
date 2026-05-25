package com.example.tvbrowser20

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.view.WindowInsetsController
import android.webkit.WebChromeClient
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.tvbrowser20.data.Channel
import com.example.tvbrowser20.data.JsKey
import com.example.tvbrowser20.data.Source
import com.example.tvbrowser20.data.SourceConfig
import com.example.tvbrowser20.databinding.ActivityMainBinding
import com.example.tvbrowser20.ui.ChannelAdapter
import com.example.tvbrowser20.ui.SourceTabAdapter
import com.example.tvbrowser20.web.JsInjector
import com.example.tvbrowser20.web.TvWebChromeClient
import com.example.tvbrowser20.web.TvWebViewClient
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var channelAdapter: ChannelAdapter
    private lateinit var sourceTabAdapter: SourceTabAdapter

    private val sources: List<Source> = SourceConfig.SOURCES

    // All domains from all sources, used by the whitelist
    private val allAllowedDomains: Set<String> by lazy {
        sources.flatMap { it.allowedDomains }.toSet()
    }

    // State
    private var activeSrcIdx  = 0    // which source tab is selected
    private var focusedChIdx  = 0    // keyboard-highlighted channel in current source
    private var playingChIdx  = -1   // channel that is actually playing
    private var playingSrcIdx = -1   // source that is playing
    private var sidebarVisible = true
    private var currentJsKey   = ""  // JS key for the current source
    private var errorShown     = false
    private var lastBackPressMs = 0L

    private var fullscreenView: View? = null
    private var famelackManualSidebarOpenUntilMs = 0L

    // Per-source web channel lists (keyed by source id)
    private val webChannelsMap = mutableMapOf<String, List<Channel>>()
    private val webPageLoadedSet = mutableSetOf<String>()

    private fun currentSourceId(): String = sources[activeSrcIdx].id

    private fun getActiveChannels(): List<Channel> {
        val srcId = currentSourceId()
        return if (JsKey.usesWebChannelList(currentJsKey) && webChannelsMap.containsKey(srcId)) {
            webChannelsMap[srcId]!!
        } else {
            sources[activeSrcIdx].channels
        }
    }

    private fun isWebChannelPageLoaded(): Boolean {
        return JsKey.usesWebChannelList(currentJsKey) && webPageLoadedSet.contains(currentSourceId())
    }

    private fun setChannelListLoading(show: Boolean, loaded: Int? = null, total: Int? = null) {
        binding.channelLoadingOverlay.visibility = if (show) View.VISIBLE else View.GONE
        if (!show) return

        val loadedVal = (loaded ?: 0).coerceAtLeast(0)
        val totalVal = (total ?: 0).coerceAtLeast(0)
        if (totalVal > 0) {
            val pct = ((loadedVal * 100f) / totalVal.toFloat()).toInt().coerceIn(0, 100)
            binding.channelLoadingProgress.isIndeterminate = false
            binding.channelLoadingProgress.progress = pct
            binding.channelLoadingText.text = "频道加载中... $loadedVal/$totalVal"
        } else {
            binding.channelLoadingProgress.isIndeterminate = true
            binding.channelLoadingText.text = "频道加载中..."
        }
    }

    // JS interface for receiving data from the web page
    private inner class TvBridgeInterface {
        @JavascriptInterface
        fun onChannelList(json: String) {
            runOnUiThread {
                try {
                    val arr = JSONArray(json)
                    val channels = (0 until arr.length()).map { i ->
                        val name = arr.getString(i)
                        Channel(
                            id = "web_${activeSrcIdx}_$i",
                            channelNum = "${i + 1}",
                            name = name,
                            sub = ""
                        )
                    }
                    val srcId = currentSourceId()
                    webChannelsMap[srcId] = channels
                    webPageLoadedSet.add(srcId)

                    if (JsKey.usesWebChannelList(currentJsKey)) {
                        channelAdapter.setChannels(channels)
                        channelAdapter.setFocused(0)
                        channelAdapter.setPlaying(0)
                        setChannelListLoading(false)
                        focusedChIdx = 0
                        playingChIdx = 0
                        playingSrcIdx = activeSrcIdx
                        binding.chCount.text = getString(
                            R.string.channel_count_fmt,
                            sources[activeSrcIdx].label,
                            channels.size
                        )
                        if (channels.isNotEmpty()) {
                            binding.nowSrcPill.text = sources[activeSrcIdx].label
                            binding.nowName.text = channels[0].channelNum
                            binding.nowProg.text = channels[0].name
                        }
                    }
                    Log.d("MainActivity", "Web channel list received for $srcId: ${channels.size} items")
                } catch (e: Exception) {
                    setChannelListLoading(false)
                    Log.e("MainActivity", "Failed to parse channel list", e)
                }
            }
        }

        @JavascriptInterface
        fun onChannelLoadProgress(loaded: Int, total: Int) {
            runOnUiThread {
                if (!JsKey.usesWebChannelList(currentJsKey) || isWebChannelPageLoaded()) return@runOnUiThread
                setChannelListLoading(true, loaded = loaded, total = total)
            }
        }

        @JavascriptInterface
        fun onFamelackLayoutReady() {
            runOnUiThread {
                if (currentJsKey == JsKey.FAMELACK) {
                    // If user just opened sidebar manually, don't auto-collapse immediately.
                    val now = System.currentTimeMillis()
                    if (sidebarVisible && now < famelackManualSidebarOpenUntilMs) {
                        return@runOnUiThread
                    }
                    setSidebarVisible(false)
                    binding.webView.postDelayed({
                        binding.webView.evaluateJavascript(
                            "typeof TVB_enterFullscreen==='function'&&TVB_enterFullscreen()",
                            null
                        )
                    }, 300)
                }
            }
        }
    }

    // Clock
    private val clockHandler  = Handler(Looper.getMainLooper())
    private val clockRunnable = object : Runnable {
        override fun run() {
            binding.clockText.text = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            clockHandler.postDelayed(this, 1000)
        }
    }

    // UI auto-hide timers
    private val uiHandler = Handler(Looper.getMainLooper())
    private val NOW_BAR_TIMEOUT_MS = 3_000L
    private val SIDEBAR_TIMEOUT_MS = 3_000L

    // Sidebar / nowBar animation sizes in px
    private val sidebarWidthPx: Int by lazy {
        (360 * resources.displayMetrics.density).toInt()
    }
    private val nowBarBasePadPx: Int by lazy {
        (44 * resources.displayMetrics.density).toInt()
    }
    private val nowBarBottomPadPx: Int by lazy {
        (18 * resources.displayMetrics.density).toInt()
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        hideSystemUI()

        setupWebView()
        setupSourceTabs()
        setupChannelList()

        clockHandler.post(clockRunnable)

        // Boot: select first source, first channel
        switchSource(0)
        selectChannel(0)
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
        binding.webView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.webView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        clockHandler.removeCallbacks(clockRunnable)
        uiHandler.removeCallbacks(hideNowBarRunnable)
        uiHandler.removeCallbacks(hideSidebarRunnable)
        binding.webView.apply {
            stopLoading()
            destroy()
        }
    }

    // ── System UI ─────────────────────────────────────────────────────────────

    private fun hideSystemUI() {
        // Must be called after setContentView so DecorView is attached
        window.decorView.post {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.let {
                    it.hide(
                        android.view.WindowInsets.Type.statusBars() or
                        android.view.WindowInsets.Type.navigationBars()
                    )
                    it.systemBarsBehavior =
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN              or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION         or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY        or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN       or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )
            }
        }
    }

    // ── WebView setup ─────────────────────────────────────────────────────────

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        binding.webView.settings.apply {
            javaScriptEnabled              = true
            domStorageEnabled              = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode               = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            useWideViewPort                = true
            loadWithOverviewMode           = true
            builtInZoomControls            = false
            displayZoomControls            = false
            allowFileAccess                = true
            cacheMode                      = WebSettings.LOAD_DEFAULT
            userAgentString = "Mozilla/5.0 (Linux; Android 10; TV) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/120.0.0.0 Mobile Safari/537.36"
        }

        binding.webView.webViewClient = TvWebViewClient(
            allowedDomains  = allAllowedDomains,
            onPageStarted   = { showLoading(true); showError(false) },
            onPageFinished  = { url -> onWebPageReady(url) },
            onError         = { msg -> showLoading(false); showErrorMessage(msg) }
        )
        binding.webView.webChromeClient = TvWebChromeClient(fullscreenHost = object : TvWebChromeClient.FullscreenHost {
            override fun onShowFullscreen(view: View, callback: WebChromeClient.CustomViewCallback) {
                if (fullscreenView != null) {
                    callback.onCustomViewHidden()
                    return
                }
                fullscreenView = view
                view.setBackgroundColor(android.graphics.Color.BLACK)
                binding.rootLayout.addView(
                    view,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                )
                binding.webView.visibility = View.GONE
                binding.sidebar.visibility = View.GONE
                binding.nowBar.visibility = View.GONE
                binding.menuHint.visibility = View.GONE
                hideSystemUI()
            }

            override fun onHideFullscreen() {
                fullscreenView?.let { v ->
                    if (v.parent === binding.rootLayout) {
                        binding.rootLayout.removeView(v)
                    }
                }
                fullscreenView = null
                binding.webView.visibility = View.VISIBLE
                binding.menuHint.visibility = View.VISIBLE
                if (currentJsKey == JsKey.FAMELACK) {
                    binding.nowBar.alpha = 0f
                } else {
                    binding.nowBar.visibility = View.VISIBLE
                }
                hideSystemUI()
            }
        })
        binding.webView.addJavascriptInterface(TvBridgeInterface(), "Android")

        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
    }

    private fun onWebPageReady(url: String) {
        showLoading(false)
        if (JsKey.usesWebChannelList(currentJsKey) && !isWebChannelPageLoaded()) {
            setChannelListLoading(true, loaded = 0, total = 0)
        }
        val js = JsInjector.getJs(currentJsKey)
        binding.webView.evaluateJavascript(js, null)
        Log.d("MainActivity", "JS injected for $url (key=$currentJsKey)")
    }

    // ── Source tabs ───────────────────────────────────────────────────────────

    private fun setupSourceTabs() {
        sourceTabAdapter = SourceTabAdapter { idx -> switchSource(idx) }
        binding.sourceTabsRv.apply {
            adapter      = sourceTabAdapter
            layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false)
        }
        sourceTabAdapter.setSources(sources)
    }

    // ── Channel list ──────────────────────────────────────────────────────────

    private fun setupChannelList() {
        channelAdapter = ChannelAdapter { idx -> selectChannel(idx) }
        binding.channelListRv.apply {
            adapter       = channelAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)
            itemAnimator  = null  // disable default flicker animation
        }
    }

    // ── Source switching ──────────────────────────────────────────────────────

    private fun switchSource(idx: Int) {
        activeSrcIdx = idx
        focusedChIdx = 0
        currentJsKey = sources[idx].jsKey

        sourceTabAdapter.setActive(idx)
        scrollSourceTabIntoView(idx)

        val src = sources[idx]
        val channels = getActiveChannels()
        val waitingWebChannels = JsKey.usesWebChannelList(currentJsKey) && !webPageLoadedSet.contains(src.id)
        setChannelListLoading(waitingWebChannels)
        channelAdapter.setChannels(channels)
        channelAdapter.setFocused(0)
        channelAdapter.setPlaying(
            if (playingSrcIdx == idx) playingChIdx else -1
        )

        binding.chCount.text = getString(
            R.string.channel_count_fmt, src.label, channels.size
        )
        binding.channelListRv.scrollToPosition(0)

        if (currentJsKey != JsKey.FAMELACK && !sidebarVisible) {
            setSidebarVisible(true)
        }

        // Web-channel sources: auto-load the page when switching to a new tab
        if (JsKey.usesWebChannelList(currentJsKey) && !webPageLoadedSet.contains(src.id)) {
            selectChannel(0)
        }
    }

    // ── Channel selection & playback ──────────────────────────────────────────

    private fun selectChannel(idx: Int) {
        val src = sources[activeSrcIdx]
        val channels = getActiveChannels()
        val ch = channels.getOrNull(idx) ?: return

        playingChIdx  = idx
        playingSrcIdx = activeSrcIdx
        focusedChIdx  = idx

        channelAdapter.setFocused(idx)
        channelAdapter.setPlaying(idx)
        scrollChannelIntoView(idx)

        // Update now-playing bar
        binding.nowSrcPill.text = src.label
        binding.nowName.text    = ch.channelNum
        binding.nowProg.text    = ch.name

        if (isWebChannelPageLoaded()) {
            // Page already loaded: switch channel via JS
            binding.webView.evaluateJavascript("TVB_select($idx)", null)
        } else {
            // Normal flow or first load: load URL
            showError(false)
            showLoading(true)
            val url = if (JsKey.usesWebChannelList(currentJsKey)) {
                src.channels.firstOrNull()?.url ?: ch.url
            } else {
                ch.url
            }
            binding.webView.loadUrl(url)
        }
    }

    // ── Sidebar show / hide with animation ───────────────────────────────────

    private fun setSidebarVisible(visible: Boolean) {
        if (sidebarVisible == visible) return
        sidebarVisible = visible

        // Slide sidebar
        val targetTX = if (visible) 0f else -sidebarWidthPx.toFloat()
        binding.sidebar.animate()
            .translationX(targetTX)
            .setDuration(280)
            .setInterpolator(FastOutSlowInInterpolator())
            .start()

        // Animate nowBar spacer width (shifts content right when sidebar appears)
        val targetSpacerW = if (visible) sidebarWidthPx else nowBarBasePadPx
        val startSpacerW  = binding.nowBarSpacer.layoutParams.width

        ValueAnimator.ofInt(startSpacerW, targetSpacerW).apply {
            duration     = 280
            interpolator = FastOutSlowInInterpolator()
            addUpdateListener { anim ->
                val w = anim.animatedValue as Int
                binding.nowBarSpacer.layoutParams =
                    binding.nowBarSpacer.layoutParams.also { it.width = w }
                binding.nowBarSpacer.requestLayout()
            }
            start()
        }

        if (visible) {
            // Sidebar open: nowBar always fully visible, cancel nowBar auto-hide
            uiHandler.removeCallbacks(hideNowBarRunnable)
            binding.nowBar.animate().cancel()
            binding.nowBar.alpha = 1f
            // Start sidebar auto-hide countdown
            uiHandler.removeCallbacks(hideSidebarRunnable)
            uiHandler.postDelayed(hideSidebarRunnable, SIDEBAR_TIMEOUT_MS)
            // Move focus to channel list
            binding.channelListRv.requestFocus()
        } else {
            // Entering fullscreen: hide nowBar immediately, cancel sidebar timer
            uiHandler.removeCallbacks(hideNowBarRunnable)
            uiHandler.removeCallbacks(hideSidebarRunnable)
            binding.nowBar.animate().cancel()
            binding.nowBar.alpha = 0f
            // Return focus to WebView
            binding.webView.requestFocus()
        }
    }

    /**
     * Called when navigating channels with UP/DOWN in fullscreen.
     * Shows nowBar at semi-transparent alpha and starts the 3-second hide countdown.
     */
    private fun peekNowBar() {
        if (sidebarVisible) return
        binding.nowBar.animate().cancel()
        binding.nowBar.animate()
            .alpha(1f)
            .setDuration(200)
            .start()
        uiHandler.removeCallbacks(hideNowBarRunnable)
        uiHandler.postDelayed(hideNowBarRunnable, NOW_BAR_TIMEOUT_MS)
    }

    private val hideNowBarRunnable = Runnable {
        binding.nowBar.animate()
            .alpha(0f)
            .setDuration(400)
            .start()
    }

    // Sidebar auto-hide after inactivity
    private val hideSidebarRunnable = Runnable {
        if (sidebarVisible) setSidebarVisible(false)
    }

    private fun resetSidebarTimer() {
        uiHandler.removeCallbacks(hideSidebarRunnable)
        if (sidebarVisible) {
            uiHandler.postDelayed(hideSidebarRunnable, SIDEBAR_TIMEOUT_MS)
        }
    }

    // ── Key event handling ────────────────────────────────────────────────────

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event)

        val keyCode = event.keyCode
        val channels = getActiveChannels()

        return when (keyCode) {

            // ── D-pad Up ─────────────────────────────────────────────────────
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (sidebarVisible) {
                    resetSidebarTimer()
                    if (focusedChIdx > 0) {
                        focusedChIdx--
                        channelAdapter.setFocused(focusedChIdx)
                        scrollChannelIntoView(focusedChIdx)
                    }
                } else if (JsKey.usesWebChannelList(currentJsKey)) {
                    if (focusedChIdx > 0) {
                        focusedChIdx--
                        playingChIdx = focusedChIdx
                        binding.webView.evaluateJavascript("TVB_select($focusedChIdx)", null)
                        updateNowBarForFocused()
                        peekNowBar()
                    }
                } else {
                    if (focusedChIdx > 0) {
                        focusedChIdx--
                        selectChannel(focusedChIdx)
                        peekNowBar()
                    }
                }
                true
            }

            // ── D-pad Down ───────────────────────────────────────────────────
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (sidebarVisible) {
                    resetSidebarTimer()
                    if (focusedChIdx < channels.size - 1) {
                        focusedChIdx++
                        channelAdapter.setFocused(focusedChIdx)
                        scrollChannelIntoView(focusedChIdx)
                    }
                } else if (JsKey.usesWebChannelList(currentJsKey)) {
                    if (focusedChIdx < channels.size - 1) {
                        focusedChIdx++
                        playingChIdx = focusedChIdx
                        binding.webView.evaluateJavascript("TVB_select($focusedChIdx)", null)
                        updateNowBarForFocused()
                        peekNowBar()
                    }
                } else {
                    if (focusedChIdx < channels.size - 1) {
                        focusedChIdx++
                        selectChannel(focusedChIdx)
                        peekNowBar()
                    }
                }
                true
            }

            // ── D-pad Left: previous source (only when sidebar visible) ──────
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (sidebarVisible) {
                    resetSidebarTimer()
                    val prev = (activeSrcIdx - 1 + sources.size) % sources.size
                    switchSource(prev)
                }
                true
            }

            // ── D-pad Right: next source (only when sidebar visible) ─────────
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (sidebarVisible) {
                    resetSidebarTimer()
                    val next = (activeSrcIdx + 1) % sources.size
                    switchSource(next)
                }
                true
            }

            // ── OK / Enter ───────────────────────────────────────────────────
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (sidebarVisible) {
                    resetSidebarTimer()
                    selectChannel(focusedChIdx)
                } else {
                    selectChannel(focusedChIdx)
                    peekNowBar()
                }
                true
            }

            // ── Menu: toggle sidebar ─────────────────────────────────────────
            KeyEvent.KEYCODE_MENU -> {
                if (!sidebarVisible && currentJsKey == JsKey.FAMELACK) {
                    famelackManualSidebarOpenUntilMs = System.currentTimeMillis() + 8_000L
                }
                setSidebarVisible(!sidebarVisible)
                true
            }

            // ── Back: exit HTML fullscreen / toggle sidebar / double-press exit ─
            KeyEvent.KEYCODE_BACK -> {
                if (fullscreenView != null) {
                    (binding.webView.webChromeClient as? TvWebChromeClient)?.dismissCustomView()
                    true
                } else {
                    val now = System.currentTimeMillis()
                    if (now - lastBackPressMs < 2_000L) {
                        finish()
                    } else {
                        lastBackPressMs = now
                        if (!sidebarVisible && currentJsKey == JsKey.FAMELACK) {
                            famelackManualSidebarOpenUntilMs = now + 8_000L
                        }
                        setSidebarVisible(!sidebarVisible)
                    }
                    true
                }
            }

            // ── OK on error screen: retry ────────────────────────────────────
            else -> {
                if (errorShown && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                            keyCode == KeyEvent.KEYCODE_ENTER)) {
                    retryCurrentChannel()
                    true
                } else {
                    super.dispatchKeyEvent(event)
                }
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun updateNowBarForFocused() {
        val src = sources[activeSrcIdx]
        val ch  = getActiveChannels().getOrNull(focusedChIdx) ?: return
        binding.nowSrcPill.text = src.label
        binding.nowName.text    = ch.channelNum
        binding.nowProg.text    = ch.name
    }

    private fun scrollChannelIntoView(position: Int) {
        val lm = binding.channelListRv.layoutManager as? LinearLayoutManager ?: return
        val first = lm.findFirstVisibleItemPosition()
        val last  = lm.findLastVisibleItemPosition()
        if (position < first || position > last) {
            binding.channelListRv.smoothScrollToPosition(position)
        }
    }

    private fun scrollSourceTabIntoView(position: Int) {
        val rv = binding.sourceTabsRv
        rv.post {
            val lm = rv.layoutManager as? LinearLayoutManager ?: return@post
            val first = lm.findFirstVisibleItemPosition()
            val last = lm.findLastVisibleItemPosition()
            if (position in first..last) return@post

            // Center selected source tab when possible.
            val offset = (rv.width * 0.3f).toInt()
            lm.scrollToPositionWithOffset(position, offset)
        }
    }

    private fun showLoading(show: Boolean) {
        binding.loadingProgress.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showError(show: Boolean) {
        errorShown = show
        binding.errorLayout.visibility = if (show) View.VISIBLE else View.GONE
        if (show) setChannelListLoading(false)
    }

    private fun showErrorMessage(msg: String) {
        showError(true)
        binding.errorText.text = msg
    }

    private fun retryCurrentChannel() {
        if (playingSrcIdx >= 0 && playingChIdx >= 0) {
            showError(false)
            if (isWebChannelPageLoaded()) {
                binding.webView.evaluateJavascript("TVB_select($playingChIdx)", null)
            } else {
                val ch = sources.getOrNull(playingSrcIdx)?.channels?.getOrNull(playingChIdx) ?: return
                showLoading(true)
                binding.webView.loadUrl(ch.url)
            }
        }
    }
}
