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

  /** A third of the screen is a dialog rather than an embed. */
  var BLOCKING_FRAME_SHARE = 0.33;
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
  function isVisible(element, fixed, box) {
    // A frame is never a stop. We cannot see inside another origin's document,
    // so focus that lands on one has nowhere to go and every press after it
    // does nothing. The Guardian is the case: Sourcepoint's consent dialog is a
    // cross-origin iframe carrying a tabindex, it was collected as an ordinary
    // candidate, and the walk reached one of twenty-five focusables in two
    // presses before stopping dead inside it. The pointer taps into a frame the
    // way a finger does, which is the tool for the job.
    if (element.tagName && element.tagName.toLowerCase() === 'iframe') return false;

    var style = window.getComputedStyle(element);
    if (style.visibility === 'hidden' || style.display === 'none' || style.opacity === '0') return false;
    if (element.disabled) return false;
    if (element.getAttribute('aria-hidden') === 'true') return false;

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

  /**
   * Whether anything above this element pins it to the viewport.
   *
   * The ancestor chain is shared by everything in a subtree, so the answer for
   * one paragraph's link is the answer for every link in that paragraph. Asked
   * without the cache, an article of sixteen hundred controls costs a computed
   * style per ancestor per control — measured at half a second per snapshot on
   * the 8000, which is half a second per press.
   *
   * The cache lives for one snapshot. Positions change, and an answer kept
   * across presses would outlive the layout it describes.
   */
  function isFixed(element, cache) {
    var chain = [];
    var answer = false;
    for (var node = element; node && node !== document.body; node = node.parentElement) {
      if (cache.has(node)) { answer = cache.get(node); break; }
      chain.push(node);
      if (window.getComputedStyle(node).position === 'fixed') { answer = true; break; }
    }
    for (var index = 0; index < chain.length; index++) cache.set(chain[index], answer);
    return answer;
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

    // A label whose control is itself a stop is not a second stop. On an
    // ordinary form every field then costs two presses instead of one, and the
    // caption above a box is a place the ring can sit while doing nothing the
    // box does not already do. Measured on a page of one-of-every-input: 45
    // reported focusables where 27 were real controls.
    var reachable = {};
    for (var reachableIndex = 0; reachableIndex < results.length; reachableIndex++) {
      var candidate = elements[results[reachableIndex].elementIndex];
      if (candidate && candidate.id) reachable[candidate.id] = true;
    }

    return results.filter(function (entry) {
      var target = elements[entry.elementIndex];
      var names = target && target.tagName.toLowerCase() === 'label'
        ? target.getAttribute('for')
        : null;
      if (!names) return true;
      if (reachable[names]) return false;
      return bestFor[names].id === entry.id;
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

    /*
     * Only what is near enough to matter is measured.
     *
     * An article reports sixteen hundred focusables and the search can reach at
     * most the handful around the viewer: it moves within the screen while it
     * can, and scrolls by at most a screenful otherwise. Everything past that
     * band costs a computed style and a section lookup to be ranked last.
     * Measured on the 8000: half a second per snapshot, which is half a second
     * per press, on the pages people actually read.
     *
     * A screen of slack in each direction, so the next press after a scroll has
     * its candidates already in hand. When the band holds nothing in the
     * direction of travel the caller scrolls a screenful blind, which is the
     * behaviour it already had for a page with no candidates at all.
     */
    var bandTop = -window.innerHeight;
    var bandBottom = window.innerHeight * 2;
    var bandLeft = -window.innerWidth;
    var bandRight = window.innerWidth * 2;
    var fixedCache = new Map();

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

      // Geometry first, because it is the cheap question. A rectangle needs no
      // computed style, no ancestor walk and no section lookup, and it rejects
      // most of a long page on its own.
      var box = element.getBoundingClientRect();
      if (box.width < MIN_SIZE || box.height < MIN_SIZE) continue;
      if (box.bottom < bandTop || box.top > bandBottom) continue;
      if (box.right < bandLeft || box.left > bandRight) continue;

      var fixed = isFixed(element, fixedCache);
      if (!isVisible(element, fixed, box)) continue;
      if (!element.__nmSpatialId) {
        element.__nmSpatialId = 'nm' + (++identity);
      }
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
    var collected = dropDuplicateLabels(results, elements);

    lastCollected.clear();
    for (var cacheIndex = 0; cacheIndex < collected.length; cacheIndex++) {
      lastCollected.set(collected[cacheIndex].id, elements[collected[cacheIndex].elementIndex]);
    }

    /**
     * The source has to be an element the search will also see.
     *
     * Reporting the ring instead of `document.activeElement` is right — a text
     * field wears the ring without taking DOM focus, so the keyboard stays down
     * — but reporting a ring that is no longer in the collected set is worse
     * than reporting nothing. The Kotlin side looks the id up among the
     * candidates, does not find it, and falls back to a 1x1 rectangle at the
     * top-left corner, so the next press is measured from a place nobody is
     * standing. Measured as a walk that reached five controls out of
     * twenty-five and jumped across the page between them.
     */
    var present = {};
    for (var presentIndex = 0; presentIndex < collected.length; presentIndex++) {
      present[collected[presentIndex].id] = true;
    }
    // The DOM's focus first, and the ring only when the DOM has none.
    //
    // The other way round was tried and reverted the same evening: reporting
    // the ring as the source made every direction dead from the first field,
    // because Kotlin then had a real section for the source and section memory
    // resolved the move straight back onto the element it started from. The ring
    // is still what a text field wears without DOM focus, so it has to be the
    // fallback, but it cannot be the first answer.
    var active = document.activeElement && document.activeElement.__nmSpatialId;
    var ringed = active && present[active] ? active : '';
    if (!ringed && focusedByUs && present[focusedByUs]) ringed = focusedByUs;

    return {
      elements: collected,
      modal: !!modal,
      viewportWidth: window.innerWidth,
      viewportHeight: window.innerHeight,
      scrollY: Math.round(window.scrollY),
      scrollHeight: Math.round(document.documentElement.scrollHeight),
      scrollX: Math.round(window.scrollX),
      scrollWidth: Math.round(document.documentElement.scrollWidth),
      focused: ringed
    };
  }

  /**
   * What the last snapshot found, so looking an element up again does not walk
   * the document a second time.
   *
   * Every press does a snapshot and then at least one lookup, and on a long
   * article the lookup was as expensive as the snapshot. Held only until the
   * next snapshot, and each entry is checked for still being in the document
   * before it is trusted.
   */
  var lastCollected = new Map();

  function find(id) {
    var cached = lastCollected.get(id);
    if (cached && cached.isConnected !== false) return cached;

    // The full walk stays as the fallback, and goes through the same guarded
    // selector as everything else: `:has()` is newer than this app's minimum
    // Android version, and an unsupported selector throws for the whole string.
    var elements = focusableElements();
    for (var index = 0; index < elements.length; index++) {
      if (elements[index].__nmSpatialId === id) return elements[index];
    }
    return null;
  }

  /**
   * Takes the ring off everything wearing any part of it.
   *
   * The class and the attribute are applied together and can be removed
   * separately: a re-render rewrites `class` and leaves our attribute behind,
   * so looking for the class alone missed that element and it kept the marker
   * forever. Two elements then answered to `[data-nm-spatial-ring]`, one of
   * them a ghost, and anything reading the ring to find out where focus was got
   * whichever came first in the document.
   */
  function stripRingEverywhere() {
    var marked = document.querySelectorAll('.' + RING_CLASS + ',[' + RING_ATTRIBUTE + ']');
    for (var index = 0; index < marked.length; index++) {
      marked[index].classList.remove(RING_CLASS);
      marked[index].removeAttribute(RING_ATTRIBUTE);
    }
  }

  /**
   * Controls the browser drives with a native sheet rather than in the page.
   *
   * A text control with DOM focus raises the system keyboard, and the leanback
   * IME then consumes every directional key -- measured on the 8000 as six
   * presses that moved nothing on the first field of a form.
   *
   * `select` is here for consistency rather than from a measurement: it is
   * opened in the native list sheet on OK, so DOM focus on it buys nothing
   * and a focused select is documented to consume UP and DOWN for its own
   * value. A column sweep still stops at the first select on the test page
   * with this in place, so that stop has another cause and is still open.
   *
   * None of them need DOM focus from us. The ring is ours, and OK opens the
   * native overlay, which is the only moment either should take over.
   *
   * A label is included because focusing one delegates into the control it
   * names, which is how a caption three fields up raised a keyboard for an
   * input nobody had reached yet.
   */
  function raisesKeyboard(element) {
    var tag = element.tagName.toLowerCase();
    if (tag === 'textarea' || tag === 'select') return true;
    if (element.isContentEditable) return true;
    if (tag === 'label') {
      var control = element.control ||
        (element.getAttribute('for') ? document.getElementById(element.getAttribute('for')) : null);
      return !!control && raisesKeyboard(control);
    }
    if (tag !== 'input') return false;
    return NON_TEXT_INPUT_TYPES.indexOf((element.type || 'text').toLowerCase()) === -1;
  }

  /** Everything an `input` can be that does not put a caret anywhere. Written
   *  as the exceptions because the type list grows and the default for an
   *  unknown type is text. */
  var NON_TEXT_INPUT_TYPES = [
    'button', 'checkbox', 'color', 'file', 'hidden', 'image',
    'radio', 'range', 'reset', 'submit'
  ];

  function focusById(id) {
    var element = find(id);
    if (!element) return false;

    stripRingEverywhere();

    element.classList.add(RING_CLASS);
    element.setAttribute(RING_ATTRIBUTE, '');
    focusedByUs = id;

    /**
     * Arriving at a text field must not give it DOM focus.
     *
     * WebView raises the system keyboard the moment a text control is focused,
     * and the leanback IME consumes every directional key while it is up. So
     * walking onto the first field of a form ended the walk: the ring sat on it
     * and every subsequent press went into a keyboard nobody asked for. Measured
     * on the 8000 against a page carrying one of every input type — six presses,
     * no movement, which is a dead remote.
     *
     * The ring is ours and needs no DOM focus to be drawn. Editing is the
     * native overlay's job and it opens on OK, which is the only moment a
     * keyboard should ever appear.
     *
     * A label is included because focusing one delegates to the control it
     * names, which is how a label three fields up raised the keyboard for an
     * input the viewer had not reached yet.
     */
    if (!raisesKeyboard(element)) {
      // preventScroll, because the Kotlin side has already decided whether a
      // screenful of scrolling is wanted and the browser's own scroll-into-view
      // would fight that decision.
      try {
        element.focus({ preventScroll: true });
      } catch (error) {
        element.focus();
      }
    } else if (document.activeElement && document.activeElement !== document.body) {
      // And take focus off whatever had it, or the previous field keeps the
      // caret and the keyboard stays up behind the ring.
      try { document.activeElement.blur(); } catch (error) { /* not blurrable */ }
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

    ['mouseup', 'pointerup'].forEach(function (type) {
      dispatchPress(element, type, options);
    });

    // The press ends with a real activation, not a dispatched click event.
    // click() runs the behaviour the element defines — an anchor following its
    // href, a button submitting — where a dispatched event only notifies
    // listeners.
    //
    // A label activates its control, which is what the platform says a label is
    // for, and clicking the label element itself is not reliably the same
    // thing: measured on DuckDuckGo's menu, whose hamburger is a
    // <label for="sidemenu-toggle"> over a hidden checkbox. Clicking the label
    // left the checkbox untouched and the drawer shut; clicking the control it
    // names opens it. Generic to every label-and-control pair, which is most of
    // the switches and radio groups on the web.
    var control = element.tagName.toLowerCase() === 'label'
      ? (element.control || (element.htmlFor ? document.getElementById(element.htmlFor) : null))
      : null;
    (control || element).click();
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
      stealsFocus: pageMovedFocusItself(),
      blockingFrame: hasBlockingFrame(),
      focusInFrame: focusEscapedIntoFrame()
    };
  }

  /**
   * Whether focus is inside a frame right now.
   *
   * Not the same question as [hasBlockingFrame], and the one that actually
   * fires: a consent dialog calls focus() on its own iframe after it renders,
   * which is well after the mode was decided. Once focus is in there the D-pad
   * is inert, because we cannot see into another origin to find the next stop
   * and the page underneath is unreachable behind the dialog.
   */
  function focusEscapedIntoFrame() {
    var active = document.activeElement;
    return !!(active && active.tagName && active.tagName.toLowerCase() === 'iframe');
  }

  /**
   * A frame large enough to be the page, which is what a consent wall is.
   *
   * The Guardian is the case that found this. Sourcepoint's dialog is a
   * cross-origin iframe carrying a tabindex, so it was collected as an ordinary
   * candidate, focus landed on it, and every press after that did nothing: we
   * cannot see inside another origin's document and there is nothing outside it
   * to move to. One of twenty-five focusables reached, and the twenty-five were
   * behind a wall nobody could dismiss.
   *
   * The pointer can. A synthesised tap is a real touch event at a coordinate,
   * and the frame handles it exactly as it would a finger, so a page walled off
   * this way is a page the cursor has to drive.
   */
  function hasBlockingFrame() {
    var frames = document.querySelectorAll('iframe');
    var viewportArea = window.innerWidth * window.innerHeight;
    if (viewportArea <= 0) return false;
    for (var index = 0; index < frames.length; index++) {
      var rect = frames[index].getBoundingClientRect();
      if (rect.width <= 0 || rect.height <= 0) continue;
      var style = window.getComputedStyle(frames[index]);
      if (style.visibility === 'hidden' || style.display === 'none') continue;
      // Fixed or absolute, because an article's embedded video is large and is
      // not a wall. A dialog is taken out of the flow to sit over the page.
      if (style.position !== 'fixed' && style.position !== 'absolute') continue;
      if ((rect.width * rect.height) / viewportArea >= BLOCKING_FRAME_SHARE) return true;
    }
    return false;
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

  /**
   * Where the focused element is, so the app can press it with a real touch.
   *
   * Synthetic events are untrusted, and a control can tell. DuckDuckGo's menu
   * is a `display:none` checkbox driven from the label's pointer events, and no
   * event this file can create will move it — measured, repeatedly, while
   * ordinary links activated from the same code path. A touch injected into the
   * view is a trusted event, which is what the pointer already uses, so focus
   * mode borrows it. Coordinates are CSS pixels; the caller scales them.
   */
  function focusedRect() {
    var element = focusedByUs ? find(focusedByUs) : document.querySelector('[' + RING_ATTRIBUTE + ']');
    if (!element) return '';
    var box = element.getBoundingClientRect();
    if (box.width <= 0 || box.height <= 0) return '';
    return JSON.stringify({
      x: Math.round(box.left + box.width / 2),
      y: Math.round(box.top + box.height / 2),
      viewportWidth: window.innerWidth,
      viewportHeight: window.innerHeight
    });
  }

  /**
   * Lets go of a field the page focused by itself.
   *
   * A search page that focuses its own box on load also opens the suggestion
   * list attached to it, and on a television that list covers the results
   * underneath with something nobody asked for and nothing dismisses. The page
   * is entitled to focus its field; it is not entitled to keep it while we are
   * the ones navigating.
   *
   * Only ever releases focus the page took. Anything we put focus on is left
   * exactly where it is.
   */
  function releasePageFocus() {
    var active = document.activeElement;
    if (!active || active === document.body || active === document.documentElement) return false;
    if (focusedByUs && active.__nmSpatialId === focusedByUs) return false;

    var tag = active.tagName ? active.tagName.toLowerCase() : '';
    if (tag !== 'input' && tag !== 'textarea' && !active.isContentEditable) return false;

    try { active.blur(); } catch (error) { return false; }
    return true;
  }

  window.__nmSpatial = {
    collect: function () { return JSON.stringify(collect()); },
    focusedRect: focusedRect,
    releasePageFocus: releasePageFocus,
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
      stripRingEverywhere();
      if (element) {
        try { element.blur(); } catch (error) { /* already gone */ }
      }
      focusedByUs = '';
    }
  };
})();
