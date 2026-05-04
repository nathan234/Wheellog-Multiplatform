package org.freewheel.core.domain.wheel

import org.freewheel.core.domain.WheelIdentity
import org.freewheel.core.domain.WheelType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WheelReportTest {

    @Test
    fun urlPointsToCorrectRepo() {
        val url = WheelReport.buildGitHubIssueUrl(
            WheelIdentity(wheelType = WheelType.GOTWAY, btName = "X")
        )
        assertTrue(
            url.startsWith("https://github.com/nathan234/FreeWheel/issues/new?"),
            "url=$url"
        )
    }

    @Test
    fun urlIncludesTitleLabelAndBody() {
        val url = WheelReport.buildGitHubIssueUrl(
            WheelIdentity(wheelType = WheelType.GOTWAY, btName = "MASTER")
        )
        assertTrue(url.contains("title="))
        assertTrue(url.contains("labels=wheel-fingerprint"))
        assertTrue(url.contains("body="))
    }

    @Test
    fun titleIncludesWheelTypeAndBtName() {
        val url = WheelReport.buildGitHubIssueUrl(
            WheelIdentity(wheelType = WheelType.GOTWAY, btName = "MASTER-1234")
        )
        assertTrue(url.contains("title=Wheel%20fingerprint%3A%20GOTWAY%20MASTER-1234"))
    }

    @Test
    fun bodyContainsKeyIdentityFields() {
        val text = WheelReport.buildShareText(
            WheelIdentity(
                wheelType = WheelType.GOTWAY,
                btName = "EB-COMMANDER",
                version = "MasterPro v1.5.0",
                brand = "Extreme Bull",
            ),
            observedMaxKmh = 65.5,
        )
        assertTrue(text.contains("EB-COMMANDER"))
        assertTrue(text.contains("MasterPro v1.5.0"))
        assertTrue(text.contains("Extreme Bull"))
        assertTrue(text.contains("65.5"))
    }

    @Test
    fun bodySkipsEmptyFields() {
        val text = WheelReport.buildShareText(
            WheelIdentity(wheelType = WheelType.GOTWAY, btName = "X")
        )
        assertFalse(text.contains("Firmware version"))
        assertFalse(text.contains("Serial number"))
    }

    @Test
    fun observedMaxKmhOnlyIncludedWhenPositive() {
        val zero = WheelReport.buildShareText(
            WheelIdentity(wheelType = WheelType.GOTWAY, btName = "X")
        )
        assertFalse(zero.contains("Observed peak"))

        val nonZero = WheelReport.buildShareText(
            WheelIdentity(wheelType = WheelType.GOTWAY, btName = "X"),
            observedMaxKmh = 42.0,
        )
        assertTrue(nonZero.contains("Observed peak"))
    }

    @Test
    fun percentEncodeUnreservedCharsPassThrough() {
        assertEquals("Hello-_.~AZaz09", WheelReport.percentEncode("Hello-_.~AZaz09"))
    }

    @Test
    fun percentEncodeSpacesAndAmpersand() {
        assertEquals("a%20b%26c", WheelReport.percentEncode("a b&c"))
    }

    @Test
    fun percentEncodeNewlines() {
        assertEquals("a%0Ab", WheelReport.percentEncode("a\nb"))
    }

    @Test
    fun percentEncodeUtf8Multibyte() {
        // U+00E9 (é) in UTF-8 is 0xC3 0xA9
        assertEquals("%C3%A9", WheelReport.percentEncode("é"))
    }

    @Test
    fun shareTextBeginsWithFingerprintHeader() {
        val text = WheelReport.buildShareText(
            WheelIdentity(wheelType = WheelType.GOTWAY, btName = "X")
        )
        assertTrue(text.startsWith("Wheel fingerprint: "))
    }

    @Test
    fun appVersionAndPlatformIncludedWhenProvided() {
        val text = WheelReport.buildShareText(
            WheelIdentity(wheelType = WheelType.GOTWAY, btName = "X"),
            appVersion = "1.2.3",
            appPlatform = "android",
        )
        assertTrue(text.contains("1.2.3"))
        assertTrue(text.contains("android"))
    }
}
