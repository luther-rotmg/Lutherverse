#!/usr/bin/env bash
# Headless Android smoke-boot: boots a debug APK in an emulator and confirms
# the app process is alive. Not a full playthrough check -- see README.md.
#
# Exit 0 -> APK installed, app process alive 10s after launch (PASS).
# Exit 1 -> any failure (emulator didn't boot, install failed, process died).
#
# Usage: smoke-boot.sh [apk] [avd] [timeout_seconds]
#   PACKAGE / ACTIVITY env vars override auto-detection (see below).
#
# Package/Activity are auto-detected from the APK via aapt2 (build-tools)
# unless PACKAGE/ACTIVITY are set in the environment. Auto-detection exists
# because debug builds apply an applicationIdSuffix (this project uses
# ".indev"), so the installed package id and launchable activity are not
# fixed strings -- they must be read out of the APK being tested.

set -u

APK="${1:-android/build/outputs/apk/debug/android-debug.apk}"
AVD="${2:-cpdu-smoke}"
TIMEOUT_SECONDS="${3:-120}"
PACKAGE="${PACKAGE:-}"
ACTIVITY="${ACTIVITY:-}"

if [ -z "${ANDROID_HOME:-}" ]; then
    echo "FAIL: ANDROID_HOME is not set"
    exit 1
fi

EMULATOR="$ANDROID_HOME/emulator/emulator"
ADB="$ANDROID_HOME/platform-tools/adb"

if [ ! -x "$EMULATOR" ]; then
    echo "FAIL: emulator not found/executable at $EMULATOR (install the 'emulator' SDK package)"
    exit 1
fi
if [ ! -x "$ADB" ]; then
    echo "FAIL: adb not found/executable at $ADB (install the 'platform-tools' SDK package)"
    exit 1
fi
if [ ! -f "$APK" ]; then
    echo "FAIL: APK not found at $APK"
    exit 1
fi

# Auto-detect package id / launchable activity from the APK via aapt2, unless
# the caller set PACKAGE/ACTIVITY explicitly. Debug builds may carry an
# applicationIdSuffix, so a hardcoded component string would silently break
# the moment the suffix (or the launcher activity) changes.
if [ -z "$PACKAGE" ] || [ -z "$ACTIVITY" ]; then
    AAPT2=$(find "$ANDROID_HOME/build-tools" -type f \( -name "aapt2" -o -name "aapt2.exe" \) 2>/dev/null | sort -r | head -n1)
    if [ -n "$AAPT2" ]; then
        BADGING=$("$AAPT2" dump badging "$APK" 2>/dev/null)
        if [ -z "$PACKAGE" ]; then
            PACKAGE=$(printf '%s\n' "$BADGING" | grep "^package: name=" | sed -E "s/^package: name='([^']+)'.*/\1/")
        fi
        if [ -z "$ACTIVITY" ]; then
            ACTIVITY=$(printf '%s\n' "$BADGING" | grep "^launchable-activity: name=" | sed -E "s/^launchable-activity: name='([^']+)'.*/\1/")
        fi
    fi
fi
if [ -z "$PACKAGE" ] || [ -z "$ACTIVITY" ]; then
    echo "FAIL: could not determine package/activity from $APK (set PACKAGE/ACTIVITY env vars explicitly, or install the 'build-tools' aapt2 binary)"
    exit 1
fi
COMPONENT="$PACKAGE/$ACTIVITY"

echo "starting emulator ($AVD)..."
"$EMULATOR" -avd "$AVD" -no-window -no-audio -no-snapshot &
EMU_PID=$!

DEADLINE=$(( $(date +%s) + TIMEOUT_SECONDS ))
BOOTED=0
while [ "$(date +%s)" -lt "$DEADLINE" ]; do
    STATE=$("$ADB" shell getprop sys.boot_completed 2>/dev/null)
    if [ "$STATE" = "1" ]; then
        BOOTED=1
        break
    fi
    sleep 3
done

if [ "$BOOTED" -ne 1 ]; then
    "$ADB" emu kill >/dev/null 2>&1
    echo "FAIL: emulator did not boot within ${TIMEOUT_SECONDS} seconds"
    exit 1
fi

echo "installing APK ($APK)..."
"$ADB" install -r "$APK"
INSTALL_EXIT=$?
if [ "$INSTALL_EXIT" -ne 0 ]; then
    "$ADB" emu kill >/dev/null 2>&1
    echo "FAIL: install exit $INSTALL_EXIT"
    exit 1
fi

echo "launching app ($COMPONENT)..."
"$ADB" shell am start -n "$COMPONENT"
sleep 10

echo "checking process alive..."
RUNNING=$("$ADB" shell pidof "$PACKAGE" 2>/dev/null | tr -d '[:space:]')
if [ -z "$RUNNING" ]; then
    "$ADB" emu kill >/dev/null 2>&1
    echo "FAIL: app process not alive 10s after launch"
    exit 1
fi

echo "PASS: app booted and running (pid $RUNNING)"
"$ADB" emu kill >/dev/null 2>&1
exit 0
