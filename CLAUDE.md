# WheelLog KMP Migration

## Overview

WheelLog is an Android/iOS app for electric unicycle telemetry. The codebase is undergoing a Kotlin Multiplatform (KMP) migration to share protocol decoders between platforms.

## Development Policy

**Do not modify legacy Android code.** All new work targets the KMP `core/` module and the new Compose UI in `app/`. The legacy Java adapters (`app/.../utils/*Adapter*.java`), `WheelData.java`, and related files are frozen — they will be removed once the KMP migration is complete, not incrementally refactored. The goal is to eventually separate the new Compose app entirely from the old Android app.

## Project Structure

```
Wheellog.Android/
├── core/                    # KMP shared module (commonMain, androidMain, iosMain)
│   └── src/commonMain/.../core/
│       ├── alarm/           # AlarmChecker
│       ├── ble/             # BleUuids, WheelTypeDetector, WheelConnectionInfo
│       ├── domain/          # WheelState, WheelType, SmartBms, AppConstants, WheelSettingsConfig, ControlSpec
│       ├── logging/         # RideLogger
│       ├── protocol/        # All decoders + unpackers (see Decoder Architecture below)
│       ├── service/         # WheelConnectionManager, KeepAliveTimer
│       ├── telemetry/       # TelemetryBuffer
│       └── utils/           # ByteUtils, DisplayUtils, StringUtil, Lock, Logger, EnergyCalculator
├── app/                     # Android app (Jetpack Compose)
├── shared/                  # Android-only library shared between app and wearos
│                            #   (WearPage, SmartDouble, Constants)
├── iosApp/                  # iOS SwiftUI app
│   ├── Scripts/             # build-kmp-framework.sh (Xcode build phase)
│   └── WheelLog/
│       ├── Bridge/          # Swift-to-KMP wrappers (see iOS Bridge below)
│       └── Views/           # SwiftUI views (11 files)
└── wearos/                  # WearOS companion app (does NOT use KMP core;
                             #   receives data from phone via DataClient)
```

## KMP Decoder Architecture

All decoders are in `core/src/commonMain/.../protocol/` and implement the `WheelDecoder` interface. All are thread-safe (protected by Lock).

Some decoders have a paired `*Unpacker` for low-level frame reassembly; others handle framing internally:

| Decoder | Unpacker | Notes |
|---------|----------|-------|
| KingsongDecoder | — | Framing handled internally |
| GotwayDecoder | GotwayUnpacker | |
| VeteranDecoder | — | Framing handled internally |
| NinebotDecoder | NinebotUnpacker | |
| NinebotZDecoder | — | Framing handled internally |
| InMotionDecoder | InMotionUnpacker | |
| InMotionV2Decoder | InMotionV2Unpacker | |

Supporting files: `WheelDecoder.kt` (interface), `DefaultWheelDecoderFactory.kt` (creates decoder by wheel type), `AutoDetectDecoder.kt` (identifies wheel type from raw packets).

### Decoder Data Flow

```
BLE notification → WheelConnectionManager.onDataReceived(bytes)
                     ↓
              decoder.decode(bytes, currentState, config)
                     ↓ (returns DecodedData)
              ┌──────┴──────┐
              │              │
         newState        commands
              ↓              ↓
     _wheelState.emit()  sendCommand() → bleManager.write()
              ↓
        UI observes via StateFlow (Android) / polling (iOS)
```

Lifecycle:
1. **Connect** → `WheelTypeDetector` identifies wheel → `DefaultWheelDecoderFactory.createDecoder()`
2. **Init** → `decoder.getInitCommands()` sent to wheel (identity requests)
3. **Decode loop** → each BLE notification calls `decode()`, updates state, sends response commands
4. **Ready** → `decoder.isReady()` returns true → `ConnectionState.Connected` → keep-alive starts
5. **Keep-alive** → `decoder.getKeepAliveCommand()` sent at `keepAliveIntervalMs` interval
6. **Disconnect** → `decoder.reset()` → timers stopped → state cleared

## Decoder Mode (Android)

Users can choose which decoder to use:
- **Settings → Application Settings → Decoder Mode**

Options:
- `LEGACY_ONLY` (default): Original Java/Kotlin decoders
- `KMP_ONLY`: New cross-platform decoders

iOS always uses KMP decoders (no legacy option).

## Legacy vs Compose Boundary

The `app/` module contains two independent UI paths:

| | Legacy Path | Compose Path |
|---|---|---|
| Entry point | `MainActivity` | `ComposeActivity` |
| BLE service | `BluetoothService` | `WheelService` |
| Data model | `WheelData` singleton | KMP `WheelState` via `WheelViewModel` |
| Decoder | Legacy adapters or `KmpWheelBridge` | KMP decoders via `WheelConnectionManager` |
| Settings | `AppConfig` (full) | `AppConfig` (SharedPreferences only, no `wd` access) |
| UI | XML layouts + hybrid Compose | Pure Jetpack Compose |

