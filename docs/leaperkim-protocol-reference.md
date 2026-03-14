# Leaperkim / Veteran Protocol Reference

Covers all Leaperkim and Veteran (legacy protocol) EUCs. Extracted from the official Leaperkim Android app v1.4.8 (`com.laoniao.leaperkim`).

## Protocol Basics

- **Frame header**: `[0xDC, 0x5A, 0x5C]`
- **Byte order**: Big-endian for multi-byte fields
- **CRC32**: Appended as 4 bytes big-endian to all binary commands; newer firmware also validates on received frames
- **BLE UUIDs**: Service `FFE0`, Characteristic `FFE1` (same as Gotway)
- **MTU**: App requests MTU 120 on connect
- **Write chunking**: Max 20 bytes per characteristic write; queued and pumped sequentially

## Command Formats

Two formats exist, distinguished by bytes [1]:

| Format | Header (ASCII) | Header (hex) | byte[6] padding |
|--------|---------------|--------------|-----------------|
| Old | `LkAp` | `4C 6B 41 70` | `0x80` |
| New | `LdAp` | `4C 64 41 70` | `0x00` |

### Dual-format sending

The app determines which format to send based on the firmware version string:

```
if (fullVersionCode starts with "004") {
    // Old firmware (Patton): send ONLY old "LkAp" format
} else {
    // All other firmware: send BOTH old AND new format concatenated
    // crc32Encode(oldCmd) + crc32Encode(newCmd)
}
```

Most commands are sent as **both formats concatenated** unless the wheel reports hardware version prefix "004".

### Command structure

```
Old: [4C 6B 41 70] [cmd] [byte5] [0x80 padding...] [value] + CRC32(4 bytes)
New: [4C 64 41 70] [cmd] [byte5] [byte6] [0x80 padding...] [value] + CRC32(4 bytes)
```

- `cmd`: Command byte at index 4
- `byte5`: Usually `0x01` (enabled) or `0x00`
- `byte6`: `0x02` for control toggles (transport, high speed, low voltage), `0x00` for other settings
- Value is placed at a fixed byte position (varies per command), with `0x80` padding between
- `0x80` in settings readback means "unsupported / no function"

## Model IDs

Models are identified by the first 3-4 characters of the 6-digit version code (bytes 28-30 of the base frame).

| sNum | Name | HW Version Prefix | System Voltage | Cells | Continuous Pedal |
|------|------|--------------------|----------------|-------|-----------------|
| 3 | Sherman | 001 | 100.8V | 24S | No |
| 4 | Abrams | 002 | 100.8V | 24S | No |
| 5 | Sherman Max | 0011 | 100.8V | 24S | No |
| 6 | Sherman-S | 003 | 100.8V | 24S | No |
| 7 | Patton | 004 | 126V | 30S | No |
| 8 | LYNX | 005 | 151.2V | 36S | Yes |
| 9 | Sherman-L | 006 | 151.2V | 36S | Yes |
| 10 | Patton-S | 007 | 126V | 30S | Yes |

**Continuous pedal mode**: When `continuousSoftHardSet = true`, ride mode is a 0-100 slider (wire value = rideMode - 100). When `false`, ride mode is 3-position: Soft(1) / Medium(2) / Hard(3).

**Version code format**: 3 bytes at indices 28-30, reordered as `[byte30, byte28, byte29]` → 6-digit zero-padded string → `"XXX.Y.ZZ"` display format. Chars 0-2 = HW prefix, char 3 = HW suffix, chars 4-5 = SW version.

### Model name from sub-type 35 (0x23)

Newer firmware sends model name + image URL as GBK-encoded CSV: `"ModelName","?","ImageURL"`.

## String Commands (Legacy / strCmdMode)

If two consecutive frames are both < 47 bytes, the app switches to `strCmdMode = true` and sends GBK-encoded ASCII strings:

| Command | Purpose |
|---------|---------|
| `b` (single char) | Beep/horn |
| `SetLightON` | Light on |
| `SetLightOFF` | Light off |
| `SETs` | Ride mode Soft (1) |
| `SETm` | Ride mode Medium (2) |
| `SETh` | Ride mode Hard (3) |
| `CLEARMETER` | Reset trip meter |
| `AT+RINTOPRO` | Enter AT/info mode |

## Binary Commands

