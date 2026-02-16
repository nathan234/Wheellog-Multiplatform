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
| KMP shared utils | `core/src/commonMain/.../util/{ByteUtils,StringUtil,DisplayUtils}.kt` |
| KMP domain types | `core/src/commonMain/.../domain/{WheelState,WheelType}.kt` |
| Android bridge | `app/src/main/.../kmp/KmpWheelBridge.kt` |
| Decoder mode enum | `app/src/main/.../kmp/DecoderMode.kt` |
| Compose screens | `app/src/main/.../compose/screens/*.kt` |
| Compose components | `app/src/main/.../compose/components/*.kt` |
| Compose ViewModel | `app/src/main/.../compose/WheelViewModel.kt` |
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

# Build iOS app (simulator)
xcodebuild -project iosApp/WheelLog.xcodeproj -scheme WheelLog \
  -destination 'platform=iOS Simulator,name=iPhone 16 Pro' build
```

## KMP-to-Swift Conventions

- KMP `object` singletons are accessed as `.shared` in Swift
  (e.g., `DisplayUtils.shared.formatSpeed(...)`, `ByteUtils.shared.kmToMiles(...)`)
- KMP `enum` values are lowercase in Swift (e.g., `WheelType.kingsong`)
- KMP `Int` parameters become `Int32` in Swift; cast with `Int32(value)`
- KMP `WheelState` properties are accessed directly (e.g., `kmpState.displayName`)

## iOS Testing on Simulator

BLE is not available on iOS Simulator. Use the test mode instead:
1. Run app on simulator
2. Tap "Test KMP Decoder" button
3. Verifies decoder with real Kingsong packets (12% battery, 13°C)

## Testing Philosophy

Follow a **test-first** approach for all KMP shared code:

1. **Write tests before implementation** — define expected behavior in `core/src/commonTest/` first
2. **Run tests to confirm they fail** — validates the test is meaningful
3. **Implement the code** — make tests pass
4. **Verify all tests pass** before moving to platform integration

For plans and PRs, tests should appear as the first implementation step, not an afterthought. Every new KMP module (`core/src/commonMain/`) should have a corresponding test file (`core/src/commonTest/`).

Platform-specific code (Compose UI, SwiftUI views) is verified via build compilation + manual testing.

## Branch

Active development: `main`