Files in `compose/screens/` belong to the Compose path.
Files in `compose/legacy/` are hybrid screens rendered inside `MainActivity`.

When `use_compose_ui` is true, `WheelData` is never initialized and legacy Koin modules
(`notificationsModule`, `volumeKeyModule`) are not loaded.

## Key Files

| Purpose | Path |
|---------|------|
| **KMP Core** | |
| Protocol decoders | `core/src/commonMain/.../protocol/*.kt` |
| Connection manager | `core/src/commonMain/.../service/WheelConnectionManager.kt` |
| Wheel state & types | `core/src/commonMain/.../domain/{WheelState,WheelType,SmartBms}.kt` |
| Settings config | `core/src/commonMain/.../domain/{WheelSettingsConfig,ControlSpec}.kt` |
| Alarm logic | `core/src/commonMain/.../alarm/AlarmChecker.kt` |
| BLE UUIDs & detection | `core/src/commonMain/.../ble/{BleUuids,WheelTypeDetector}.kt` |
| Utils (formatting, platform) | `core/src/commonMain/.../utils/{ByteUtils,DisplayUtils,StringUtil,Lock,Logger}.kt` |
| Telemetry buffer | `core/src/commonMain/.../telemetry/TelemetryBuffer.kt` |
| **Platform Implementations** | |
| Android BLE | `core/src/androidMain/.../service/BleManager.android.kt` |
| iOS BLE | `core/src/iosMain/.../service/BleManager.ios.kt` |
| iOS Swift bridge helper | `core/src/iosMain/.../service/WheelConnectionManagerHelper.kt` |
| Lock (Android/iOS) | `core/src/{androidMain,iosMain}/.../utils/Lock.{android,ios}.kt` |
| Logger (Android/iOS) | `core/src/{androidMain,iosMain}/.../utils/Logger.{android,ios}.kt` |
| **Android App** | |
| KMP bridge | `app/src/main/.../kmp/KmpWheelBridge.kt` |
| Decoder mode enum | `app/src/main/.../kmp/DecoderMode.kt` |
| ViewModel | `app/src/main/.../compose/WheelViewModel.kt` |
| Compose screens | `app/src/main/.../compose/screens/*.kt` |
| Compose components | `app/src/main/.../compose/components/*.kt` |
| Legacy hybrid screens | `app/src/main/.../compose/legacy/*.kt` |
| **iOS App** | |
| Main bridge (orchestrator) | `iosApp/WheelLog/Bridge/WheelManager.swift` |
| StateFlow → @Published | `iosApp/WheelLog/Bridge/StateFlowObserver.swift` |
| Alarm bridge | `iosApp/WheelLog/Bridge/AlarmManager.swift` |
| Auto-reconnect | `iosApp/WheelLog/Bridge/AutoReconnectManager.swift` |
| Background mode | `iosApp/WheelLog/Bridge/BackgroundManager.swift` |
| Location tracking | `iosApp/WheelLog/Bridge/LocationManager.swift` |
| Ride logging bridge | `iosApp/WheelLog/Bridge/RideLogger.swift` |
| Ride storage (iOS-only) | `iosApp/WheelLog/Bridge/RideStore.swift` |
| Telemetry bridge | `iosApp/WheelLog/Bridge/TelemetryBuffer.swift` |
| SwiftUI views | `iosApp/WheelLog/Views/*.swift` |

## iOS Bridge Architecture

`WheelConnectionManagerHelper.kt` (in iosMain) provides Swift-friendly wrappers around the KMP `WheelConnectionManager`. It exposes convenience methods that Swift can call without dealing with Kotlin coroutines directly.

On the Swift side, `WheelManager.swift` is the main orchestrator. It owns the KMP manager instance and coordinates the other Bridge files. `StateFlowObserver` polls KMP StateFlows on a timer and publishes changes to SwiftUI via `@Published` properties.

## Major Dependencies

| Dependency | Version | Used in |
|------------|---------|---------|
| Kotlin | 2.2.10 | All modules |
| Kotlinx Coroutines | 1.10.2 | core |
| Koin (DI) | 4.1.1 | core, app |
| Blessed (Android BLE) | 2.4.1 | core androidMain |
| Jetpack Compose | 1.9.2 | app |
| Room (SQLite) | 2.7.2 | app |
| Vico (charts) | 2.1.2 | app |
| Swift Charts | — | iosApp |
| CoreBluetooth | — | core iosMain |

## Build Commands

