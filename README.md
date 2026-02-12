# WheelLog Multiplatform

Open-source companion app for electric unicycles (EUC), built with **Kotlin Multiplatform** to share protocol decoders across Android and iOS.

## Supported Wheels

| Manufacturer | Models |
|---|---|
| KingSong | S18, S22, 16X, 18L/XL, and more |
| Gotway / Begode | MCM5, Nikola, Monster, Sherman, etc. |
| Veteran | Sherman, Abrams, Patton |
| InMotion | V5F, V8, V10, V11, V12, V13 |
| InMotion V2 | V14, Challenger |
| Ninebot | Z6/Z10, S2, One C/P/E+, Mini |

## Features

### Real-Time Telemetry
Live dashboard with speed gauge, battery, temperature, voltage, current, power, PWM, and distance tracking.

### Alarm System
Configurable speed, current, temperature, and low-battery alarms with audio beeps, haptic feedback, and optional wheel horn activation. Alarms fire with a 5-second cooldown and work in background mode via local notifications.

### Ride Logging
Record rides to CSV with optional GPS coordinates. Auto-start logging on connect, browse past rides, and share CSV files directly from the app.

### Real-Time Charts
Scrolling 60-second telemetry chart with toggleable series for speed, current, power, and temperature. Built with Swift Charts on iOS.

### Auto-Reconnect
Automatic reconnection with exponential backoff (2s to 30s) when connection drops, with cancel UI in the connection banner.

### Wheel Settings
Read and change wheel settings over BLE. Pedals mode (Hard/Medium/Soft) can be changed directly from the app on supported wheels.

### Background Mode
Maintains BLE connection, alarms, and ride logging when the app is backgrounded. Alarm notifications are delivered as local push notifications.

<!-- Screenshots will go here -->
<!-- | ![Dashboard](screenshots/dashboard.png) | ![Chart](screenshots/chart.png) | ![Settings](screenshots/settings.png) | -->
<!-- |---|---|---| -->

## Architecture

```
Wheellog.Android/
├── core/                    # Kotlin Multiplatform shared module
│   └── src/
│       ├── commonMain/      # Protocol decoders, wheel state, connection manager
│       ├── androidMain/     # Android BLE implementation
│       └── iosMain/         # iOS CoreBluetooth implementation
├── app/                     # Android app
├── iosApp/                  # iOS SwiftUI app
│   └── WheelLog/
│       ├── Bridge/          # Swift-to-KMP wrappers
│       └── Views/           # SwiftUI views
└── wearos/                  # WearOS companion app
```

All wheel protocol decoders live in `core/` and are shared between platforms. The decoders are thread-safe (protected by `Lock`) and fully tested with 581+ unit tests.

## Building

### Prerequisites

- Android Studio or IntelliJ with Kotlin Multiplatform plugin
- Xcode 15+ (for iOS)
- JDK 17+

### Android

```bash
./gradlew :app:assembleDebug
```

### iOS

```bash
# Build the KMP framework
./gradlew :core:linkReleaseFrameworkIosSimulatorArm64

# Then open and build in Xcode
open iosApp/WheelLog.xcodeproj
```

### Tests

```bash
# Run all KMP decoder tests
./gradlew :core:testDebugUnitTest
```

## Android Decoder Mode

The Android app supports three decoder modes under **Settings > Application Settings > Decoder Mode**:

| Mode | Description |
|---|---|
| **Legacy Only** (default) | Original Java/Kotlin decoders |
| **KMP Only** | New cross-platform decoders |
| **Both** | Run both in parallel for comparison |

iOS always uses the KMP decoders.

## Contributing

Pull requests are welcome on the `main` branch. Please run `./gradlew :core:testDebugUnitTest` before submitting.

## Acknowledgments

Originally based on [WheelLog.Android](https://github.com/Wheellog/Wheellog.Android) by the WheelLog team and [palachzzz fork](https://github.com/palachzzz/WheelLogAndroid).
