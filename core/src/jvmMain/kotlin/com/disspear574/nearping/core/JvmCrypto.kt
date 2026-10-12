package com.disspear574.nearping.core

import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.NamedParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** JVM crypto on Java 21 built-ins; bytes must match CryptoKit exactly (see CryptoKitVectorsTest). */
class JvmCrypto(private val random: SecureRandom = SecureRandom()) : Crypto {

    override fun randomBytes(count: Int): ByteArray =
        ByteArray(count).also { random.nextBytes(it) }

    override fun createEphemeralKeyPair(): EphemeralKeyPair {
        val private = randomBytes(KEY_BYTES)
        return EphemeralKeyPair(private, x25519PublicKey(private))
    }

    override fun sharedSecret(privateKey: ByteArray, peerPublicKey: ByteArray): ByteArray =
        RawKeys.x25519(privateKey, peerPublicKey)

    override fun deriveSessionKey(sharedSecret: ByteArray, salt: ByteArray): ByteArray {
        val prk = hmac(salt, sharedSecret)
        return hmac(prk, INFO + byteArrayOf(1)).copyOf(KEY_BYTES)
    }

    // CryptoKit `.combined` layout: nonce ‖ ciphertext ‖ tag.
    override fun seal(key: ByteArray, plaintext: ByteArray): ByteArray =
        sealWithNonce(key, randomBytes(NONCE_BYTES), plaintext)

    internal fun sealWithNonce(key: ByteArray, nonce: ByteArray, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(CIPHER)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "ChaCha20"), IvParameterSpec(nonce))
        return nonce + cipher.doFinal(plaintext)
    }

    override fun open(key: ByteArray, sealed: ByteArray): ByteArray? {
        if (sealed.size < NONCE_BYTES + TAG_BYTES) return null
        return try {
            val cipher = Cipher.getInstance(CIPHER)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "ChaCha20"),
                IvParameterSpec(sealed, 0, NONCE_BYTES))
            cipher.doFinal(sealed, NONCE_BYTES, sealed.size - NONCE_BYTES)
        } catch (e: Exception) {
            null
        }
    }

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }.doFinal(data)

    private companion object {
        val INFO = "nearping-session-v1".toByteArray()
        const val KEY_BYTES = 32
        const val NONCE_BYTES = 12
        const val TAG_BYTES = 16
        const val CIPHER = "ChaCha20-Poly1305"
    }
}

internal fun x25519PublicKey(privateKey: ByteArray): ByteArray =
    RawKeys.x25519(privateKey, ByteArray(32).also { it[0] = 9 })

// The JDK has no seed-to-public-key API, so the generator is fed a SecureRandom that returns the seed.
internal fun ed25519PublicKey(privateKey: ByteArray): ByteArray {
    val seeded = object : SecureRandom() {
        override fun nextBytes(bytes: ByteArray) { privateKey.copyInto(bytes, endIndex = bytes.size) }
    }
    val generator = KeyPairGenerator.getInstance("Ed25519")
    generator.initialize(NamedParameterSpec.ED25519, seeded)
    val pair = generator.generateKeyPair()
    check(RawKeys.raw(pair.private.encoded).contentEquals(privateKey)) { "Ed25519: seed not accepted" }
    return RawKeys.raw(pair.public.encoded)
}

class JvmSigner(private val privateKey: ByteArray) : Signer {
    override fun sign(data: ByteArray): ByteArray =
        Signature.getInstance("Ed25519").run {
            initSign(RawKeys.ed25519Private(privateKey))
            update(data)
            sign()
        }

    override fun verify(data: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean = try {
        Signature.getInstance("Ed25519").run {
            initVerify(RawKeys.ed25519Public(publicKey))
            update(data)
            verify(signature)
        }
    } catch (e: Exception) {
        false
    }
}

/** Converts raw 32-byte keys to JDK objects and back; X.509 and PKCS#8 are a fixed prefix plus the key (RFC 8410). */
private object RawKeys {
    private val X25519_PUBLIC = hexBytes("302a300506032b656e032100")
    private val X25519_PRIVATE = hexBytes("302e020100300506032b656e04220420")
    private val ED25519_PUBLIC = hexBytes("302a300506032b6570032100")
    private val ED25519_PRIVATE = hexBytes("302e020100300506032b657004220420")

    fun x25519(privateKey: ByteArray, publicKey: ByteArray): ByteArray {
        require(privateKey.size == 32 && publicKey.size == 32)
        val factory = KeyFactory.getInstance("X25519")
        val private: PrivateKey = factory.generatePrivate(PKCS8EncodedKeySpec(X25519_PRIVATE + privateKey))
        val public: PublicKey = factory.generatePublic(X509EncodedKeySpec(X25519_PUBLIC + publicKey))
        return KeyAgreement.getInstance("X25519").run {
            init(private)
            doPhase(public, true)
            generateSecret()
        }
    }

    fun ed25519Private(raw: ByteArray): PrivateKey {
        require(raw.size == 32)
        return KeyFactory.getInstance("Ed25519").generatePrivate(PKCS8EncodedKeySpec(ED25519_PRIVATE + raw))
    }

    fun ed25519Public(raw: ByteArray): PublicKey {
        require(raw.size == 32)
        return KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(ED25519_PUBLIC + raw))
    }

    fun raw(encoded: ByteArray): ByteArray = encoded.copyOfRange(encoded.size - 32, encoded.size)

    private fun hexBytes(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}
