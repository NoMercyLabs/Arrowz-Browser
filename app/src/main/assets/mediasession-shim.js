/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 *
 * Injected at document start via evaluateJavascript, never as an appended
 * script tag: sites with a strict content security policy block injected tags,
 * and those are disproportionately the well-built sites where this works best.
 */
(function () {
  if (window.__nmMediaShim) return;
  window.__nmMediaShim = true;

  var bridge = window.NoMercyMedia;
  if (!bridge) return;

  var actions = {};
  var active = null;

  // Which element the user is listening to. Deliberately NOT "which element is
  // playing right now": a paused element is still the current one, and dropping
  // it on pause deactivates the session, after which the remote's play key has
  // nothing to resume. That is the exact case a now-playing state exists for.
  function pick() {
    var nodes = document.querySelectorAll('video, audio');
    var best = null;
    for (var i = 0; i < nodes.length; i++) {
      var el = nodes[i];
      if (el.muted || el.volume === 0) continue;
      // Never started, so it is not what anybody is listening to. This is also
      // what keeps a muted autoplaying advert off the now-playing state.
      if (!el.__nmStarted) continue;
      if (isNaN(el.duration) || el.duration < 5) continue;
      if (!best || el.duration > best.duration) best = el;
    }
    return best;
  }

  function artwork() {
    var meta = document.querySelector('meta[property="og:image"], meta[name="og:image"]');
    return meta ? meta.content : '';
  }

  function report() {
    var el = active;
    if (!el) {
      bridge.onPlaybackStopped();
      return;
    }
    var md = (navigator.mediaSession && navigator.mediaSession.metadata) || null;
    bridge.onPlaybackState(
      !el.paused,
      md && md.title ? md.title : (document.title || ''),
      md && md.artist ? md.artist : (location.hostname || ''),
      md && md.artwork && md.artwork.length ? md.artwork[0].src : artwork(),
      isFinite(el.duration) ? Math.round(el.duration * 1000) : 0,
      Math.round((el.currentTime || 0) * 1000),
      Object.keys(actions).join(',')
    );
  }

  function bind(el) {
    if (el.__nmBound) return;
    el.__nmBound = true;
    el.addEventListener('play', function () {
      el.__nmStarted = true;
      active = pick();
      report();
    });

    ['pause', 'loadedmetadata', 'volumechange'].forEach(function (name) {
      el.addEventListener(name, function () {
        active = pick();
        report();
      });
    });

    // Ended is the only event that means "there is nothing to resume". Pause
    // does not, and treating it as such is what deactivates the session.
    el.addEventListener('ended', function () {
      el.__nmStarted = false;
      active = pick();
      report();
    });
    // Throttled: timeupdate fires several times a second and the position only
    // needs to be roughly right for a now-playing display.
    var last = 0;
    el.addEventListener('timeupdate', function () {
      var now = Date.now();
      if (now - last < 1000) return;
      last = now;
      if (active === el) report();
    });
  }

  function scan() {
    var nodes = document.querySelectorAll('video, audio');
    for (var i = 0; i < nodes.length; i++) bind(nodes[i]);
  }

  scan();
  new MutationObserver(scan).observe(document.documentElement, {
    childList: true,
    subtree: true
  });

  // The API path. Sites that use it get exactly what they declared; sites that
  // do not still work through the observer above.
  if (navigator.mediaSession) {
    var originalSetActionHandler = navigator.mediaSession.setActionHandler;
    navigator.mediaSession.setActionHandler = function (action, handler) {
      if (handler) actions[action] = handler;
      else delete actions[action];
      try {
        originalSetActionHandler.call(navigator.mediaSession, action, handler);
      } catch (e) { /* older engines reject unknown actions */ }
      report();
    };

    var descriptor = Object.getOwnPropertyDescriptor(
      Object.getPrototypeOf(navigator.mediaSession) || {},
      'metadata'
    );
    if (descriptor && descriptor.set) {
      Object.defineProperty(navigator.mediaSession, 'metadata', {
        get: function () { return descriptor.get.call(this); },
        set: function (value) {
          descriptor.set.call(this, value);
          report();
        }
      });
    }
  }

  // Called from the app when a transport key arrives. The page's own handler
  // wins; the element is the fallback, which is the path most sites take.
  window.__nmMediaAction = function (action) {
    if (actions[action]) {
      try { actions[action](); return true; } catch (e) { /* fall through */ }
    }
    var el = active || pick() || document.querySelector('video, audio');
    if (!el) return false;
    if (action === 'play') el.play();
    else if (action === 'pause') el.pause();
    else if (action === 'stop') { el.pause(); el.currentTime = 0; }
    else if (action === 'seekforward') el.currentTime = el.currentTime + 10;
    else if (action === 'seekbackward') el.currentTime = el.currentTime - 10;
    else return false;
    return true;
  };
})();
