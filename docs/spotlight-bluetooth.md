# Logitech Spotlight — Bluetooth Integration

The Logitech Spotlight presentation remote can connect to macOS in two ways:

1. **USB receiver (dongle)** — full HID++ 2.0 support via hid4java
2. **Bluetooth Low Energy (direct pairing)** — requires a native CoreBluetooth bridge

This document covers how the BLE path works, its limitations, and how it fits into the codebase.

## How It Works

When connected via BLE, macOS exposes the Spotlight as a standard HID keyboard. Button presses generate arrow key / page up/down events, which `NativeHidMonitor` captures through an `IOHIDManager` input callback and a `CGEventTap`.

**Vibration** is more complex. macOS's HID driver silently drops output reports for BLE HID devices — `IOHIDDeviceSetReport` returns success but the data never reaches the device. To work around this, we communicate over a **vendor-specific GATT characteristic** that Logitech exposes alongside the standard HID service, using HID++ 2.0 commands directly.

### Architecture

```
┌─────────────────────┐
│   SpotlightManager  │  Kotlin (JVM)
│                     │
│  ┌───────────────┐  │  Button events (BLE)
│  │NativeHidMonitor│──┼──→ IOKit IOHIDManager + CGEventTap (via JNA)
│  └───────────────┘  │
│                     │
│  ┌───────────────┐  │  Vibration (BLE)
│  │SpotlightBleLib│──┼──→ libspotlightble.dylib (Swift, CoreBluetooth)
│  └───────────────┘  │
└─────────────────────┘
```

The Swift dylib (`native/macos/SpotlightBle.swift`) handles the CoreBluetooth lifecycle:

1. Retrieves the already-connected Spotlight peripheral
2. Discovers Logitech's vendor GATT service
3. Subscribes to notifications and resolves the PresenterControl feature index via HID++ IRoot
4. Exposes C functions (`spotlight_ble_vibrate`, etc.) loaded by JNA at runtime

See [Spotlight BLE Protocol](spotlight-ble-protocol.md) for the full protocol details.

## Build

The dylib is compiled automatically by the Gradle `compileSpotlightBle` task during the desktop build. It requires `swiftc` (ships with Xcode Command Line Tools). On non-macOS platforms, the task is skipped and BLE vibration is silently unavailable.

No manual steps are needed — `./gradlew :composeApp:run` handles everything.

## Why Swift Instead of Kable?

The project already uses [Kable](https://github.com/JuulLabs/kable) (Kotlin Multiplatform BLE library) for phone connections, so it's natural to ask why the Spotlight vibration path needs a separate Swift dylib.

The problem is device discovery. The Spotlight stops advertising once it's paired and connected to macOS. Kable's `Scanner` only finds devices that are actively advertising — both filtered and unfiltered scans return zero results for an already-connected Spotlight.

The only way to reach it is CoreBluetooth's `retrieveConnectedPeripherals(withServices:)`, which returns peripherals the system already has a connection to. Kable does not expose this API ([discussion #469](https://github.com/JuulLabs/kable/discussions/469)), and there is no timeline for adding it.

Alternatives considered:

| Approach | Why not |
|----------|---------|
| Kable Scanner | Device doesn't advertise once connected — scanner finds nothing |
| JNA calls to Objective-C runtime | Possible but fragile — requires manual `objc_msgSend` calls, selector lookups, and block-based callback bridging for an async CoreBluetooth flow |
| Wait for Kable support | No timeline; the `retrieveConnectedPeripherals` request has been open since 2023 |

The Swift dylib is ~150 lines, compiles automatically during the Gradle build, and returns `null` on non-macOS. It's the simplest working solution.

## Limitations

- **macOS only.** CoreBluetooth is an Apple framework. On Windows/Linux, BLE vibration is not available (button input still works via other paths).
- **Vibration only.** The GATT channel is used exclusively for vibration. Button input comes through the standard HID path (`NativeHidMonitor`), not through this channel.
- **Logitech Spotlight specific.** The GATT service UUID and HID++ feature set are specific to the Spotlight (and possibly other Logitech HID++ 2.0 BLE devices). Other presenters are not supported.
- **Startup delay.** The CoreBluetooth connection and HID++ feature discovery take ~1 second after the HID device is detected. The first button press may not vibrate if it happens within that window.
- **No reconnection handling.** If the Spotlight disconnects and reconnects via BLE, the GATT channel is re-initialized when `NativeHidMonitor` fires the `onConnected` callback. However, CoreBluetooth may take a moment to re-establish the GATT connection.

## Files

| File | Role |
|------|------|
| `native/macos/SpotlightBle.swift` | Swift dylib source — CoreBluetooth GATT communication |
| `shared/build.gradle.kts` | `compileSpotlightBle` task — compiles the dylib |
| `shared/.../SpotlightManager.kt` | Orchestrates USB and BLE paths, loads the dylib via JNA |
| `shared/.../NativeHidMonitor.kt` | IOKit HID monitoring for button input and device connect/disconnect |
