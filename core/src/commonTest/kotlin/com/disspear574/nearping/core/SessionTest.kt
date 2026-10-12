package com.disspear574.nearping.core

import com.disspear574.nearping.core.wire.Framing
import com.disspear574.nearping.core.wire.Message
import com.disspear574.nearping.core.wire.Wire
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class Recorder : SessionEvents {
    val established = mutableListOf<Peer>()
    val messages = mutableListOf<Message>()
    val failures = mutableListOf<String>()
    override fun onEstablished(peer: Peer) { established += peer }
    override fun onMessage(peer: Peer, message: Message) { messages += message }
    override fun onFailed(reason: String) { failures += reason }
}

/** Two sides linked by queues, so delivery order is deterministic. */
private class Pair2(trustEachOther: Boolean) {
    val aKey = ByteArray(32) { 0x11 }
    val bKey = ByteArray(32) { 0x22 }
    val aOut = ArrayDeque<ByteArray>()
    val bOut = ArrayDeque<ByteArray>()
    val aEvents = Recorder()
    val bEvents = Recorder()
    val aRegistry = PeerRegistry(MemoryPeers(if (trustEachOther) listOf(Peer(bKey, "Б")) else emptyList()))
    val bRegistry = PeerRegistry(MemoryPeers(if (trustEachOther) listOf(Peer(aKey, "А")) else emptyList()))

    val a = Session(Identity(aKey, "А"), FakeSigner(aKey), FakeCrypto(1), aRegistry, { aOut += it }, aEvents)
    val b = Session(Identity(bKey, "Б"), FakeSigner(bKey), FakeCrypto(2), bRegistry, { bOut += it }, bEvents)

    fun handshake() {
        a.start()
        b.start()
        pump()
    }

    fun pump() {
        var guard = 0
        while ((aOut.isNotEmpty() || bOut.isNotEmpty()) && guard++ < 100) {
            while (aOut.isNotEmpty()) b.onBytes(aOut.removeFirst())
            while (bOut.isNotEmpty()) a.onBytes(bOut.removeFirst())
        }
    }
}

class SessionHandshakeTest {
    @Test
    fun bothSidesEstablish() {
        val pair = Pair2(trustEachOther = true)
        pair.handshake()
        assertEquals(emptyList<String>(), pair.aEvents.failures)
        assertEquals(emptyList<String>(), pair.bEvents.failures)
        assertTrue(pair.a.isEstablished)
        assertTrue(pair.b.isEstablished)
    }

    @Test
    fun eachSideLearnsTheOthersName() {
        val pair = Pair2(trustEachOther = true)
        pair.handshake()
        assertEquals(listOf("Б"), pair.aEvents.established.map { it.displayName })
        assertEquals(listOf("А"), pair.bEvents.established.map { it.displayName })
    }

    @Test
    fun trustedPingIsDelivered() {
        val pair = Pair2(trustEachOther = true)
        pair.handshake()
        assertTrue(pair.a.sendMessage(Message.Ping("p1", urgent = true)))
        pair.pump()
        assertEquals(emptyList<String>(), pair.bEvents.failures)
        assertEquals(listOf<Message>(Message.Ping("p1", urgent = true)), pair.bEvents.messages)
    }

    @Test
    fun pingFromUntrustedPeerIsDropped() {
        val pair = Pair2(trustEachOther = false)
        pair.handshake()
        pair.a.sendMessage(Message.Ping("p1", urgent = true))
        pair.pump()
        assertEquals(emptyList<Message>(), pair.bEvents.messages)
    }

    @Test
    fun pairRequestFromUntrustedPeerStillArrives() {
        val pair = Pair2(trustEachOther = false)
        pair.handshake()
        pair.a.sendMessage(Message.PairRequest("А"))
        pair.pump()
        assertEquals(emptyList<String>(), pair.bEvents.failures)
        assertEquals(listOf<Message>(Message.PairRequest("А")), pair.bEvents.messages)
    }

    @Test
    fun nothingIsSentBeforeHandshakeCompletes() {
        val pair = Pair2(trustEachOther = true)
        assertFalse(pair.a.sendMessage(Message.Ping("p1", urgent = false)))
    }
}

class SessionAttackTest {
    @Test
    fun forgedSignatureIsRejected() {
        val pair = Pair2(trustEachOther = true)
        pair.a.start()
        pair.b.start()
        // Response signed by the wrong key.
        while (pair.aOut.isNotEmpty()) pair.b.onBytes(pair.aOut.removeFirst())
        val challenge = pair.bOut.map { frameToMessage(it) }.filterIsInstance<Message.AuthChallenge>()
        pair.bOut.clear()
        val forged = Message.AuthResponse(
            challenge = challenge.first().challenge,
            ephemeralKey = Base64Url.encode(ByteArray(32) { 0x55 }),
            signature = Base64Url.encode(ByteArray(33) { 0x00 }),
        )
        pair.b.onBytes(Framing.frame(Wire.encode(Message.Hello(Base64Url.encode(pair.aKey), "А"))))
        pair.b.onBytes(Framing.frame(Wire.encode(forged)))
        assertTrue(pair.bEvents.failures.isNotEmpty(), "forgery must be rejected")
    }

    @Test
    fun replayedAuthResponseIsRejected() {
        val pair = Pair2(trustEachOther = true)
        pair.handshake()
        // Replay of an intercepted response: the challenge is already spent.
        val replay = Message.AuthResponse(
            challenge = Base64Url.encode(ByteArray(32)),
            ephemeralKey = Base64Url.encode(ByteArray(32) { 0x33 }),
            signature = Base64Url.encode(ByteArray(33)),
        )
        pair.a.onBytes(Framing.frame(Wire.encode(replay)))
        assertTrue(pair.aEvents.failures.isNotEmpty(), "replay must be rejected")
    }

    @Test
    fun contentBeforeHandshakeIsRefused() {
        val pair = Pair2(trustEachOther = true)
        pair.b.start()
        pair.bOut.clear()
        pair.b.onBytes(Framing.frame(Wire.encode(Message.Ping("p1", urgent = true))))
        assertTrue(pair.bEvents.failures.any { it.contains("before handshake") })
    }

    @Test
    fun connectingToSelfIsRefused() {
        val pair = Pair2(trustEachOther = true)
        pair.b.start()
        pair.bOut.clear()
        pair.b.onBytes(Framing.frame(Wire.encode(Message.Hello(Base64Url.encode(pair.bKey), "Б"))))
        assertTrue(pair.bEvents.failures.any { it.contains("self") })
    }

    @Test
    fun garbageFramingDoesNotCrash() {
        val pair = Pair2(trustEachOther = true)
        pair.b.start()
        pair.b.onBytes(byteArrayOf(0x7F, -1, -1, -1, 0, 0))
        assertTrue(pair.bEvents.failures.isNotEmpty())
    }
}

private fun frameToMessage(frame: ByteArray): Message =
    Wire.decode(Framing.Parser().feed(frame).first())
