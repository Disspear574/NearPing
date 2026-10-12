package com.disspear574.nearping.core

/** Platform-persisted peer storage. */
interface PeerStorage {
    fun load(): List<Peer>
    fun save(peers: List<Peer>)
}

/** Trusted peers indexed by public key, not by name or address. */
class PeerRegistry(private val storage: PeerStorage) {
    private val byKey = LinkedHashMap<String, Peer>()

    init {
        storage.load().forEach { byKey[it.keyHex] = it }
    }

    val trusted: List<Peer> get() = byKey.values.toList()

    fun isTrusted(publicKey: ByteArray): Boolean = byKey.containsKey(hexOf(publicKey))

    fun trust(peer: Peer) {
        byKey[peer.keyHex] = peer
        storage.save(trusted)
    }

    fun forget(publicKey: ByteArray) {
        if (byKey.remove(hexOf(publicKey)) != null) storage.save(trusted)
    }

    fun named(publicKey: ByteArray): Peer? = byKey[hexOf(publicKey)]

    private fun hexOf(publicKey: ByteArray) = Peer(publicKey, "").keyHex
}
