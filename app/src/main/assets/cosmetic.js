/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 *
 * Element hiding. A blocked request cannot hide a slot the page draws itself,
 * so hiding is a separate mechanism from blocking, and this is its end of it.
 *
 * Its own stylesheet, never shared with the one the focus ring and the caption
 * rule live in: these selectors are written by strangers, and a rule that fails
 * to parse takes the rest of its sheet down with it.
 */
(function () {
  'use strict';

  if (window.__nmHide) return;

  var sheet = null;
  var ruleIndex = -1;

  function styleSheet() {
    if (sheet) return sheet;
    try {
      sheet = new CSSStyleSheet();
      document.adoptedStyleSheets = document.adoptedStyleSheets.concat([sheet]);
    } catch (error) {
      var element = document.createElement('style');
      document.documentElement.appendChild(element);
      sheet = element.sheet;
    }
    return sheet;
  }

  /** Replaces rather than appends: a navigation within one document is a
   *  different host, and the previous host's selectors are not this one's. */
  function apply(css) {
    var target = styleSheet();
    if (!target) return;
    try {
      if (ruleIndex >= 0 && ruleIndex < target.cssRules.length) {
        target.deleteRule(ruleIndex);
        ruleIndex = -1;
      }
      if (!css) return;
      ruleIndex = target.insertRule(css, target.cssRules.length);
    } catch (error) {
      /* One malformed selector in a list of thousands should cost the hiding,
         never the page. */
    }
  }

  window.__nmHide = { apply: apply };
})();