All values shown as decimal. Command byte is at index [4]. Value position is the 0-based index where the value byte is placed.

### Commands sent as both old+new format (via `sendBytesDataCombine`)

| CMD | Value pos | Setting | Value range | Notes |
|-----|-----------|---------|-------------|-------|
| 12 (0x0C) | 7 | Ride mode | 1-3 or 10-100+ | byte5=1; 1=soft,2=med,3=hard; continuous=progress+10 |
| 13 (0x0D) | 8 | Light on/off | 0/1 | byte5=1 |
| 14 (0x0E) | 9 | Horn/beep | 1 | byte5=0 |
| 22 (0x16) | 17 | Shutdown in 10s | 1 | byte5=1; old: byte[16]=1,byte[17]=0x80 |
| 22 (0x16) | 17 | Fall protection angle | 35-75 | byte5=1; progress + 35 |

### Commands sent as new format only (`LdAp` via `sendBytesData`)

| CMD | Value pos | Setting | Value range | Notes |
|-----|-----------|---------|-------------|-------|
| 13 (0x0D) | 8 | Clear meter (trip reset) | 1 | byte5=0, byte6=2 |
| 15 (0x0F) | 10 | Pedal hardness | 0-100 | byte5=1 |
| 16 (0x10) | 11 | Pedal tilt (angle adj) | -80 to +80 | byte5=1; progress - 80; div by 10 for degrees |
| 17 (0x11) | 12 | Alarm speed | 10-120 km/h | byte5=1; progress + 10 |
| 17 (0x11) | 12 | Stop speed (tiltback) | 10-120 km/h | byte5=1; same cmd, context-dependent |
| 18 (0x12) | 13 | Stop power rate (PWM limit) | 30-100% | byte5=1; progress + 30 |
| 18 (0x12) | — | Time sync | — | Overloaded; see Time Sync section |
| 20 (0x14) | 15 | Screen backlight | 0-100% | byte5=1 |
| 20 (0x14) | 15 | Read error log | 1 | byte5=1, value=1 |
| 21 (0x15) | 16 | Gyroscope calibration | 1 | byte5=1; toggle start/stop |
| 22 (0x16) | 17 | Transport mode | 0/1 | byte5=1, byte6=2 |
| 23 (0x17) | 18 | Unit switch (km/mi) | 0/1 | byte5=1; 0=km, 1=mi |
| 24 (0x18) | 19 | Volume/light adjust | -15 to +15 | byte5=1; progress - 15; display /10 percent |
| 25 (0x19) | 20 | Low voltage mode | 0/1 | byte5=1, byte6=2 |
| 26 (0x1A) | 21 | High speed mode | 0/1 | byte5=1, byte6=2 |
| 28 (0x1C) | 23 | Key tone volume | 0-100% | byte5=1 |
| 29 (0x1D) | 24 | Max charge voltage | 0-120 | byte5=1; display = (value/10) + baseVol |
| 31 (0x1F) | 26 | Acc/dec speed helper | 0-100% | byte5=1 |
| 33 (0x21) | 28 | Acceleration reduction | 0-100% | byte5=1 |
| 34 (0x22) | 29 | Brake pressure alarm | 80-125% | byte5=1; progress + 80 |

### Lock/password command (CMD 25 / 0x19)

Built via `genPwdCmd()` — uses time sync prefix with cmd byte incremented by 7 (18 + 7 = 25):

```
[4C 64 41 70] [25] [0] [5] [year-2000] [month] [day] [hour] [min] [sec] [tz]
[pwd_hi] [pwd_mid] [pwd_lo] [action] [newpwd_hi] [newpwd_mid] [newpwd_lo]
+ CRC32
```

Password is a 6-digit number encoded as 3 bytes (big-endian 24-bit).

| Action | Purpose |
|--------|---------|
| 0 | Unlock |
| 1 | Lock |
| 2 | Disable auto-lock |
| 3 | Enable auto-lock |
| 11 | Set / modify / clear password |

## Time Sync

Sent automatically on first data reception (once per connection):

```
[4C 64 41 70] [18] [0] [5] [year-2000] [month] [day] [hour] [min] [sec] [tz_hours]
+ CRC32
```

Then sent again after a 2-second delay. Only sent once per connection (`hasSyncTime` flag).

