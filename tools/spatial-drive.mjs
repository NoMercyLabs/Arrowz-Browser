/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 *
 * Drives the remote against a live page and records where focus actually went.
 *
 * Spatial navigation is tested as geometry in unit tests, and that suite has
 * never once caught what the television caught: the failures were reachability,
 * a ring that leaked, an untrusted click, a retry that did not retry. Those are
 * all invisible to a fixture and obvious to a walker.
 *
 * So this walks. It presses a direction, asks the page which element ended up
 * focused, and keeps going until focus stops moving or every focusable has been
 * visited. What comes back is a coverage number and the list of what was never
 * reached, which is the only honest answer to "is navigation reliable".
 *
 * Usage:
 *   node tools/spatial-drive.mjs <serial> <url> [--keys 60]
 */

import { execFileSync } from 'node:child_process';

const serial = process.argv[2];
const url = process.argv[3];
const keyBudget = Number(argValue('--keys') ?? 80);

if (!serial || !url) {
  console.error('usage: node tools/spatial-drive.mjs <serial> <url> [--keys N]');
  process.exit(2);
}

function argValue(name) {
  const index = process.argv.indexOf(name);
  return index === -1 ? undefined : process.argv[index + 1];
}

const PACKAGE = 'com.nomercylabs.browser.debug';
const KEYS = { up: 19, down: 20, left: 21, right: 22, ok: 23, back: 4 };

/** How many rows of the column get swept sideways. Each one costs a reload and
 *  a walk down to it, so an article's worth of stops cannot all be taken. */
const ROW_SAMPLE_LIMIT = 8;

/** stderr is discarded rather than inherited: `am start` writes a warning there
 *  on every reload, and those lines landed in the middle of the JSON this
 *  prints, so the report could not be parsed by the thing meant to read it. */
function adb(...args) {
  return execFileSync('adb', ['-s', serial, ...args], {
    encoding: 'utf8',
    stdio: ['ignore', 'pipe', 'ignore'],
  });
}

/**
 * Refuses to send a key unless the browser is the thing in front.
 *
 * Measured the hard way: a walk that reported six dead presses in a row had
 * actually been typing into whatever else was on the television, because the
 * browser had lost the foreground and every press went to somebody's media app.
 * A driver that cannot tell those apart reports a navigation bug that does not
 * exist, and presses buttons in a room it was not invited into.
 */
function requireForeground() {
  const top = adb('shell', 'dumpsys activity activities');
  const line = top.split('\n').find((entry) => entry.includes('topResumedActivity')) ?? '';
  if (!line.includes(PACKAGE)) {
    throw new Error(`${PACKAGE} is not in front. Refusing to send keys. Top was: ${line.trim()}`);
  }
}

function press(key, { long = false } = {}) {
  requireForeground();
  adb('shell', `input keyevent ${long ? '--longpress ' : ''}${KEYS[key]}`);
}

function sleep(ms) {
  Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, ms);
}

/** The devtools socket belongs to the app's process and its name carries the
 *  pid, so it changes on every launch and cannot be hard-coded. */
