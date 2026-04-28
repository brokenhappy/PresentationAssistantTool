---
name: screenshot-testing
description: Run the Presentation Assistant app on iOS Simulator, Android Emulator, or Desktop, interact with the UI, and take screenshots. Use when the user wants to visually test the app, capture screenshots, or verify UI behavior on any platform.
license: MIT
metadata:
  author: wout.werkman@jetbrains.com
  version: "1.0.0"
  domain: testing
  triggers: screenshot, run app, visual test, iOS simulator, Android emulator, desktop run, UI test, take screenshot, verify UI, test on device
  role: tester
  scope: interaction
  output-format: mixed
---

# Screenshot Testing Skill

Run the Presentation Assistant app on any platform, interact with its UI, and capture screenshots for visual verification.

## Quick Reference

| Platform | Build & Run | Screenshot | Interact |
|----------|------------|------------|----------|
| iOS Simulator | `references/ios-simulator.md` | `xcrun simctl io booted screenshot` | AppleScript to Simulator.app |
| Android Emulator | `references/android-emulator.md` | `adb exec-out screencap -p` | `adb shell input` |
| Desktop | `references/desktop.md` | `screencapture` | AppleScript |

## Core Workflow

1. **Choose platform** — Determine which platform(s) to test based on user request
2. **Start device** — Boot simulator/emulator if not running (load the platform reference)
3. **Build & deploy** — Build and install the app on the target device
4. **Wait for launch** — Give the app time to start (3-5 seconds after launch command)
5. **Take screenshot** — Capture the current state and view it with the Read tool
6. **Interact** — Perform taps, swipes, or text input as needed (load the platform reference)
7. **Screenshot again** — Capture after interaction to verify the result

## Important Rules

- **Always save screenshots to `/tmp/screenshots/`** — create the directory first
- **Always use the Read tool to view screenshots** — it supports PNG/JPEG images
- **Wait after interactions** — sleep 1-2 seconds after taps/launches before screenshotting
- **Clean status bar on iOS** — override it for clean screenshots: `xcrun simctl status_bar booted override --time "9:41" --batteryLevel 100 --batteryState charged`
- **Project root** is `/Users/Wout.Werkman/IdeaProjects/PresentationAssistant`
- **iOS bundle ID** is `com.woutwerkman.pa`
- **Android package** is `com.woutwerkman.pa`
- **Desktop main class** is `com.woutwerkman.pa.MainKt`

## Platform Selection Guide

If the user doesn't specify a platform:
- For general UI testing → use Desktop (fastest build)
- For mobile-specific testing → use iOS Simulator (best tooling)
- For Android-specific → use Android Emulator
- For cross-platform comparison → run on all three

## Screenshot Naming Convention

Use descriptive names: `/tmp/screenshots/{platform}_{screen}_{state}.png`

Examples:
- `/tmp/screenshots/ios_home_initial.png`
- `/tmp/screenshots/android_settings_dark_mode.png`
- `/tmp/screenshots/desktop_presentation_active.png`
