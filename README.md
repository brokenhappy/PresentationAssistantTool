# Imprass

A presentation timing and remote control app. Run presentations from your desktop with an always-on-top timer bar, control slides from your phone over Bluetooth, and get vibration alerts when you're running over time.

Built with Kotlin Multiplatform and Compose Multiplatform. Runs on macOS, Android, and iOS.

## Features

### Desktop (macOS)

- **Always-on-top minified bar** — shows current bullet point, countdown timer, and schedule delta (how far ahead or behind you are)
- **Drag-and-drop** a `.json` presentation file onto the bar to load it
- **Expanded view** — full bullet point list, run history, and per-bullet statistics
- **Logitech Spotlight support** — use your physical presenter clicker (USB dongle or Bluetooth) to advance slides, with vibration feedback for timing alerts
- **Global keyboard shortcut** — Cmd+Alt+Shift+P to advance slides from any app
- **Attachments** — per-bullet URLs or file paths shown in a small floating window (QR code for URLs, clickable icon for files)
- **System tray** — quick access to show/hide the presentation, open expanded view, manage devices, or quit

### Mobile (Android & iOS)

The phone acts as a wireless remote control, paired to the desktop over Bluetooth Low Energy.

- **Swipe slider** — swipe right to advance, left to go back. Release before the edge to cancel
- **Speaker notes** — full-screen landscape view with large text for the current bullet, plus previous/next context. Keeps the screen awake
- **Countdown timer** — shows remaining time per bullet point (turns red when overtime)
- **Expanded view** — browse all bullet points, jump to any slide, view run statistics

### Timing & Statistics

- Tracks time spent on each bullet point across multiple runs
- Computes per-bullet averages and total presentation averages
- **Countdown timer** per bullet based on your historical average
- **Schedule delta** — how far ahead or behind you are overall
- **Vibration alerts** — two short buzzes 10 seconds before your average, one long buzz when time's up (works on both Spotlight and phone)
- Toggle individual runs in/out of statistics to exclude outliers

## Presentation File Format

Create a `.json` file:

```json
{
  "title": "My Talk",
  "bulletPoints": {
    "intro": "Introduction",
    "demo": "Live demo",
    "qa": "Q&A"
  },
  "attachments": {
    "demo": "https://example.com/demo-link"
  }
}
```

- `bulletPoints` — ordered map of unique keys to display text. Keys are internal identifiers; values are what you see on screen
- `attachments` — optional. Map bullet keys to a URL or absolute file path. A floating window appears during that bullet

## Pairing Your Phone

1. Open the desktop app
2. Click the tray icon → **Devices...**
3. Open the phone app — it shows a QR code
4. On desktop, click **Scan QR Code** and point your webcam at the phone, or manually enter the device ID
5. Once connected, the phone switches to the control screen automatically

Paired devices are remembered. On subsequent launches, the desktop reconnects automatically when the phone app is open.

## Building

**Requirements:** JDK 17+, macOS (for desktop and iOS builds), Android SDK 35 (for Android builds), Xcode (for iOS builds)

### Desktop

```bash
./gradlew :composeApp:run           # Run
./gradlew :composeApp:packageDmg    # Package as .dmg
```

### Android

```bash
./gradlew :composeApp:installDebug  # Install debug APK on connected device
```

### iOS

Open `iosApp/iosApp.xcodeproj` in Xcode and build to a device or simulator.

### Tests

```bash
./gradlew :shared:allTests          # Shared logic tests
./gradlew :composeApp:desktopTest   # Desktop UI tests
```
