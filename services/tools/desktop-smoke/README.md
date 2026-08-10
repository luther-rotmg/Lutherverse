# desktop-smoke

Proves the `core` module actually boots and renders.

## Why this exists

`services/tools/smoke-boot/` targets Android and has **never executed the APK**. The
emulator never reaches `sys.boot_completed`, so the run dies before `adb install`.
Even when it did run it was a PID-alive check, which never verified gameplay.

This script runs the real desktop jar, waits for the game to render a configurable
number of frames, and asserts a clean exit. Reaching a frame proves assets loaded,
the GL context came up, and the initial scene constructed.

## Usage

```
pwsh services/tools/desktop-smoke/desktop-smoke.ps1 [-Frames 120] [-TimeoutSeconds 180]
```

Exit 0 on a confirmed boot, 1 otherwise.

First green run: 2026-08-10, `desktop-2.1.0-1.0.jar`, 120 frames.

## How it works

`DesktopLauncher.installSmokeWatchdog()` reads `-Dsmoke.frames=N`. When set, a daemon
thread polls `Gdx.graphics.getFrameId()` and halts the JVM with status 0 once it
reaches N, or status 1 if it never does. With the property unset the method returns
immediately and starts no thread, so normal launches are unaffected.

Two deliberate details:

- The watchdog starts **before** `new Lwjgl3Application(...)`, because that constructor
  blocks for the lifetime of the application and never returns. A watchdog installed
  after it would never run.
- It uses `Runtime.halt` rather than `System.exit`, because shutdown hooks can block on
  the GL thread and hang the smoke.

## Constraints

- **Requires a display.** Headless CI cannot run this as written.
- Removing the `-Dsmoke.frames` hook from `DesktopLauncher` silently turns this script
  into a PID-alive check, which is the exact failure this tool was built to end. The
  script guards against that by requiring the `SMOKE: reached frame` marker in stdout,
  not merely a zero exit code.
- Android remains a **manual** pre-release check. It is not an automated gate and must
  not be cited as one.
