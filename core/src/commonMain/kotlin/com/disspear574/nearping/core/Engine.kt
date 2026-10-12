package com.disspear574.nearping.core

import com.disspear574.nearping.core.wire.AckState
import com.disspear574.nearping.core.wire.Message

interface Clock {
    fun nowEpochSeconds(): Long
}

/** Implemented by the platform; addressed by connection id because an inbound peer key is unknown until `hello`. */
interface Transport {
    fun connect(connectionId: String, endpoint: String)
    fun send(connectionId: String, bytes: ByteArray)
    fun disconnect(connectionId: String)
}

/** Sender-side call state. */
enum class CallState { SENT, DELIVERED, SEEN, FAILED, RATE_LIMITED }

/** Flat peer snapshot for the UI, cheap to pass across the Swift bridge. */
class PeerView(
    val keyHex: String,
    val displayName: String,
    val fingerprint: String,
    val isTrusted: Boolean,
    val isOnline: Boolean,
    val pausedUntilEpochSeconds: Long?,
    val foreignProtocolVersion: Int?,
) {
    val isPaused: Boolean get() = pausedUntilEpochSeconds != null
    val isForeignVersion: Boolean get() = foreignProtocolVersion != null
}

interface EngineListener {
    fun onPeersChanged()
    fun onPairingRequest(peer: Peer)
    fun onPairingRejected(peer: Peer)
    fun onIncomingCall(peer: Peer, callId: String, urgent: Boolean)
    fun onCallState(keyHex: String, callId: String, state: CallState)
}

