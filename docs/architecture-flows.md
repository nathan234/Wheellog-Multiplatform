# Architecture: User Action → Code Path

A hop-by-hop reference for how a user action becomes a BLE write, a state update, or a UI change. Complements [CLAUDE.md](../CLAUDE.md) (which covers *where things live*) — this doc covers *how requests flow*.

All line numbers are Android + KMP core; iOS equivalents are summarized in a single section at the bottom. Paths are relative to the repo root.

## Layer overview

```
UI (Compose)              freewheel/.../compose/screens/*, components/*
  ↓ method call
WheelViewModel            freewheel/.../compose/WheelViewModel.kt          (binds service, merges state flows)
  ↓ delegated call
WheelService (bound)      freewheel/.../compose/service/WheelService.kt    (Android foreground service)
  ↓ owns
WheelConnectionManager    core/.../service/WheelConnectionManager.kt       (event-loop orchestrator)
  ↓ event channel → reducer →
WheelDecoder              core/.../protocol/*Decoder.kt                    (stateless; returns DecodedData)
  ↓                       ↓ buildCommand()
BleManager (expect/actual) core/.../service/BleManager.*.kt                 (Blessed on Android, CoreBluetooth on iOS)
  ↓
BLE peripheral (wheel)
```

Granular StateFlows emitted by `WheelConnectionManager`: `telemetryState`, `identityState`, `bmsState`, `settingsState`, `capabilities`, `connectionState`, `eventLogEntries`. `WheelViewModel.stateCollectionJob` collects all of them at `WheelViewModel.kt:467`.

## Key types

| Type | Purpose | Defined in |
|---|---|---|
| `DecoderState` | Accumulated per-connection state (`telemetry`, `identity`, `bms`, `settings`) | `core/.../protocol/DecoderState.kt` |
| `DecodeResult` | Sealed: `Buffering`, `Success(DecodedData)`, `Failure` | `core/.../protocol/DecodeResult.kt` |
| `DecodedData` | Nullable sub-states + `commands: List<ByteArray>` for protocol responses | `core/.../protocol/DecodedData.kt` |
| `WheelCommand` | Sealed command set (Beep, SetPedalsMode, ToggleLight, …) | `core/.../protocol/WheelCommand.kt` |
| `ConnectionState` | `Idle`, `Scanning`, `Connecting`, `Connected`, `ConnectionLost`, … | `core/.../service/ConnectionState.kt` |

## Flow 1 — Startup + BLE scan

1. `ComposeActivity.onCreate()` — `freewheel/.../compose/ComposeActivity.kt:82`
2. Binds foreground service — `ComposeActivity.kt:167 bindWheelService()`
3. On service connect → `ComposeActivity.kt:53 onServiceConnected` → `viewModel.attachService(...)` — `ComposeActivity.kt:55`
4. `WheelViewModel.attachService()` wires flows, creates `AutoConnectManager`, starts collectors — `WheelViewModel.kt:425`
5. `WheelViewModel.startupScan()` — `WheelViewModel.kt:750`
6. → `BleManager.startScan()` — `core/.../service/BleManager.android.kt:474`
7. Scan results arrive via `onDiscoveredPeripheral` — `BleManager.android.kt:107`, forwarded into `_discoveredDevices` flow observed by the Compose device picker.

## Flow 2 — Connect to a wheel

1. User taps a device → `WheelViewModel` calls `connectionManager.connect(address)` (also goes through `AutoConnectManager` for the auto-reconnect path).
2. `WheelConnectionManager.connect(address, wheelType)` — `core/.../service/WheelConnectionManager.kt:210`
3. Sends `ConnectRequested` into the internal event channel; reducer transitions to `Connecting` and calls `BleManager.connect()`.
4. OS services discovered → `BleManager` fires callback → `WheelConnectionManager.onServicesDiscovered(services, deviceName)` — `WheelConnectionManager.kt:283`
5. Wheel type detection: `WheelTypeDetector` inspects services/name; `DefaultWheelDecoderFactory.createDecoder(type)` — `core/.../protocol/DefaultWheelDecoderFactory.kt` — returns the decoder instance.
6. Reducer stores `decoder` + fresh `DecoderState`, transitions to `Connected`.

## Flow 3 — Telemetry frame received

