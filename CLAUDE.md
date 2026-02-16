# WheelLog KMP Migration

## Overview

WheelLog is an Android/iOS app for electric unicycle telemetry. The codebase is undergoing a Kotlin Multiplatform (KMP) migration to share protocol decoders between platforms.

## Project Structure

```
Wheellog.Android/
├── core/                    # KMP shared module (commonMain, androidMain, iosMain)
│   └── src/commonMain/.../core/
│       ├── alarm/           # AlarmChecker
│       ├── ble/             # BleUuids, WheelTypeDetector, WheelConnectionInfo
│       ├── domain/          # WheelState, WheelType, SmartBms, AppConstants
│       ├── logging/         # RideLogger
│       ├── protocol/        # All decoders + unpackers (see Decoder Architecture below)
│       ├── service/         # WheelConnectionManager, KeepAliveTimer
│       ├── telemetry/       # TelemetryBuffer
│       ├── ui/              # WheelSettingsConfig, ControlSpec
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
| InmotionDecoder | InmotionUnpacker | |
| InmotionV2Decoder | InmotionV2Unpacker | |

Supporting files: `WheelDecoder.kt` (interface), `DefaultWheelDecoderFactory.kt` (creates decoder by wheel type), `AutoDetectDecoder.kt` (identifies wheel type from raw packets).

**Naming note:** InMotion classes use lowercase-m (`InmotionDecoder`, not `InMotionDecoder`) throughout the codebase, even though the manufacturer's name is "InMotion". Search for `Inmotion` (lowercase m) when looking for these files.

## Decoder Mode (Android)

Users can choose which decoder to use:
- **Settings → Application Settings → Decoder Mode**

Options:
- `LEGACY_ONLY` (default): Original Java/Kotlin decoders
- `KMP_ONLY`: New cross-platform decoders
- `BOTH`: Run both in parallel for comparison

iOS always uses KMP decoders (no legacy option).

## Key Files

| Purpose | Path |
|---------|------|
| **KMP Core** | |
| Protocol decoders | `core/src/commonMain/.../protocol/*.kt` |
| Connection manager | `core/src/commonMain/.../service/WheelConnectionManager.kt` |
| Wheel state & types | `core/src/commonMain/.../domain/{WheelState,WheelType,SmartBms}.kt` |
| Settings config | `core/src/commonMain/.../ui/{WheelSettingsConfig,ControlSpec}.kt` |
| Alarm logic | `core/src/commonMain/.../alarm/AlarmChecker.kt` |
| BLE UUIDs & detection | `core/src/commonMain/.../ble/{BleUuids,WheelTypeDetector}.kt` |
| Utils (formatting, platform) | `core/src/commonMain/.../utils/{ByteUtils,DisplayUtils,StringUtil,Lock,Logger}.kt` |
| Telemetry buffer | `core/src/commonMain/.../telemetry/TelemetryBuffer.kt` |
| **Platform Implementations** | |
| Android BLE | `core/src/androidMain/.../service/BleManager.android.kt` |
| iOS BLE | `core/src/iosMain/.../service/BleManager.ios.kt` |
| iOS Swift bridge factory | `core/src/iosMain/.../service/WheelConnectionManagerFactory.kt` |
| Lock (Android/iOS) | `core/src/{androidMain,iosMain}/.../utils/Lock.{android,ios}.kt` |
| Logger (Android/iOS) | `core/src/{androidMain,iosMain}/.../utils/Logger.{android,ios}.kt` |
| **Android App** | |
| KMP bridge | `app/src/main/.../kmp/KmpWheelBridge.kt` |
| Decoder mode enum | `app/src/main/.../kmp/DecoderMode.kt` |
| ViewModel | `app/src/main/.../compose/WheelViewModel.kt` |
| Compose screens | `app/src/main/.../compose/screens/*.kt` |
| Compose components | `app/src/main/.../compose/components/*.kt` |
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

`WheelConnectionManagerFactory.kt` (in iosMain) provides Swift-friendly wrappers around the KMP `WheelConnectionManager`. Despite the name, it's a facade/adapter, not a factory — it exposes convenience methods that Swift can call without dealing with Kotlin coroutines directly.

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

### iOS Testing on Simulator

BLE is not available on iOS Simulator. Use the test mode instead:
1. Run app on simulator
2. Tap "Test KMP Decoder" button
3. Verifies decoder with real Kingsong packets (12% battery, 13°C)

## Branch

Active development: `main`
