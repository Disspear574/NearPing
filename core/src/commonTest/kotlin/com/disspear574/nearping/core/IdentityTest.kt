package com.disspear574.nearping.core

import kotlin.test.Test
import kotlin.test.assertEquals

class IdentityTest {
    @Test
    fun fingerprintComesFromPublicKey() {
        val id = Identity(byteArrayOf(0xA3.toByte(), 0xF9.toByte(), 0x2B, 0x71), "Владислав")
        assertEquals("a3f9-2b71", id.fingerprint)
    }

    @Test
    fun displayNameKeepsWhatUserTyped() {
        val id = Identity(ByteArray(32), "Владислав")
        assertEquals("Владислав", id.displayName)
    }

    @Test
    fun displayNameIsTrimmed() {
        val id = Identity(ByteArray(32), "  Аня  ")
        assertEquals("Аня", id.displayName)
    }

    @Test
    fun blankDisplayNameFallsBack() {
        val id = Identity(ByteArray(32), "   ")
        assertEquals("NearPing", id.displayName)
    }

    @Test
    fun keyHexIsStableAcrossInstances() {
        val bytes = ByteArray(32) { it.toByte() }
        assertEquals(Peer(bytes, "a").keyHex, Peer(bytes.copyOf(), "b").keyHex)
    }
}
