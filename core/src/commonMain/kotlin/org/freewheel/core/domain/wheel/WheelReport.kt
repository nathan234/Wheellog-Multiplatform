package org.freewheel.core.domain.wheel

import org.freewheel.core.domain.WheelIdentity

/**
 * Pure helpers that turn a live [WheelIdentity] into a shareable wheel report —
 * either a pre-populated GitHub issue URL or plain text for the system share sheet.
 *
 * No I/O, no networking, no persistence. Submission is always user-initiated:
 * the platform layer hands the URL/text to the OS browser or share sheet, and
 * the user reviews and submits manually.
 *
 * Used to surface an "unrecognized wheel" report from the live dashboard, so the
 * catalog can be grown without a persistent encounter log.
 */
object WheelReport {

    const val GITHUB_OWNER = "nathan234"
    const val GITHUB_REPO = "FreeWheel"
    const val ISSUE_LABEL = "wheel-fingerprint"

    /**
     * Builds a `https://github.com/.../issues/new?...` URL with title/body/label
     * pre-populated. The user reviews and submits the issue in their browser.
     */
    fun buildGitHubIssueUrl(
        identity: WheelIdentity,
        observedMaxKmh: Double = 0.0,
        appVersion: String = "",
        appPlatform: String = "",
        serviceUuids: List<String> = emptyList(),
    ): String {
        val title = "Wheel fingerprint: ${titleFor(identity)}"
        val body = buildBody(identity, observedMaxKmh, appVersion, appPlatform, serviceUuids)
        return buildString {
            append("https://github.com/")
            append(GITHUB_OWNER).append('/').append(GITHUB_REPO)
            append("/issues/new?")
            append("title=").append(percentEncode(title))
            append("&labels=").append(percentEncode(ISSUE_LABEL))
            append("&body=").append(percentEncode(body))
        }
    }

    /** Plain-text payload for the system share sheet (Telegram, email, etc.). */
    fun buildShareText(
        identity: WheelIdentity,
        observedMaxKmh: Double = 0.0,
        appVersion: String = "",
        appPlatform: String = "",
        serviceUuids: List<String> = emptyList(),
    ): String =
        "Wheel fingerprint: ${titleFor(identity)}\n\n" +
            buildBody(identity, observedMaxKmh, appVersion, appPlatform, serviceUuids)

    private fun titleFor(identity: WheelIdentity): String {
        val parts = listOf(
            identity.wheelType.name,
            identity.btName.ifEmpty { identity.model }.ifEmpty { identity.version }
        )
        return parts.filter { it.isNotEmpty() }.joinToString(" ").ifEmpty { "unknown" }
    }

    private fun buildBody(
        identity: WheelIdentity,
        observedMaxKmh: Double,
        appVersion: String,
        appPlatform: String,
        serviceUuids: List<String>,
    ): String = buildString {
        appendField("Wheel type", identity.wheelType.name)
        appendField("Advertised BLE name", identity.btName)
        appendField("Decoded model", identity.model)
        appendField("Firmware version", identity.version)
        appendField("Brand override", identity.brand)
        appendField("Decoder name", identity.name)
        appendField("Mode", identity.modeStr)
        appendField("Serial number", identity.serialNumber)
        appendField("Service UUIDs", serviceUuids.joinToString(", "))
        appendField("Observed peak speed (km/h)", observedMaxKmh.takeIf { it > 0.0 }?.toString().orEmpty())
        appendField("App version", appVersion)
        appendField("App platform", appPlatform)
    }

    private fun StringBuilder.appendField(label: String, value: String) {
        if (value.isEmpty()) return
        append("**").append(label).append("**: ").append(value).append('\n')
    }

    /**
     * RFC 3986 percent-encoding for URL query strings. Unreserved characters
     * (A-Z a-z 0-9 - _ . ~) pass through; everything else is %XX-encoded byte-wise (UTF-8).
     */
    internal fun percentEncode(input: String): String {
        val out = StringBuilder(input.length)
        for (byte in input.encodeToByteArray()) {
            val b = byte.toInt() and 0xFF
            val c = b.toChar()
            val safe = (c in 'A'..'Z') || (c in 'a'..'z') || (c in '0'..'9') ||
                c == '-' || c == '_' || c == '.' || c == '~'
            if (safe) {
                out.append(c)
            } else {
                out.append('%')
                out.append(HEX[b ushr 4])
                out.append(HEX[b and 0x0F])
            }
        }
        return out.toString()
    }

    private val HEX = "0123456789ABCDEF".toCharArray()
}
