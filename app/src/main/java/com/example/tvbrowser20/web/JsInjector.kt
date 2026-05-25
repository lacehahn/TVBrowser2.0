package com.example.tvbrowser20.web

import com.example.tvbrowser20.data.JsKey

/**
 * Provides JavaScript injection strategies per source type.
 *
 * HOW TO ADD A NEW JS RULE FOR A SITE:
 *   1. Add a new private const val MY_SITE_JS = "..." with your injection code.
 *   2. Add a new JsKey constant in Source.kt.
 *   3. Map it in getJs() below.
 *   4. Set jsKey = JsKey.MY_SITE on the Source in SourceConfig.
 */
object JsInjector {

    fun getJs(jsKey: String): String = when (jsKey) {
        JsKey.NONE     -> ""
        JsKey.CCTV     -> CCTV_JS
        JsKey.YIBA     -> YIBA_JS
        JsKey.FAMELACK -> FAMELACK_JS
        else           -> DEFAULT_JS
    }

    // ── Default: works for most single-video pages ────────────────────────────
    private val DEFAULT_JS = """
(function() {
  'use strict';
  document.documentElement.style.cssText = 'margin:0;padding:0;overflow:hidden;background:#000';
  document.body.style.cssText            = 'margin:0;padding:0;overflow:hidden;background:#000';

  var PLAYER_SELECTORS = [
    'video',
    '.player', '#player', '#liveplayer', '#liveBox',
    '.video-player', '.live-player', '.live-player-wrap',
    '.m-player', '.dplayer', '.xgplayer',
    'iframe[src*="player"]', 'iframe[src*="live"]', 'iframe'
  ];

  function findPlayer() {
    for (var i = 0; i < PLAYER_SELECTORS.length; i++) {
      var el = document.querySelector(PLAYER_SELECTORS[i]);
      if (el) return el;
    }
    return null;
  }

  function fullscreenEl(el) {
    el.style.cssText = [
      'position:fixed', 'top:0', 'left:0', 'right:0', 'bottom:0',
      'width:100vw', 'height:100vh', 'z-index:999999',
      'background:#000', 'border:none', 'margin:0', 'padding:0'
    ].join(';');
  }

  function hideOthers(keep) {
    var children = document.body.children;
    for (var i = 0; i < children.length; i++) {
      var c = children[i];
      if (c !== keep && !c.contains(keep)) {
        c.style.setProperty('display', 'none', 'important');
      }
    }
  }

  function setup(player) {
    var ancestor = player;
    var p = player.parentElement;
    for (var i = 0; i < 4 && p && p !== document.body; i++) {
      if (p.classList.contains('player') ||
          p.classList.contains('video-player') ||
          p.classList.contains('live-player') ||
          p.id === 'player' || p.id === 'liveBox') {
        ancestor = p;
        break;
      }
      p = p.parentElement;
    }

    fullscreenEl(ancestor);
    hideOthers(ancestor);

    var video = (player.tagName === 'VIDEO') ? player : ancestor.querySelector('video');
    if (video) {
      video.autoplay    = true;
      video.controls    = true;
      video.muted       = false;
      video.style.cssText = 'width:100%;height:100%;object-fit:contain;background:#000';
      try { video.play(); } catch (e) {}
    }
    console.log('[TVBrowser] player fullscreened:', ancestor.tagName, ancestor.className || ancestor.id);
  }

  var tries = 0;
  function trySetup() {
    var player = findPlayer();
    if (player) {
      setup(player);
    } else if (tries++ < 15) {
      setTimeout(trySetup, 400);
    } else {
      console.log('[TVBrowser] no player found after retries');
    }
  }

  trySetup();
})();
""".trimIndent()