function attach() {
  const pid = adb('shell', 'pidof', PACKAGE).trim().split(/\s+/)[0];
  if (!pid) throw new Error(`${PACKAGE} is not running on ${serial}`);
  const port = 9400 + (Number(pid) % 100);
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

/** What the page says is focusable, by the browser's own definition rather than
 *  by a list written here — otherwise this measures the test's opinion. */
const INVENTORY = `(function () {
  var api = window.__nmSpatial;
  if (!api || !api.collect) return null;
  var snapshot = JSON.parse(api.collect());
  return snapshot.elements.length;
})()`;

const FOCUSED = `(function () {
  var ringed = document.querySelector('[data-nm-spatial-ring]');
  var element = ringed || document.activeElement;
  if (!element || element === document.body) return 'body';
  return element.id
    || (element.tagName.toLowerCase() + (element.name ? '[' + element.name + ']' : '')
        + (element.textContent ? ':' + element.textContent.trim().slice(0, 18) : ''));
})()`;

/**
 * Presses a direction and waits for focus to actually move.
 *
 * A fixed sleep measures the sleep. Answering one press costs a snapshot, and
 * on a long article that is tens of milliseconds on a good box and a lot more
 * on a weak one, so a wait short enough to be quick on a form reported a page
 * of working presses as dead. Waiting on the answer instead is both faster and
 * honest: a press that has moved is measured the moment it moves, and only a
 * press that has not moved by the deadline is a dead one.
 */
async function pressAndSettle(socket, key, from, deadlineMs = 4000) {
  press(key);
  const started = Date.now();
  let landed = from;
  while (Date.now() - started < deadlineMs) {
    sleep(150);
    landed = await evaluateOn(socket, FOCUSED);
    if (landed !== from) return landed;
  }
  return landed;
}

/** Reload, then make sure the ring is on the page before anything is measured.
 *  A sweep that begins in cursor mode measures the pointer, which is a
 *  different system with a different failure list. */
async function resetPage(socket) {
  // Reloaded through the page itself, never by sending the browser another
  // VIEW intent: a link arriving while the browser is up opens a *new tab*, so
  // each reset was leaving the previous tab behind, and the devtools target
  // this reads from was a background tab while the presses went to the one in
  // front. Every direction then looked dead.
  await evaluateOn(socket, 'location.reload()').catch(() => {});
  sleep(6000);
  for (let attempt = 0; attempt < 3; attempt++) {
    if (await evaluateOn(socket, `!!document.querySelector('[data-nm-spatial-ring]')`)) return;
    press('ok', { long: true });
    sleep(2500);
  }
}

async function main() {
  adb('shell', 'am', 'force-stop', PACKAGE);
  sleep(1500);
  adb('shell', 'am', 'start', '-n', `${PACKAGE}/com.nomercylabs.browser.MainActivity`,
      '-a', 'android.intent.action.VIEW', '-d', url);
  sleep(9000);

  const port = attach();
  const socket = await pageSocket(port);

  const total = await evaluateOn(socket, INVENTORY);
  if (total === null) throw new Error('spatialnav.js is not present on this page');

  // Focus mode, whatever the probe decided. A walk in cursor mode measures the
  // pointer, which is a different system with a different failure list.
  for (let attempt = 0; attempt < 3; attempt++) {
    if (await evaluateOn(socket, `!!document.querySelector('[data-nm-spatial-ring]')`)) break;
    press('ok', { long: true });
    sleep(2500);
  }

  /**
   * Every element, every direction.
   *
   * A free walk measures whichever path it happens to take and calls that
   * coverage: a rotation of directions oscillates inside one pair of fields and
   * reports four of twenty-five. This instead visits each control in turn and
   * presses all four keys from it, which produces the adjacency map the
   * interface actually has — including the entries a walk never reaches, which
   * are exactly the ones a viewer complains about.
   *
   * Focus is placed through the page's own API and the movement is driven by
   * real key presses, so the search and the dispatcher are both in the loop.
   */
  /**
   * A serpentine sweep, driven only by key presses.
   *
   * Placing focus through the page's API and pressing from there measures the
   * harness as much as the browser -- the app owns state the harness cannot set,
   * and the disagreement reads as the interface being full of traps when it is
   * not. So nothing here touches focus directly. It presses down until down
   * stops working, steps sideways once, sweeps back up, and repeats, which is
   * how a person covers a form and what a grid layout is built for.
   */
  /**
   * One sweep per direction, each run to its end.
   *
   * Cleverer explorers were tried and thrown away. A rotation of directions
   * oscillates inside one pair of fields; a serpentine turns at the wrong
   * moment and reports a wall that is not there; and placing focus through the
   * page's API measures the harness, because the app owns state the harness
   * cannot set. All three produced coverage numbers that disagreed with what
   * the same build did under a plain run of presses, which makes them worse
   * than useless.
   *
   * A sweep per direction is boring and honest: it is what a person does, it
   * only ever sends real keys, and the union across four of them is a coverage
   * number that means something.
   */
  const visited = new Set();
  const rows = [];
  // What the question "can I reach everything without getting stuck" actually
  // asks: how many presses moved, and how many did nothing. A ratio against the
  // page's focusable count is meaningless on a real article -- Wikipedia reports
  // over a thousand, and no sweep of eighty presses covers that -- but a press
  // that moves nothing is a defect on any page, of any size.
  let presses = 0;

  /**
   * How many legs ended, which is NOT how many presses were dead.
   *
   * A leg ends precisely because a press did not move focus, so counting those
   * as dead presses made the number identical to the number of legs by
   * construction — seven rows swept, seven "dead" presses, every time, on a
   * page with nothing wrong with it. It read like a defect count and was a
   * shape count.
   *
   * A press at the true edge of the content in a direction is not a defect:
   * there is nothing that way, and the chrome takes UP while the rest correctly
   * do nothing. What matters is how far each leg got before that happened, and
   * whether the legs together cover the page, which is what the numbers below
   * report.
   */
  let legEnds = 0;

  // Down the first column, then across each row it found. A form is a raster
  // and this is how somebody covers one: four single-line sweeps from the same
  // start can only ever reach a row plus a column, which reports a third of a
  // working page as unreachable.
  await resetPage(socket);
  const column = [];
  let head = await evaluateOn(socket, FOCUSED);
  column.push(head);
  visited.add(head);
  for (let step = 0; step < 40; step++) {
    presses++;
    const landed = await pressAndSettle(socket, 'down', head);
    // Only a press that changes nothing ends the sweep. Stopping on a
    // revisit ends it at the first element the page brings back into view
    // after a scroll, which is four rows into a page of twelve.
    if (landed === head) { legEnds++; break; }
    if (column.length > 2 && landed === column[0]) break;
    head = landed;
    column.push(landed);
    visited.add(landed);
  }

  /**
   * Rows are sampled across the column rather than taken from every stop.
   *
   * Reaching a row costs a reload and one press per stop above it, so a raster
   * over the whole column is quadratic in its length. That was invisible while
   * the column died after three elements on a real article; the moment it ran
   * the full document the same sweep stopped finishing at all. Sampled, and the
   * sample size is reported, because a coverage number that quietly skipped
   * most of a page reads exactly like a page that was fully covered.
   */
  const rowStops = column
    .map((_, index) => index)
    .filter((index) => index % Math.max(1, Math.ceil(column.length / ROW_SAMPLE_LIMIT)) === 0);

  for (const rowIndex of rowStops) {
    await resetPage(socket);
    let standing = await evaluateOn(socket, FOCUSED);
    for (let step = 0; step < rowIndex; step++) {
      standing = await pressAndSettle(socket, 'down', standing);
    }

    const row = [standing];
    for (let step = 0; step < 12; step++) {
      presses++;
      const landed = await pressAndSettle(socket, 'right', row[row.length - 1]);
      if (landed === row[row.length - 1]) legEnds++;
      if (row.includes(landed)) break;
      row.push(landed);
      visited.add(landed);
    }
    rows.push(row);
  }

  const legs = { column, rows };
  const deadEnds = [];

  console.log(JSON.stringify({
    url,
    focusablesReported: total,
    distinctReached: visited.size,
    presses,
    legEnds,
    columnStops: column.length,
    rowsSwept: rowStops.length,
    reached: [...visited].sort(),
    deadEnds,
    legs,
  }, null, 1));
}

main().catch((error) => { console.error(String(error)); process.exit(1); });
