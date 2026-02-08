package com.cooper.wheellog.core.protocol

import com.cooper.wheellog.core.domain.WheelType

/**
 * Default factory for creating wheel protocol decoders.
 */
class DefaultWheelDecoderFactory : WheelDecoderFactory {

    override fun createDecoder(wheelType: WheelType): WheelDecoder? {
        return when (wheelType) {
            WheelType.GOTWAY, WheelType.GOTWAY_VIRTUAL -> GotwayDecoder()
            WheelType.VETERAN -> VeteranDecoder()
            WheelType.KINGSONG -> KingsongDecoder()
            // TODO: Add other decoders as they are migrated
            // WheelType.INMOTION -> InmotionDecoder()
            // WheelType.INMOTION_V2 -> InmotionV2Decoder()
            // WheelType.NINEBOT -> NinebotDecoder()
            // WheelType.NINEBOT_Z -> NinebotZDecoder()
            else -> null
        }
    }

    override fun supportedTypes(): List<WheelType> {
        return listOf(
            WheelType.GOTWAY,
            WheelType.GOTWAY_VIRTUAL,
            WheelType.VETERAN,
            WheelType.KINGSONG
            // Add more as decoders are implemented
        )
    }
}

/**
 * Extension function to get a decoder for a wheel type with automatic caching.
 */
class CachingWheelDecoderFactory(
    private val delegate: WheelDecoderFactory = DefaultWheelDecoderFactory()
) : WheelDecoderFactory {

    private val cache = mutableMapOf<WheelType, WheelDecoder>()

    override fun createDecoder(wheelType: WheelType): WheelDecoder? {
        return cache.getOrPut(wheelType) {
            delegate.createDecoder(wheelType) ?: return null
        }
    }

    override fun supportedTypes(): List<WheelType> = delegate.supportedTypes()

    /**
     * Clear the cache and reset all decoders.
     */
    fun clearCache() {
        cache.values.forEach { it.reset() }
        cache.clear()
    }

    /**
     * Get a cached decoder without creating a new one.
     */
    fun getDecoderOrNull(wheelType: WheelType): WheelDecoder? = cache[wheelType]
}
