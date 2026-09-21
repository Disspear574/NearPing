package app.earshot.core

/** Эфемерная пара X25519: живёт одно соединение и не сохраняется. */
class EphemeralKeyPair(val privateKey: ByteArray, val publicKey: ByteArray)

/**
 * Криптографические примитивы платформы. На Apple — CryptoKit, на JVM — BouncyCastle.
 * Ядро само ничего не считает: своя реализация криптографии — верный способ ошибиться.
 */
interface Crypto {
    fun randomBytes(count: Int): ByteArray
    fun createEphemeralKeyPair(): EphemeralKeyPair
    fun sharedSecret(privateKey: ByteArray, peerPublicKey: ByteArray): ByteArray
    fun deriveSessionKey(sharedSecret: ByteArray, salt: ByteArray): ByteArray
    fun seal(key: ByteArray, plaintext: ByteArray): ByteArray
    /** null, если печать не сходится: подделка, чужой ключ или испорченный кадр. */
    fun open(key: ByteArray, sealed: ByteArray): ByteArray?
}