    // ── CCTV 官方页面 ─────────────────────────────────────────────────────────
    private val CCTV_JS = """
(function() {
  'use strict';
  document.documentElement.style.cssText = 'margin:0;padding:0;overflow:hidden;background:#000';
  document.body.style.cssText            = 'margin:0;padding:0;overflow:hidden;background:#000';

  var HIDE_SELECTORS = [
    'header', 'nav', 'footer', '.nav', '.header', '.footer',
    '.m-header', '.m-nav', '.m-footer',
    '.toolbar', '.live-info', '.live-detail',
    '.live-top', '.live-bottom', '.ad', '.advertisement',
    '.share', '.comment', '.social', '.recommend'
  ];

  function hideSiteChrome() {
    HIDE_SELECTORS.forEach(function(sel) {
      var els = document.querySelectorAll(sel);
      els.forEach(function(el) {
        el.style.setProperty('display', 'none', 'important');
      });
    });
  }

  function findCctvPlayer() {
    return document.querySelector('.live-player-wrap') ||
           document.querySelector('.liveBox')           ||
           document.querySelector('#liveBox')           ||
           document.querySelector('.m-player')          ||
           document.querySelector('.player')            ||
           document.querySelector('#player')            ||
           document.querySelector('video');
  }

  function setup(player) {
    player.style.cssText = [
      'position:fixed', 'top:0', 'left:0',
      'width:100vw', 'height:100vh', 'z-index:999999',
      'background:#000', 'border:none', 'margin:0', 'padding:0'
    ].join(';');

    hideSiteChrome();

    var video = player.querySelector('video') ||
                (player.tagName === 'VIDEO' ? player : null);
    if (video) {
      video.autoplay  = true;
      video.controls  = true;
      video.muted     = false;
      video.style.cssText = 'width:100%;height:100%;object-fit:contain';
      try { video.play(); } catch (e) {}
    }
    console.log('[TVBrowser/CCTV] ready:', player.className || player.id);
  }

  var tries = 0;
  function trySetup() {
    var player = findCctvPlayer();
    if (player) {
      setup(player);
    } else if (tries++ < 20) {
      setTimeout(trySetup, 500);
    }
  }

  // CCTV pages load JS late; give them a head start
  setTimeout(trySetup, 800);
})();
""".trimIndent()

    // ── yibababa 第三方页面 (频道列表 + 播放器全屏) ─────────────────────────
    private val YIBA_JS = """
(function() {
  'use strict';

  var currentIdx = -1;

  function getItems() {
    return Array.prototype.slice.call(
      document.querySelectorAll('#video-list li')
    );
  }

  function sendChannelList() {
    var items = getItems();
    var names = items.map(function(li) { return li.textContent.trim(); });
    try {
      Android.onChannelList(JSON.stringify(names));
      console.log('[TVBrowser/YIBA] sent ' + names.length + ' channels to Android');
    } catch(e) {
      console.log('[TVBrowser/YIBA] Android bridge not available');
    }
  }

  function fullscreenPlayer() {
    var container = document.getElementById('video-player-container');
    if (!container) return;
    container.style.cssText = [
      'position:fixed','top:0','left:0',
      'width:100vw','height:100vh','z-index:999999','background:#000'
    ].join(';');

    var list = document.getElementById('video-list-container');
    if (list) list.style.setProperty('display','none','important');

    var logo = document.getElementById('logo');
    if (logo) logo.style.setProperty('display','none','important');

    var tc = document.getElementById('tcplayer');
    if (tc) tc.style.cssText = 'width:100%!important;height:100%!important';

    var video = container.querySelector('video');
    if (video) {
      video.style.cssText = 'width:100%;height:100%;object-fit:contain;background:#000;transform:scale(1.05)';
      video.muted = false;
      try { video.play(); } catch(e) {}
    }
    console.log('[TVBrowser/YIBA] fullscreen idx=' + currentIdx);
  }

  function waitAndFullscreen() {
    var tries = 0;
    function check() {
      var video = document.querySelector('#video-player-container video');
      if (video && video.src) {
        fullscreenPlayer();
      } else if (tries++ < 30) {
        setTimeout(check, 300);
      }
    }
    setTimeout(check, 400);
  }

  function selectByIndex(idx) {
    var items = getItems();
    if (idx < 0 || idx >= items.length) return;
    currentIdx = idx;
    items[idx].click();
    waitAndFullscreen();
  }

  window.TVB_select = function(idx) {
    selectByIndex(idx);
  };

  window.TVB_getIdx = function() { return currentIdx; };
  window.TVB_getCount = function() { return getItems().length; };

  function init() {
    var items = getItems();
    if (items.length > 0) {
      sendChannelList();
      selectByIndex(0);
    } else {
      console.log('[TVBrowser/YIBA] no items in #video-list');
    }
  }

  var waitItems = 0;
  function waitForItems() {
    var items = getItems();
    if (items.length > 0) {
      init();
    } else if (waitItems++ < 20) {
      setTimeout(waitForItems, 500);
    }
  }

  setTimeout(waitForItems, 800);
})();
""".trimIndent()

