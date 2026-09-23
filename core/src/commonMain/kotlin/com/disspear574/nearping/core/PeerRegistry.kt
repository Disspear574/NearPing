package com.disspear574.nearping.core

/** Сохраняется платформой. Ключ — единственное, по чему узнают пира. */
interface PeerStorage {
    fun load(): List<Peer>
    fun save(peers: List<Peer>)
}

/**
 * Доверенные пиры. Индексируются публичным ключом, а не именем и не адресом:
 * имя человек меняет сам, адрес меняет роутер, ключ не меняется никогда.
 */
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
