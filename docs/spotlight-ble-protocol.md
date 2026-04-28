# Spotlight BLE Protocol — Technical Specification

This document describes how the Logitech Spotlight communicates over Bluetooth Low Energy on macOS, including the GATT service layout, the HID++ 2.0 message format over BLE, and why the standard HID output path doesn't work.

## Why Not IOHIDManager?

macOS represents BLE HID devices as `IOHIDDevice` instances. While `IOHIDDeviceSetReport` with `kIOHIDReportTypeOutput` returns `kIOReturnSuccess` (0), the data is **never transmitted** over the BLE interrupt channel. This is a macOS limitation in the BLE HOGP (HID over GATT Profile) driver — it accepts the call but silently drops the output report.

Other approaches that were ruled out:

| Approach | Result |
|----------|--------|
| `IOHIDDeviceSetReport` (output type) | Returns success, data not transmitted |
| `IOHIDDeviceSetReport` (feature type) | `kIOReturnNotFound` (0xe00002f0) |
| hid4java exclusive open (`kIOHIDOptionsTypeSeizeDevice`) | `kIOReturnNotPrivileged` (0xe00002c1) — system keyboard driver holds the device |
| `IOHIDDeviceGetReport` | `kIOReturnNoSpace` (0xe00002db) — device doesn't support GET_REPORT |

## GATT Service Layout

The Spotlight exposes the following GATT services when connected via BLE:

| Service UUID | Name |
|-------------|------|
| `180A` | Device Information |
| `180F` | Battery |
| `00010000-0000-1000-8000-011F2000046D` | Logitech HID++ |

The last one is a vendor-specific service. The base UUID contains `046D` (Logitech's USB vendor ID).

### HID++ Characteristic

| Property | Value |
|----------|-------|
| UUID | `00010001-0000-1000-8000-011F2000046D` |
| Properties | Read, Write, Write Without Response, Notify |
| Max write length | 18 bytes (with response), 20 bytes (without response) |
| Notification size | 18 bytes |

## Message Format

Over this GATT characteristic, HID++ messages use a **stripped format** — no report ID and no device index byte. The BLE connection implicitly addresses the device.

```
Byte:  [0]            [1]              [2..N]
       feature_index  func_id | sw_id  parameters (zero-padded)
```

- **feature_index** (1 byte): The feature slot resolved via IRoot.
- **func_id | sw_id** (1 byte): High nibble = function number, low nibble = software ID (use 0x7).
- **parameters** (up to 6 bytes for 8-byte messages): Function-specific payload.

Messages are sent as **8 bytes total**, zero-padded. This differs from USB HID++ which uses 7-byte short reports or 20-byte long reports with a report ID prefix.

Responses arrive as 18-byte notifications in the same format.

### Comparison with USB HID++

| | USB (via hid4java) | BLE (via GATT) |
|-|-------------------|-----------------|
| Transport | HID output report | GATT characteristic write |
| Report ID | `0x10` (short) / `0x11` (long) | Not used |
| Device index | `0x01` (wireless) / `0xFF` (corded) | Not used |
| Message size | 7 or 20 bytes | 8 bytes |
| Response size | 7 or 20 bytes | 18 bytes |

## HID++ 2.0 Feature Discovery

The Spotlight uses HID++ 2.0 (protocol version 4.24 as reported by IRoot). Features are addressed by **index** (a slot number), resolved at runtime via the IRoot feature table at index `0x00`.

### IRoot.getIndex (function 0)

Request:
```
[0x00, 0x07, feature_id_hi, feature_id_lo, 0x00, 0x00, 0x00, 0x00]
  │      │     │                │
  │      │     └────────────────┴── Feature ID to look up
  │      └── func=0 (getIndex), swId=7
  └── IRoot is always at feature index 0x00
```

Response:
```
[0x00, 0x07, feature_index, feature_type, 0x00, ...]
                │               │
                │               └── Feature type flags
                └── The slot number to use in subsequent commands
```

If `feature_index` is `0x00`, the feature is not available.

### IRoot.getVersion (function 1)

Request: `[0x00, 0x17, 0x00, ...]`
Response: `[0x00, 0x17, major, minor, ...]` → Protocol version (e.g., 4.24)

## PresenterControl (Feature 0x1A00)

On the Spotlight, this feature is typically at index **0x09** (resolved via IRoot at runtime).

### vibrate (function 1)

Triggers the haptic motor.

Request:
```
[feat_idx, 0x17, duration, freq_hi, duty_cycle, 0x00, 0x00, 0x00]
    │        │      │         │          │
    │        │      │         │          └── 0x80 = 50% duty cycle
    │        │      │         └── 0xE8 (part of frequency, typically 1000Hz combined)
    │        │      └── Duration in 100ms units (1-10)
    │        └── func=1 (vibrate), swId=7
    └── PresenterControl feature index (e.g., 0x09)
```

Response: `[feat_idx, 0x17, 0x00, ...]` — all-zero params indicates success.

### Vibration Parameters

| Parameter | Byte | Range | Notes |
|-----------|------|-------|-------|
| Duration | 2 | 1–10 | In 100ms units. 1 = 100ms, 10 = 1000ms |
| Frequency high | 3 | — | `0xE8` for standard vibration |
| Duty cycle | 4 | — | `0x80` for 50% duty cycle |

## Error Responses

Errors are returned with `feature_index = 0xFF`:

```
[0xFF, error_feat_idx, error_func_sw_id, error_code, ...]
```

| Error Code | Name |
|-----------|------|
| 0x01 | Unknown |
| 0x02 | Invalid argument |
| 0x03 | Out of range |
| 0x05 | Not allowed |
| 0x06 | Invalid function ID |
| 0x07 | Busy |
| 0x08 | Unsupported |

## HID Descriptor (for reference)

The Spotlight's BLE HID descriptor (165 bytes) defines four collections:

| Report ID | Collection | Usage Page | Usage |
|-----------|-----------|------------|-------|
| 0x01 | Keyboard | 0x01 (Generic Desktop) | 0x06 (Keyboard) |
| 0x02 | Mouse | 0x01 (Generic Desktop) | 0x02 (Mouse) |
| 0x03 | Consumer Control | 0x0C (Consumer) | 0x01 (Consumer Control) |
| 0x11 | Vendor | 0xFF43 | 0x0202 |

The vendor collection (report ID 0x11) declares both input and output reports of 19 bytes each. However, as noted above, macOS does not forward output reports to BLE HID devices via `IOHIDDeviceSetReport`.

## References

- [Logitech HID++ 2.0 specification (cpg-docs)](https://github.com/Logitech/cpg-docs)
- [Projecteur — Spotlight HID++ documentation](https://github.com/jahnf/Projecteur)
- [mxlight — macOS Logitech BLE example](https://github.com/rosickey/mxlight)
- [Linux kernel hid-logitech-hidpp.c](https://github.com/torvalds/linux/blob/master/drivers/hid/hid-logitech-hidpp.c)
