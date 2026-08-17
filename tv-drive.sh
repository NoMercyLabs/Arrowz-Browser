#!/usr/bin/env bash
# Copyright (c) 2026 NoMercy Labs
# SPDX-License-Identifier: MIT
#
# Send remote keys to Arrowz Browser on a device, and to nothing else.
#
# Every key is checked against the foreground activity first. A television is
# somebody's television: keys injected while another app is in front land in
# that app, and "DOWN, OK" in a music player is a real action taken on somebody
# else's behalf. This has happened, which is why the check is here rather than
# in a habit.

set -euo pipefail

APPLICATION_ID="com.nomercylabs.arrowz.debug"
DEVICE=""
SETTLE="1.2"
SHOT=""

usage() {
    cat <<'EOF'
Usage: ./tv-drive.sh -d <serial> [options] <key>...

  -d, --device <serial>   Target device. Required.
  -p, --package <id>      Foreground package to require.
                          Default com.nomercylabs.arrowz.debug
  -w, --wait <seconds>    Settle time between keys. Default 1.2
  -s, --shot <path>       Screenshot to this path when the last key is done.
      --status            Print the foreground activity and exit.

Keys are names or raw keycodes: up down left right ok back, or 19 20 21 22 23 4.
LONG prefixes a key with a long press, e.g. LONGback.

Refuses to send anything unless the required package is in the foreground, so a
run that starts while another app is in front stops instead of driving it.
EOF
}

foreground() {
    adb -s "$DEVICE" shell "dumpsys activity activities | grep -m1 topResumedActivity" 2>/dev/null |
        sed -n 's/.* \([A-Za-z0-9_.]*\)\/.*/\1/p' | tr -d '\r'
}

# OK is the one key that does something irreversible, and the nav bar's
# microphone is the one target that does something loud: it opens the system
# recogniser, which takes the screen and listens to the room. A directional key
# that gets dropped — they do, intermittently — leaves focus one control away
# from it, so "down, right, ok" has fired the microphone more than once.
#
# Knowing the foreground app is not enough to prevent that. This checks where
# the focus ring actually is before OK is allowed through.
MIC_REGION="1395,60,1485,145"

focus_is_on_microphone() {
    local shot
    shot="$(mktemp -t nmfocus.XXXXXX).png"
    adb -s "$DEVICE" exec-out screencap -p > "$shot" 2>/dev/null || { rm -f "$shot"; return 1; }

    python - "$shot" "$MIC_REGION" <<'PY'
import sys
try:
    from PIL import Image
except ImportError:
    sys.exit(1)          # cannot tell; do not block

image = Image.open(sys.argv[1]).convert('RGB')
x0, y0, x1, y1 = (int(v) for v in sys.argv[2].split(','))
accent = (140, 163, 184)                    # Tokens focus ring, dark palette

hits = 0
for x in range(x0, min(x1, image.width), 2):
    for y in range(y0, min(y1, image.height), 2):
        pixel = image.getpixel((x, y))
        if sum(abs(a - b) for a, b in zip(pixel, accent)) < 60:
            hits += 1

# An unfocused button sits near the surface colour and scores a handful of
# pixels from antialiasing; a focused one is ringed and scores far more.
sys.exit(0 if hits > 40 else 2)
PY
    local verdict=$?
    rm -f "$shot"
    return $((verdict == 0 ? 0 : 1))
}

keycode_for() {
    case "${1,,}" in
        up) echo 19 ;;
        down) echo 20 ;;
        left) echo 21 ;;
        right) echo 22 ;;
        ok | center | enter) echo 23 ;;
        back) echo 4 ;;
        *[!0-9]*) echo "unknown key: $1" >&2; exit 2 ;;
        *) echo "$1" ;;
    esac
}

declare -a KEYS=()
STATUS_ONLY=0

while [[ $# -gt 0 ]]; do
    case "$1" in
        -d | --device) DEVICE="$2"; shift 2 ;;
        -p | --package) APPLICATION_ID="$2"; shift 2 ;;
        -w | --wait) SETTLE="$2"; shift 2 ;;
        -s | --shot) SHOT="$2"; shift 2 ;;
        --status) STATUS_ONLY=1; shift ;;
        -h | --help) usage; exit 0 ;;
        *) KEYS+=("$1"); shift ;;
    esac
done

if [[ -z "$DEVICE" ]]; then
    echo "a device is required, so a key can never go to whichever box answers first" >&2
    usage
    exit 2
fi

if [[ "$STATUS_ONLY" == 1 ]]; then
    echo "foreground: $(foreground)"
    exit 0
fi

if [[ ${#KEYS[@]} -eq 0 ]]; then
    usage
    exit 2
fi

for key in "${KEYS[@]}"; do
    current="$(foreground)"
    if [[ "$current" != "$APPLICATION_ID" ]]; then
        echo "refusing to send '$key': $APPLICATION_ID is not in front, $current is." >&2
        echo "the television belongs to somebody who may be using it." >&2
        exit 1
    fi

    if [[ "${key,,}" == "ok" || "${key,,}" == "center" || "$key" == "23" ]]; then
        if focus_is_on_microphone; then
            echo "refusing to send '$key': the focus ring is on the microphone button." >&2
            echo "OK there opens the system recogniser and listens to the room." >&2
            exit 1
        fi
    fi

    if [[ "$key" == LONG* ]]; then
        code="$(keycode_for "${key#LONG}")"
        adb -s "$DEVICE" shell input keyevent --longpress "$code"
    else
        code="$(keycode_for "$key")"
        adb -s "$DEVICE" shell input keyevent "$code"
    fi
    echo "sent $key ($code)"
    sleep "$SETTLE"
done

if [[ -n "$SHOT" ]]; then
    adb -s "$DEVICE" exec-out screencap -p > "$SHOT"
    echo "screenshot $SHOT"
fi