## Telemetry Data Parsing

### Base Frame (bytes 0-37, always present)

| Index | Size | Field | Conversion |
|-------|------|-------|------------|
| 0-2 | 3 | Frame header | `0xDC 0x5A 0x5C` |
| 3 | 1 | Length | — |
| 4-5 | 2 | Voltage | BE uint16 / 100.0 (volts) |
| 6-7 | 2 | Speed | BE int16 / 10.0 (km/h, signed) |
| 8-9 | 2 | Trip distance (low) | Combined with 10-11 |
| 10-11 | 2 | Trip distance (high) | `(high * 65536) + low` |
| 12-13 | 2 | Total distance (low) | Combined with 14-15 |
| 14-15 | 2 | Total distance (high) | `(high * 65536) + low` |
| 16-17 | 2 | Machine current | BE int16 / 10.0 (signed, amps) |
| 18-19 | 2 | Temperature | BE int16 / 100.0 (signed, °C) |
| 20-21 | 2 | Shutdown time | BE uint16 |
| 22 | 1 | (unused) | — |
| 23 | 1 | Charge mode | 0 = not charging, >0 = charging |
| 24-25 | 2 | Danger speed (alarm) | BE int16 / 10.0 (km/h) |
| 26-27 | 2 | Stop speed (tiltback) | BE int16 / 10.0 (km/h) |
| 28-30 | 3 | Version code | Reordered: `[byte30, byte28, byte29]` → 6-digit int |
| 31 | 1 | Ride mode | 1=soft, 2=medium, 3=hard, 100+=continuous |
| 32-33 | 2 | Pitch angle (car pose) | BE int16 (signed) |
| 34-35 | 2 | PWM output | BE int16 |
| 36-37 | 2 | Battery temp mode | BE int16 (if frame ≥ 38 bytes) |

**Derived values**:
- `actual_current = (machine_current * pwm_output) / 10000`
- `power = actual_current * voltage`

### Extended Data (byte[46] = sub-type)

When frame length > 46, `byte[46]` identifies the sub-frame type. Sub-data starts at index 46 (`SUB_DATA_START_INDEX`).

| byte[46] | Content | Notes |
|----------|---------|-------|
| 0 | Main ext + left battery current + LR angle | — |
| 1 | Cell voltages 1-15 + cell count | Left battery |
| 2 | Cell voltages 16-30 + fall protection angle + battery % | Left battery |
| 3 | Temps 0-6 + cell voltages 31-120 | Left battery |
| 4 | Main ext + right battery current + LR angle | — |
| 5 | Cell voltages 1-15 + cell count + lock state | Right battery |
| 6 | Cell voltages 16-30 | Right battery |
| 7 | Temps 0-6 + cell voltages 31-120 | Right battery |
| 8 | Control settings readback | All current settings |
| 32 (0x20) | Error log entries (old format) | 3 per frame |
| 33 (0x21) | Error log entries (new format) | With timestamps |
| 35 (0x23) | Car name + image URL | GBK-encoded CSV |

### Sub-type 0/4 — Main Extension

| Index | Size | Field |
|-------|------|-------|
| 67-68 | 2 | LR angle (roll) — signed, / 100.0 for degrees |
| 69-70 | 2 | Left battery current (sub-type 0) or right (sub-type 4) — signed abs / 100.0 |
| 71-72 | 2 | Right battery current (sub-type 0 only) — signed abs / 100.0 |

### Sub-type 1/5 — Cell Voltages 1-15

| Index | Size | Field |
|-------|------|-------|
| 51 | 1 | Lock state byte (sub-type 5 only) |
| 52 | 1 | Cell count (clamped 2-120, default 36) |
| 53-82 | 30 | 15 cell voltages as BE uint16 / 1000.0 (volts) |

### Sub-type 2/6 — Cell Voltages 16-30

| Index | Size | Field |
|-------|------|-------|
| 47 | 1 | **Fall protection angle** (sub-type 2 only, raw byte, degrees) |
| 50 | 1 | Battery percentage 0-100 (sub-type 2 only; if valid → `isNewBatteryCulModel`) |
| 53-82 | 30 | 15 cell voltages as BE uint16 / 1000.0 (volts) |

### Sub-type 3/7 — Temperatures + Cell Voltages 31-120

