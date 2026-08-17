/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 *
 * Drives the RELEASE build, which has no devtools socket because it is not
 * debuggable. So focus is read the only way left from outside: the screen. Each
 * press is followed by a screenshot, and the frames are compared to each other
 * so a press that changed nothing is visible as a frame identical to the one
 * before it.
 *
 *   node release-walk.mjs <serial> <presses> <outdir> [url]
 */
import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { writeFileSync, mkdirSync } from 'node:fs';

const serial = process.argv[2];
const presses = Number(process.argv[3] ?? 8);
const outDir = process.argv[4];
// Overridable so the same walk can be run against the debug build. A run that
// reports nothing moving is either a broken app or a broken harness, and
// comparing the two variants is the only thing that tells them apart.
const PACKAGE = process.env.NM_WALK_PACKAGE ?? 'com.nomercylabs.arrowz';

mkdirSync(outDir, { recursive: true });

function adb(args, binary = false) {
  return execFileSync('adb', ['-s', serial, ...args], {
    encoding: binary ? 'buffer' : 'utf8',
    maxBuffer: 64 * 1024 * 1024,
    stdio: ['ignore', 'pipe', 'ignore'],
  });
}
function sleep(ms) { Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, ms); }

function requireForeground() {
  const line = adb(['shell', 'dumpsys', 'activity', 'activities'])
    .split('\n').find((entry) => entry.includes('topResumedActivity')) ?? '';
  if (!line.includes(PACKAGE)) throw new Error(`not in front: ${line.trim()}`);
}

function frame(name) {
  const png = adb(['exec-out', 'screencap', '-p'], true);
  writeFileSync(`${outDir}/${name}.png`, png);
  return { bytes: png.length, hash: createHash('sha1').update(png).digest('hex').slice(0, 12) };
}

// Opened here when a page is named, so the whole check is one command rather
// than a command with a remembered setup step in front of it.
const url = process.argv[5];
if (url) {
  adb(['shell', 'am', 'force-stop', PACKAGE]);
  sleep(1500);
  adb(['shell', 'am', 'start', '-n', `${PACKAGE}/com.nomercylabs.arrowz.MainActivity`,
       '-a', 'android.intent.action.VIEW', '-d', url]);
  sleep(10000);
}

sleep(2000);
requireForeground();
// Focus mode, since the probe may have chosen the pointer.
adb(['shell', 'input', 'keyevent', '--longpress', '23']);
sleep(2500);

let previous = frame('step-00');
console.log('step 00', JSON.stringify(previous));
let identical = 0;
for (let step = 1; step <= presses; step++) {
  requireForeground();
  adb(['shell', 'input', 'keyevent', '20']);
  sleep(1500);
  const now = frame(`step-${String(step).padStart(2, '0')}`);
  const changed = now.hash !== previous.hash;
  if (!changed) identical++;
  console.log(`step ${String(step).padStart(2, '0')}`, JSON.stringify({ ...now, changed }));
  previous = now;
}
console.log(JSON.stringify({ presses, framesIdenticalToPrevious: identical }));
