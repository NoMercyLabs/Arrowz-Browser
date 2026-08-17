/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

/*
 * Reports the largest icon the page declares.
 *
 * The page reports rather than being asked. Asking meant calling
 * evaluateJavascript on the WebView the host hands back when a page finishes
 * loading, and that view is not attached to the window: postDelayed on it never
 * runs and evaluateJavascript drops its callback without a word, so even "1+1"
 * came back with nothing. Injecting at document start runs on the view that is
 * actually rendering, which is the same reason every other bridge here works
 * this way.
 *
 * Order is by drawn size, not by convention. An apple-touch-icon is 180px by
 * definition, a manifest icon is whatever it declares, and og:image is a share
 * card that is always big enough for a tile. /favicon.ico is last because it is
 * 16 or 32 pixels on most of the web, and stretched across a tile it looks
 * worse than the two letters it would replace.
 */
(function () {
  if (window.__nmSiteIcon) return;
  window.__nmSiteIcon = true;

  function declaredSize(link) {
    var raw = (link.getAttribute('sizes') || '').split('x')[0];
    var parsed = parseInt(raw, 10);
    return isNaN(parsed) ? 0 : parsed;
  }

  // BitmapFactory draws neither of these, so a page offering only them has
  // nothing we can use and should fall through to its share card.
  function undrawable(link) {
    var type = (link.getAttribute('type') || '').toLowerCase();
    var href = (link.href || '').toLowerCase().split('?')[0];
    return type.indexOf('svg') >= 0 || type.indexOf('icon') >= 0 ||
      href.indexOf('.svg') === href.length - 4 || href.indexOf('.ico') === href.length - 4;
  }

  function best() {
    var found = null;
    var foundSize = -1;
    var links = document.querySelectorAll(
      'link[rel~="apple-touch-icon"],link[rel~="apple-touch-icon-precomposed"],link[rel~="icon"]'
    );
    for (var i = 0; i < links.length; i++) {
      if (!links[i].href || undrawable(links[i])) continue;
      var size = declaredSize(links[i]);
      // An apple-touch-icon that declares no size is 180 by the convention that
      // named it, so it outranks a 32px icon that did declare one.
      if (links[i].rel.indexOf('apple-touch-icon') >= 0 && size === 0) size = 180;
      if (size > foundSize) {
        foundSize = size;
        found = links[i].href;
      }
    }

    // Any real icon beats the share card, declared size or not. GitHub names no
    // size on its favicon.png and has no touch icon, and ranking by declared
    // size alone reached past it to a 1200x630 marketing banner that is both
    // the wrong shape for a tile and too big to fetch.
    if (found) return found;

    var shareCard = document.querySelector('meta[property="og:image"]');
    if (shareCard && shareCard.content) return shareCard.content;
    return location.origin + '/favicon.ico';
  }

  function report() {
    if (!window.NmSiteIcon || !location.origin || location.origin === 'null') return;
    try {
      window.NmSiteIcon.onIconFound(location.origin, best());
    } catch (ignored) {
      // The bridge is absent while a page loads in a tab being rebuilt after a
      // renderer death. The next load reports again.
    }
  }

  // Twice on purpose. The head is usually parsed before this runs, and a page
  // that adds its icon from script has done so by load.
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', report);
  } else {
    report();
  }
  window.addEventListener('load', report);
})();
