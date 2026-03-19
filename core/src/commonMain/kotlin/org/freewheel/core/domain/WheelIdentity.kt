package org.freewheel.core.domain

/**
 * Wheel identity information that is set once per connection.
 * Set once per connection — avoids triggering UI updates on every telemetry frame.
 */
data class WheelIdentity(
    val wheelType: WheelType = WheelType.Unknown,
    val name: String = "",
    val model: String = "",
    val modeStr: String = "",
    val version: String = "",
    val serialNumber: String = "",
    val btName: String = ""
) {
    companion object {
        /** Swift-callable factory — Kotlin default-parameter constructors aren't visible from ObjC/Swift. */
        fun empty() = WheelIdentity()
    }

    val displayName: String get() {
        val brand = wheelType.displayName
        val label = model.ifEmpty { name }.ifEmpty { btName }
        if (label.isEmpty()) return brand.ifEmpty { "Dashboard" }
        if (brand.isEmpty() || label.startsWith(brand, ignoreCase = true)) return label
        return "$brand $label"
    }
}
