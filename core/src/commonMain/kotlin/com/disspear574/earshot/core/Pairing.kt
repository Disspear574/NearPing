package com.disspear574.earshot.core

import com.disspear574.earshot.core.wire.Message

enum class PairingDecision { ASK_USER, ALREADY_PENDING }

/**
 * Взаимное добавление: видеть в сети можно всех, вызывать — только тех,
 * кто принял запрос. Барьер держится на ключе, поэтому переживает смену сети.
 */
class PairingMachine(private val registry: PeerRegistry) {
    private val pending = LinkedHashMap<String, Peer>()

    val pendingRequests: List<Peer> get() = pending.values.toList()

    fun onIncoming(request: Message.PairRequest, from: Peer): PairingDecision {
        // The other side may have forgotten us: local trust must not swallow a new request.
        if (pending.containsKey(from.keyHex)) return PairingDecision.ALREADY_PENDING
        // Имя берётся из запроса, а не из анонса: человек мог переименоваться,
        // и в диалоге должно стоять то имя, которое он назвал сам.
        pending[from.keyHex] = Peer(from.publicKey, request.displayName)
        return PairingDecision.ASK_USER
    }

    fun accept(peer: Peer) {
        registry.trust(pending.remove(peer.keyHex) ?: peer)
    }

    fun clear(keyHex: String) { pending.remove(keyHex) }

    fun reject(peer: Peer) {
        pending.remove(peer.keyHex)
    }
}
