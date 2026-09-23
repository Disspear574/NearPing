package com.disspear574.nearping.core

import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import java.security.SecureRandom

/**
 * Криптография под JVM. Байты обязаны совпадать с CryptoKit до последнего:
 * клиенты разных платформ общаются одним протоколом.
 */
class JvmCrypto(private val random: SecureRandom = SecureRandom()) : Crypto {

    override fun randomBytes(count: Int): ByteArray =
        ByteArray(count).also { random.nextBytes(it) }

    override fun createEphemeralKeyPair(): EphemeralKeyPair {
        val priv = X25519PrivateKeyParameters(random)
        return EphemeralKeyPair(priv.encoded, priv.generatePublicKey().encoded)
    }

    override fun sharedSecret(privateKey: ByteArray, peerPublicKey: ByteArray): ByteArray {
        val agreement = X25519Agreement()
        agreement.init(X25519PrivateKeyParameters(privateKey, 0))
        val out = ByteArray(agreement.agreementSize)
        agreement.calculateAgreement(X25519PublicKeyParameters(peerPublicKey, 0), out, 0)
        return out
    }

    override fun deriveSessionKey(sharedSecret: ByteArray, salt: ByteArray): ByteArray {
        val hkdf = HKDFBytesGenerator(SHA256Digest())
        hkdf.init(HKDFParameters(sharedSecret, salt, INFO))
        return ByteArray(KEY_BYTES).also { hkdf.generateBytes(it, 0, it.size) }
    }

    /** Формат CryptoKit `.combined`: nonce ‖ ciphertext ‖ tag. */
    override fun seal(key: ByteArray, plaintext: ByteArray): ByteArray {
        val nonce = randomBytes(NONCE_BYTES)
        val cipher = ChaCha20Poly1305()
        cipher.init(true, AEADParameters(KeyParameter(key), TAG_BITS, nonce))
        val body = ByteArray(cipher.getOutputSize(plaintext.size))
        var written = cipher.processBytes(plaintext, 0, plaintext.size, body, 0)
        written += cipher.doFinal(body, written)
        return nonce + body.copyOf(written)
    }

    override fun open(key: ByteArray, sealed: ByteArray): ByteArray? {
        if (sealed.size < NONCE_BYTES + TAG_BYTES) return null
        val nonce = sealed.copyOfRange(0, NONCE_BYTES)
        val body = sealed.copyOfRange(NONCE_BYTES, sealed.size)
        return try {
            val cipher = ChaCha20Poly1305()
            cipher.init(false, AEADParameters(KeyParameter(key), TAG_BITS, nonce))
            val out = ByteArray(cipher.getOutputSize(body.size))
            var written = cipher.processBytes(body, 0, body.size, out, 0)
            written += cipher.doFinal(out, written)
            out.copyOf(written)
        } catch (e: Exception) {
            // Печать не сошлась: подделка, чужой ключ или испорченный кадр.
            null
        }
    }

    private companion object {
        val INFO = "nearping-session-v1".toByteArray()
        const val KEY_BYTES = 32
        const val NONCE_BYTES = 12
        const val TAG_BYTES = 16
        const val TAG_BITS = 128
    }
}

/** Подпись Ed25519 постоянным ключом личности. */
class JvmSigner(private val privateKey: ByteArray) : Signer {
    override fun sign(data: ByteArray): ByteArray {
        val signer = Ed25519Signer()
        signer.init(true, Ed25519PrivateKeyParameters(privateKey, 0))
        signer.update(data, 0, data.size)
        return signer.generateSignature()
    }

    override fun verify(data: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean = try {
        val verifier = Ed25519Signer()
        verifier.init(false, Ed25519PublicKeyParameters(publicKey, 0))
        verifier.update(data, 0, data.size)
        verifier.verifySignature(signature)
    } catch (e: Exception) {
        false
    }
}
