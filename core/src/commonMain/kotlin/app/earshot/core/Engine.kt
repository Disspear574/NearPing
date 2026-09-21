package app.earshot.core

import app.earshot.core.wire.AckState
import app.earshot.core.wire.Message

interface Clock {
    fun nowEpochSeconds(): Long
}

/** Реализуется платформой: NWConnection на Apple, сокеты на JVM. */
interface Transport {
    fun connect(keyHex: String, endpoint: String)
    fun send(keyHex: String, bytes: ByteArray)
    fun disconnect(keyHex: String)
}

/** Состояние вызова у отправителя. */
enum class CallState { SENT, DELIVERED, SEEN, FAILED }

/** Снимок пира для интерфейса: плоский, чтобы дёшево пересекать мост в Swift. */
class PeerView(
    val keyHex: String,
    val displayName: String,
    val fingerprint: String,
    val isTrusted: Boolean,
    val isOnline: Boolean,
    val pausedUntilEpochSeconds: Long?,
) {
    val isPaused: Boolean get() = pausedUntilEpochSeconds != null
}

interface EngineListener {
    fun onPeersChanged()
    fun onPairingRequest(peer: Peer)
    fun onPairingRejected(peer: Peer)
    fun onIncomingCall(peer: Peer, callId: String, urgent: Boolean)
    fun onCallState(keyHex: String, callId: String, state: CallState)
}

/**
 * Связывает обнаружение, соединения, доверие и паузу.
 *
 * Всё, что знает о сети и криптографии, приходит снаружи интерфейсами: ядро
 * остаётся общим и годится для JVM-клиента под Linux без единой правки.
 */
