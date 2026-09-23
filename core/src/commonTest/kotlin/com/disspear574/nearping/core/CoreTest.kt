package com.disspear574.nearping.core

import kotlin.test.Test
import kotlin.test.assertEquals

class CoreTest {
    @Test
    fun protocolVersionIsOne() {
        assertEquals(1, Core.protocolVersion)
    }

    @Test
    fun fingerprintIsGroupedHex() {
        val bytes = byteArrayOf(0xA3.toByte(), 0xF9.toByte(), 0x2B, 0x71)
        assertEquals("a3f9-2b71", Core.fingerprint(bytes))
    }

    @Test
    fun fingerprintUsesFirstFourBytesOnly() {
        val bytes = ByteArray(32) { it.toByte() }
        assertEquals("0001-0203", Core.fingerprint(bytes))
    }
}
