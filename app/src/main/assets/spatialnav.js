/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 *
 * The page side of D-pad navigation. It reports geometry and applies focus;
 * every decision about where a press goes is made in Kotlin, where it is tested
 * without a device.
 *
 * Nothing here appends a <script> or <style> tag. A site with a strict content
 * security policy blocks both, and those are exactly the well-built sites where
 * this would otherwise work best, so the ring is written through the CSSOM and
 * this file arrives through evaluateJavascript at document start.
 */
(function () {
  'use strict';

  if (window.__nmSpatial) return;

  // Reserved, and deliberately unguessable-looking: cosmetic filter rules from
  // a blocklist can hide anything they can name, including our own overlay.
  var RING_ATTRIBUTE = 'data-nm-spatial-ring';
  var RING_CLASS = 'nm-spatial-focus-ring-9f3c';

  var FOCUSABLE = [
    'a[href]', 'button', 'input', 'select', 'textarea', 'summary', 'audio[controls]',
    'video[controls]', '[tabindex]', '[role="button"]', '[role="link"]',
    '[role="menuitem"]', '[role="tab"]', '[role="checkbox"]', '[contenteditable]'
  ].join(',');

  var MIN_SIZE = 8;
  var identity = 0;

  function styleSheet() {
    if (styleSheet.cached) return styleSheet.cached;
    try {
      var sheet = new CSSStyleSheet();
      document.adoptedStyleSheets = document.adoptedStyleSheets.concat([sheet]);
      styleSheet.cached = sheet;
      return sheet;
    } catch (error) {
      // Constructable stylesheets are the clean route; where they are missing,
      // an empty <style> that we never fill from markup is still CSSOM-only.
      var element = document.createElement('style');
      document.documentElement.appendChild(element);
      styleSheet.cached = element.sheet;
      return element.sheet;
    }
  }

  /**
   * The ring is defined once, in Kotlin, and handed here. Native and web focus
   * drifting apart within two releases is what a second definition guarantees.
   */
  function applyRingStyle(color, widthPx, radiusPx) {
    var sheet = styleSheet();
    if (!sheet || applyRingStyle.done) return;
    try {
      sheet.insertRule(
        '.' + RING_CLASS + '{outline:' + widthPx + 'px solid ' + color + ' !important;' +
        'outline-offset:2px !important;border-radius:' + radiusPx + 'px;}',
        sheet.cssRules.length
      );
      applyRingStyle.done = true;
    } catch (error) {
      /* A sheet we cannot write to leaves the site's own focus style, which is
         worse but not broken. */
    }
  }

  function isVisible(element) {
    var style = window.getComputedStyle(element);
    if (style.visibility === 'hidden' || style.display === 'none' || style.opacity === '0') return false;
    if (element.disabled) return false;
    if (element.getAttribute('aria-hidden') === 'true') return false;
    var box = element.getBoundingClientRect();
    return box.width >= MIN_SIZE && box.height >= MIN_SIZE;
  }

  var SECTION_SELECTOR = 'ul,ol,nav,table,[role="list"],[role="row"],[role="tablist"],' +
    '[role="menu"],[role="grid"],[role="listbox"],[role="toolbar"]';

  /**
   * The row or grid an element belongs to.
   *
   * Section memory is what makes a long grid usable: leaving it and coming back
   * has to return to the item the viewer was on, not to the first one. That
   * only works if a section is identified the same way on every press, so the
   * container is stamped once and keeps its stamp.
   */
  function sectionOf(element) {
    var container = element.closest ? element.closest(SECTION_SELECTOR) : null;
    if (!container) return 'document';
    if (!container.__nmSectionId) {
      container.__nmSectionId = 'sec' + (++identity);
    }
    return container.__nmSectionId;
  }

  function isFixed(element) {
    for (var node = element; node && node !== document.body; node = node.parentElement) {
      if (window.getComputedStyle(node).position === 'fixed') return true;
    }
    return false;
  }

  function collect() {
    var elements = document.querySelectorAll(FOCUSABLE);
    var results = [];
    var order = 0;
    for (var index = 0; index < elements.length; index++) {
      var element = elements[index];
      order++;
      if (!isVisible(element)) continue;
      if (!element.__nmSpatialId) {
        element.__nmSpatialId = 'nm' + (++identity);
      }
      var box = element.getBoundingClientRect();
      results.push({
        id: element.__nmSpatialId,
        left: Math.round(box.left),
        top: Math.round(box.top),
        right: Math.round(box.right),
        bottom: Math.round(box.bottom),
        order: order,
        // A sticky header and the body it floats over are their own groups, or
        // focus ping-pongs between them on every press.
        fixed: isFixed(element),
        section: sectionOf(element)
      });
    }
    return {
      elements: results,
      viewportWidth: window.innerWidth,
      viewportHeight: window.innerHeight,
      scrollY: Math.round(window.scrollY),
      scrollHeight: Math.round(document.documentElement.scrollHeight),
      scrollX: Math.round(window.scrollX),
      scrollWidth: Math.round(document.documentElement.scrollWidth),
      focused: document.activeElement && document.activeElement.__nmSpatialId
        ? document.activeElement.__nmSpatialId
        : ''
    };
  }

  function find(id) {
    var elements = document.querySelectorAll(FOCUSABLE);
    for (var index = 0; index < elements.length; index++) {
      if (elements[index].__nmSpatialId === id) return elements[index];
    }
    return null;
  }

  function focusById(id) {
    var element = find(id);
    if (!element) return false;

    var previous = document.querySelector('.' + RING_CLASS);
    if (previous) {
      previous.classList.remove(RING_CLASS);
      previous.removeAttribute(RING_ATTRIBUTE);
    }

    element.classList.add(RING_CLASS);
    element.setAttribute(RING_ATTRIBUTE, '');
    // preventScroll, because the Kotlin side has already decided whether a
    // screenful of scrolling is wanted and the browser's own scroll-into-view
    // would fight that decision.
    try {
      element.focus({ preventScroll: true });
    } catch (error) {
      element.focus();
    }

    // Except when the element is not fully on screen. Focus was landing on
    // things sitting past the right edge — the menu button on DuckDuckGo's
    // header — so the ring was drawn somewhere nobody could see and the press
    // read as doing nothing at all. `nearest` moves the least that makes it
    // visible, which is what a browser does for its own focus.
    var box = element.getBoundingClientRect();
    var offscreen = box.left < 0 || box.top < 0 ||
      box.right > window.innerWidth || box.bottom > window.innerHeight;
    if (offscreen) {
      element.scrollIntoView({ block: 'nearest', inline: 'nearest' });
    }
    return true;
  }

  function activate() {
    var element = document.querySelector('.' + RING_CLASS);
    if (!element) return false;
    element.click();
    return true;
  }

  /**
   * Whether this page can be navigated by focus at all, as a count rather than
   * a guess. A page with a handful of links spread over a screenful is a page
   * where the pointer is the better tool.
   */
  function probe() {
    var snapshot = collect();
    var visible = 0;
    for (var index = 0; index < snapshot.elements.length; index++) {
      var box = snapshot.elements[index];
      if (box.bottom > 0 && box.top < snapshot.viewportHeight) visible++;
    }
    return {
      total: snapshot.elements.length,
      visible: visible,
      viewportHeight: snapshot.viewportHeight,
      // A page that moves focus itself on load is one that will fight ours.
      stealsFocus: document.activeElement !== document.body &&
        document.activeElement !== document.documentElement
    };
  }

  window.__nmSpatial = {
    collect: function () { return JSON.stringify(collect()); },
    probe: function () { return JSON.stringify(probe()); },
    focus: focusById,
    activate: activate,
    style: applyRingStyle,
    clear: function () {
      var element = document.querySelector('.' + RING_CLASS);
      if (element) {
        element.classList.remove(RING_CLASS);
        element.removeAttribute(RING_ATTRIBUTE);
      }
    }
  };
})();