class Engine(
    private val identity: Identity,
    private val signer: Signer,
    private val crypto: Crypto,
    private val registry: PeerRegistry,
    private val transport: Transport,
    private val clock: Clock,
    private val listener: EngineListener,
) {
    private val pairing = PairingMachine(registry)
    private val sessions = LinkedHashMap<String, Session>()
    private val seen = LinkedHashMap<String, Peer>()
    private val endpoints = LinkedHashMap<String, String>()
    private val pausedPeers = LinkedHashMap<String, Long>()
    private val outgoingCalls = LinkedHashMap<String, String>()

    private var pausedUntil: Long? = null
    private var callCounter = 0L

    val myFingerprint: String get() = identity.fingerprint
    val myDisplayName: String get() = identity.displayName

    val isPaused: Boolean get() = activePauseUntil() != null

    fun pauseUntil(epochSeconds: Long?) {
        pausedUntil = epochSeconds
        val presence = Message.Presence(activePauseUntil())
        sessions.values.forEach { it.sendMessage(presence) }
        listener.onPeersChanged()
    }

    // MARK: обнаружение

    fun onPeerFound(peer: Peer, endpoint: String) {
        if (peer.publicKey.contentEquals(identity.publicKey)) return
        seen[peer.keyHex] = peer
        endpoints[peer.keyHex] = endpoint
        if (!sessions.containsKey(peer.keyHex)) transport.connect(peer.keyHex, endpoint)
        listener.onPeersChanged()
    }

    fun onPeerLost(keyHex: String) {
        seen.remove(keyHex)
        endpoints.remove(keyHex)
        listener.onPeersChanged()
    }

    // MARK: транспорт

    fun onConnected(keyHex: String) {
        val session = Session(
            identity = identity,
            signer = signer,
            crypto = crypto,
            registry = registry,
            send = { bytes -> transport.send(keyHex, bytes) },
            events = Events(keyHex),
        )
        sessions[keyHex] = session
        session.start()
    }

    fun onBytes(keyHex: String, chunk: ByteArray) {
        sessions[keyHex]?.onBytes(chunk)
    }

    fun onDisconnected(keyHex: String) {
        sessions.remove(keyHex)
        pausedPeers.remove(keyHex)
        // Вызовы, не получившие подтверждения, повисли бы навсегда.
        outgoingCalls.entries.filter { it.value == keyHex }.forEach { (callId, _) ->
            listener.onCallState(keyHex, callId, CallState.FAILED)
        }
        outgoingCalls.entries.removeAll { it.value == keyHex }
        listener.onPeersChanged()
    }

    // MARK: действия пользователя

    fun peers(): List<PeerView> {
        val keys = LinkedHashSet<String>()
        registry.trusted.forEach { keys += it.keyHex }
        keys += seen.keys
        return keys.map { keyHex ->
            val peer = registry.named(hexToPeerKey(keyHex)) ?: seen[keyHex]
            PeerView(
                keyHex = keyHex,
                displayName = peer?.displayName ?: Identity.FALLBACK_NAME,
                fingerprint = peer?.fingerprint ?: "",
                isTrusted = peer != null && registry.isTrusted(peer.publicKey),
                isOnline = sessions[keyHex]?.isEstablished == true,
                pausedUntilEpochSeconds = pausedPeers[keyHex]?.takeIf { it > clock.nowEpochSeconds() },
            )
        }
    }

    fun pendingPairingRequests(): List<Peer> = pairing.pendingRequests

    fun requestPairing(keyHex: String) {
        sessions[keyHex]?.sendMessage(Message.PairRequest(identity.displayName))
    }

    fun acceptPairing(keyHex: String) {
        val peer = pairing.pendingRequests.firstOrNull { it.keyHex == keyHex } ?: return
        pairing.accept(peer)
        sessions[keyHex]?.sendMessage(Message.PairAccept)
        listener.onPeersChanged()
    }

    fun rejectPairing(keyHex: String) {
        val peer = pairing.pendingRequests.firstOrNull { it.keyHex == keyHex } ?: return
        pairing.reject(peer)
        sessions[keyHex]?.sendMessage(Message.PairReject)
        listener.onPeersChanged()
    }

    fun forget(keyHex: String) {
        registry.forget(hexToPeerKey(keyHex))
        listener.onPeersChanged()
    }

    /** Возвращает id вызова, либо null — если отправить некому. */
    fun call(keyHex: String, urgent: Boolean): String? {
        val session = sessions[keyHex] ?: return null
        if (!registry.isTrusted(hexToPeerKey(keyHex))) return null
        val callId = "c${++callCounter}"
        if (!session.sendMessage(Message.Ping(callId, urgent))) return null
        outgoingCalls[callId] = keyHex
        listener.onCallState(keyHex, callId, CallState.SENT)
        return callId
    }

    /** Человек увидел вызов: закрыл баннер или уведомление. */
    fun markSeen(keyHex: String, callId: String) {
        sessions[keyHex]?.sendMessage(Message.Ack(callId, AckState.SEEN))
    }

    private fun activePauseUntil(): Long? = pausedUntil?.takeIf { it > clock.nowEpochSeconds() }

    private fun hexToPeerKey(keyHex: String): ByteArray =
        ByteArray(keyHex.length / 2) { i ->
            keyHex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }

    private inner class Events(private val keyHex: String) : SessionEvents {
        override fun onEstablished(peer: Peer) {
            seen[peer.keyHex] = peer
            sessions[keyHex]?.sendMessage(Message.Presence(activePauseUntil()))
            listener.onPeersChanged()
        }

        override fun onMessage(peer: Peer, message: Message) {
            when (message) {
                is Message.PairRequest ->
                    if (pairing.onIncoming(message, peer) == PairingDecision.ASK_USER) {
                        listener.onPairingRequest(peer)
                    }

                is Message.PairAccept -> {
                    registry.trust(peer)
                    listener.onPeersChanged()
                }

                is Message.PairReject -> listener.onPairingRejected(peer)

                is Message.Ping -> onPing(peer, message)

                is Message.Ack -> {
                    val state = if (message.state == AckState.SEEN) CallState.SEEN else CallState.DELIVERED
                    listener.onCallState(keyHex, message.id, state)
                }

                is Message.Presence -> {
                    val until = message.pausedUntilEpochSeconds
                    if (until == null) pausedPeers.remove(keyHex) else pausedPeers[keyHex] = until
                    listener.onPeersChanged()
                }

                else -> Unit
            }
        }

        override fun onFailed(reason: String) {
            transport.disconnect(keyHex)
            onDisconnected(keyHex)
        }

        private fun onPing(peer: Peer, ping: Message.Ping) {
            // Подтверждение доставки уходит всегда: отправитель должен различать
            // «не дошло» и «дошло, но человек закрылся паузой».
            sessions[keyHex]?.sendMessage(Message.Ack(ping.id, AckState.DELIVERED))
            // Пауза глушит оба типа вызова, включая срочный. Иначе паузе нет доверия.
            if (isPaused) return
            listener.onIncomingCall(peer, ping.id, ping.urgent)
        }
    }
}
