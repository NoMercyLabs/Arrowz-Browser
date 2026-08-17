/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 *
 * The page side of native form editing. It describes a field that took focus
 * and writes a value back; whether that opens anything is decided in Kotlin.
 *
 * Nothing here appends a <script> or <style> tag. A site with a strict content
 * security policy blocks both, and this arrives through evaluateJavascript at
 * document start instead.
 */
(function () {
  'use strict';

  if (window.__nmForms) return;

  // Typed into. Checkboxes, radios, buttons and file pickers are activated
  // rather than edited, and an overlay over one of those is a keyboard nobody
  // asked for.
  var TEXT_TYPES = [
    'text', 'search', 'email', 'tel', 'url', 'password', 'number',
    'date', 'time', 'datetime-local', 'month', 'week', ''
  ];

  var identity = 0;
  var dirty = false;

  // Our own focus() must not read as the viewer focusing something. Committing
  // refocuses the element so navigation carries on from it, and without this
  // that refocus reopens the sheet we just closed, forever.
  var suppressReports = false;

  function stamp(element) {
    if (!element.__nmFieldId) {
      element.__nmFieldId = 'fld' + (++identity);
    }
    return element.__nmFieldId;
  }

  function find(id) {
    var candidates = document.querySelectorAll('input,textarea,select,[contenteditable]');
    for (var index = 0; index < candidates.length; index++) {
      if (candidates[index].__nmFieldId === id) return candidates[index];
    }
    return null;
  }

  function kindOf(element) {
    var tag = element.tagName ? element.tagName.toLowerCase() : '';
    if (tag === 'select') return 'select';
    if (tag === 'textarea') return 'text';
    if (tag === 'input') {
      var type = (element.getAttribute('type') || '').toLowerCase();
      return TEXT_TYPES.indexOf(type) >= 0 ? 'text' : '';
    }
    if (element.isContentEditable) return 'editable';
    return '';
  }

  /**
   * The label an accessibility tree would report, in the order it would try.
   * A field that resolves to nothing is still editable — refusing would refuse
   * most of the login forms on the web — and is labelled generically instead.
   */
  function labelOf(element) {
    var byFor = element.id ? document.querySelector('label[for="' + CSS.escape(element.id) + '"]') : null;
    if (byFor && byFor.textContent.trim()) return byFor.textContent.trim();

    var wrapping = element.closest ? element.closest('label') : null;
    if (wrapping && wrapping.textContent.trim()) return wrapping.textContent.trim();

    var aria = element.getAttribute('aria-label');
    if (aria && aria.trim()) return aria.trim();

    var labelledBy = element.getAttribute('aria-labelledby');
    if (labelledBy) {
      var target = document.getElementById(labelledBy);
      if (target && target.textContent.trim()) return target.textContent.trim();
    }

    var placeholder = element.getAttribute('placeholder');
    if (placeholder && placeholder.trim()) return placeholder.trim();

    var title = element.getAttribute('title');
    if (title && title.trim()) return title.trim();

    var name = element.getAttribute('name');
    return name ? name.trim() : '';
  }

  function optionsOf(element) {
    if (element.tagName.toLowerCase() !== 'select') return [];
    var options = [];
    for (var index = 0; index < element.options.length; index++) {
      var option = element.options[index];
      options.push({
        label: (option.textContent || option.value || '').trim(),
        value: option.value,
        selected: option.selected
      });
    }
    return options;
  }

  function valueOf(element) {
    if (element.isContentEditable && element.tagName.toLowerCase() !== 'input') {
      return element.textContent || '';
    }
    return element.value === undefined ? '' : element.value;
  }

  function describe(element) {
    var kind = kindOf(element);
    if (!kind) return null;
    return {
      id: stamp(element),
      kind: kind,
      label: labelOf(element),
      value: valueOf(element),
      inputType: (element.getAttribute('type') || '').toLowerCase(),
      required: !!element.required,
      // A field the page has already rejected. Showing that in the sheet saves
      // a round trip through a submit the viewer cannot see the result of.
      invalid: element.validity ? !element.validity.valid : false,
      maxLength: typeof element.maxLength === 'number' && element.maxLength > 0 ? element.maxLength : 0,
      multiline: element.tagName.toLowerCase() === 'textarea' || !!element.isContentEditable,
      options: optionsOf(element)
    };
  }

  function report(element) {
    if (suppressReports) return;
    var described = describe(element);
    if (!described) return;
    if (window.NmForms && window.NmForms.onFieldFocused) {
      window.NmForms.onFieldFocused(JSON.stringify(described));
    }
  }

  /**
   * A framework that controls its own inputs replaces the value setter on the
   * element instance and listens for its own synthetic events. Assigning
   * `.value` updates the pixels and leaves the framework's state untouched, so
   * the next render puts the old value back and the viewer watches what they
   * typed disappear. The prototype setter is the path the framework's own
   * instrumentation wraps.
   */
  function writeValue(element, value) {
    var prototype = window.HTMLInputElement.prototype;
    if (element.tagName.toLowerCase() === 'textarea') {
      prototype = window.HTMLTextAreaElement.prototype;
    }
    var descriptor = Object.getOwnPropertyDescriptor(prototype, 'value');
    if (descriptor && descriptor.set) {
      descriptor.set.call(element, value);
    } else {
      element.value = value;
    }
  }

  function fire(element, name) {
    element.dispatchEvent(new Event(name, { bubbles: true }));
  }

  function commit(id, value) {
    var element = find(id);
    if (!element) return false;

    if (element.isContentEditable && element.tagName.toLowerCase() !== 'input') {
      element.textContent = value;
    } else {
      writeValue(element, value);
    }
    fire(element, 'input');
    fire(element, 'change');
    refocus(element);
    return true;
  }

  function select(id, optionIndex) {
    var element = find(id);
    if (!element || element.tagName.toLowerCase() !== 'select') return false;
    if (optionIndex < 0 || optionIndex >= element.options.length) return false;

    element.selectedIndex = optionIndex;
    fire(element, 'input');
    fire(element, 'change');
    refocus(element);
    return true;
  }

  /** So spatial navigation carries on from the field rather than from the top
   *  of the document, without the refocus reading as a fresh interaction. */
  function refocus(element) {
    suppressReports = true;
    try {
      element.focus({ preventScroll: true });
    } catch (error) {
      element.focus();
    }
    suppressReports = false;
  }

  function blurFocused() {
    var active = document.activeElement;
    if (!active || active === document.body) return false;
    if (!kindOf(active)) return false;
    suppressReports = true;
    active.blur();
    suppressReports = false;
    return true;
  }

  document.addEventListener('focusin', function (event) {
    report(event.target);
  }, true);

  /*
   * Any edit at all. The tab that holds it is then exempt from suspension,
   * because WebView.saveState does not reliably carry unsaved input and losing
   * what somebody typed on a television keyboard is unforgivable.
   *
   * Pushed once rather than polled, because the exemption is read while memory
   * pressure is being answered and a round trip through the page is exactly
   * what is unavailable at that moment.
   */
  function markDirty() {
    if (dirty) return;
    dirty = true;
    if (window.NmForms && window.NmForms.onDirty) window.NmForms.onDirty();
  }

  document.addEventListener('input', markDirty, true);
  document.addEventListener('change', markDirty, true);

  window.__nmForms = {
    commit: commit,
    select: select,
    blur: blurFocused,
    isDirty: function () { return dirty ? 'true' : 'false'; },
    // Whatever holds focus right now, asked rather than remembered: a page can
    // move its own focus at any time and a stale answer sends BACK to the wrong
    // meaning.
    focused: function () {
      var active = document.activeElement;
      return active && kindOf(active) ? 'true' : 'false';
    }
  };
})();
