/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 *
 * Applies the television's caption preferences to web video.
 *
 * Everything here goes through the CSSOM. A strict-CSP site drops an appended
 * <style> tag, and captions would then be unstyled on exactly the well-built
 * sites where the preference should have been honoured.
 */
(function () {
  'use strict';

  if (window.__nmCaptions) return;

  var sheet = null;
  var ruleIndex = -1;

  function styleSheet() {
    if (sheet) return sheet;
    try {
      sheet = new CSSStyleSheet();
      document.adoptedStyleSheets = document.adoptedStyleSheets.concat([sheet]);
    } catch (error) {
      // Constructable stylesheets are the clean route; where they are missing,
      // an empty <style> we only ever write to through the CSSOM is still not
      // markup, so a CSP that forbids inline styles is still satisfied.
      var element = document.createElement('style');
      document.documentElement.appendChild(element);
      sheet = element.sheet;
    }
    return sheet;
  }

  /**
   * Replaces rather than appends. The preference can change while a page is
   * open, and a second rule for the same selector would leave the older one
   * still deciding whatever the newer one does not mention.
   */
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
      /* A sheet we cannot write to leaves the page's own caption style, which
         ignores the preference but is not broken. */
    }
  }

  window.__nmCaptions = { apply: apply };
})();
