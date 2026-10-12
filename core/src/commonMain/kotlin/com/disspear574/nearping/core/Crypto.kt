package com.disspear574.nearping.core

/** Ephemeral X25519 key pair, valid for one connection. */
class EphemeralKeyPair(val privateKey: ByteArray, val publicKey: ByteArray)

/** Platform crypto primitives; the core never implements crypto itself. */
interface Crypto {
    fun randomBytes(count: Int): ByteArray
    fun createEphemeralKeyPair(): EphemeralKeyPair
    fun sharedSecret(privateKey: ByteArray, peerPublicKey: ByteArray): ByteArray
    fun deriveSessionKey(sharedSecret: ByteArray, salt: ByteArray): ByteArray
    fun seal(key: ByteArray, plaintext: ByteArray): ByteArray
    fun open(key: ByteArray, sealed: ByteArray): ByteArray?
}
