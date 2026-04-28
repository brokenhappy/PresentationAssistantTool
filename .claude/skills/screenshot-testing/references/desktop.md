# Desktop (JVM/macOS) Reference

## Build & Run

```bash
cd /Users/Wout.Werkman/IdeaProjects/PresentationAssistant

# Run the desktop app (foreground — blocks the terminal)
./gradlew desktopRun

# Run in background so you can interact while it's running
./gradlew desktopRun &
DESKTOP_PID=$!

# Wait for the window to appear
sleep 5
```

**Note:** `desktopRun` compiles and launches the Compose Desktop app. The first run may take a while to compile. The window title can be used to identify it for screenshots.

## Taking Screenshots

### Capture a specific window

```bash
mkdir -p /tmp/screenshots

# List all windows to find the app window ID
osascript -e '
tell application "System Events"
    tell process "java"
        set winList to every window
        set result to {}
        repeat with w in winList
            set end of result to {name of w, position of w, size of w}
        end repeat
        return result
    end tell
end tell
'

# Capture the entire screen (simplest)
screencapture /tmp/screenshots/desktop_screenshot.png

# Capture a specific window interactively (click to select)
screencapture -w /tmp/screenshots/desktop_window.png

# Capture a window by ID (non-interactive)
# First get the window ID:
WINDOW_ID=$(osascript -e '
tell application "System Events"
    tell process "java"
        return id of window 1
    end tell
end tell
')
screencapture -l $WINDOW_ID /tmp/screenshots/desktop_window.png
```

### Using screencapture with coordinates

```bash
# Capture a specific rectangle: -R x,y,width,height
screencapture -R 100,100,800,600 /tmp/screenshots/desktop_region.png
```

### Using the Compose Desktop process name

The desktop app runs as a Java process. The process name in System Events is typically `"java"`. If multiple Java processes are running, you may need to identify the correct one by window title.

```bash
# Find the window by title pattern
osascript -e '
tell application "System Events"
    set allProcesses to every process whose background only is false
    repeat with proc in allProcesses
        try
            set wins to every window of proc
            repeat with w in wins
                if name of w contains "Presentation" then
                    return {name of proc, name of w, id of w}
                end if
            end repeat
        end try
    end repeat
    return "Window not found"
end tell
'
```

## Interacting with the App

### Click

```bash
# Bring the app to front and click at coordinates
osascript -e '
tell application "System Events"
    -- Find the Presentation Assistant window
    set targetProc to first process whose name is "java"
    tell targetProc
        set frontmost to true
        set frontWindow to window 1
        set {winX, winY} to position of frontWindow
        set {winW, winH} to size of frontWindow
    end tell
    delay 0.3
    -- Click at position relative to window
    -- Example: click center of window
    click at {winX + (winW / 2), winY + (winH / 2)}
end tell
'
```

### Type Text

```bash
# Type text into the focused element
osascript -e '
tell application "System Events"
    set targetProc to first process whose name is "java"
    set frontmost of targetProc to true
    delay 0.3
    keystroke "hello world"
end tell
'
```

### Key Presses

```bash
# Press Enter
osascript -e '
tell application "System Events"
    key code 36
end tell
'

# Press Escape
osascript -e '
tell application "System Events"
    key code 53
end tell
'

# Press Tab
osascript -e '
tell application "System Events"
    key code 48
end tell
'

# Keyboard shortcut (e.g., Cmd+Q to quit)
osascript -e '
tell application "System Events"
    keystroke "q" using command down
end tell
'

# Arrow keys
osascript -e '
tell application "System Events"
    key code 126  -- Up
    key code 125  -- Down
    key code 123  -- Left
    key code 124  -- Right
end tell
'
```

### Window Management

```bash
# Resize the window
osascript -e '
tell application "System Events"
    tell process "java"
        set size of window 1 to {1024, 768}
    end tell
end tell
'

# Move the window
osascript -e '
tell application "System Events"
    tell process "java"
        set position of window 1 to {100, 100}
    end tell
end tell
'

# Get window info
osascript -e '
tell application "System Events"
    tell process "java"
        return {position of window 1, size of window 1, name of window 1}
    end tell
end tell
'
```

## Determining Click Coordinates

1. Take a screenshot first
2. View it with the Read tool
3. Get the window position and size via AppleScript
4. Map the visual element position from the screenshot to window-relative coordinates
5. Add the window position offset to get screen coordinates for `click at`

## Stop the App

```bash
# Gracefully via Gradle (if running in foreground, Ctrl+C)
# Or kill the process
pkill -f "com.woutwerkman.pa.MainKt"

# Or via AppleScript
osascript -e '
tell application "System Events"
    tell process "java"
        set frontmost to true
    end tell
    keystroke "q" using command down
end tell
'
```

## Troubleshooting

- **Window not found as "java":** The process might appear under a different name. Use the window-title search approach shown above.
- **AppleScript permission denied:** macOS requires accessibility permissions for System Events automation. Go to System Settings → Privacy & Security → Accessibility and add Terminal/iTerm.
- **Build fails:** Try `./gradlew clean desktopRun` for a fresh build.
- **Multiple Java windows:** Filter by window title containing "Presentation" to find the right one.
- **screencapture -l fails:** The window ID format may vary. Try `screencapture -w` for interactive selection, or use the full-screen capture and crop mentally.