```bash
# Run KMP tests
./gradlew :core:testDebugUnitTest

# Compile Android app
./gradlew :app:compileDebugKotlin

# Build Android APK
./gradlew :app:assembleDebug

# Build KMP framework for iOS Simulator
./gradlew :core:linkReleaseFrameworkIosSimulatorArm64

# Build KMP framework for physical iPhone
./gradlew :core:linkReleaseFrameworkIosArm64

# Build iOS app (simulator)
xcodebuild -project iosApp/WheelLog.xcodeproj -scheme WheelLog \
  -destination 'platform=iOS Simulator,name=iPhone 16 Pro' build
```

The Xcode project has a build phase that runs `iosApp/Scripts/build-kmp-framework.sh` automatically, so building from Xcode handles the KMP framework step. The manual Gradle commands above are for CI or command-line builds.

## KMP-to-Swift Conventions

- KMP `object` singletons are accessed as `.shared` in Swift
  (e.g., `DisplayUtils.shared.formatSpeed(...)`, `ByteUtils.shared.kmToMiles(...)`)
- KMP `enum` values are lowercase in Swift (e.g., `WheelType.kingsong`)
- KMP `Int` parameters become `Int32` in Swift; cast with `Int32(value)`
- KMP `WheelState` properties are accessed directly (e.g., `kmpState.displayName`)

## Testing

### Philosophy

Follow a **test-first** approach for all KMP shared code:

1. **Write tests before implementation** — define expected behavior in `core/src/commonTest/` first
2. **Run tests to confirm they fail** — validates the test is meaningful
3. **Implement the code** — make tests pass
4. **Verify all tests pass** before moving to platform integration

Every new KMP module (`core/src/commonMain/`) should have a corresponding test file (`core/src/commonTest/`).

### Test Locations

| Location | Framework | What it covers |
|----------|-----------|----------------|
| `core/src/commonTest/` | kotlin-test, coroutines-test | KMP shared code (decoders, state, utils) |
| `app/src/test/` | JUnit 4, Truth, Mockk, Robolectric | Android app logic (ViewModel, bridge, services) |
| `shared/src/test/` | JUnit 4 | Shared Android utilities |

Platform-specific UI (Compose, SwiftUI) is verified via build compilation + manual testing.

### Test Patterns

Decoder tests follow a consistent pattern:

1. **Instantiate decoder directly**: `val decoder = GotwayDecoder()`
2. **Create default state and config**: `val state = WheelState()`, `val config = DecoderConfig()`
3. **Build test frames**: Use hex strings or frame builders to create raw byte arrays
4. **Feed to decoder**: `val result = decoder.decode(frame, state, config)`
5. **Assert on result**: Check `result.newState.speed`, `result.commands`, etc.

Shared test utilities are in `core/src/commonTest/.../protocol/TestUtils.kt`:
- `"55AA...".hexToByteArray()` — convert hex string to bytes
- `shortToBytesBE(value)` — encode Int as 2-byte big-endian array

Frame builders (decoder-specific, in respective test files):
- `buildLiveDataFrame()` / `buildGotwayLiveDataFrame()` — Gotway frame 0x00
- `buildIM2Frame(flags, command, data)` — InMotion V2 message with escaping + checksum
- `buildSettingsFrame(payload)` — InMotion V2 settings frame

### iOS Testing on Simulator

BLE is not available on iOS Simulator. Use the test mode instead:
1. Run app on simulator
2. Tap "Test KMP Decoder" button
3. Verifies decoder with real Kingsong packets (12% battery, 13°C)

## Common Pitfalls

Protocol decoder gotchas discovered during development:

- **GotwayDecoder retry counter**: `infoAttempt` is 0-indexed and compared with `<`. Fallback triggers
  when counter **reaches** `MAX_INFO_ATTEMPTS` (50), not after 50 iterations — loop needs 51 iterations
  to see the fallback.
- **InMotionV2 MAIN_INFO sub-types**: Command `0x02` (MAIN_INFO) is used for car type, serial, AND
  version requests. The sub-type is in `data[0]`: `0x01`=car type, `0x02`=serial, `0x06`=versions.
  Don't confuse the command byte with the sub-type.
- **InMotionV2 response bit**: Response frames have command byte OR'd with `0x80`
  (e.g., SETTINGS `0x20` → response `0xA0`). Mask with `0x7F` to get the base command.
- **NinebotZ state ordering**: The 14 connection states must be traversed in order — skipping states
  causes the wheel to stop responding. BMS states are conditional on `bmsReadingMode`.
- **Kingsong 0xA4 acknowledgment**: When a `0xA4` frame is received, the decoder must respond with a
  `0x98` (alarm settings request) command. This is done via the `commands` return list from `decode()`.

## Branch

Active development: `main`
