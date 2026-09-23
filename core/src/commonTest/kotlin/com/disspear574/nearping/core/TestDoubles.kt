package com.disspear574.nearping.core

/**
 * Подделка криптографии: настоящая математика здесь не нужна, нужны те же
 * алгебраические свойства — симметричность общего секрета и проверяемость подписи.
 */
internal class FakeCrypto(private val marker: Byte) : Crypto {
    var counter: Byte = 0

    override fun randomBytes(count: Int): ByteArray {
        counter++
        return ByteArray(count) { (marker + counter).toByte() }
    }

    override fun createEphemeralKeyPair(): EphemeralKeyPair {
        val scalar = ByteArray(32) { (marker * 7 + 3).toByte() }
        return EphemeralKeyPair(privateKey = scalar, publicKey = scalar)
    }

    // shared(a, b) == shared(b, a): порядок задаётся сравнением, как в настоящем X25519
    override fun sharedSecret(privateKey: ByteArray, peerPublicKey: ByteArray): ByteArray =
        if (lexicographic(privateKey, peerPublicKey) <= 0) {
            privateKey + peerPublicKey
        } else {
            peerPublicKey + privateKey
        }

    override fun deriveSessionKey(sharedSecret: ByteArray, salt: ByteArray): ByteArray {
        val out = ByteArray(KEY_BYTES)
        (sharedSecret + salt).forEachIndexed { i, b ->
            out[i % KEY_BYTES] = (out[i % KEY_BYTES] + b).toByte()
        }
        return out
    }

    override fun seal(key: ByteArray, plaintext: ByteArray): ByteArray =
        byteArrayOf(SEALED) + key.copyOfRange(0, TAG_BYTES) + plaintext

    override fun open(key: ByteArray, sealed: ByteArray): ByteArray? {
        if (sealed.size < 1 + TAG_BYTES || sealed[0] != SEALED) return null
        val tag = sealed.copyOfRange(1, 1 + TAG_BYTES)
        if (!tag.contentEquals(key.copyOfRange(0, TAG_BYTES))) return null
        return sealed.copyOfRange(1 + TAG_BYTES, sealed.size)
    }

    private fun lexicographic(a: ByteArray, b: ByteArray): Int {
        for (i in 0 until minOf(a.size, b.size)) {
            val diff = (a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF)
            if (diff != 0) return diff
        }
        return a.size - b.size
    }

    private companion object {
        const val SEALED: Byte = 0x7B
        const val KEY_BYTES = 32
        const val TAG_BYTES = 4
    }
}

internal class FakeSigner(private val ownKey: ByteArray) : Signer {
    override fun sign(data: ByteArray): ByteArray = ownKey + checksum(data)

    override fun verify(data: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean {
        if (signature.size != publicKey.size + 1) return false
        val claimed = signature.copyOfRange(0, publicKey.size)
        return claimed.contentEquals(publicKey) && signature.last() == checksum(data)
    }

    private fun checksum(data: ByteArray): Byte {
        var acc = 0
        for (b in data) acc = (acc * 31 + b) and 0xFF
        return acc.toByte()
    }
}

internal class MemoryPeers(private var peers: List<Peer> = emptyList()) : PeerStorage {
    override fun load() = peers
    override fun save(peers: List<Peer>) { this.peers = peers }
}