    // ── Famelack — channel list + .video-player-wrapper 铺满 (0,0)，不隐藏页面其它元素 ─
    // 勿对 <video> 使用 object-fit / flex；勿改 <video> 的 position。
    private val FAMELACK_JS = """
(function() {
  'use strict';

  var currentIdx = -1;
  var TAG = '[TVBrowser/Famelack]';
  var YOUTUBE_FULLSCREEN_DELAY_MS = 5000;
  var youtubeDelayTimer = null;

  function clearYoutubeDelayTimer() {
    if (youtubeDelayTimer) {
      clearTimeout(youtubeDelayTimer);
      youtubeDelayTimer = null;
    }
  }

  function isYoutubeActive() {
    var yt = document.getElementById('youtube-player');
    if (!yt) return false;
    var st = getComputedStyle(yt);
    return st.display !== 'none' && yt.getBoundingClientRect().width > 80;
  }

  function hideYoutubeRightSidebar() {
    ['sidebar-items', 'sidebar-header'].forEach(function(id) {
      var el = document.getElementById(id);
      if (el) el.style.setProperty('display', 'none', 'important');
    });
    document.querySelectorAll('#global-header, .global-header, header.global-header')
      .forEach(function(el) {
        el.style.setProperty('display', 'none', 'important');
      });
  }

  function getSidebar() {
    return document.getElementById('sidebar-items');
  }

  function getChannelEntries() {
    var sidebar = getSidebar();
    if (!sidebar) return [];
    return Array.prototype.slice.call(
      sidebar.querySelectorAll('.sidebar-entry[data-channel-name]:not(.country-item)')
    );
  }

  /**
   * 仅将 .video-player-wrapper 铺满视口并固定在左上角 (0,0)。
   * 不隐藏、不删除页面其它 DOM；只调整 wrapper 及其内部播放器尺寸。
   */
  function expandPlayerWrapperFullscreen() {
    var wrapper = document.querySelector('#video-container .video-player-wrapper') ||
                  document.querySelector('.video-player-wrapper');
    if (!wrapper) {
      console.log(TAG, 'no .video-player-wrapper');
      return;
    }

    var vw = window.innerWidth || document.documentElement.clientWidth || 1280;
    var vh = window.innerHeight || document.documentElement.clientHeight || 720;

    // Keep original DOM hierarchy; enforce fullscreen styles with !important.
    wrapper.style.setProperty('position', 'fixed', 'important');
    wrapper.style.setProperty('top', '0px', 'important');
    wrapper.style.setProperty('left', '0px', 'important');
    wrapper.style.setProperty('right', '0px', 'important');
    wrapper.style.setProperty('bottom', '0px', 'important');
    wrapper.style.setProperty('width', '100vw', 'important');
    wrapper.style.setProperty('height', '100vh', 'important');
    wrapper.style.setProperty('z-index', '2147483646', 'important');
    wrapper.style.setProperty('margin', '0', 'important');
    wrapper.style.setProperty('padding', '0', 'important');
    wrapper.style.setProperty('transform', 'none', 'important');
    wrapper.style.setProperty('background', '#000', 'important');

    var vjs = wrapper.querySelector('#video-player') || document.getElementById('video-player');
    if (vjs) {
      vjs.classList.remove('video-player-dimensions');
      vjs.style.setProperty('width', '100%', 'important');
      vjs.style.setProperty('height', '100%', 'important');
    }

    var yt = wrapper.querySelector('#youtube-player') || document.getElementById('youtube-player');
    if (yt) {
      yt.style.setProperty('width', '100%', 'important');
      yt.style.setProperty('height', '100%', 'important');
    }

    window.scrollTo(0, 0);
    triggerPlayerResize();
    setTimeout(triggerPlayerResize, 500);
    setTimeout(triggerPlayerResize, 1500);
    console.log(TAG, '.video-player-wrapper at 0,0', vw + 'x' + vh);
  }

  function expandPlayerLayout() {
    expandPlayerWrapperFullscreen();
  }

  function triggerPlayerResize() {
    var vw = window.innerWidth || 1280;
    var vh = window.innerHeight || 720;
    try {
      if (typeof videojs !== 'undefined') {
        var p = videojs.getPlayer('video-player');
        if (p) {
          if (typeof p.dimensions === 'function') p.dimensions(vw, vh);
          if (typeof p.trigger === 'function') p.trigger('resize');
        }
      }
    } catch (e) {}
  }

  function notifyAndroidHideSidebar() {
    try {
      Android.onFamelackLayoutReady();
    } catch (e) {}
  }

  function requestElementFullscreen(el) {
    if (!el) return false;
    var fn = el.requestFullscreen ||
             el.webkitRequestFullscreen ||
             el.webkitEnterFullscreen ||
             el.mozRequestFullScreen ||
             el.msRequestFullscreen;
    if (!fn) return false;
    try {
      var r = fn.call(el);
      if (r && r.catch) r.catch(function() {});
      console.log(TAG, 'requestFullscreen ->', el.id || el.tagName);
      return true;
    } catch (e) {
      console.log(TAG, 'requestFullscreen failed', e);
      return false;
    }
  }

  function tryVideoJsFullscreen() {
    try {
      if (typeof videojs !== 'undefined') {
        var p = videojs.getPlayer('video-player');
        if (p && typeof p.requestFullscreen === 'function') {
          if (!p.isFullscreen || !p.isFullscreen()) p.requestFullscreen();
          return true;
        }
      }
    } catch (e) {}
    return false;
  }

  function isMediaReady() {
    var video = document.getElementById('video-player_html5_api') ||
                document.querySelector('#video-player video');
    if (video) {
      var src = (video.currentSrc || video.src || '').trim();
      if (video.readyState > 0 && src) return true;
    }
    var vjs = document.getElementById('video-player');
    if (vjs && (vjs.classList.contains('vjs-playing') || vjs.classList.contains('vjs-has-started'))) {
      return true;
    }
    if (isYoutubeActive()) return true;
    return false;
  }

  function enterTvFullscreen() {
    if (isYoutubeActive()) {
      hideYoutubeRightSidebar();
      expandPlayerWrapperFullscreen();
      notifyAndroidHideSidebar();
      return;
    }
    expandPlayerWrapperFullscreen();
    notifyAndroidHideSidebar();
  }

  function applyTvFullscreen() {
    if (isYoutubeActive()) {
      hideYoutubeRightSidebar();
      expandPlayerWrapperFullscreen();
      notifyAndroidHideSidebar();
      return;
    }
    var wrapper = document.querySelector('.video-player-wrapper');
    if (!wrapper) return;
    if (requestElementFullscreen(wrapper)) return;
    if (tryVideoJsFullscreen()) return;
    var container = document.getElementById('video-container');
    if (container && requestElementFullscreen(container)) return;
    var btn = document.querySelector('#video-player .vjs-fullscreen-control');
    if (btn) btn.click();
  }

  function waitAndEnterFullscreen() {
    clearYoutubeDelayTimer();
    var tries = 0;
    function tick() {
      if (isMediaReady() || tries++ >= 50) {
        if (isYoutubeActive()) {
          console.log(TAG, 'youtube detected, wait 5s before layout adjust');
          youtubeDelayTimer = setTimeout(function() {
            youtubeDelayTimer = null;
            hideYoutubeRightSidebar();
            expandPlayerWrapperFullscreen();
            notifyAndroidHideSidebar();
          }, YOUTUBE_FULLSCREEN_DELAY_MS);
        } else {
          enterTvFullscreen();
        }
        return;
      }
      setTimeout(tick, 300);
    }
    setTimeout(tick, 500);
  }

  function clickEntry(entry) {
    var btn = entry.querySelector('button.video-link');
    (btn || entry).click();
  }

  function entryRowHeight() {
    var rowH = 64;
    try {
      var style = getComputedStyle(document.documentElement);
      var key = window.matchMedia('(max-width: 600px)').matches
        ? '--sidebar-entry-height-mobile'
        : '--sidebar-entry-height-desktop';
      var raw = style.getPropertyValue(key).trim();
      if (raw.endsWith('rem')) rowH = parseFloat(raw) * (parseFloat(style.fontSize) || 16);
      else if (raw) rowH = parseFloat(raw) || rowH;
    } catch (e) {}
    return rowH;
  }

  function scrollToVirtualIndex(idx, done) {
    var sidebar = getSidebar();
    if (!sidebar) { done(null); return; }

    var rowH = entryRowHeight();
    sidebar.scrollTop = Math.max(0, idx * rowH - sidebar.clientHeight / 3);

    var tries = 0;
    function find() {
      var el = sidebar.querySelector('[data-index="' + idx + '"]') ||
               sidebar.querySelector('[data-virtual-index="' + idx + '"]');
      if (el) return done(el);
      var entries = getChannelEntries();
      if (entries[idx]) return done(entries[idx]);
      if (tries++ < 25) setTimeout(find, 120);
      else done(null);
    }
    setTimeout(find, 150);
  }

  function selectByIndex(idx) {
    scrollToVirtualIndex(idx, function(entry) {
      if (!entry) {
        console.log(TAG, 'entry not found for idx=' + idx);
        return;
      }
      clearYoutubeDelayTimer();
      currentIdx = idx;
      clickEntry(entry);
      console.log(TAG, 'selected idx=' + idx, entry.dataset.channelName || '');
      waitAndEnterFullscreen();
    });
  }

  function collectAllChannelNames(done) {
    var sidebar = getSidebar();
    if (!sidebar) { done([]); return; }

    var names = [];
    var seen = {};
    var lastScroll = -1;
    var rounds = 0;
    var rowH = entryRowHeight();
    var initialTotal = 0;

    function estimateInitialTotal() {
      // Prefer virtualized list container height / row height (available at startup).
      var vlist = sidebar.querySelector('.virtualized-list');
      if (vlist) {
        var h = parseFloat((vlist.style.height || '').replace('px', ''));
        if (!isNaN(h) && h > 0 && rowH > 0) {
          return Math.max(1, Math.round(h / rowH));
        }
      }
      // Fallback to max index visible at startup.
      return estimateTotal();
    }

    function estimateTotal() {
      var maxIdx = -1;
      sidebar.querySelectorAll('.sidebar-entry[data-index], .sidebar-entry[data-virtual-index]')
        .forEach(function(el) {
          var raw = el.getAttribute('data-index') || el.getAttribute('data-virtual-index');
          var n = parseInt(raw, 10);
          if (!isNaN(n) && n > maxIdx) maxIdx = n;
        });
      if (maxIdx >= 0) return maxIdx + 1;
      return getChannelEntries().length;
    }

    function reportProgress(loaded, total) {
      try { Android.onChannelLoadProgress(loaded, total); } catch (e) {}
    }

    function pass() {
      getChannelEntries().forEach(function(el) {
        var name = el.dataset.channelName;
        if (name && !seen[name]) {
          seen[name] = true;
          names.push(name);
        }
      });
      var total = Math.max(initialTotal, estimateTotal());
      reportProgress(names.length, total);

      if (sidebar.scrollTop === lastScroll && rounds > 4) {
        sidebar.scrollTop = 0;
        reportProgress(names.length, names.length);
        return done(names);
      }
      lastScroll = sidebar.scrollTop;
      rounds++;
      if (rounds > 120) {
        sidebar.scrollTop = 0;
        reportProgress(names.length, names.length);
        return done(names);
      }
      sidebar.scrollTop += Math.max(sidebar.clientHeight, rowH * 3);
      setTimeout(pass, 120);
    }

    initialTotal = estimateInitialTotal();
    reportProgress(0, initialTotal);
    sidebar.scrollTop = 0;
    setTimeout(pass, 200);
  }

  function sendChannelList(names) {
    try {
      Android.onChannelList(JSON.stringify(names));
      console.log(TAG, 'sent ' + names.length + ' channels to Android');
    } catch (e) {
      console.log(TAG, 'Android bridge not available');
    }
  }

  window.TVB_select = function(idx) { selectByIndex(idx); };
  window.TVB_getIdx = function() { return currentIdx; };
  window.TVB_getCount = function() { return getChannelEntries().length; };

  function init() {
    collectAllChannelNames(function(names) {
      if (names.length > 0) {
        sendChannelList(names);
        selectByIndex(0);
      } else {
        console.log(TAG, 'no channels found in sidebar');
      }
    });
  }

  window.TVB_trimChrome = function() { expandPlayerLayout(); notifyAndroidHideSidebar(); };
  window.TVB_enterFullscreen = function() { applyTvFullscreen(); };

  var waitItems = 0;
  function waitForItems() {
    if (getChannelEntries().length > 0) {
      init();
    } else if (waitItems++ < 40) {
      setTimeout(waitForItems, 500);
    }
  }

  setTimeout(waitForItems, 1200);
})();
""".trimIndent()
}
