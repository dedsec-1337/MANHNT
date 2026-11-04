package com.manhunt.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatchQrPayloadTest {

    private val uid = "kT3x9QmZpLw2VbN8yUaR5cDhE1f0"

    @Test
    fun encode_usesTheDocumentedFormat() {
        assertEquals("MANHNT|ABCDEF|$uid|0427", CatchQrPayload("ABCDEF", uid, "0427").encode())
    }

    @Test
    fun parse_roundTripsEncode() {
        val original = CatchQrPayload("QWERTY", uid, "9081")
        assertEquals(original, CatchQrPayload.parse(original.encode()))
    }

    @Test
    fun parse_keepsLeadingZerosInTheCode() {
        assertEquals("0007", CatchQrPayload.parse("MANHNT|ABCDEF|$uid|0007")?.catchCode)
    }

    @Test
    fun parse_trimsSurroundingWhitespace() {
        assertEquals("1234", CatchQrPayload.parse("  MANHNT|ABCDEF|$uid|1234\n")?.catchCode)
    }

    @Test
    fun parse_normalisesJoinCodeToUppercase() {
        assertEquals("ABCDEF", CatchQrPayload.parse("MANHNT|abcdef|$uid|1234")?.joinCode)
    }

    @Test
    fun parse_acceptsUuidStylePlayerIds() {
        assertTrue(CatchQrPayload.parse("MANHNT|ABCDEF|123e4567-e89b-12d3-a456-426614174000|1234") != null)
    }

    @Test
    fun parse_rejectsNullAndBlank() {
        assertEquals(null, CatchQrPayload.parse(null))
        assertEquals(null, CatchQrPayload.parse(""))
        assertEquals(null, CatchQrPayload.parse("   "))
    }

    @Test
    fun parse_rejectsForeignQrCodes() {
        assertEquals(null, CatchQrPayload.parse("https://example.com/?q=1"))
        assertEquals(null, CatchQrPayload.parse("WIFI:T:WPA;S:cafe;P:secret;;"))
        assertEquals(null, CatchQrPayload.parse("manhnt|ABCDEF|$uid|1234"))
    }

    @Test
    fun parse_rejectsWrongFieldCount() {
        assertEquals(null, CatchQrPayload.parse("MANHNT|ABCDEF|$uid"))
        assertEquals(null, CatchQrPayload.parse("MANHNT|ABCDEF|$uid|1234|extra"))
        // A separator smuggled into the player id shifts the field count.
        assertEquals(null, CatchQrPayload.parse("MANHNT|ABCDEF|ab|cd|1234"))
    }

    @Test
    fun parse_rejectsMalformedCatchCodes() {
        for (bad in listOf("", "123", "12345", "12a4", "١٢٣٤", "12 4", "-123")) {
            assertEquals("code <$bad>", null, CatchQrPayload.parse("MANHNT|ABCDEF|$uid|$bad"))
        }
    }

    @Test
    fun parse_rejectsMalformedJoinCodes() {
        for (bad in listOf("", "ABC", "ABCDEFGHIJKLM", "AB CDE", "ABC-EF")) {
            assertEquals("join <$bad>", null, CatchQrPayload.parse("MANHNT|$bad|$uid|1234"))
        }
    }

    @Test
    fun parse_rejectsMalformedPlayerIds() {
        assertEquals(null, CatchQrPayload.parse("MANHNT|ABCDEF||1234"))
        assertEquals(null, CatchQrPayload.parse("MANHNT|ABCDEF|has space|1234"))
        assertEquals(null, CatchQrPayload.parse("MANHNT|ABCDEF|../secrets|1234"))
        assertEquals(null, CatchQrPayload.parse("MANHNT|ABCDEF|${"a".repeat(129)}|1234"))
        assertFalse(CatchQrPayload.parse("MANHNT|ABCDEF|${"a".repeat(128)}|1234") == null)
    }

    @Test
    fun qrCodeUtil_payloadMatchesTheParserFormat() {
        // QrCodeUtil.catchPayload trims + uppercases; mirror that here without Android deps.
        val encoded = CatchQrPayload(" abcdef ".trim().uppercase(), uid, " 4821 ".trim()).encode()
        assertEquals(CatchQrPayload("ABCDEF", uid, "4821"), CatchQrPayload.parse(encoded))
    }
}
