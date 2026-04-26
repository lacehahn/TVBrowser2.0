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
        JsKey.NONE  -> ""
        JsKey.CCTV  -> CCTV_JS
        JsKey.YIBA  -> YIBA_JS
        else        -> DEFAULT_JS
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
}
