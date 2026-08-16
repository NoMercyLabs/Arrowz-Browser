#!/usr/bin/env bash
# Copyright (c) 2026 NoMercy Labs
# SPDX-License-Identifier: MIT
#
# Build, install and launch NoMercy Browser on one or more Android TV devices.

set -euo pipefail

APPLICATION_ID="com.nomercylabs.browser"
ACTIVITY=".MainActivity"

BUILD_TYPE="debug"
CLEAN=0
LAUNCH=1
LOGS=0
SCREENSHOT=""
declare -a DEVICES=()
declare -a CONNECT=()

usage() {
    cat <<'EOF'
Usage: ./deploy.sh [options]

  -d, --device <serial>      Target this device. Repeatable. Defaults to every
                             connected device.
  -c, --connect <host:port>  adb connect before deploying. Repeatable.
  -r, --release              Build release instead of debug.
      --clean                Uninstall first, so the app starts with no stored
                             state. Use when tab, cookie or settings storage
                             changes shape.
      --no-launch            Install without starting the app.
      --logs                 Follow the app's logcat after launch.
      --screenshot <path>    Capture the screen after launch. With several
                             devices the serial is appended to the filename.
  -h, --help                 This.

Environment:
  NM_TV_DEVICES   Space separated host:port list to connect to automatically.

Examples:
  ./deploy.sh
  ./deploy.sh -c 192.168.2.80:5555 --clean --screenshot shot.png
  ./deploy.sh -d 192.168.2.80:5555 --logs
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        -d|--device)     DEVICES+=("$2"); shift 2 ;;
        -c|--connect)    CONNECT+=("$2"); shift 2 ;;
        -r|--release)    BUILD_TYPE="release"; shift ;;
        --clean)         CLEAN=1; shift ;;
        --no-launch)     LAUNCH=0; shift ;;
        --logs)          LOGS=1; shift ;;
        --screenshot)    SCREENSHOT="$2"; shift 2 ;;
        -h|--help)       usage; exit 0 ;;
        *) echo "Unknown option: $1" >&2; usage; exit 2 ;;
    esac
done

cd "$(dirname "${BASH_SOURCE[0]}")"

if [[ -n "${NM_TV_DEVICES:-}" ]]; then
    for entry in $NM_TV_DEVICES; do CONNECT+=("$entry"); done
fi

for target in "${CONNECT[@]:-}"; do
    [[ -z "$target" ]] && continue
    echo "connect  $target"
    adb connect "$target" >/dev/null 2>&1 || true
done

if [[ ${#DEVICES[@]} -eq 0 ]]; then
    while read -r serial _; do
        [[ -n "$serial" ]] && DEVICES+=("$serial")
    done < <(adb devices | awk 'NR>1 && $2=="device" {print $1}')
fi

if [[ ${#DEVICES[@]} -eq 0 ]]; then
    echo "No devices. Connect one, or pass --connect host:port." >&2
    exit 1
fi

# The debug build carries an applicationIdSuffix, so installing a debug build
# and then launching the release package silently starts the wrong app, or
# nothing at all.
if [[ "$BUILD_TYPE" == "release" ]]; then
    PACKAGE="$APPLICATION_ID"
    GRADLE_TASK="assembleRelease"
    APK="app/build/outputs/apk/release/app-release.apk"
else
    PACKAGE="${APPLICATION_ID}.debug"
    GRADLE_TASK="assembleDebug"
    APK="app/build/outputs/apk/debug/app-debug.apk"
fi

echo "build    $GRADLE_TASK"
./gradlew "$GRADLE_TASK" --console=plain -q

if [[ ! -f "$APK" ]]; then
    echo "No APK at $APK. The release build is unsigned unless NM_KEYSTORE_PATH is set." >&2
    exit 1
fi

deploy_one() {
    local serial="$1"
    local label="[$serial]"

    if [[ $CLEAN -eq 1 ]]; then
        adb -s "$serial" uninstall "$PACKAGE" >/dev/null 2>&1 || true
        echo "$label uninstalled"
    fi

    if ! adb -s "$serial" install -r -d "$APK" >/dev/null 2>&1; then
        # A signature change is the usual reason a reinstall fails, and it is
        # not recoverable without removing the old app.
        echo "$label install failed, retrying after uninstall"
        adb -s "$serial" uninstall "$PACKAGE" >/dev/null 2>&1 || true
        adb -s "$serial" install "$APK" >/dev/null
    fi
    echo "$label installed"

    if [[ $LAUNCH -eq 1 ]]; then
        # Cleared so the Displayed line we wait for is this launch's, not a
        # previous one still sitting in the buffer.
        adb -s "$serial" logcat -c >/dev/null 2>&1 || true
        adb -s "$serial" shell am start -n "${PACKAGE}/${APPLICATION_ID}${ACTIVITY}" >/dev/null 2>&1
        echo "$label launched"
    fi
}

for serial in "${DEVICES[@]}"; do
    deploy_one "$serial" &
done
wait

# Waits until the first frame is actually on screen.
#
# topResumedActivity is set before anything is drawn, so waiting on it captures
# whatever was previously on screen and looks exactly like a failed launch.
# ActivityTaskManager logs "Displayed <component>" when the first frame lands,
# which is the only signal that means what we need it to mean.
wait_for_displayed() {
    local serial="$1"
    for _ in $(seq 1 60); do
        if adb -s "$serial" logcat -d -s ActivityTaskManager:I 2>/dev/null \
            | tr -d '\r' | grep -q "Displayed ${PACKAGE}/"; then
            return 0
        fi
        sleep 0.5
    done
    echo "[$serial] warning: no Displayed line, falling back" >&2
    sleep 2
    return 1
}

if [[ -n "$SCREENSHOT" && $LAUNCH -eq 1 ]]; then
    for serial in "${DEVICES[@]}"; do
        wait_for_displayed "$serial" || true
        out="$SCREENSHOT"
        if [[ ${#DEVICES[@]} -gt 1 ]]; then
            out="${SCREENSHOT%.*}-${serial//[:.]/_}.${SCREENSHOT##*.}"
        fi
        adb -s "$serial" exec-out screencap -p > "$out"
        echo "[$serial] screenshot $out"
    done
fi

if [[ $LOGS -eq 1 ]]; then
    serial="${DEVICES[0]}"
    echo "logs     $serial (ctrl-c to stop)"
    pid="$(adb -s "$serial" shell pidof "$PACKAGE" | tr -d '\r')"
    if [[ -n "$pid" ]]; then
        adb -s "$serial" logcat --pid="$pid"
    else
        adb -s "$serial" logcat
    fi
fi
