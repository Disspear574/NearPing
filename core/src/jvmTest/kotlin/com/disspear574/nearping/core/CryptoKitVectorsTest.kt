package com.disspear574.nearping.core

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

/** Reference bytes taken from CryptoKit on fixed inputs; the JVM implementation must match them so Linux and Mac interoperate. */
class CryptoKitVectorsTest {
    private val crypto = JvmCrypto()
    private val a = ByteArray(32) { it.toByte() }
    private val b = ByteArray(32) { (it + 32).toByte() }
    private val identitySeed = ByteArray(32) { (it * 7 + 1).toByte() }

    @Test fun x25519PublicKeys() {
        assertContentEquals(hex("8f40c5adb68f25624ae5b214ea767a6ec94d829d3d7b5e1ad1ba6f3e2138285f"), x25519PublicKey(a))
        assertContentEquals(hex("358072d6365880d1aeea329adf9121383851ed21a28e3b75e965d0d2cd166254"), x25519PublicKey(b))
    }

    @Test fun x25519SharedSecret() {
        val expected = hex("9663aa1da97e848a914a436d04163dfbb89178f107f1b5b77ed3854203382854")
        assertContentEquals(expected, crypto.sharedSecret(a, x25519PublicKey(b)))
        assertContentEquals(expected, crypto.sharedSecret(b, x25519PublicKey(a)))
    }

    @Test fun ed25519PublicKeyFromSeed() {
        assertContentEquals(hex("e4030998cfd5ad1723c169f956aa0b9eb8619b5992bd612c2af428ebc79f8df0"), ed25519PublicKey(identitySeed))
    }

    @Test fun ed25519SignatureAcceptedByCryptoKitIsAcceptedHere() {
        // Ed25519 is deterministic (RFC 8032); CryptoKit accepted this signature.
        val signature = hex("cf84158006c5b4e7beb56afbdfa1737492e2a3d84125b7cccece37cf3ca34ef6" +
            "ad06e899309b1f011e6b738e5b04957320808edf25756c72b3c15440b0bd3c0a")
        val data = "nearping-vector".encodeToByteArray()
        assertContentEquals(signature, JvmSigner(identitySeed).sign(data))
        assertTrue(JvmSigner(identitySeed).verify(data, signature, ed25519PublicKey(identitySeed)))
    }

    @Test fun hkdfSessionKey() {
        assertContentEquals(
            hex("2bc83ddc623c249584bda66b651e92a27b82eeb46f1e7b7ac6be12022081f831"),
            crypto.deriveSessionKey(ByteArray(32) { 0x0b }, ByteArray(64) { it.toByte() }),
        )
    }

    @Test fun chachaPolyCombined() {
        val key = ByteArray(32) { 0x42 }
        val sealed = hex("000102030405060708090a0b34d484fe4a85a3ec49816a1e7fea8963b7491282ad884c52a8ac585559b7fb24ff1314")
        assertContentEquals(sealed, crypto.sealWithNonce(key, ByteArray(12) { it.toByte() }, "\u0414\u0438\u043c\u0430 \u0437\u043e\u0432\u0451\u0442".encodeToByteArray()))
        assertContentEquals("\u0414\u0438\u043c\u0430 \u0437\u043e\u0432\u0451\u0442".encodeToByteArray(), crypto.open(key, sealed))
    }

    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}
