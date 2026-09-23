package com.disspear574.nearping.core

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import java.security.SecureRandom
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JvmCryptoTest {
    private val crypto = JvmCrypto()

    @Test
    fun sharedSecretIsSymmetric() {
        val a = crypto.createEphemeralKeyPair()
        val b = crypto.createEphemeralKeyPair()
        assertContentEquals(
            crypto.sharedSecret(a.privateKey, b.publicKey),
            crypto.sharedSecret(b.privateKey, a.publicKey),
        )
    }

    @Test
    fun ephemeralKeysAreThirtyTwoBytes() {
        val pair = crypto.createEphemeralKeyPair()
        assertEquals(32, pair.privateKey.size)
        assertEquals(32, pair.publicKey.size)
    }

    @Test
    fun sessionKeyIsDeterministicForSameInputs() {
        val secret = crypto.randomBytes(32)
        val salt = crypto.randomBytes(64)
        assertContentEquals(
            crypto.deriveSessionKey(secret, salt),
            crypto.deriveSessionKey(secret, salt),
        )
    }

    @Test
    fun differentSaltGivesDifferentKey() {
        val secret = crypto.randomBytes(32)
        assertFalse(
            crypto.deriveSessionKey(secret, crypto.randomBytes(64))
                .contentEquals(crypto.deriveSessionKey(secret, crypto.randomBytes(64)))
        )
    }

    @Test
    fun sealedMessageOpensWithTheSameKey() {
        val key = crypto.randomBytes(32)
        val message = "Дима зовёт".encodeToByteArray()
        val opened = crypto.open(key, crypto.seal(key, message))
        assertContentEquals(message, opened)
    }

    @Test
    fun sealedMessageDoesNotOpenWithAnotherKey() {
        val message = "Дима зовёт".encodeToByteArray()
        val sealed = crypto.seal(crypto.randomBytes(32), message)
        assertNull(crypto.open(crypto.randomBytes(32), sealed))
    }

    @Test
    fun tamperedCiphertextIsRefused() {
        val key = crypto.randomBytes(32)
        val sealed = crypto.seal(key, "Дима зовёт".encodeToByteArray())
        sealed[sealed.size - 1] = (sealed[sealed.size - 1] + 1).toByte()
        assertNull(crypto.open(key, sealed))
    }

    @Test
    fun sealedLayoutMatchesCryptoKitCombined() {
        // nonce(12) ‖ ciphertext ‖ tag(16) — иначе macOS не откроет наш кадр.
        val key = crypto.randomBytes(32)
        val plaintext = ByteArray(40)
        assertEquals(12 + plaintext.size + 16, crypto.seal(key, plaintext).size)
    }

    @Test
    fun truncatedSealedFrameIsRefused() {
        val key = crypto.randomBytes(32)
        assertNull(crypto.open(key, ByteArray(8)))
    }
}

class JvmSignerTest {
    private fun keyPair(): kotlin.Pair<ByteArray, ByteArray> {
        val priv = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val pub = Ed25519PrivateKeyParameters(priv, 0).generatePublicKey().encoded
        return priv to pub
    }

    @Test
    fun ownSignatureVerifies() {
        val (priv, pub) = keyPair()
        val signer = JvmSigner(priv)
        val data = "challenge".encodeToByteArray()
        assertTrue(signer.verify(data, signer.sign(data), pub))
    }

    @Test
    fun signatureOverOtherDataFails() {
        val (priv, pub) = keyPair()
        val signer = JvmSigner(priv)
        assertFalse(signer.verify("другое".encodeToByteArray(), signer.sign("challenge".encodeToByteArray()), pub))
    }

    @Test
    fun signatureFromAnotherKeyFails() {
        val (privA, _) = keyPair()
        val (_, pubB) = keyPair()
        val signer = JvmSigner(privA)
        val data = "challenge".encodeToByteArray()
        assertFalse(signer.verify(data, signer.sign(data), pubB))
    }

    @Test
    fun garbageSignatureDoesNotThrow() {
        val (priv, pub) = keyPair()
        assertFalse(JvmSigner(priv).verify("x".encodeToByteArray(), ByteArray(3), pub))
    }

    @Test
    fun publicKeyIsThirtyTwoBytes() {
        assertEquals(32, keyPair().second.size)
    }
}