/** Ties together discovery, connections, trust and pause. */
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
    private val foreignVersions = LinkedHashMap<String, Int>()
    // Pair accept is honoured only from peers we asked; kept in memory so a restart asks the other side again.
    private val outgoingPairings = LinkedHashSet<String>()

    private val lastHeard = LinkedHashMap<String, Long>()
    private val handshakeStarted = LinkedHashMap<String, Long>()
    private var lastHeartbeatSent = clock.nowEpochSeconds()
    private var pausedUntil: Long? = null
    private var callCounter = 0L

    val myFingerprint: String get() = identity.fingerprint
    val myDisplayName: String get() = identity.displayName

    val isPaused: Boolean get() = activePauseUntil() != null
    val pausedUntilEpochSeconds: Long? get() = activePauseUntil()

    fun rename(displayName: String) {
        identity.displayName = displayName
        sessions.keys.toList().forEach { connectionId ->
            transport.disconnect(connectionId)
            onDisconnected(connectionId)
        }
        listener.onPeersChanged()
    }

    fun pauseUntil(epochSeconds: Long?) {
        pausedUntil = epochSeconds
        val presence = Message.Presence(activePauseUntil(), heartbeat = true)
        sessions.values.forEach { it.sendMessage(presence) }
        listener.onPeersChanged()
    }


    fun onPeerFound(peer: Peer, endpoint: String) {
        if (peer.publicKey.contentEquals(identity.publicKey)) return
        seen[peer.keyHex] = peer
        endpoints[peer.keyHex] = endpoint
        foreignVersions.remove(peer.keyHex)
        // Only the side with the smaller key dials, so a simultaneous find does not open two connections.
        val shouldDial = compareKeys(identity.publicKey, peer.publicKey) < 0
        if (shouldDial && !connectionByPeer.containsKey(peer.keyHex) && dialled.add(peer.keyHex)) {
            transport.connect(outgoingConnectionId(peer.keyHex), endpoint)
        }
        listener.onPeersChanged()
    }

    fun onForeignPeerFound(peer: Peer, protocolVersion: Int) {
        if (peer.publicKey.contentEquals(identity.publicKey)) return
        seen[peer.keyHex] = peer
        endpoints.remove(peer.keyHex)
        foreignVersions[peer.keyHex] = protocolVersion
        listener.onPeersChanged()
    }

    fun retryConnections() {
        seen.values.toList().forEach { peer ->
            endpoints[peer.keyHex]?.let { onPeerFound(peer, it) }
        }
    }

    fun tick() {
        val now = clock.nowEpochSeconds()
        // A clock correction must not leave deadlines far in the future.
        lastHeard.keys.toList().forEach { if (lastHeard.getValue(it) > now) lastHeard[it] = now }
        handshakeStarted.keys.toList().forEach { if (handshakeStarted.getValue(it) > now) handshakeStarted[it] = now }
        val expired = (lastHeard.filterValues { now - it >= 12 }.keys +
            handshakeStarted.filterValues { now - it >= 12 }.keys).toSet()
        expired.forEach { id ->
            transport.disconnect(id)
            onDisconnected(id)
        }
        if (now < lastHeartbeatSent || now - lastHeartbeatSent >= 3) {
            lastHeartbeatSent = now
            val presence = Message.Presence(activePauseUntil(), heartbeat = true)
            sessions.values.toList().filter { it.isEstablished }.forEach { it.sendMessage(presence) }
        }
    }

    fun onPeerLost(keyHex: String) {
        seen.remove(keyHex)
        endpoints.remove(keyHex)
        dialled.remove(keyHex)
        foreignVersions.remove(keyHex)
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
        handshakeStarted[connectionId] = clock.nowEpochSeconds()
        session.start()
    }

    fun onBytes(connectionId: String, chunk: ByteArray) {
        sessions[connectionId]?.onBytes(chunk)
    }

    fun onDisconnected(connectionId: String) {
        sessions.remove(connectionId)
        lastHeard.remove(connectionId)
        handshakeStarted.remove(connectionId)
        // A failed TCP connection has no authenticated peer mapping yet.
        if (connectionId.startsWith("out:")) dialled.remove(connectionId.removePrefix("out:"))
        val keyHex = peerByConnection.remove(connectionId)
        if (keyHex != null) {
            if (connectionByPeer[keyHex] == connectionId) connectionByPeer.remove(keyHex)
            dialled.remove(keyHex)
            pausedPeers.remove(keyHex)
            // Unacknowledged calls would otherwise hang forever.
            val orphaned = outgoingCalls.filterValues { it == connectionId }.keys.toList()
            orphaned.forEach { callId ->
                outgoingCalls.remove(callId)
                listener.onCallState(keyHex, callId, CallState.FAILED)
            }
        }
        listener.onPeersChanged()
    }


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
                foreignProtocolVersion = foreignVersions[keyHex],
            )
        }
    }

    fun pendingPairingRequests(): List<Peer> = pairing.pendingRequests

    fun requestPairing(keyHex: String) {
        val session = sessionFor(keyHex) ?: return
        outgoingPairings.add(keyHex)
        if (!session.sendMessage(Message.PairRequest(identity.displayName))) outgoingPairings.remove(keyHex)
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
        outgoingPairings.remove(keyHex)
        pairing.clear(keyHex)
        registry.forget(hexToPeerKey(keyHex))
        listener.onPeersChanged()
    }

    private val pingLimiter = PingLimiter()
    private val incomingLimiter = PingLimiter()

    fun callCooldownSeconds(keyHex: String, urgent: Boolean): Long =
        pingLimiter.remaining(keyHex, urgent, clock.nowEpochSeconds())

    fun call(keyHex: String, urgent: Boolean): String? {
        if (callCooldownSeconds(keyHex, urgent) > 0) return null
        val connectionId = connectionByPeer[keyHex] ?: return null
        val session = sessions[connectionId] ?: return null
        if (!registry.isTrusted(hexToPeerKey(keyHex))) return null
        val callId = "c${++callCounter}"
        if (!session.sendMessage(Message.Ping(callId, urgent))) return null
        pingLimiter.record(keyHex, urgent, clock.nowEpochSeconds())
        outgoingCalls[callId] = connectionId
        listener.onCallState(keyHex, callId, CallState.SENT)
        return callId
    }

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
            handshakeStarted.remove(connectionId)
            // Both ends may have dialled at once; the second connection is redundant.
            val existing = connectionByPeer[peer.keyHex]
            if (existing != null && existing != connectionId && sessions[existing]?.isEstablished == true) {
                transport.disconnect(connectionId)
                sessions.remove(connectionId)
                return
            }
            // Update the name only after the key is verified, never from open Bonjour TXT.
            if (registry.isTrusted(peer.publicKey)) registry.trust(peer)
            seen[peer.keyHex] = peer
            peerByConnection[connectionId] = peer.keyHex
            connectionByPeer[peer.keyHex] = connectionId
            sessions[connectionId]?.sendMessage(Message.Presence(activePauseUntil(), heartbeat = true))
            listener.onPeersChanged()
        }

        override fun onMessage(peer: Peer, message: Message) {
            if (lastHeard.containsKey(connectionId) || message is Message.Presence && message.heartbeat) {
                lastHeard[connectionId] = clock.nowEpochSeconds()
            }
            when (message) {
                is Message.PairRequest ->
                    // Ask even a trusted peer: it may have removed us and re-requests, so it must be allowed to refuse.
                    if (pairing.onIncoming(message, peer) == PairingDecision.ASK_USER) {
                        listener.onPairingRequest(peer)
                    }

                is Message.PairAccept -> {
                    // Only answers to our own request count; a late accept after we removed the peer is dropped.
                    if (outgoingPairings.remove(peer.keyHex)) {
                        registry.trust(peer)
                        listener.onPeersChanged()
                    }
                }

                is Message.PairReject -> {
                    if (outgoingPairings.remove(peer.keyHex)) listener.onPairingRejected(peer)
                }

                is Message.Ping -> onPing(peer, message)

                is Message.Ack -> {
                    // Any answer means the call reached the machine; only unanswered calls fail on disconnect.
                    outgoingCalls.remove(message.id)
                    val state = when (message.state) {
                        AckState.DELIVERED -> CallState.DELIVERED
                        AckState.SEEN -> CallState.SEEN
                        AckState.RATE_LIMITED -> CallState.RATE_LIMITED
                    }
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
            // Pause mutes urgent calls too; the ack still goes out so the sender can tell "not delivered" from "paused".
            if (isPaused) {
                sessions[connectionId]?.sendMessage(Message.Ack(ping.id, AckState.DELIVERED))
                return
            }
            // A rate-limited call is not "delivered"; the sender must learn the truth despite network races.
            val now = clock.nowEpochSeconds()
            if (incomingLimiter.remaining(peer.keyHex, ping.urgent, now) > 0) {
                sessions[connectionId]?.sendMessage(Message.Ack(ping.id, AckState.RATE_LIMITED))
                return
            }
            incomingLimiter.record(peer.keyHex, ping.urgent, now)
            sessions[connectionId]?.sendMessage(Message.Ack(ping.id, AckState.DELIVERED))
            listener.onIncomingCall(peer, ping.id, ping.urgent)
        }
    }
}
