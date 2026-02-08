package com.cooper.wheellog.core.ble

import com.cooper.wheellog.core.domain.WheelType

/**
 * Contains the BLE service and characteristic UUIDs needed to communicate with a wheel.
 * This information is determined during service discovery and wheel type detection.
 */
data class WheelConnectionInfo(
    val wheelType: WheelType,
    val readServiceUuid: String,
    val readCharacteristicUuid: String,
    val writeServiceUuid: String,
    val writeCharacteristicUuid: String,
    val descriptorUuid: String = BleUuids.CLIENT_CHARACTERISTIC_CONFIG
) {
    companion object {
        /**
         * Create connection info for a Kingsong wheel.
         */
        fun forKingsong(): WheelConnectionInfo = WheelConnectionInfo(
            wheelType = WheelType.KINGSONG,
            readServiceUuid = BleUuids.Kingsong.SERVICE,
            readCharacteristicUuid = BleUuids.Kingsong.READ_CHARACTERISTIC,
            writeServiceUuid = BleUuids.Kingsong.SERVICE,
            writeCharacteristicUuid = BleUuids.Kingsong.WRITE_CHARACTERISTIC
        )

        /**
         * Create connection info for a Gotway/Begode wheel.
         */
        fun forGotway(): WheelConnectionInfo = WheelConnectionInfo(
            wheelType = WheelType.GOTWAY,
            readServiceUuid = BleUuids.Gotway.SERVICE,
            readCharacteristicUuid = BleUuids.Gotway.READ_CHARACTERISTIC,
            writeServiceUuid = BleUuids.Gotway.SERVICE,
            writeCharacteristicUuid = BleUuids.Gotway.WRITE_CHARACTERISTIC
        )

        /**
         * Create connection info for a Veteran wheel.
         */
        fun forVeteran(): WheelConnectionInfo = WheelConnectionInfo(
            wheelType = WheelType.VETERAN,
            readServiceUuid = BleUuids.Gotway.SERVICE,
            readCharacteristicUuid = BleUuids.Gotway.READ_CHARACTERISTIC,
            writeServiceUuid = BleUuids.Gotway.SERVICE,
            writeCharacteristicUuid = BleUuids.Gotway.WRITE_CHARACTERISTIC
        )

        /**
         * Create connection info for an InMotion V1 wheel.
         */
        fun forInmotion(): WheelConnectionInfo = WheelConnectionInfo(
            wheelType = WheelType.INMOTION,
            readServiceUuid = BleUuids.Inmotion.READ_SERVICE,
            readCharacteristicUuid = BleUuids.Inmotion.READ_CHARACTERISTIC,
            writeServiceUuid = BleUuids.Inmotion.WRITE_SERVICE,
            writeCharacteristicUuid = BleUuids.Inmotion.WRITE_CHARACTERISTIC
        )

        /**
         * Create connection info for an InMotion V2 wheel.
         */
        fun forInmotionV2(): WheelConnectionInfo = WheelConnectionInfo(
            wheelType = WheelType.INMOTION_V2,
            readServiceUuid = BleUuids.InmotionV2.SERVICE,
            readCharacteristicUuid = BleUuids.InmotionV2.READ_CHARACTERISTIC,
            writeServiceUuid = BleUuids.InmotionV2.SERVICE,
            writeCharacteristicUuid = BleUuids.InmotionV2.WRITE_CHARACTERISTIC
        )

        /**
         * Create connection info for a Ninebot wheel.
         */
        fun forNinebot(): WheelConnectionInfo = WheelConnectionInfo(
            wheelType = WheelType.NINEBOT,
            readServiceUuid = BleUuids.Ninebot.SERVICE,
            readCharacteristicUuid = BleUuids.Ninebot.READ_CHARACTERISTIC,
            writeServiceUuid = BleUuids.Ninebot.SERVICE,
            writeCharacteristicUuid = BleUuids.Ninebot.WRITE_CHARACTERISTIC
        )

        /**
         * Create connection info for a Ninebot Z wheel.
         */
        fun forNinebotZ(): WheelConnectionInfo = WheelConnectionInfo(
            wheelType = WheelType.NINEBOT_Z,
            readServiceUuid = BleUuids.NinebotZ.SERVICE,
            readCharacteristicUuid = BleUuids.NinebotZ.READ_CHARACTERISTIC,
            writeServiceUuid = BleUuids.NinebotZ.SERVICE,
            writeCharacteristicUuid = BleUuids.NinebotZ.WRITE_CHARACTERISTIC
        )

        /**
         * Create connection info for a wheel type.
         */
        fun forType(wheelType: WheelType): WheelConnectionInfo? = when (wheelType) {
            WheelType.KINGSONG -> forKingsong()
            WheelType.GOTWAY -> forGotway()
            WheelType.GOTWAY_VIRTUAL -> forGotway()
            WheelType.VETERAN -> forVeteran()
            WheelType.INMOTION -> forInmotion()
            WheelType.INMOTION_V2 -> forInmotionV2()
            WheelType.NINEBOT -> forNinebot()
            WheelType.NINEBOT_Z -> forNinebotZ()
            WheelType.Unknown -> null
        }
    }
}
