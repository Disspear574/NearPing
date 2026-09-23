package com.disspear574.nearping.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TxtRecordTest {
    private val identity = Identity(ByteArray(32) { 0x2B }, "Дима")

    @Test
    fun recordRoundTrips() {
        val parsed = TxtRecord.parse(TxtRecord.of(identity))
        assertEquals("Дима", parsed?.displayName)
        assertEquals(identity.fingerprint, parsed?.fingerprint)
    }

    @Test
    fun foreignVersionIsIgnored() {
        assertNull(TxtRecord.parse(TxtRecord.of(identity) + ("v" to "99")))
    }

    @Test
    fun missingKeyIsIgnored() {
        assertNull(TxtRecord.parse(mapOf("v" to "1", "n" to "Дима")))
    }

    @Test
    fun truncatedKeyIsIgnored() {
        assertNull(TxtRecord.parse(mapOf("v" to "1", "pk" to "AAAA", "n" to "Дима")))
    }

    @Test
    fun blankNameFallsBack() {
        val parsed = TxtRecord.parse(TxtRecord.of(identity) + ("n" to "  "))
        assertEquals(Identity.FALLBACK_NAME, parsed?.displayName)
    }

    @Test
    fun recordDoesNotLeakPauseOrAnythingElse() {
        assertEquals(setOf("v", "pk", "n"), TxtRecord.of(identity).keys)
    }
}

class Base64UrlTest {
    @Test
    fun roundTripsArbitraryBytes() {
        val bytes = ByteArray(32) { (it * 7 - 3).toByte() }
        assertEquals(bytes.toList(), Base64Url.decode(Base64Url.encode(bytes)).toList())
    }

    @Test
    fun roundTripsLengthsAroundPadding() {
        for (size in 1..8) {
            val bytes = ByteArray(size) { (it + 1).toByte() }
            assertEquals(bytes.toList(), Base64Url.decode(Base64Url.encode(bytes)).toList(), "длина $size")
        }
    }

    @Test
    fun outputIsUrlSafe() {
        val encoded = Base64Url.encode(ByteArray(32) { (it * 11).toByte() })
        assertEquals(emptyList(), encoded.filter { it == '+' || it == '/' || it == '=' }.toList())
    }
}
