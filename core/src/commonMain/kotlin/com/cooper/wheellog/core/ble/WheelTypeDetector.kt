package com.cooper.wheellog.core.ble

import com.cooper.wheellog.core.domain.WheelType

/**
 * Detects wheel type based on discovered BLE services and characteristics.
 *
 * The detection logic is based on unique service/characteristic combinations
 * that distinguish each manufacturer's protocol:
 *
 * - **Inmotion V1**: Has ffe0/ffe4 (read) AND ffe5/ffe9 (write) - separate services
 * - **Inmotion V2**: Has Nordic UART (6e400001) AND ffe0/ffe4 characteristics
 * - **Ninebot Z**: Has Nordic UART (6e400001) only (no ffe0)
 * - **Kingsong**: Has fff0 service OR ffe0/ffe1 with specific characteristics
 * - **Gotway/Veteran**: Has ffe0/ffe1 only (simplest profile)
 * - **Ninebot**: Has ffe0/ffe1 (similar to Gotway, differentiated by name)
 *
 * Note: Some wheel types share similar UUIDs and require additional
 * heuristics like device name patterns for accurate detection.
 */
class WheelTypeDetector {

    /**
     * Result of wheel type detection.
     */
    sealed class DetectionResult {
        /**
         * Successfully detected wheel type.
         */
        data class Detected(
            val wheelType: WheelType,
            val readServiceUuid: String,
            val readCharacteristicUuid: String,
            val writeServiceUuid: String,
            val writeCharacteristicUuid: String,
            val confidence: Confidence = Confidence.HIGH
        ) : DetectionResult()

        /**
         * Could not determine wheel type.
         */
        data class Unknown(val reason: String) : DetectionResult()

        /**
         * Multiple possible wheel types detected (ambiguous).
         */
        data class Ambiguous(
            val possibleTypes: List<WheelType>,
            val reason: String
        ) : DetectionResult()
    }

    /**
     * Confidence level of detection.
     */
    enum class Confidence {
        HIGH,   // Unique service combination, very reliable
        MEDIUM, // Common services but distinguishing characteristics present
        LOW     // May need device name or data to confirm
    }

    /**
     * Detect wheel type from discovered services.
     *
     * @param services The discovered BLE services
     * @param deviceName Optional device name for additional heuristics
     * @return Detection result with wheel type and connection info
     */
    fun detect(services: DiscoveredServices, deviceName: String? = null): DetectionResult {
        // Check for Nordic UART service (used by Inmotion V2 and Ninebot Z)
        val hasNordicUart = services.hasService(BleUuids.InmotionV2.SERVICE)

        // Check for standard wheel service (ffe0)
        val hasStandardService = services.hasService(BleUuids.Gotway.SERVICE)

        // Check for Inmotion-specific write service (ffe5)
        val hasInmotionWriteService = services.hasService(BleUuids.Inmotion.WRITE_SERVICE)

        // Check for KingSong-specific service (fff0)
        val hasKingsongService = services.hasService("0000fff0-0000-1000-8000-00805f9b34fb")

        // Check for specific characteristics
        val hasInmotionReadChar = services.hasCharacteristic(
            BleUuids.Inmotion.READ_SERVICE,
            BleUuids.Inmotion.READ_CHARACTERISTIC
        )
        val hasInmotionWriteChar = services.hasCharacteristic(
            BleUuids.Inmotion.WRITE_SERVICE,
            BleUuids.Inmotion.WRITE_CHARACTERISTIC
        )

        return when {
            // Inmotion V2: Has Nordic UART AND standard ffe0 with ffe4 characteristic
            hasNordicUart && hasInmotionReadChar -> {
                DetectionResult.Detected(
                    wheelType = WheelType.INMOTION_V2,
                    readServiceUuid = BleUuids.InmotionV2.SERVICE,
                    readCharacteristicUuid = BleUuids.InmotionV2.READ_CHARACTERISTIC,
                    writeServiceUuid = BleUuids.InmotionV2.SERVICE,
                    writeCharacteristicUuid = BleUuids.InmotionV2.WRITE_CHARACTERISTIC,
                    confidence = Confidence.HIGH
                )
            }

            // Ninebot Z: Has Nordic UART only (no ffe0 with ffe4)
            hasNordicUart && !hasInmotionReadChar -> {
                DetectionResult.Detected(
                    wheelType = WheelType.NINEBOT_Z,
                    readServiceUuid = BleUuids.NinebotZ.SERVICE,
                    readCharacteristicUuid = BleUuids.NinebotZ.READ_CHARACTERISTIC,
                    writeServiceUuid = BleUuids.NinebotZ.SERVICE,
                    writeCharacteristicUuid = BleUuids.NinebotZ.WRITE_CHARACTERISTIC,
                    confidence = Confidence.HIGH
                )
            }

            // Inmotion V1: Has separate read (ffe0/ffe4) and write (ffe5/ffe9) services
            hasInmotionWriteService && hasInmotionWriteChar && hasInmotionReadChar -> {
                DetectionResult.Detected(
                    wheelType = WheelType.INMOTION,
                    readServiceUuid = BleUuids.Inmotion.READ_SERVICE,
                    readCharacteristicUuid = BleUuids.Inmotion.READ_CHARACTERISTIC,
                    writeServiceUuid = BleUuids.Inmotion.WRITE_SERVICE,
                    writeCharacteristicUuid = BleUuids.Inmotion.WRITE_CHARACTERISTIC,
                    confidence = Confidence.HIGH
                )
            }

            // KingSong: Has fff0 service or specific name pattern
            hasKingsongService -> {
                DetectionResult.Detected(
                    wheelType = WheelType.KINGSONG,
                    readServiceUuid = BleUuids.Kingsong.SERVICE,
                    readCharacteristicUuid = BleUuids.Kingsong.READ_CHARACTERISTIC,
                    writeServiceUuid = BleUuids.Kingsong.SERVICE,
                    writeCharacteristicUuid = BleUuids.Kingsong.WRITE_CHARACTERISTIC,
                    confidence = Confidence.HIGH
                )
            }

            // Standard service (ffe0/ffe1) - Could be Gotway, Veteran, Ninebot, or KingSong
            hasStandardService -> {
                detectFromNameOrAmbiguous(deviceName, services)
            }

            // No recognized services
            else -> {
                DetectionResult.Unknown(
                    "No recognized wheel services found. Services: ${services.serviceUuids()}"
                )
            }
        }
    }

