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

  /*
   * What focus can land on: the native controls, every ARIA widget role, and
   * labels.
   *
   * Labels are not padding in this list. A label is an activation target in
   * HTML — clicking one activates the control it names — and sites use that to
   * style a choice however they like while the real input is a hidden pixel.
   * DuckDuckGo's own theme picker is exactly this: `<label for="setting_kae_b">`
   * over an input too small to collect. Without labels those options cannot be
   * reached at all, which is what "I should be able to select those" measured.
   *
   * The roles are the accessibility definition of a widget rather than a list
   * grown one site at a time. What is deliberately NOT here is `cursor:pointer`:
   * on that same settings page it matched 41 elements, nearly all of them
   * decorative children inheriting the style from a parent.
   */
  var FOCUSABLE = [
    'a[href]', 'button', 'input', 'select', 'textarea', 'summary', 'audio[controls]',
    'video[controls]', '[tabindex]', '[contenteditable]',
    'label[for]', 'label:has(input)', 'label:has(select)', 'label:has(textarea)',
    '[role="button"]', '[role="link"]', '[role="menuitem"]', '[role="tab"]',
    '[role="checkbox"]', '[role="radio"]', '[role="switch"]', '[role="option"]',
    '[role="menuitemradio"]', '[role="menuitemcheckbox"]', '[role="combobox"]',
    '[role="slider"]', '[role="spinbutton"]', '[role="textbox"]',
    '[role="searchbox"]', '[role="treeitem"]'
  ].join(',');

  var FOCUSABLE_WITHOUT_HAS = FOCUSABLE.split(',')
    .filter(function (selector) { return selector.indexOf(':has(') < 0; })
    .join(',');

  var MIN_SIZE = 8;
  var identity = 0;

  /**
   * The element we last put focus on, remembered as an id rather than by the
   * class we style it with.
   *
   * A framework that re-renders rewrites the class attribute and takes our ring
   * with it, so the class is not evidence of anything a moment later. Measured
   * on DuckDuckGo: focus was on the element we had put it on, and the class was
   * already gone.
   */
  var focusedByUs = '';

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

  /**
   * Whether focus can ever land on this and be seen.
   *
   * The style checks are the obvious half. The other half is position, and it
   * is the one that matters most on a real site: a closed slide-out menu is
   * parked off-canvas with a transform, fully styled, not hidden by display,
   * visibility, opacity or aria-hidden, and every check above passes it.
   *
   * Measured on DuckDuckGo on the 8010: 56 collected elements, 10 of them on
   * screen. RIGHT from the header jumped past the button that opens the drawer
   * into the drawer itself, where focus sat invisible and every later press
   * moved around inside something nobody could see.
   *
   * So the test is reachability. A fixed element never scrolls, so it has to be
   * in the viewport now; anything else has to be inside the area the page can
   * scroll to. Below the fold is reachable and stays; outside the document
   * entirely is not.
   */
  function isVisible(element, fixed) {
    var style = window.getComputedStyle(element);
    if (style.visibility === 'hidden' || style.display === 'none' || style.opacity === '0') return false;
    if (element.disabled) return false;
    if (element.getAttribute('aria-hidden') === 'true') return false;

    var box = element.getBoundingClientRect();
    if (box.width < MIN_SIZE || box.height < MIN_SIZE) return false;

    if (fixed) {
      return box.right > 0 && box.bottom > 0 &&
        box.left < window.innerWidth && box.top < window.innerHeight;
    }

    var left = box.left + window.scrollX;
    var top = box.top + window.scrollY;
    var reachableWidth = Math.max(document.documentElement.scrollWidth, window.innerWidth);
    var reachableHeight = Math.max(document.documentElement.scrollHeight, window.innerHeight);
    return left + box.width > 0 && top + box.height > 0 &&
      left < reachableWidth && top < reachableHeight;
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

  /**
   * `:has()` is a decade newer than this app's minimum Android version, and an
   * unsupported selector makes `querySelectorAll` throw for the whole string
   * rather than skip the part it dislikes. On a WebView that old, one bad
   * selector would cost the page every focusable it has.
   */
  function focusableElements() {
    try {
      return document.querySelectorAll(FOCUSABLE);
    } catch (error) {
      return document.querySelectorAll(FOCUSABLE_WITHOUT_HAS);
    }
  }

  /**
   * Two labels naming the same control are two stops that do the same thing,
   * and DuckDuckGo's theme picker has exactly that: a swatch and a caption,
   * both pointing at one radio. The larger box is the one somebody is aiming
   * at, so the smaller is dropped rather than costing a press to cross.
   */
  function dropDuplicateLabels(results, elements) {
    var bestFor = {};
    for (var index = 0; index < results.length; index++) {
      var target = elements[results[index].elementIndex];
      if (!target || target.tagName.toLowerCase() !== 'label') continue;
      var names = target.getAttribute('for');
      if (!names) continue;

      var area = (results[index].right - results[index].left) *
        (results[index].bottom - results[index].top);
      if (!bestFor[names] || area > bestFor[names].area) {
        bestFor[names] = { area: area, id: results[index].id };
      }
    }

    return results.filter(function (entry) {
      var target = elements[entry.elementIndex];
      var names = target && target.tagName.toLowerCase() === 'label'
        ? target.getAttribute('for')
        : null;
      return !names || bestFor[names].id === entry.id;
    });
  }

  /**
   * Puts the ring back if a re-render took it off.
   *
   * Focus stays on the element; only the class we style it with is rewritten,
   * so the viewer is left on a page with no visible focus at all and no way to
   * tell where the next press will go. Checked on every collect, which is once
   * per press, so it heals before the next move.
   */
  function restoreRingIfStripped() {
    if (!focusedByUs) return;
    var element = find(focusedByUs);
    if (!element) return;
    if (!element.classList.contains(RING_CLASS)) {
      element.classList.add(RING_CLASS);
      element.setAttribute(RING_ATTRIBUTE, '');
    }
  }

  /**
   * An open modal, by the semantics the platform actually defines for one.
   *
   * `<dialog open>`, `aria-modal="true"` and `role="dialog"` are the whole
   * vocabulary a page has for saying "this is in front and the rest is not".
   * Where a page says it, focus is kept inside and BACK dismisses; where a page
   * does not, this finds nothing and ordinary navigation applies.
   *
   * No site-specific selectors. DuckDuckGo's own slide-out panel declares none
   * of these, so it is walked like any other part of the page rather than being
   * special-cased into a modal it never claimed to be.
   */
  function openModal() {
    var candidates = document.querySelectorAll(
      'dialog[open], [aria-modal="true"], [role="dialog"], [role="alertdialog"]'
    );
    for (var index = 0; index < candidates.length; index++) {
      var element = candidates[index];
      if (element.getAttribute('aria-hidden') === 'true') continue;
      var style = window.getComputedStyle(element);
      if (style.display === 'none' || style.visibility === 'hidden') continue;
      var box = element.getBoundingClientRect();
      if (box.width < MIN_SIZE || box.height < MIN_SIZE) continue;
      if (box.right <= 0 || box.bottom <= 0) continue;
      if (box.left >= window.innerWidth || box.top >= window.innerHeight) continue;
      return element;
    }
    return null;
  }

  function collect() {
    restoreRingIfStripped();
    var modal = openModal();
    var elements = focusableElements();
    var results = [];
    var order = 0;
    for (var index = 0; index < elements.length; index++) {
      var element = elements[index];
      order++;
      // Fixedness is needed before the visibility test rather than after it: a
      // fixed element is judged against the viewport and everything else
      // against the document.
      // With a modal in front, nothing behind it is a candidate. That is what
      // makes it a trap rather than a suggestion: focus cannot walk out into
      // the page underneath, which is where a D-pad otherwise strands somebody
      // with the dialog still on screen and no way back into it.
      if (modal && !modal.contains(element)) continue;

      var fixed = isFixed(element);
      if (!isVisible(element, fixed)) continue;
      if (!element.__nmSpatialId) {
        element.__nmSpatialId = 'nm' + (++identity);
      }
      var box = element.getBoundingClientRect();
      results.push({
        elementIndex: index,
        id: element.__nmSpatialId,
        left: Math.round(box.left),
        top: Math.round(box.top),
        right: Math.round(box.right),
        bottom: Math.round(box.bottom),
        order: order,
        // A sticky header and the body it floats over are their own groups, or
        // focus ping-pongs between them on every press.
        fixed: fixed,
        section: sectionOf(element)
      });
    }
    return {
      elements: dropDuplicateLabels(results, elements),
      modal: !!modal,
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
    focusedByUs = id;
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

  /**
   * Presses the focused control the way a finger does.
   *
   * `element.click()` alone is not a press. It fires one click event and
   * nothing else, and a control whose handler sits on `pointerdown` or
   * `mousedown` — which is most menu buttons, because it feels faster — never
   * hears it. Measured on DuckDuckGo's menu button on the 8010: focus was on
   * it, OK was pressed, and the drawer did not open.
   *
   * So the whole sequence is sent, in the order a pointer produces it, and
   * exactly one click among them: a toggle that acts on both `mousedown` and
   * `click` would otherwise open and immediately close again.
   */
  function activate() {
    var element = focusedByUs ? find(focusedByUs) : document.querySelector('.' + RING_CLASS);
    if (!element) return false;

    var box = element.getBoundingClientRect();
    var options = {
      bubbles: true,
      cancelable: true,
      composed: true,
      clientX: Math.round(box.left + box.width / 2),
      clientY: Math.round(box.top + box.height / 2),
      button: 0,
      buttons: 1
    };

    ['pointerover', 'pointerenter', 'pointerdown', 'mousedown'].forEach(function (type) {
      dispatchPress(element, type, options);
    });

    try { element.focus({ preventScroll: true }); } catch (error) { /* not focusable */ }

    ['mouseup', 'pointerup', 'click'].forEach(function (type) {
      dispatchPress(element, type, type === 'click' ? options : options);
    });
    return true;
  }

  /** Pointer events where the engine has them, mouse events otherwise. An
   *  unsupported constructor throws and would take the whole press with it. */
  function dispatchPress(element, type, options) {
    var event;
    try {
      if (type.indexOf('pointer') === 0) {
        event = new PointerEvent(type, options);
      } else {
        event = new MouseEvent(type, options);
      }
    } catch (error) {
      try {
        event = new MouseEvent(type, options);
      } catch (fallbackError) {
        return;
      }
    }
    element.dispatchEvent(event);
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
      stealsFocus: pageMovedFocusItself()
    };
  }

  /**
   * Whether the PAGE moved focus, as opposed to us.
   *
   * A page that grabs focus on load will keep fighting ours, so it keeps it.
   * But the element carrying our own ring is our doing, and counting it made
   * the probe report "this page steals focus" the moment we focused anything —
   * so a second look at the same document could never upgrade to focus mode,
   * because our own answer had become the evidence.
   */
  function pageMovedFocusItself() {
    var active = document.activeElement;
    if (!active || active === document.body || active === document.documentElement) return false;
    if (focusedByUs && active.__nmSpatialId === focusedByUs) return false;
    return true;
  }

  /**
   * Dismisses an open modal the way the platform says to.
   *
   * Escape is what every dialog implementation listens for, including
   * `<dialog>`'s own built-in handling, so this needs no knowledge of the site.
   * Reported back so BACK can fall through to its next meaning when the page
   * ignored it.
   */
  function dismissModal() {
    var modal = openModal();
    if (!modal) return false;

    var target = document.activeElement && modal.contains(document.activeElement)
      ? document.activeElement
      : modal;
    ['keydown', 'keyup'].forEach(function (type) {
      target.dispatchEvent(new KeyboardEvent(type, {
        key: 'Escape', code: 'Escape', keyCode: 27, which: 27,
        bubbles: true, cancelable: true
      }));
    });
    if (typeof modal.close === 'function') {
      try { modal.close(); } catch (error) { /* not a <dialog> */ }
    }
    return true;
  }

  window.__nmSpatial = {
    collect: function () { return JSON.stringify(collect()); },
    hasModal: function () { return openModal() ? 'true' : 'false'; },
    dismissModal: dismissModal,
    probe: function () { return JSON.stringify(probe()); },
    focus: focusById,
    activate: activate,
    style: applyRingStyle,
    /**
     * Takes the ring off whatever is wearing it.
     *
     * Looked up by id rather than by the class, because the class is exactly
     * what a re-render removes: searching for it found nothing while the ring
     * was still drawn, so a stale ring stayed on screen next to a pointer and
     * the two focus systems appeared to be running at once.
     */
    clear: function () {
      var element = focusedByUs ? find(focusedByUs) : null;
      if (!element) element = document.querySelector('.' + RING_CLASS);
      if (element) {
        element.classList.remove(RING_CLASS);
        element.removeAttribute(RING_ATTRIBUTE);
        try { element.blur(); } catch (error) { /* already gone */ }
      }
      focusedByUs = '';
    }
  };
})();