1. BLE notification → `BleManager.onCharacteristicUpdate` — `BleManager.android.kt:295`
2. Forwarded to `WheelConnectionManager.onDataReceived(data)` — `WheelConnectionManager.kt:246`
3. `events.trySend(DataReceived(bytes))` → reducer calls `decoder.decode(bytes, state, config)`.
4. On `DecodeResult.Success`: reducer folds the non-null sub-states from `DecodedData` into `DecoderState` and emits **only the changed sub-states** on the corresponding StateFlows (`telemetryState`, `identityState`, `bmsState`, `settingsState`).
5. Any `commands: List<ByteArray>` on the result (e.g. Kingsong's mandatory `0x98` reply to `0xA4`) are written back via `BleManager`.
6. `WheelViewModel.stateCollectionJob` mirrors each flow into `_realTelemetry` / `_realIdentity` / … — `WheelViewModel.kt:467`
7. Compose screens collect `WheelViewModel.telemetryState` (which merges real + demo) and recompose.

> Each sub-state flow emits independently — a frame that only carries settings does not re-emit telemetry. This is why the ViewModel separates `stateCollectionJob` into four launches.

## Flow 4 — Send a command (beep / settings change)

### Beep
1. User taps beep button → `WheelViewModel.wheelBeep()` — `WheelViewModel.kt:807`
2. → `connectionManager.wheelBeep()` — `WheelConnectionManager.kt:309` (thin wrapper)
3. → `sendCommand(WheelCommand.Beep)` — `WheelConnectionManager.kt:238`
4. Pushes a `CommandRequested(cmd)` event; reducer calls `dispatchCommand(cmd, decoder, state)` — `WheelConnectionManager.kt:1094`
5. `decoder.buildCommand(cmd, state, config)` returns one or more `ByteArray` payloads.
6. Each payload → `BleManager.writeCharacteristic(...)`.

### Settings change (e.g. `setPedalsMode`)
- Same path: `WheelViewModel.setPedalsMode(mode)` → `WheelConnectionManager.setPedalsMode(mode)` — `WheelConnectionManager.kt:311` → `sendCommand(WheelCommand.SetPedalsMode(mode))` → `dispatchCommand`.
- The *pending* value is not tracked by the VM — it's read back from the wheel's next settings frame. Default `-1` in `WheelState` means "never read from wheel yet".

## Flow 5 — Alarm fires

1. `WheelViewModel.startAlarmMonitoring()` — `WheelViewModel.kt:1593` — launches a collector on `activeTelemetryOrNull`.
2. On each emission it calls `alarmChecker.check(telemetry, config, now)` — `core/.../alarm/AlarmChecker.kt:75`
3. `AlarmChecker` returns an `AlarmResult` listing currently-triggered alarm types (speed, current, temperature, battery, PWM, pre-warning, wheel alarm).
4. ViewModel updates `_activeAlarms` (for the UI) and calls `alarmHandler.handleAlarmResult(...)` which maps alarm types to vibration patterns and tone generator calls.

`AlarmChecker` is pure — it receives `TelemetryState` + `AlarmConfig` and returns a value. No side effects, no flows, easy to unit-test.

## Flow 6 — Start / stop ride log

### Start
1. `WheelViewModel.toggleLogging()` — `WheelViewModel.kt:1077` → `startLogging()` — `WheelViewModel.kt:1085`
2. Creates file under `Android/data/<pkg>/files/rides/`, calls `rideLogger.start(path, withGps, now)` — `core/.../logging/RideLogger.kt:102`
3. `startLogSampling()` launches a coroutine — `WheelViewModel.kt:1140` — that collects `activeTelemetryOrNull` and calls `rideLogger.writeSample(telemetry, modeStr, gps, now)` — `RideLogger.kt:137` (throttled internally to 1 Hz).

### Stop
1. `toggleLogging()` → `stopLogging()` — `WheelViewModel.kt:1107`
2. Cancels `logSamplingJob`, calls `rideLogger.stop(now, lastTotalDistance)` → returns `RideMetadata`.
3. `runBlocking(Dispatchers.IO) { tripRepository.insertNewData(...) }` — must be blocking because callers (disconnect, service shutdown, `onCleared`) need the Room INSERT to land before the scope cancels.

## Flow 7 — Disconnect + auto-reconnect

Two distinct reconnect mechanisms — do not confuse them:

1. **OS-level auto-reconnect** (mid-ride). On unexpected disconnect:
   - `BleManager.onDisconnectedPeripheral` — `BleManager.android.kt:143`
   - Calls Blessed's `central.autoConnectPeripheral(...)` — the OS handles reconnection when the wheel comes back in range.
   - On success, `onServicesDiscovered` fires again and the reducer resumes in `Connected` state.
   - A paused ride is *resumed* (not restarted): `WheelViewModel.kt:498-506` detects the pause flag and calls `rideLogger.resume()`.

2. **App-level `AutoConnectManager`** (app-start / user-initiated).
   - Created in `WheelViewModel.attachService` — `WheelViewModel.kt:445`
   - Watches `connectionState`, calls `cm.connect(address)` when state becomes `Idle`/`Disconnected` and the address is in the profile whitelist.
   - Must NOT run during mid-ride disconnects — it would cancel the OS-level auto-reconnect. See comment at `WheelViewModel.kt:509-513`.

The VM also pauses the ride (not stops it) on `ConnectionLost` so the OS reconnect path can seamlessly resume.

## iOS equivalents

iOS mirrors Android with a thinner Swift bridge — the KMP `core` handles the state/decoder logic identically; only the platform adapters differ.

- **Orchestrator**: `iosApp/FreeWheel/Bridge/WheelManager.swift` — owns a `WheelConnectionManagerHelper` (from `core/.../iosMain/.../service/WheelConnectionManagerHelper.kt`), which exposes the Kotlin manager via Swift-friendly wrappers and runs the sub-state flow collectors as Swift callbacks (no polling).
- **BLE adapter**: `core/.../iosMain/.../service/BleManager.ios.kt` (CoreBluetooth).
- **Commands**: Swift calls `WheelConnectionManagerHelper.wheelBeep()` etc., which delegate to the same KMP `WheelConnectionManager.sendCommand` path.
- **Alarms / ride log / background / location**: dedicated files in `iosApp/FreeWheel/Bridge/` (`AlarmManager.swift`, `RideLogger.swift`, `BackgroundManager.swift`, `LocationManager.swift`) — each is analogous to the Android piece owned by `WheelViewModel`.
- **No `WheelService` equivalent**: iOS uses `BackgroundManager` for background BLE instead of a foreground-service pattern.

## Related docs

- [CLAUDE.md](../CLAUDE.md) — directory map, key file index, KMP conventions
- [docs/claude-reference.md](claude-reference.md) — command matrix, decoder config fields, lifecycle details
- [docs/decoder-parity.md](decoder-parity.md) — per-decoder parity checklist vs legacy adapters
- [KMP_MIGRATION_PLAN.md](../KMP_MIGRATION_PLAN.md) — migration phases and BLE implementation status