| Index | Size | Field |
|-------|------|-------|
| 47-58 | 12 | 6 temperature values as BE int16 / 100.0 (°C) |
| 59-70 | 12 | Cell voltages 31-36 as BE uint16 / 1000.0 |
| 71-238 | 168 | Cell voltages 37-120 as BE uint16 / 1000.0 |

### Sub-type 8 — Control Settings Readback

`0x80` = unsupported / no function for that setting.

| Index | Field | Notes |
|-------|-------|-------|
| 50 | Pedal hardness | 0-100 |
| 52 | Stop speed | km/h |
| 53 | Stop power rate | % |
| 55 | Screen backlight | % |
| 56 | Gyroscope status | 0/1/2 |
| 57 | Transport mode | 0/1 |
| 58 | Unit (km/mi) | 0/1 |
| 59 | Volume | signed byte / 10 for % |
| 60 | Low voltage mode | 0/1 |
| 61 | High speed mode | 0/1 |
| 63 | Key tone | % |
| 64 | Max charge voltage | value/10 + baseVol |
| 65 | Max charge vol base | Default 145V if 0x80 |
| 66 | Up/down speed helper (acc/dec) | % |
| 68 | Accel reduction | % |
| 69 | Brake pressure alarm | % |

## Lock State Byte

Read from sub-type 5, byte 51. Bitfield:

| Bit | Meaning |
|-----|---------|
| 0 | Password operation success |
| 4 | Currently locked |
| 5 | Auto-lock enabled |
| 6 | Password is set |
| 7 | Lock feature hidden (hide lock UI) |

## Battery SOC Tables

Each model has a 100-entry lookup table mapping `rawVoltage * 100` to 0-100% SOC. The app also has a fallback piecewise-linear formula for 100.8V (24S) wheels:

| Voltage (×100) | SOC |
|----------------|-----|
| ≤ 7560 | 0% |
| 7560-8000 | 0-13% |
| 8000-8320 | 13-25% |
| 8320-8570 | 25-38% |
| 8570-8820 | 38-50% |
| 8820-9048 | 50-63% |
| 9048-9336 | 63-75% |
| 9336-9660 | 75-87% |
| 9660-10000 | 87-100% |

Newer firmware (sub-type 2, byte 50) reports battery % directly (0-100), bypassing the lookup table.

## Commands Not Yet Implemented in FreeWheel

These commands are confirmed in the official app but not yet wired in VeteranDecoder:

| CMD | Setting | Value pos | byte6 | Status in FreeWheel |
|-----|---------|-----------|-------|---------------------|
| 15 (0x0F) | Pedal hardness | 10 | 2 | Not wired |
| 18 (0x12) | Time sync | — | — | Not wired |
| 23 (0x17) | Unit switch | 18 | 2 | Not wired |
| 25 (0x19) | Lock/password | — | — | `SetLock` returns `emptyList()` |
| 31 (0x1F) | Acc/dec speed helper | 26 | 2 | Not wired |
| 33 (0x21) | Acceleration reduction | 28 | 2 | Not wired |
| 34 (0x22) | Brake pressure alarm | 29 | 2 | Not wired |

## Overloaded Command Bytes

Several command bytes are reused for different purposes:

| CMD | Usage 1 | Usage 2 | Disambiguation |
|-----|---------|---------|----------------|
| 13 (0x0D) | Light on/off (byte6=0x80/0x00) | Clear meter (byte5=0, byte6=2) | byte5 + byte6 |
| 17 (0x11) | Alarm speed | Stop speed (tiltback) | Context-dependent |
| 18 (0x12) | Stop power rate (PWM limit) | Time sync | Payload structure differs |
| 20 (0x14) | Screen backlight | Read error log | byte5/value differs |
| 22 (0x16) | Transport mode (byte6=2) | Fall protection angle (byte6=0) | byte6 |
| 22 (0x16) | Shutdown (byte[16]=1) | Fall protection angle | Payload position |

## Misc

- **Heartbeat timeout**: 1500ms — if no data arrives, `receivingData = false`
- **Signed integers**: Values > 32768 treated as negative (`val -= 65536`)
- **String encoding**: GBK (Chinese-compatible superset of ASCII)
- **strCmdMode auto-detection**: Two consecutive frames < 47 bytes → string mode; ≥ 47 bytes → binary mode
