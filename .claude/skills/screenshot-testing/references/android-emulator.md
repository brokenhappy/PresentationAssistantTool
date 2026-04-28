# Android Emulator Reference

## Environment

- **Android SDK:** `/Users/Wout.Werkman/Library/Android/sdk`
- **Emulator:** `/Users/Wout.Werkman/Library/Android/sdk/emulator/emulator`
- **ADB:** `/Users/Wout.Werkman/Library/Android/sdk/platform-tools/adb` (also on PATH)
- **Available AVDs:** `Pixel_9_Pro_API_34`, `Pixel_9_Pro_Fold`

## Start Emulator

```bash
# List available AVDs
/Users/Wout.Werkman/Library/Android/sdk/emulator/emulator -list-avds

# Start emulator in background (headless or with GUI)
/Users/Wout.Werkman/Library/Android/sdk/emulator/emulator -avd Pixel_9_Pro_API_34 &

# Wait for device to be ready
adb wait-for-device
adb shell getprop sys.boot_completed  # returns "1" when ready

# Full wait loop:
adb wait-for-device && until [ "$(adb shell getprop sys.boot_completed 2>/dev/null)" = "1" ]; do sleep 1; done
```

## Build & Install

```bash
# Build the debug APK
cd /Users/Wout.Werkman/IdeaProjects/PresentationAssistant
./gradlew composeApp:assembleDebug

# The APK is at:
# composeApp/build/outputs/apk/debug/composeApp-debug.apk

# Install on emulator
adb install -r composeApp/build/outputs/apk/debug/composeApp-debug.apk

# Launch the app
adb shell am start -n com.woutwerkman.pa/.MainActivity

# Or combine: build + install + launch
./gradlew composeApp:assembleDebug && \
adb install -r composeApp/build/outputs/apk/debug/composeApp-debug.apk && \
adb shell am start -n com.woutwerkman.pa/.MainActivity
```

## Taking Screenshots

```bash
# Create screenshot directory
mkdir -p /tmp/screenshots

# Capture screenshot (pipe directly to file)
adb exec-out screencap -p > /tmp/screenshots/android_screenshot.png

# Alternative: capture on device then pull
adb shell screencap /sdcard/screenshot.png
adb pull /sdcard/screenshot.png /tmp/screenshots/android_screenshot.png
adb shell rm /sdcard/screenshot.png
```

After capturing, use the **Read** tool on the PNG file to view it.

## Interacting with the App

### Tap

```bash
# Tap at coordinates (x, y) — these are screen pixels
adb shell input tap 540 960

# Long press
adb shell input swipe 540 960 540 960 1000  # hold for 1000ms
```

### Swipe / Scroll

```bash
# Swipe up (scroll down): from (x1,y1) to (x2,y2) over duration_ms
adb shell input swipe 540 1500 540 500 300

# Swipe down (scroll up)
adb shell input swipe 540 500 540 1500 300

# Swipe left
adb shell input swipe 900 960 100 960 300

# Swipe right
adb shell input swipe 100 960 900 960 300
```

### Text Input

```bash
# Type text (the text field must be focused)
adb shell input text "hello%sworld"  # %s = space

# For special characters, use key events instead
```

### Key Events

```bash
# Common key events
adb shell input keyevent KEYCODE_BACK          # Back button
adb shell input keyevent KEYCODE_HOME          # Home button
adb shell input keyevent KEYCODE_ENTER         # Enter/Return
adb shell input keyevent KEYCODE_DEL           # Backspace
adb shell input keyevent KEYCODE_TAB           # Tab
adb shell input keyevent KEYCODE_ESCAPE        # Escape
adb shell input keyevent KEYCODE_VOLUME_UP     # Volume up
adb shell input keyevent KEYCODE_VOLUME_DOWN   # Volume down
adb shell input keyevent KEYCODE_DPAD_UP       # D-pad up
adb shell input keyevent KEYCODE_DPAD_DOWN     # D-pad down
```

### UI Inspection (find tap targets)

```bash
# Dump the current UI hierarchy (XML)
adb exec-out uiautomator dump /dev/tty

# Get screen resolution
adb shell wm size

# Get display density
adb shell wm density
```

**To find where to tap:** Use `uiautomator dump` to get the view hierarchy with bounds, or take a screenshot first and estimate coordinates visually.

### App Management

```bash
# Force stop the app
adb shell am force-stop com.woutwerkman.pa

# Clear app data
adb shell pm clear com.woutwerkman.pa

# Uninstall
adb uninstall com.woutwerkman.pa

# List installed packages (verify installation)
adb shell pm list packages | grep woutwerkman
```

### Permissions

```bash
# Grant Bluetooth permissions (needed for this app)
adb shell pm grant com.woutwerkman.pa android.permission.BLUETOOTH_SCAN
adb shell pm grant com.woutwerkman.pa android.permission.BLUETOOTH_CONNECT
adb shell pm grant com.woutwerkman.pa android.permission.BLUETOOTH_ADVERTISE
adb shell pm grant com.woutwerkman.pa android.permission.ACCESS_FINE_LOCATION
```

### Dark Mode

```bash
# Enable dark mode
adb shell cmd uimode night yes

# Disable dark mode
adb shell cmd uimode night no
```

## Determining Tap Coordinates

1. Get screen resolution: `adb shell wm size` (e.g., 1080x2340)
2. Take a screenshot and view it with the Read tool
3. The screenshot pixel coordinates map directly to `adb shell input tap` coordinates
4. For the UI hierarchy approach: `adb exec-out uiautomator dump /dev/tty` gives bounds like `[left,top][right,bottom]` — tap at the center: `((left+right)/2, (top+bottom)/2)`

## Shutdown

```bash
# Graceful shutdown
adb emu kill

# Or kill the emulator process
pkill -f "qemu-system"
```

## Troubleshooting

- **"No devices found":** Make sure emulator is booted: `adb devices` should show the device.
- **APK install fails:** Try `adb install -r -t` (allow test APKs, replace existing).
- **App crashes:** Check logcat: `adb logcat -s "AndroidRuntime:E" | head -50`
- **Emulator slow to boot:** Use `-no-snapshot-load` flag, or wait longer with the boot loop.
- **Permission dialogs:** Pre-grant permissions with `adb shell pm grant` commands above.