    /**
     * Try to detect wheel type from device name when services are ambiguous.
     */
    private fun detectFromNameOrAmbiguous(
        deviceName: String?,
        services: DiscoveredServices
    ): DetectionResult {
        val name = deviceName?.uppercase() ?: ""

        return when {
            // Veteran patterns
            name.contains("VETERAN") ||
            name.contains("SHERMAN") ||
            name.contains("LYNX") ||
            name.contains("PATTON") ||
            name.contains("ABRAMS") -> {
                DetectionResult.Detected(
                    wheelType = WheelType.VETERAN,
                    readServiceUuid = BleUuids.Gotway.SERVICE,
                    readCharacteristicUuid = BleUuids.Gotway.READ_CHARACTERISTIC,
                    writeServiceUuid = BleUuids.Gotway.SERVICE,
                    writeCharacteristicUuid = BleUuids.Gotway.WRITE_CHARACTERISTIC,
                    confidence = Confidence.MEDIUM
                )
            }

            // Gotway/Begode patterns
            name.contains("GW") ||
            name.contains("GOTWAY") ||
            name.contains("BEGODE") ||
            name.contains("MCMASTER") ||
            name.contains("NIKOLA") ||
            name.contains("MONSTER") ||
            name.contains("MSP") ||
            name.contains("RSHS") ||
            name.contains("EX.N") ||
            name.contains("HERO") ||
            name.contains("MASTER") -> {
                DetectionResult.Detected(
                    wheelType = WheelType.GOTWAY,
                    readServiceUuid = BleUuids.Gotway.SERVICE,
                    readCharacteristicUuid = BleUuids.Gotway.READ_CHARACTERISTIC,
                    writeServiceUuid = BleUuids.Gotway.SERVICE,
                    writeCharacteristicUuid = BleUuids.Gotway.WRITE_CHARACTERISTIC,
                    confidence = Confidence.MEDIUM
                )
            }

            // KingSong patterns
            name.contains("KS-") ||
            name.contains("KINGSONG") ||
            name.startsWith("KS") -> {
                DetectionResult.Detected(
                    wheelType = WheelType.KINGSONG,
                    readServiceUuid = BleUuids.Kingsong.SERVICE,
                    readCharacteristicUuid = BleUuids.Kingsong.READ_CHARACTERISTIC,
                    writeServiceUuid = BleUuids.Kingsong.SERVICE,
                    writeCharacteristicUuid = BleUuids.Kingsong.WRITE_CHARACTERISTIC,
                    confidence = Confidence.MEDIUM
                )
            }

            // Ninebot patterns
            name.contains("NINEBOT") ||
            name.contains("NB-") -> {
                DetectionResult.Detected(
                    wheelType = WheelType.NINEBOT,
                    readServiceUuid = BleUuids.Ninebot.SERVICE,
                    readCharacteristicUuid = BleUuids.Ninebot.READ_CHARACTERISTIC,
                    writeServiceUuid = BleUuids.Ninebot.SERVICE,
                    writeCharacteristicUuid = BleUuids.Ninebot.WRITE_CHARACTERISTIC,
                    confidence = Confidence.MEDIUM
                )
            }

            // Ambiguous - return possible types for auto-detect
            else -> {
                DetectionResult.Ambiguous(
                    possibleTypes = listOf(
                        WheelType.GOTWAY,
                        WheelType.KINGSONG,
                        WheelType.NINEBOT
                    ),
                    reason = "Standard BLE profile detected. Device name '$deviceName' " +
                            "does not match known patterns. Will use auto-detection."
                )
            }
        }
    }

