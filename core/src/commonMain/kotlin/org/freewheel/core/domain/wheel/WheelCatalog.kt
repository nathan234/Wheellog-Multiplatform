package org.freewheel.core.domain.wheel

import org.freewheel.core.domain.WheelIdentity
import org.freewheel.core.domain.WheelType

/**
 * Static catalog of known wheels with their stock top speeds, used to scale the
 * speedometer red zone per-wheel.
 *
 * Resolution order for the gauge maximum (in km/h):
 *   1. user-set override (per-MAC)
 *   2. catalog match against wheel identity / advertised name
 *   3. auto-estimate from observed peak speed (× [AUTO_ESTIMATE_HEADROOM])
 *   4. wheel-type fallback default
 *   5. [ABSOLUTE_FALLBACK_KMH]
 *
 * Identity strings are scanned in priority order (version, model, brand, name,
 * btName) with longest-token-wins to disambiguate families like "COMMANDER" vs
 * "COMMANDER MAX". The advertised BLE name is already captured into
 * [WheelIdentity.btName] by [org.freewheel.core.service.WheelConnectionManager]
 * during service discovery — no separate parameter is required.
 */
object WheelCatalog {

    /** Per-model entries. Populate as wheel speeds are confirmed. */
    val entries: List<WheelCatalogEntry> = emptyList()

    /** Wheel-type fallbacks when no per-model entry matches. */
    val typeDefaults: Map<WheelType, Double> = emptyMap()

    /** Last-resort fallback when neither catalog match nor type default exists. */
    const val ABSOLUTE_FALLBACK_KMH: Double = 50.0

    /**
     * Multiplier applied to observed peak speed to derive an auto-estimated gauge max.
     * 1.20 places the observed peak around the start of the red zone (since
     * [org.freewheel.core.domain.dashboard.DashboardMetric.SPEED] uses redAbove = 0.75:
     * observed / 1.20 ≈ 0.83 of gauge, well into red).
     */
    const val AUTO_ESTIMATE_HEADROOM: Double = 1.20

    fun match(
        wheelType: WheelType,
        identity: WheelIdentity = WheelIdentity(),
    ): WheelCatalogEntry? = matchIn(entries, wheelType, identity)

    fun resolveTopSpeedKmh(
        userOverrideKmh: Double? = null,
        wheelType: WheelType = WheelType.Unknown,
        identity: WheelIdentity = WheelIdentity(),
        observedMaxKmh: Double = 0.0,
    ): Double {
        userOverrideKmh?.takeIf { it > 0.0 }?.let { return it }
        match(wheelType, identity)?.let { return it.topSpeedKmh }
        if (observedMaxKmh > 0.0) return observedMaxKmh * AUTO_ESTIMATE_HEADROOM
        typeDefaults[wheelType]?.takeIf { it > 0.0 }?.let { return it }
        return ABSOLUTE_FALLBACK_KMH
    }
}

/**
 * Pure matching helper used by [WheelCatalog.match] and tests.
 * Identity fields are tried in priority order: version → model → brand → name →
 * btName. Longest-token match wins among entries filtered by [wheelType].
 */
internal fun matchIn(
    entries: List<WheelCatalogEntry>,
    wheelType: WheelType,
    identity: WheelIdentity,
): WheelCatalogEntry? {
    val candidates = listOfNotNull(
        identity.version.takeIf { it.isNotEmpty() },
        identity.model.takeIf { it.isNotEmpty() },
        identity.brand.takeIf { it.isNotEmpty() },
        identity.name.takeIf { it.isNotEmpty() },
        identity.btName.takeIf { it.isNotEmpty() },
    ).map { it.uppercase() }
    if (candidates.isEmpty()) return null

    var best: WheelCatalogEntry? = null
    var bestLen = 0
    for (entry in entries) {
        if (entry.wheelType != wheelType) continue
        for (token in entry.nameTokens) {
            val upToken = token.uppercase()
            if (upToken.length > bestLen && candidates.any { it.contains(upToken) }) {
                best = entry
                bestLen = upToken.length
            }
        }
    }
    return best
}
