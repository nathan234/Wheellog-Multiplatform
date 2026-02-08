# WheelLog KMP Migration

## Overview

WheelLog is an Android/iOS app for electric unicycle telemetry. The codebase is undergoing a Kotlin Multiplatform (KMP) migration to share protocol decoders between platforms.

## Project Structure

```
Wheellog.Android/
├── app/                    # Android app
├── core/                   # KMP shared module
│   └── src/
│       ├── commonMain/     # Shared Kotlin code
│       ├── androidMain/    # Android-specific implementations
│       └── iosMain/        # iOS-specific implementations
├── iosApp/                 # iOS SwiftUI app
│   └── WheelLog/
│       ├── Bridge/         # Swift-to-KMP wrappers
│       └── Views/          # SwiftUI views
└── wearos/                 # WearOS app
```

## KMP Decoder Status

All wheel protocol decoders are implemented in KMP (`core/src/commonMain/.../protocol/`):
- KingsongDecoder, GotwayDecoder, VeteranDecoder
- NinebotDecoder, NinebotZDecoder
- InmotionDecoder, InmotionV2Decoder

All decoders are thread-safe (protected by Lock).

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
| KMP decoders | `core/src/commonMain/.../protocol/*.kt` |
| Android bridge | `app/src/main/.../kmp/KmpWheelBridge.kt` |
| Decoder mode enum | `app/src/main/.../kmp/DecoderMode.kt` |
| iOS BLE manager | `core/src/iosMain/.../service/BleManager.ios.kt` |
| iOS Swift bridge | `iosApp/WheelLog/Bridge/WheelManager.swift` |
| iOS views | `iosApp/WheelLog/Views/*.swift` |

## Build Commands

```bash
# Build KMP framework for iOS Simulator
./gradlew :core:linkReleaseFrameworkIosSimulatorArm64

# Build KMP framework for physical iPhone
./gradlew :core:linkReleaseFrameworkIosArm64

# Run KMP tests
./gradlew :core:testDebugUnitTest

# Compile Android app
./gradlew :app:compileDebugKotlin
```

## iOS Testing on Simulator

BLE is not available on iOS Simulator. Use the test mode instead:
1. Run app on simulator
2. Tap "Test KMP Decoder" button
3. Verifies decoder with real Kingsong packets (12% battery, 13°C)

## Branch

Active development: `feature/kmp-migration`
