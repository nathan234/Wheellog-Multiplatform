# WheelLog KMP Migration Plan

This document tracks the migration of WheelLog's core functionality to Kotlin Multiplatform for iOS support.

## Overview

**Goal**: Extract protocol decoding and BLE communication into a shared KMP module (`core`) that can be used by both Android and iOS apps.

**Branch**: `feature/kmp-migration`

---

## Phase 1: Domain Models & Utilities ✅ COMPLETE

- [x] WheelState data class
- [x] WheelType enum
- [x] SmartBms data class
- [x] AlarmType enum
- [x] ByteUtils (LE/BE byte conversions)
- [x] StringUtil

---

## Phase 2: Protocol Decoders ✅ COMPLETE

### Interface & Infrastructure
- [x] WheelDecoder interface
- [x] DecoderConfig (useMph, useFahrenheit, useCustomPercents)
- [x] WheelCommand sealed class
- [x] DecodedData result class
- [x] WheelDecoderFactory interface
- [x] DefaultWheelDecoderFactory
- [x] CachingWheelDecoderFactory

### Wheel Decoders
| Decoder | Unpacker | Status | Tests |
|---------|----------|--------|-------|
| GotwayDecoder | GotwayUnpacker | ✅ | ✅ |
| VeteranDecoder | (uses Gotway) | ✅ | ✅ |
| KingsongDecoder | (inline) | ✅ | ✅ |
| InmotionDecoder | InmotionUnpacker | ✅ | ✅ |
| InmotionV2Decoder | InmotionV2Unpacker | ✅ | ✅ |
| NinebotDecoder | NinebotUnpacker | ✅ | ✅ |
| NinebotZDecoder | NinebotZUnpacker | ✅ | ✅ |
| AutoDetectDecoder | (delegates) | ✅ | ✅ |

---

## Phase 3: BLE Layer 🔄 IN PROGRESS

### 3.1 BLE UUIDs & Service Detection ✅
- [x] Create `BleUuids.kt` with all manufacturer UUIDs
- [x] Create `WheelTypeDetector` to identify wheel type from services
- [x] Unit tests for UUID matching and wheel detection

### 3.2 BLE Abstractions (Common) ✅
- [x] ConnectionState sealed class
- [x] BleDevice data class
- [x] BleManager expect class (stubs)
- [x] WheelConnectionManager with keep-alive timer
- [x] KeepAliveTimer for periodic commands
- [x] DataTimeoutTracker for connection loss detection
- [x] CommandScheduler for delayed commands
- [x] Unit tests for timer components

### 3.3 Android BLE Implementation
- [ ] Implement BleManager.android.kt with blessed-android
- [ ] Bridge to existing BluetoothService (incremental migration)
- [ ] Characteristic read/write with chunking (20-byte for Inmotion)
- [ ] MTU negotiation
- [ ] Integration tests

### 3.4 iOS BLE Implementation
- [ ] Implement BleManager.ios.kt with CoreBluetooth
- [ ] CBCentralManager wrapper
- [ ] CBPeripheral handling
- [ ] Characteristic discovery and notification

### 3.5 Keep-Alive Timer
- [ ] Platform-agnostic timer abstraction
- [ ] Decoder-specific intervals (see table below)
- [ ] Unit tests for timer behavior

**Keep-Alive Intervals by Decoder**:
| Decoder | Interval (ms) |
|---------|---------------|
| Gotway/Veteran | N/A (wheel-initiated) |
| Kingsong | N/A (wheel-initiated) |
| Inmotion V1 | 250 |
| Inmotion V2 | 25 |
| Ninebot | 125 |
| Ninebot Z | 25 |

---

## Phase 4: Integration

### 4.1 Android Integration
- [ ] WheelData adapter to use KMP WheelState
- [ ] BluetoothService delegates to core BleManager
- [ ] Maintain backward compatibility with existing UI

### 4.2 iOS App Scaffold
- [ ] Create iOS Xcode project
- [ ] Add core module as dependency
- [ ] Basic SwiftUI wheel display
- [ ] BLE scanning and connection

---

## Phase 5: Advanced Features

- [ ] Alarm handling
- [ ] Trip statistics
- [ ] PWM/power calculations
- [ ] Firmware update support (where applicable)

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        App Layer                             │
│  ┌─────────────────┐              ┌─────────────────┐       │
│  │  Android App    │              │    iOS App      │       │
│  │  (Kotlin/Java)  │              │   (Swift/UI)    │       │
│  └────────┬────────┘              └────────┬────────┘       │
└───────────┼────────────────────────────────┼────────────────┘
            │                                │
┌───────────┼────────────────────────────────┼────────────────┐
│           ▼                                ▼                 │
│  ┌─────────────────────────────────────────────────────┐    │
│  │              WheelConnectionManager                  │    │
│  │  - State management (StateFlow)                      │    │
│  │  - Decoder orchestration                             │    │
│  │  - Keep-alive timer                                  │    │
│  └─────────────────────────────────────────────────────┘    │
│                            │                                 │
│           ┌────────────────┼────────────────┐               │
│           ▼                ▼                ▼               │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │
│  │  Decoders   │  │  BleManager │  │ WheelState  │         │
│  │  (Gotway,   │  │  (expect/   │  │ (Domain)    │         │
│  │   KS, etc.) │  │   actual)   │  │             │         │
│  └─────────────┘  └──────┬──────┘  └─────────────┘         │
│                          │                                   │
│              KMP Core Module (commonMain)                    │
└──────────────────────────┼───────────────────────────────────┘
                           │
         ┌─────────────────┼─────────────────┐
         ▼                                   ▼
┌─────────────────┐                 ┌─────────────────┐
│  androidMain    │                 │    iosMain      │
│  BleManager     │                 │   BleManager    │
│  (blessed-      │                 │  (CoreBluetooth)│
│   android)      │                 │                 │
└─────────────────┘                 └─────────────────┘
```

---

## BLE Service UUIDs Reference

### Common (0000xxxx-0000-1000-8000-00805f9b34fb)
- `ffe0` - Primary service for most wheels
- `ffe1` - Read/Write characteristic (KS, Gotway, Ninebot)
- `ffe4` - Inmotion read
- `ffe5` - Inmotion write service
- `ffe9` - Inmotion write characteristic

### Nordic UART (Inmotion V2, Ninebot Z)
- Service: `6e400001-b5a3-f393-e0a9-e50e24dcca9e`
- Write:   `6e400002-b5a3-f393-e0a9-e50e24dcca9e`
- Read:    `6e400003-b5a3-f393-e0a9-e50e24dcca9e`

---

## Testing Strategy

1. **Unit Tests** (commonTest)
   - Decoder parsing with real packet data
   - UUID matching logic
   - State machine transitions
   - Timer behavior (mocked)

2. **Integration Tests** (androidTest)
   - BLE connection with mock peripheral
   - Full decode → state update flow

3. **Behavioral Equivalence**
   - Compare KMP decoder output with original adapter output
   - Use packet captures from real wheels

---

## Current Status

**Last Updated**: 2024-02-07

**Recent Commits**:
- `0966f78` Add Inmotion and Ninebot decoders to KMP core module
- `b21ba88` Add decoder verification tests for KMP core module
- `3c4f1ea` Add Kotlin Multiplatform core module for iOS support

**Next Steps**:
1. Create BleUuids.kt with all service/characteristic UUIDs
2. Implement WheelTypeDetector
3. Add unit tests for wheel type detection
4. Complete WheelConnectionManager with timer support