    /**
     * Get the UUIDs for a known wheel type.
     * Used when wheel type is pre-configured or restored from settings.
     */
    fun getUuidsForType(wheelType: WheelType): WheelConnectionInfo? {
        return when (wheelType) {
            WheelType.KINGSONG -> WheelConnectionInfo(
                wheelType = WheelType.KINGSONG,
                readServiceUuid = BleUuids.Kingsong.SERVICE,
                readCharacteristicUuid = BleUuids.Kingsong.READ_CHARACTERISTIC,
                writeServiceUuid = BleUuids.Kingsong.SERVICE,
                writeCharacteristicUuid = BleUuids.Kingsong.WRITE_CHARACTERISTIC
            )
            WheelType.GOTWAY, WheelType.GOTWAY_VIRTUAL -> WheelConnectionInfo(
                wheelType = wheelType,
                readServiceUuid = BleUuids.Gotway.SERVICE,
                readCharacteristicUuid = BleUuids.Gotway.READ_CHARACTERISTIC,
                writeServiceUuid = BleUuids.Gotway.SERVICE,
                writeCharacteristicUuid = BleUuids.Gotway.WRITE_CHARACTERISTIC
            )
            WheelType.VETERAN -> WheelConnectionInfo(
                wheelType = WheelType.VETERAN,
                readServiceUuid = BleUuids.Gotway.SERVICE,
                readCharacteristicUuid = BleUuids.Gotway.READ_CHARACTERISTIC,
                writeServiceUuid = BleUuids.Gotway.SERVICE,
                writeCharacteristicUuid = BleUuids.Gotway.WRITE_CHARACTERISTIC
            )
            WheelType.INMOTION -> WheelConnectionInfo(
                wheelType = WheelType.INMOTION,
                readServiceUuid = BleUuids.Inmotion.READ_SERVICE,
                readCharacteristicUuid = BleUuids.Inmotion.READ_CHARACTERISTIC,
                writeServiceUuid = BleUuids.Inmotion.WRITE_SERVICE,
                writeCharacteristicUuid = BleUuids.Inmotion.WRITE_CHARACTERISTIC
            )
            WheelType.INMOTION_V2 -> WheelConnectionInfo(
                wheelType = WheelType.INMOTION_V2,
                readServiceUuid = BleUuids.InmotionV2.SERVICE,
                readCharacteristicUuid = BleUuids.InmotionV2.READ_CHARACTERISTIC,
                writeServiceUuid = BleUuids.InmotionV2.SERVICE,
                writeCharacteristicUuid = BleUuids.InmotionV2.WRITE_CHARACTERISTIC
            )
            WheelType.NINEBOT -> WheelConnectionInfo(
                wheelType = WheelType.NINEBOT,
                readServiceUuid = BleUuids.Ninebot.SERVICE,
                readCharacteristicUuid = BleUuids.Ninebot.READ_CHARACTERISTIC,
                writeServiceUuid = BleUuids.Ninebot.SERVICE,
                writeCharacteristicUuid = BleUuids.Ninebot.WRITE_CHARACTERISTIC
            )
            WheelType.NINEBOT_Z -> WheelConnectionInfo(
                wheelType = WheelType.NINEBOT_Z,
                readServiceUuid = BleUuids.NinebotZ.SERVICE,
                readCharacteristicUuid = BleUuids.NinebotZ.READ_CHARACTERISTIC,
                writeServiceUuid = BleUuids.NinebotZ.SERVICE,
                writeCharacteristicUuid = BleUuids.NinebotZ.WRITE_CHARACTERISTIC
            )
            WheelType.Unknown -> null
        }
    }
}

/**
 * Connection information for a detected wheel.
 */
data class WheelConnectionInfo(
    val wheelType: WheelType,
    val readServiceUuid: String,
    val readCharacteristicUuid: String,
    val writeServiceUuid: String,
    val writeCharacteristicUuid: String
)
