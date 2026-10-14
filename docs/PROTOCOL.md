# Shower head NFC memory map

How ByeDirty reads a Hello Klean smart shower head (model `HKSSH001`). This was worked out by comparing
labelled before/after scans (hot, cold and warm water, then a filter reset) with what the official iOS app
shows and what its "send device data" export names each field. The decoder is
[`ShowerHeadReading.kt`](../app/src/main/java/dev/snivik/byedirty/data/ShowerHeadReading.kt), and its tests run
against real scans.

## The tag

- **Chip:** ISO 15693 (NFC-V) STMicroelectronics ST25DV64K, IC reference `0x51`: 2048 blocks of 4 bytes (8 KB).
- **Power:** the shower head runs off a supercapacitor charged by the water flow. It writes to the tag over
  I²C, and phones read it over NFC.
- **Addressing:**
  - Blocks above `0xFF` need the *extended* read commands (`0x30`, `0x33`, with 16-bit addresses).
  - Plain reads (`0x20`, `0x23`) take an 8-bit address and wrap around. That's why generic NFC apps show the
    first 256 blocks repeating.
- **Byte order:** all numbers are little-endian unless noted.
- **Copies:** most live counters are written twice side by side (tear-safe copies); read the first.

| Blocks | Contents |
|---|---|
| `0x000`–`0x007` | Capability container (`E2 40 …`, 8-byte form) and an NDEF URI record: `https://helloklean.com` |
| `0x080`–`0x0FF` | Identity, configuration and live counters (below) |
| `0x100`–`0x7FF` | Ring buffer of 256 shower records, 28 bytes each |

## Identity and configuration

The export's field names are in `code`.

| Block | Bytes | Field | Example |
|---|---|---|---|
| `0x80`–`0x85` | ASCII | `model` | `HKSSH001` |
| `0x86`–`0x8B` | ASCII | `serialNumber` | `TW26030001` |
| `0x8C` | ASCII | `hardwareVersion` | `0001` |
| `0x8D` | ASCII | `firmwareVersion` | `0001` |
| `0x8E` | u32 | `rtcID` | |
| `0x8F` | u32 | `norFlashId` | |
| `0x94` | 4 × u8 | `filterRiminderRed`, `filterRiminderWhite`, `tempRiminderRed`, `tempRiminderWhite` (LED settings) | 50, 50, 50, 50 |
| `0x95` | u8, u8, u16 | `resetKeyHoldTime` (s), `datalogCombinedInterval`, `filterMaxWaterUsage` (L) | 5, 10, 8000 |
| `0x96` | u16, u16 | `filterElementReminder` (L used before the head warns), `highTempLEDReminder` (0.1 °C) | 6400, 450 |
| `0x97` | u16, u16 | `highTempTimingTemp`, `lowTempTimingTemp` (0.1 °C): what counts as hot / cold | 420, 250 |
| `0x98` | 4 × u8 | `waterPressure1…4ErrorCompensation` | 100 × 4 |
| `0x99` | u32 | `resetTimestamp` (Unix seconds; default 2001-01-01) | |

## Live counters

| Block | Type | Field | Notes |
|---|---|---|---|
| `0xA4` | u16 ×2 | `currentFilterWaterUsage` | litres through the current filter |
| `0xA5` | u16 ×2 | `remainingWaterUsage` | litres left; back to `filterMaxWaterUsage` after a reset |
| `0xA6` | u32 | `currentFilterWorkingTime` | seconds of flow through the current filter |
| `0xA8` | u32 ×2 | `totalWorkingTime` | lifetime seconds of flow |
| `0xAA` | u16 ×2 | `totalFiltersUsed` | goes up by one per reset; also the filter number in log records |
| `0xAB` | u16 ×2 | `currentNFCEEPROM` | records written to the log so far |
| `0xAC` | u16, u16 | `currentNORFlash`, `unUploadedNORFlash` | the head keeps a longer log in its own flash |
| `0xB1` | u32 ×2 | `currentTimestamp` | device clock at the last log update |
| `0xB3` | u16 ×2 | `supercapacitorVoltage` | mV |
| `0xB4` | u16 ×2 | | records written for the current filter |
| `0xB5` | u16 ×2 | | byte offset of the newest log record (e.g. `7380` = slot 227) |

The clock is a real-time clock backed by the supercapacitor. When the capacitor runs flat it restarts at
2001-01-01, so timestamps are good for ordering but aren't real dates.

## Shower records

The records start at byte `0x400`, 28 bytes each, in a ring of 256. To read them oldest first, start just after
the slot that `0xB5` points to. While water runs, the head updates the newest record in place. Pauses shorter
than about `datalogCombinedInterval` stay in the same record.

| Byte | Field |
|---|---|
| 0 | `filterNumber` |
| 1–4 | `timestamp`, u32 device clock at the last update |
| 5–6 | `logNumber`, u16, from 1 for each filter |
| 7–22 | `waterLogData`: 4 × (u16 volume in 0.1 L, u16 seconds), one per pressure band. Only band 0 ("low", 1.5) and band 1 ("medium", 3) have been seen in use. |
| 23–24 | `highTemperatureTime`, seconds above `highTempTimingTemp` |
| 25–26 | `lowTemperatureTime`, seconds below `lowTempTimingTemp` |
| 27 | `checksum`: the low byte of the sum of bytes 0–26 |

A filter reset opens a new record with `logNumber` 1.

## How the official app calculates its numbers

These match its screens exactly:

- **Which showers count:** only sessions of at least 60 seconds (total of all bands), across filter changes.
- **Average shower:** the mean duration over the last 7 or 30 of those showers, shown in rounded minutes.
- **Average hot / cold:** the mean `highTemperatureTime` and `lowTemperatureTime` per shower, rounded down to
  seconds.
- **Hot % / cold %:** total hot or cold seconds ÷ total seconds, rounded down.
- **Streak:** consecutive most recent showers with `lowTemperatureTime` > 0.
- **Days left:** litres left ÷ (average litres per shower × showers per week ÷ 7). Showers per week is a number
  the app asks you for. ByeDirty gets 75 days where the official app showed 74.
