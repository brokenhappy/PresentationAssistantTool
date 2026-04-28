# iOS Simulator Reference

## Available Simulators

List available devices:
```bash
xcrun simctl list devices available | grep iPhone
```

**Recommended default:** Use the latest iPhone Pro (currently iPhone 17 Pro, UDID `099313C5-BA58-4776-879D-FEF77B30AEA0`). If that runtime isn't suitable, fall back to iPhone 16 Pro on iOS 18.4.

## Boot & Setup

```bash
# Boot a simulator (use UDID or device name)
xcrun simctl boot "iPhone 16 Pro"

# Wait for boot to complete
xcrun simctl bootstatus booted

# Open Simulator.app to see the device visually
open -a Simulator

# Clean status bar for screenshots
xcrun simctl status_bar booted override \
  --time "9:41" \
  --batteryLevel 100 \
  --batteryState charged \
  --cellularMode active \
  --cellularBars 4 \
  --wifiBars 3 \
  --operatorName ""
```

## Build & Install

The iOS app is built via Xcode. Build from command line:

```bash
# Build for simulator (Debug)
xcodebuild \
  -project /Users/Wout.Werkman/IdeaProjects/PresentationAssistant/iosApp/iosApp.xcodeproj \
  -scheme iosApp \
  -configuration Debug \
  -destination "platform=iOS Simulator,name=iPhone 16 Pro" \
  -derivedDataPath /tmp/iosAppBuild \
  build

# Find the built .app
APP_PATH=$(find /tmp/iosAppBuild -name "iosApp.app" -type d | grep "Debug-iphonesimulator" | head -1)

# Install on booted simulator
xcrun simctl install booted "$APP_PATH"

# Launch the app
xcrun simctl launch booted com.woutwerkman.pa
```

**Alternative one-liner (build + run):**
```bash
xcodebuild \
  -project /Users/Wout.Werkman/IdeaProjects/PresentationAssistant/iosApp/iosApp.xcodeproj \
  -scheme iosApp \
  -configuration Debug \
  -destination "platform=iOS Simulator,name=iPhone 16 Pro" \
  -derivedDataPath /tmp/iosAppBuild \
  build && \
xcrun simctl install booted "$(find /tmp/iosAppBuild -name 'iosApp.app' -path '*Debug-iphonesimulator*' -type d | head -1)" && \
xcrun simctl launch booted com.woutwerkman.pa
```

## Taking Screenshots

```bash
# Create screenshot directory
mkdir -p /tmp/screenshots

# Capture screenshot
xcrun simctl io booted screenshot /tmp/screenshots/ios_screenshot.png

# View it (use the Read tool on the resulting file)
```

After capturing, use the **Read** tool on the PNG file to view it.

## Interacting with the App

### Via simctl (limited but reliable)

```bash
# Terminate and relaunch
xcrun simctl terminate booted com.woutwerkman.pa
xcrun simctl launch booted com.woutwerkman.pa

# Open a URL in the app (if deep links are configured)
xcrun simctl openurl booted "presentationassistant://some-path"

# Send a push notification
echo '{"aps":{"alert":"Test"}}' | xcrun simctl push booted com.woutwerkman.pa -

# Change appearance
xcrun simctl ui booted appearance dark
xcrun simctl ui booted appearance light

# Paste text into simulator
echo "some text" | xcrun simctl pbcopy booted
```

### Via AppleScript (tap, swipe, type)

AppleScript can send mouse clicks and keystrokes to the Simulator.app window. Coordinates are relative to the Simulator window.

```bash
# Click at a specific position in the Simulator window
# First, bring Simulator to front, then click at coordinates (x, y) relative to the window
osascript -e '
tell application "Simulator" to activate
delay 0.5
tell application "System Events"
    tell process "Simulator"
        set frontWindow to window 1
        set {winX, winY} to position of frontWindow
        set {winW, winH} to size of frontWindow
    end tell
    -- Click at center of the simulator screen
    -- Adjust x,y offsets based on where you need to tap
    click at {winX + 200, winY + 400}
end tell
'

# Type text (the simulator must be focused and a text field must be active)
osascript -e '
tell application "Simulator" to activate
delay 0.3
tell application "System Events"
    keystroke "hello world"
end tell
'

# Press specific keys
osascript -e '
tell application "System Events"
    tell process "Simulator"
        -- Press Enter/Return
        key code 36
        -- Press Escape  
        key code 53
        -- Press Backspace
        key code 51
    end tell
end tell
'

# Swipe (drag from one point to another)
osascript -e '
tell application "Simulator" to activate
delay 0.3
tell application "System Events"
    tell process "Simulator"
        set frontWindow to window 1
        set {winX, winY} to position of frontWindow
    end tell
    -- Swipe up: drag from bottom to top
    set startX to winX + 200
    set startY to winY + 600
    set endX to winX + 200  
    set endY to winY + 200
    -- Use mouse down, move, mouse up for swipe
    do shell script "osascript -e '\''
        tell application \"System Events\"
            set mousePos to {" & startX & ", " & startY & "}
            -- Unfortunately System Events does not support drag natively
            -- Use the click approach with keyboard shortcuts instead
        end tell
    '\''"
end tell
'
```

### Determining Tap Coordinates

To find where to tap:
1. Take a screenshot first
2. View it with the Read tool to see the UI layout
3. Estimate coordinates based on the screenshot dimensions and element positions
4. The Simulator window has a title bar (~28px) — account for this offset
5. For Retina simulators, screen coordinates may differ from pixel coordinates. The AppleScript coordinates use screen points (not pixels).

**Practical approach:** Take a screenshot, identify the element visually, then estimate coordinates proportionally. iPhone simulators in Simulator.app typically render at a scale — check `Window > Physical Size` vs `Window > Point Accurate` in Simulator menus.

### Getting Simulator Window Info

```bash
# Get the window position and size
osascript -e '
tell application "System Events"
    tell process "Simulator"
        set frontWindow to window 1
        set winPos to position of frontWindow
        set winSize to size of frontWindow
        return {winPos, winSize}
    end tell
end tell
'
```

## Shutdown

```bash
xcrun simctl shutdown booted
# Or shutdown all
xcrun simctl shutdown all
```

## Troubleshooting

- **Build fails with framework errors:** Run `./gradlew shared:iosSimulatorArm64Binaries` and `./gradlew composeApp:iosSimulatorArm64Binaries` first to generate the KMP frameworks, then retry the Xcode build.
- **App crashes on launch:** Check `xcrun simctl spawn booted log stream --level error` for crash logs.
- **Simulator already booted:** `xcrun simctl list devices booted` to see which device is running.
- **Multiple simulators booted:** Use the specific UDID instead of `booted`.
