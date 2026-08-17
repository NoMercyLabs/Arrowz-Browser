/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 *
 * Fills every kind of web field through the native overlay and checks what the
 * page actually received.
 *
 * The overlay is the part of this browser a viewer touches most and the part a
 * unit test can say least about: what matters is whether the value reached the
 * element and whether the page's own script saw `input` and `change`, because a
 * framework that never sees those has been handed nothing.
 *
 * Usage:
 *   node tools/form-drive.mjs <serial> <url>
 */

import { execFileSync } from 'node:child_process';

const serial = process.argv[2];
const url = process.argv[3];

if (!serial || !url) {
  console.error('usage: node tools/form-drive.mjs <serial> <url>');
  process.exit(2);
}

const PACKAGE = 'com.nomercylabs.browser.debug';
const KEYS = { down: 20, ok: 23, back: 4, enter: 66 };

function adb(...args) {
  return execFileSync('adb', ['-s', serial, ...args], {
    encoding: 'utf8',
    stdio: ['ignore', 'pipe', 'ignore'],
  });
}

function requireForeground() {
  const top = adb('shell', 'dumpsys activity activities');
  const line = top.split('\n').find((entry) => entry.includes('topResumedActivity')) ?? '';
  if (!line.includes(PACKAGE)) throw new Error(`${PACKAGE} is not in front. Refusing to send keys.`);
}

function press(key) {
  requireForeground();
  adb('shell', `input keyevent ${KEYS[key]}`);
}

/** `input text` splits on spaces and drops everything after the first one, so a
 *  typed phrase arrived as its last word. %s is the escape it understands. */
function type(text) {
  requireForeground();
  adb('shell', `input text ${JSON.stringify(text.replace(/ /g, '%s'))}`);
}

function sleep(ms) {
  Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, ms);
}

function attach() {
  const pid = adb('shell', 'pidof', PACKAGE).trim().split(/\s+/)[0];
  if (!pid) throw new Error(`${PACKAGE} is not running on ${serial}`);
  const port = 9500 + (Number(pid) % 100);
  adb('forward', `tcp:${port}`, `localabstract:webview_devtools_remote_${pid}`);
  return port;
}

async function pageSocket(port) {
  const listing = await (await fetch(`http://localhost:${port}/json/list`)).json();
  const page = listing.find((entry) => entry.type === 'page' && entry.url.startsWith('http'));
  if (!page) throw new Error('no page target on the devtools socket');
  return page.webSocketDebuggerUrl;
}

function evaluateOn(socketUrl, expression) {
  return new Promise((resolve, reject) => {
    const socket = new WebSocket(socketUrl);
    const timer = setTimeout(() => { socket.close(); reject(new Error('evaluate timed out')); }, 15000);
    socket.onopen = () => socket.send(JSON.stringify({
      id: 1,
      method: 'Runtime.evaluate',
      params: { expression, awaitPromise: true, returnByValue: true },
    }));
    socket.onmessage = (event) => {
      const message = JSON.parse(event.data);
      if (message.id !== 1) return;
      clearTimeout(timer);
      socket.close();
      const result = message.result?.result;
      if (result?.subtype === 'error') reject(new Error(result.description));
      else resolve(result?.value);
    };
    socket.onerror = (error) => { clearTimeout(timer); reject(error); };
  });
}

/**
 * What each field should end up holding.
 *
 * Deliberately not the same string everywhere: a number field that accepted
 * letters, or an email field that dropped the @, would both pass a test that
 * typed "abc" into all of them.
 */
const CASES = [
  { id: 'f-text', typed: 'hello there', expect: 'hello there' },
  { id: 'f-email', typed: 'you@example.org', expect: 'you@example.org' },
  { id: 'f-number', typed: '42', expect: '42' },
  { id: 'f-tel', typed: '0612345678', expect: '0612345678' },
  { id: 'f-url', typed: 'https://example.org', expect: 'https://example.org' },
  { id: 'f-password', typed: 'hunter2', expect: 'hunter2' },
  { id: 'f-search', typed: 'query', expect: 'query' },
  { id: 'f-required', typed: 'filled', expect: 'filled' },
  { id: 'f-textarea', typed: 'a longer answer', expect: 'a longer answer' },
];

async function main() {
  const port = attach();
  const socket = await pageSocket(port);

  await evaluateOn(socket, `location.href=${JSON.stringify(url)}`).catch(() => {});
  sleep(7000);

  const results = [];

  for (const testCase of CASES) {
    // Placed through the page rather than walked to: this measures the overlay,
    // and spending forty presses arriving at each field measures navigation
    // again, which has its own harness.
    await evaluateOn(socket, `(function () {
      var element = document.getElementById(${JSON.stringify(testCase.id)});
      window.__nmSpatial.focus(element.__nmSpatialId);
    })()`);
    sleep(500);

    // Wait for the page to say the field took focus, which is the moment the
    // overlay is actually up. Typing on a fixed delay put characters into the
    // sheet before it existed, and the run came out shifted by one field --
    // every value landing in the box after the one it was meant for.
    press('ok');
    let opened = false;
    for (let wait = 0; wait < 12 && !opened; wait++) {
      sleep(400);
      opened = await evaluateOn(socket,
        `document.activeElement && document.activeElement.id === ${JSON.stringify(testCase.id)}`);
    }

    type(testCase.typed);
    sleep(900);

    // Committed through the sheet's own Done row, not by sending ENTER. The
    // field's Go action belongs to the IME, and an injected ENTER does not
    // trigger it -- so the sheet stayed open, every later press went into it,
    // and eight of nine fields received nothing at all.
    press('down');
    sleep(500);
    press('ok');

    // And wait for the value to arrive rather than assuming it has: the commit
    // is a round trip through the page.
    let state = null;
    for (let wait = 0; wait < 12; wait++) {
      sleep(400);
      state = JSON.parse(await evaluateOn(socket, 'window.formState()'));
      if (state[testCase.id] === testCase.expect) break;
    }
    const got = state ? state[testCase.id] : null;
    results.push({
      id: testCase.id,
      expected: testCase.expect,
      got,
      ok: got === testCase.expect,
      sawInput: !!state && state.events.includes(`input:${testCase.id}`),
      sawChange: !!state && state.events.includes(`change:${testCase.id}`),
    });

    // Deliberately no BACK here. Pressing it after a commit walked the browser
    // out to its home screen, and every field after the first was then being
    // typed into a page nobody was looking at.
  }

  const failures = results.filter((entry) => !entry.ok || !entry.sawInput || !entry.sawChange);
  console.log(JSON.stringify({
    url,
    passed: results.length - failures.length,
    total: results.length,
    results,
  }, null, 1));
  process.exit(failures.length === 0 ? 0 : 1);
}

main().catch((error) => { console.error(String(error)); process.exit(1); });
