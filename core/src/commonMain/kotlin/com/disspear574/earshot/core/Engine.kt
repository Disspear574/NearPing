package com.disspear574.earshot.core

import com.disspear574.earshot.core.wire.AckState
import com.disspear574.earshot.core.wire.Message

interface Clock {
    fun nowEpochSeconds(): Long
}

/**
 * Реализуется платформой: NWConnection на Apple, сокеты на JVM.
 *
 * Адресуется идентификатором соединения, а не ключом пира: у входящего
 * соединения ключ неизвестен до `hello`, а к одному пиру их может быть два.
 */
interface Transport {
    fun connect(connectionId: String, endpoint: String)
    fun send(connectionId: String, bytes: ByteArray)
    fun disconnect(connectionId: String)
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
    private val peerByConnection = LinkedHashMap<String, String>()
    private val connectionByPeer = LinkedHashMap<String, String>()
    private val seen = LinkedHashMap<String, Peer>()
    private val endpoints = LinkedHashMap<String, String>()
    private val dialled = LinkedHashSet<String>()
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
        // Звонит только тот, чей ключ меньше. Иначе обе стороны находят друг друга
        // одновременно, обе набирают, и получается два соединения на одну пару.
        val shouldDial = compareKeys(identity.publicKey, peer.publicKey) < 0
        if (shouldDial && !connectionByPeer.containsKey(peer.keyHex) && dialled.add(peer.keyHex)) {
            transport.connect(outgoingConnectionId(peer.keyHex), endpoint)
        }
        listener.onPeersChanged()
    }

    fun onPeerLost(keyHex: String) {
        seen.remove(keyHex)
        endpoints.remove(keyHex)
        dialled.remove(keyHex)
        listener.onPeersChanged()
    }

    private fun outgoingConnectionId(keyHex: String) = "out:$keyHex"

    private fun compareKeys(a: ByteArray, b: ByteArray): Int {
        for (i in 0 until minOf(a.size, b.size)) {
            val diff = (a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF)
            if (diff != 0) return diff
        }
        return a.size - b.size
    }

    // MARK: транспорт

    fun onConnected(connectionId: String) {
        val session = Session(
            identity = identity,
            signer = signer,
            crypto = crypto,
            registry = registry,
            send = { bytes -> transport.send(connectionId, bytes) },
            events = Events(connectionId),
        )
        sessions[connectionId] = session
        session.start()
    }

    fun onBytes(connectionId: String, chunk: ByteArray) {
        sessions[connectionId]?.onBytes(chunk)
    }

    fun onDisconnected(connectionId: String) {
        sessions.remove(connectionId)
        val keyHex = peerByConnection.remove(connectionId)
        if (keyHex != null) {
            if (connectionByPeer[keyHex] == connectionId) connectionByPeer.remove(keyHex)
            dialled.remove(keyHex)
            pausedPeers.remove(keyHex)
            // Вызовы, не получившие подтверждения, повисли бы навсегда.
            val orphaned = outgoingCalls.filterValues { it == connectionId }.keys.toList()
            orphaned.forEach { callId ->
                outgoingCalls.remove(callId)
                listener.onCallState(keyHex, callId, CallState.FAILED)
            }
        }
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
                isOnline = connectionByPeer[keyHex]?.let { sessions[it]?.isEstablished } == true,
                pausedUntilEpochSeconds = pausedPeers[keyHex]?.takeIf { it > clock.nowEpochSeconds() },
            )
        }
    }

    fun pendingPairingRequests(): List<Peer> = pairing.pendingRequests

    fun requestPairing(keyHex: String) {
        sessionFor(keyHex)?.sendMessage(Message.PairRequest(identity.displayName))
    }

    fun acceptPairing(keyHex: String) {
        val peer = pairing.pendingRequests.firstOrNull { it.keyHex == keyHex } ?: return
        pairing.accept(peer)
        sessionFor(keyHex)?.sendMessage(Message.PairAccept)
        listener.onPeersChanged()
    }

    fun rejectPairing(keyHex: String) {
        val peer = pairing.pendingRequests.firstOrNull { it.keyHex == keyHex } ?: return
        pairing.reject(peer)
        sessionFor(keyHex)?.sendMessage(Message.PairReject)
        listener.onPeersChanged()
    }

    fun forget(keyHex: String) {
        registry.forget(hexToPeerKey(keyHex))
        listener.onPeersChanged()
    }

    /** Возвращает id вызова, либо null — если отправить некому. */
    fun call(keyHex: String, urgent: Boolean): String? {
        val connectionId = connectionByPeer[keyHex] ?: return null
        val session = sessions[connectionId] ?: return null
        if (!registry.isTrusted(hexToPeerKey(keyHex))) return null
        val callId = "c${++callCounter}"
        if (!session.sendMessage(Message.Ping(callId, urgent))) return null
        outgoingCalls[callId] = connectionId
        listener.onCallState(keyHex, callId, CallState.SENT)
        return callId
    }

    /** Человек увидел вызов: закрыл баннер или уведомление. */
    fun markSeen(keyHex: String, callId: String) {
        sessionFor(keyHex)?.sendMessage(Message.Ack(callId, AckState.SEEN))
    }

    private fun sessionFor(keyHex: String): Session? =
        connectionByPeer[keyHex]?.let { sessions[it] }

    private fun activePauseUntil(): Long? = pausedUntil?.takeIf { it > clock.nowEpochSeconds() }

    private fun hexToPeerKey(keyHex: String): ByteArray =
        ByteArray(keyHex.length / 2) { i ->
            keyHex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }

    private inner class Events(private val connectionId: String) : SessionEvents {
        private val keyHex: String get() = peerByConnection[connectionId] ?: ""

        override fun onEstablished(peer: Peer) {
            // Оба конца могли набрать одновременно: второе соединение лишнее.
            val existing = connectionByPeer[peer.keyHex]
            if (existing != null && existing != connectionId && sessions[existing]?.isEstablished == true) {
                transport.disconnect(connectionId)
                sessions.remove(connectionId)
                return
            }
            seen[peer.keyHex] = peer
            peerByConnection[connectionId] = peer.keyHex
            connectionByPeer[peer.keyHex] = connectionId
            sessions[connectionId]?.sendMessage(Message.Presence(activePauseUntil()))
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
            transport.disconnect(connectionId)
            onDisconnected(connectionId)
        }

        private fun onPing(peer: Peer, ping: Message.Ping) {
            // Подтверждение доставки уходит всегда: отправитель должен различать
            // «не дошло» и «дошло, но человек закрылся паузой».
            sessions[connectionId]?.sendMessage(Message.Ack(ping.id, AckState.DELIVERED))
            // Пауза глушит оба типа вызова, включая срочный. Иначе паузе нет доверия.
            if (isPaused) return
            listener.onIncomingCall(peer, ping.id, ping.urgent)
        }
    }
}
