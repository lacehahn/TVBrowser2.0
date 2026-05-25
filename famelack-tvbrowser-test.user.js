// ==UserScript==
// @name         TVBrowser20 — Famelack 测试
// @namespace    https://github.com/lacehahn/TVBrowser2.0
// @version      1.2.0
// @description  在桌面浏览器复现 TVBrowser20 的 Famelack 注入逻辑，便于游猴调试
// @author       TVBrowser20
// @match        https://famelack.com/*
// @match        https://www.famelack.com/*
// @icon         https://famelack.com/favicon.ico
// @grant        none
// @inject-into  page
// @run-at       document-idle
// ==/UserScript==

(function () {
  'use strict';

  var TAG = '[TVBrowser/Famelack]';
  var VER = '1.2.0';
  var channelNames = [];
  var hudEl = null;
  var initStarted = false;
  var observer = null;
  var pollTimer = null;

  document.documentElement.setAttribute('data-tvbrowser-test', VER);

  function isTvPage() {
    return /^\/tv\/[^/]+/i.test(location.pathname);
  }

  /** /tv/jp/zcW6vCKG9ZO6s9 → zcW6vCKG9ZO6s9 */
  function getChannelIdFromUrl() {
    var parts = location.pathname.replace(/\/+$/, '').split('/').filter(Boolean);
    if (parts[0] !== 'tv' || parts.length < 3) return null;
    return parts[2];
  }

  function resolveStartIndex() {
    var id = getChannelIdFromUrl();
    if (id) {
      var el = document.querySelector('.sidebar-entry[data-nanoid="' + id + '"]');
      if (el) {
        var vi = el.getAttribute('data-virtual-index') || el.getAttribute('data-index');
        if (vi != null && vi !== '') return parseInt(vi, 10);
      }
    }
    var playing = document.querySelector('.sidebar-entry.playing.active');
    if (playing) {
      var pvi = playing.getAttribute('data-virtual-index') || playing.getAttribute('data-index');
      if (pvi != null && pvi !== '') return parseInt(pvi, 10);
    }
    return 0;
  }

  // ── 模拟 App 的 Android 桥 ─────────────────────────────────────────────
  window.Android = window.Android || {};
  if (!window.Android.onChannelList) {
    window.Android.onChannelList = function (json) {
      try {
        channelNames = JSON.parse(json);
      } catch (e) {
        channelNames = [];
      }
      console.log(TAG, 'onChannelList:', channelNames.length, channelNames);
      updateHud();
    };
  }
  if (!window.Android.onFamelackLayoutReady) {
    window.Android.onFamelackLayoutReady = function () {
      console.log(TAG, 'onFamelackLayoutReady → TVB_enterFullscreen in 300ms');
      setTimeout(function () {
        if (typeof window.TVB_enterFullscreen === 'function') {
          window.TVB_enterFullscreen();
        }
      }, 300);
    };
  }

  // ── 以下与 JsInjector.kt FAMELACK_JS 保持一致 ─────────────────────────
  var currentIdx = -1;

  function isYoutubeActive() {
    var yt = document.getElementById('youtube-player');
    if (!yt) return false;
    var st = getComputedStyle(yt);
    return st.display !== 'none' && yt.getBoundingClientRect().width > 80;
  }

  function getSidebar() {
    return document.getElementById('sidebar-items');
  }

  function getChannelEntries() {
    var roots = [];
    var sidebar = getSidebar();
    if (sidebar) roots.push(sidebar);
    roots.push(document);

    for (var r = 0; r < roots.length; r++) {
      var root = roots[r];
      var list = root.querySelectorAll(
        '.sidebar-entry[data-channel-name]:not(.country-item)'
      );
      if (list.length) return Array.prototype.slice.call(list);
      list = root.querySelectorAll('.sidebar-entry[data-nanoid]:not(.country-item)');
      if (list.length) return Array.prototype.slice.call(list);
    }
    return [];
  }

  function hasPlayerShell() {
    return !!(document.getElementById('video-container') ||
      document.getElementById('youtube-player') ||
      document.getElementById('video-player'));
  }

  function trimPageChrome() {
    [
      'navigation-drawer', 'navigation-drawer-overlay', 'globe-container',
      'globe-viz', 'globe-interaction-blocker', 'media-dock',
      'loader-wrapper', 'loader-background', 'loading-indicator',
      'search-elements-toggle', 'mode-dock', 'mode-menu', 'overlay'
    ].forEach(function (id) {
      var el = document.getElementById(id);
      if (el) el.style.setProperty('display', 'none', 'important');
    });
    document.querySelectorAll('.main-content, .search-wrapper').forEach(function (el) {
      el.style.setProperty('display', 'none', 'important');
    });
    ['sidebar-header', 'sidebar-items'].forEach(function (id) {
      var el = document.getElementById(id);
      if (el) el.style.setProperty('display', 'none', 'important');
    });
    document.querySelectorAll('.scrub-wrap, .sidebar-skeleton').forEach(function (el) {
      el.style.setProperty('display', 'none', 'important');
    });
  }

  function removeCenterStyle() {
    ['tvb-famelack-style', 'tvb-famelack-layout'].forEach(function (id) {
      var old = document.getElementById(id);
      if (old) old.remove();
    });
  }

  function pinPlayerToViewport() {
    if (isYoutubeActive()) {
      console.log(TAG, 'skip viewport pin for youtube');
      return;
    }
    trimPageChrome();
    removeCenterStyle();

    var container = document.getElementById('video-container');
    if (!container) {
      console.log(TAG, 'no #video-container to pin');
      return;
    }

    document.documentElement.style.margin = '0';
    document.documentElement.style.padding = '0';
    document.documentElement.style.overflow = 'hidden';
    document.body.style.margin = '0';
    document.body.style.padding = '0';
    document.body.style.overflow = 'hidden';
    document.body.style.position = 'relative';
    document.body.style.width = '100%';
    document.body.style.height = '100%';

    var siteHeader = document.querySelector('header');
    if (siteHeader) siteHeader.style.setProperty('display', 'none', 'important');

    var app = document.getElementById('app-content');
    if (app) app.style.setProperty('display', 'none', 'important');

    if (container.parentElement !== document.body) {
      document.body.appendChild(container);
    }

    container.style.setProperty('display', 'block', 'important');
    container.style.position = 'absolute';
    container.style.top = '0';
    container.style.left = '0';
    container.style.right = '0';
    container.style.bottom = '0';
    container.style.width = '100%';
    container.style.height = '100%';
    container.style.zIndex = '2147483646';
    container.style.margin = '0';
    container.style.padding = '0';
    container.style.background = '#000';

    var wrapper = container.querySelector('.video-player-wrapper');
    if (wrapper) {
      wrapper.style.width = '100%';
      wrapper.style.height = '100%';
      wrapper.style.margin = '0';
    }

    var vjs = document.getElementById('video-player');
    if (vjs) {
      vjs.classList.remove('video-player-dimensions');
      vjs.style.width = '100%';
      vjs.style.height = '100%';
    }

    window.scrollTo(0, 0);
    triggerPlayerResize();
    setTimeout(triggerPlayerResize, 500);
    setTimeout(triggerPlayerResize, 1500);
    console.log(TAG, 'player pinned to viewport (0,0)');
    updateHud();
  }

  function expandPlayerLayout() {
    pinPlayerToViewport();
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
    } catch (e) { /* ignore */ }
  }

  function notifyAndroidHideSidebar() {
    try {
      window.Android.onFamelackLayoutReady();
    } catch (e) { /* ignore */ }
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
      if (r && r.catch) r.catch(function () { });
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
    } catch (e) { /* ignore */ }
    return false;
  }

  function isMediaReady() {
    var video = document.getElementById('video-player_html5_api') ||
      document.querySelector('#video-player video');
    if (video) {
      var src = (video.currentSrc || video.src || '').trim();
      if (video.readyState > 0 && src) return 'native';
    }
    var vjs = document.getElementById('video-player');
    if (vjs && (vjs.classList.contains('vjs-playing') || vjs.classList.contains('vjs-has-started'))) {
      return 'native';
    }
    if (isYoutubeActive()) return 'youtube';
    return null;
  }

  function enterTvFullscreen() {
    if (isYoutubeActive()) {
      notifyAndroidHideSidebar();
      return;
    }
    pinPlayerToViewport();
    notifyAndroidHideSidebar();
  }

  function applyTvFullscreen() {
    if (isYoutubeActive()) return;

    var container = document.getElementById('video-container');
    var mode = isMediaReady();

    if (mode === 'native') {
      if (tryVideoJsFullscreen()) return;
      if (requestElementFullscreen(container)) return;
      if (requestElementFullscreen(document.getElementById('video-player'))) return;
      var btn = document.querySelector('#video-player .vjs-fullscreen-control');
      if (btn) { btn.click(); return; }
    } else if (container) {
      requestElementFullscreen(container);
    }
    console.log(TAG, 'viewport pin done; HTML FS optional');
  }

  function waitAndEnterFullscreen() {
    var tries = 0;
    function tick() {
      var mode = isMediaReady();
      if (mode === 'youtube') {
        enterTvFullscreen();
        return;
      }
      if (mode === 'native') {
        enterTvFullscreen();
        return;
      }
      if (tries++ < 50) {
        setTimeout(tick, 300);
      } else {
        enterTvFullscreen();
      }
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
    } catch (e) { /* ignore */ }
    return rowH;
  }

  function scrollToVirtualIndex(idx, done) {
    var urlId = getChannelIdFromUrl();
    if (urlId) {
      var byNanoid = document.querySelector('.sidebar-entry[data-nanoid="' + urlId + '"]');
      if (byNanoid) return done(byNanoid);
    }

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
    scrollToVirtualIndex(idx, function (entry) {
      if (!entry) {
        console.log(TAG, 'entry not found for idx=' + idx);
        return;
      }
      currentIdx = idx;
      clickEntry(entry);
      console.log(TAG, 'selected idx=' + idx, entry.dataset.channelName || '');
      waitAndEnterFullscreen();
      updateHud();
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

    function pass() {
      getChannelEntries().forEach(function (el) {
        var name = el.dataset.channelName;
        if (name && !seen[name]) {
          seen[name] = true;
          names.push(name);
        }
      });

      if (sidebar.scrollTop === lastScroll && rounds > 4) {
        sidebar.scrollTop = 0;
        return done(names);
      }
      lastScroll = sidebar.scrollTop;
      rounds++;
      if (rounds > 120) {
        sidebar.scrollTop = 0;
        return done(names);
      }
      sidebar.scrollTop += Math.max(sidebar.clientHeight, rowH * 3);
      setTimeout(pass, 120);
    }

    sidebar.scrollTop = 0;
    setTimeout(pass, 200);
  }

  function sendChannelList(names) {
    try {
      window.Android.onChannelList(JSON.stringify(names));
    } catch (e) {
      console.log(TAG, 'Android bridge not available');
    }
  }

  window.TVB_select = function (idx) { selectByIndex(idx); };
  window.TVB_getIdx = function () { return currentIdx; };
  window.TVB_getCount = function () { return getChannelEntries().length; };
  window.TVB_trimChrome = function () { expandPlayerLayout(); notifyAndroidHideSidebar(); };
  window.TVB_enterFullscreen = function () { applyTvFullscreen(); };
  window.TVB_status = function () {
    return {
      idx: currentIdx,
      count: getChannelEntries().length,
      media: isMediaReady(),
      youtube: isYoutubeActive(),
      channel: channelNames[currentIdx] || null
    };
  };

  function finishInit(names) {
    if (!names.length) {
      initStarted = false;
      setHudMessage('采集失败：侧栏无频道', '#f66');
      console.warn(TAG, 'no channels in sidebar');
      return;
    }
    sendChannelList(names);
    var startIdx = resolveStartIndex();
    var urlId = getChannelIdFromUrl();
    if (urlId) {
      var playing = document.querySelector(
        '.sidebar-entry.playing[data-nanoid="' + urlId + '"]'
      );
      if (playing) {
        currentIdx = startIdx;
        console.log(TAG, 'deep link active:', urlId, 'idx=' + startIdx);
        updateHud();
        waitAndEnterFullscreen();
        return;
      }
    }
    selectByIndex(startIdx >= 0 ? startIdx : 0);
    updateHud();
  }

  function init() {
    if (initStarted) return;
    initStarted = true;
    setHudMessage('正在初始化…', '#ff0');

    var visible = getChannelEntries();
    if (visible.length >= 1) {
      var quickNames = visible.map(function (e) {
        return e.dataset.channelName || e.dataset.nanoid || '?';
      });
      console.log(TAG, 'fast init, visible=', visible.length);
      finishInit(quickNames);
      collectAllChannelNames(function (full) {
        if (full.length > quickNames.length) {
          channelNames = full;
          try {
            window.Android.onChannelList(JSON.stringify(full));
          } catch (e) { /* ignore */ }
          updateHud();
        }
      });
      return;
    }

    collectAllChannelNames(finishInit);
  }

  // ── 调试 HUD（仅游猴测试用）────────────────────────────────────────────
  function ensureHud() {
    if (hudEl && document.body.contains(hudEl)) return;
    hudEl = document.createElement('div');
    hudEl.id = 'tvb-famelack-hud';
    hudEl.style.cssText =
      'position:fixed;bottom:12px;left:12px;z-index:2147483647;' +
      'background:rgba(0,0,0,.88);color:#0f0;font:12px/1.5 monospace;' +
      'padding:10px 14px;border-radius:8px;max-width:440px;pointer-events:none;' +
      'box-shadow:0 4px 20px rgba(0,0,0,.5);';
    (document.body || document.documentElement).appendChild(hudEl);
  }

  function setHudMessage(msg, color) {
    ensureHud();
    hudEl.style.color = color || '#0f0';
    hudEl.innerHTML =
      '<b>TVBrowser Famelack 测试 v' + VER + '</b><br>' + msg + '<br>' +
      '<small>' + location.pathname + '</small><br>' +
      '<small>↑/↓ 换台 · F 全屏 · P Pin · T 裁切 · S 状态</small>';
  }

  function updateHud() {
    ensureHud();
    if (typeof window.TVB_status !== 'function') {
      setHudMessage('API 未就绪', '#f66');
      return;
    }
    var st = window.TVB_status();
    hudEl.style.color = '#0f0';
    hudEl.innerHTML =
      '<b>TVBrowser Famelack 测试 v' + VER + '</b><br>' +
      '频道 ' + (st.idx >= 0 ? st.idx + 1 : '—') + ' / ' + st.count +
      (st.channel ? ' · ' + st.channel : '') + '<br>' +
      '媒体: ' + (st.media || '—') +
      (st.youtube ? ' · YouTube' : '') + '<br>' +
      '<small>↑/↓ 换台 · F 全屏 · P Pin · T 裁切 · S 状态 · Esc 退出全屏</small>';
  }

  function createHud() {
    ensureHud();
    updateHud();
  }

  document.addEventListener('keydown', function (e) {
    if (e.target && /^(INPUT|TEXTAREA|SELECT)$/.test(e.target.tagName)) return;

    var n = typeof window.TVB_getCount === 'function' ? window.TVB_getCount() : 0;

    if (e.key === 's' || e.key === 'S') {
      e.preventDefault();
      console.log(TAG, typeof window.TVB_status === 'function' ? window.TVB_status() : 'not ready');
      updateHud();
      return;
    }
    if (n <= 0) return;

    switch (e.key) {
      case 'ArrowUp':
        e.preventDefault();
        window.TVB_select(Math.max(0, window.TVB_getIdx() - 1));
        break;
      case 'ArrowDown':
        e.preventDefault();
        window.TVB_select(Math.min(n - 1, window.TVB_getIdx() + 1));
        break;
      case 'f':
      case 'F':
        e.preventDefault();
        window.TVB_enterFullscreen();
        break;
      case 'p':
      case 'P':
        e.preventDefault();
        pinPlayerToViewport();
        break;
      case 't':
      case 'T':
        e.preventDefault();
        window.TVB_trimChrome();
        break;
      case 'Escape':
        if (document.fullscreenElement) {
          document.exitFullscreen();
        }
        break;
      default:
        break;
    }
  }, true);

  function pageReady() {
    return getChannelEntries().length > 0 || hasPlayerShell();
  }

  function tryStart(reason) {
    if (!isTvPage()) {
      setHudMessage('请打开电视页，例如 /tv/jp/频道ID', '#fa0');
      console.log(TAG, 'not a /tv/ page:', location.pathname);
      return;
    }
    var n = getChannelEntries().length;
    if (n > 0) {
      console.log(TAG, 'ready (' + reason + '), channels=' + n, 'urlId=', getChannelIdFromUrl());
      init();
      return;
    }
    if (hasPlayerShell()) {
      console.log(TAG, 'player shell only (' + reason + '), wait for sidebar');
      setHudMessage('播放器已出现，等待频道列表…', '#ff0');
      return;
    }
    setHudMessage('等待页面加载… (' + reason + ')', '#ff0');
  }

  function startWatching() {
    initStarted = false;
    if (pollTimer) {
      clearTimeout(pollTimer);
      pollTimer = null;
    }
    if (observer) {
      observer.disconnect();
      observer = null;
    }
    tryStart('watch');
  }

  function boot() {
    console.log(TAG, 'boot v' + VER, location.href);
    ensureHud();
    setHudMessage('脚本已注入 (page)，等待页面…', '#8cf');

    if (!document.body) {
      document.addEventListener('DOMContentLoaded', boot, { once: true });
      return;
    }

    startWatching();

    var waitItems = 0;
    function poll() {
      if (initStarted) return;
      if (getChannelEntries().length > 0) {
        tryStart('poll');
        return;
      }
      if (waitItems++ < 120) {
        pollTimer = setTimeout(poll, 500);
      } else {
        var diag = 'sidebar=' + !!getSidebar() +
          ' entries=' + getChannelEntries().length +
          ' player=' + hasPlayerShell();
        setHudMessage('超时：' + diag, '#f66');
        console.warn(TAG, 'timeout', diag);
      }
    }
    pollTimer = setTimeout(poll, 800);

    observer = new MutationObserver(function () {
      if (!initStarted && pageReady()) {
        tryStart('mutation');
      }
    });
    observer.observe(document.documentElement, { childList: true, subtree: true });
  }

  function hookSpaNavigation() {
    function onNav() {
      console.log(TAG, 'navigation', location.href);
      setTimeout(startWatching, 600);
    }
    window.addEventListener('popstate', onNav);
    ['pushState', 'replaceState'].forEach(function (method) {
      var orig = history[method];
      if (typeof orig !== 'function') return;
      history[method] = function () {
        var ret = orig.apply(this, arguments);
        onNav();
        return ret;
      };
    });
  }

  window.__TVB_FAMELACK_BOOT__ = boot;
  hookSpaNavigation();

  if (!window.__TVB_FAMELACK_API__) {
    window.__TVB_FAMELACK_API__ = true;
    boot();
  } else {
    console.log(TAG, 're-boot after navigation');
    boot();
  }
})();
