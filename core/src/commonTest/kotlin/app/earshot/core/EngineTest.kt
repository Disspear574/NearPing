package app.earshot.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class TestClock(var now: Long = 1_000) : Clock {
    override fun nowEpochSeconds(): Long = now
}

private class Recorded : EngineListener {
    val pairingRequests = mutableListOf<Peer>()
    val rejections = mutableListOf<Peer>()
    val incoming = mutableListOf<Triple<String, String, Boolean>>()
    val callStates = mutableListOf<Pair<String, CallState>>()
    var changes = 0
    override fun onPeersChanged() { changes++ }
    override fun onPairingRequest(peer: Peer) { pairingRequests += peer }
    override fun onPairingRejected(peer: Peer) { rejections += peer }
    override fun onIncomingCall(peer: Peer, callId: String, urgent: Boolean) {
        incoming += Triple(peer.displayName, callId, urgent)
    }
    override fun onCallState(keyHex: String, callId: String, state: CallState) {
        callStates += callId to state
    }
}

/** Две машины в одной «сети»: доставка через очереди, порядок детерминирован. */
private class TwoMachines {
    val clock = TestClock()
    val aKey = ByteArray(32) { 0x11 }
    val bKey = ByteArray(32) { 0x22 }
    val aHex = Peer(aKey, "").keyHex
    val bHex = Peer(bKey, "").keyHex

    val aEvents = Recorded()
    val bEvents = Recorded()
    private val queue = ArrayDeque<Triple<Boolean, String, ByteArray>>()

    private inner class Wire(private val toB: Boolean) : Transport {
        override fun connect(keyHex: String, endpoint: String) {}
        override fun send(keyHex: String, bytes: ByteArray) { queue += Triple(toB, keyHex, bytes) }
        override fun disconnect(keyHex: String) {}
    }

    val a = Engine(Identity(aKey, "Аня"), FakeSigner(aKey), FakeCrypto(1),
        PeerRegistry(MemoryPeers()), Wire(toB = true), clock, aEvents)
    val b = Engine(Identity(bKey, "Борис"), FakeSigner(bKey), FakeCrypto(2),
        PeerRegistry(MemoryPeers()), Wire(toB = false), clock, bEvents)

    fun connect() {
        a.onPeerFound(Peer(bKey, "Борис"), "b:1")
        b.onPeerFound(Peer(aKey, "Аня"), "a:1")
        a.onConnected(bHex)
        b.onConnected(aHex)
        pump()
    }

    fun pump() {
        var guard = 0
        while (queue.isNotEmpty() && guard++ < 200) {
            val (toB, _, bytes) = queue.removeFirst()
            if (toB) b.onBytes(aHex, bytes) else a.onBytes(bHex, bytes)
        }
    }

    fun pairThem() {
        a.requestPairing(bHex); pump()
        b.acceptPairing(aHex); pump()
    }
}

class EngineConnectionTest {
    @Test
    fun bothSidesComeOnline() {
        val net = TwoMachines()
        net.connect()
        assertTrue(net.a.peers().single { it.keyHex == net.bHex }.isOnline)
        assertTrue(net.b.peers().single { it.keyHex == net.aHex }.isOnline)
    }

    @Test
    fun peerIsVisibleButNotTrustedBeforePairing() {
        val net = TwoMachines()
        net.connect()
        assertFalse(net.a.peers().single { it.keyHex == net.bHex }.isTrusted)
    }

    @Test
    fun pairingRequestReachesTheOtherSide() {
        val net = TwoMachines()
        net.connect()
        net.a.requestPairing(net.bHex)
        net.pump()
        assertEquals(listOf("Аня"), net.bEvents.pairingRequests.map { it.displayName })
    }

    @Test
    fun acceptingPairingTrustsBothWays() {
        val net = TwoMachines()
        net.connect()
        net.pairThem()
        assertTrue(net.a.peers().single { it.keyHex == net.bHex }.isTrusted)
        assertTrue(net.b.peers().single { it.keyHex == net.aHex }.isTrusted)
    }

    @Test
    fun rejectionIsReportedToTheSender() {
        val net = TwoMachines()
        net.connect()
        net.a.requestPairing(net.bHex); net.pump()
        net.b.rejectPairing(net.aHex); net.pump()
        assertEquals(listOf("Борис"), net.aEvents.rejections.map { it.displayName })
        assertFalse(net.a.peers().single { it.keyHex == net.bHex }.isTrusted)
    }

    @Test
    fun disconnectMarksOutstandingCallFailed() {
        val net = TwoMachines()
        net.connect()
        net.pairThem()
        net.a.call(net.bHex, urgent = false)
        net.a.onDisconnected(net.bHex)
        assertTrue(net.aEvents.callStates.any { it.second == CallState.FAILED })
    }
}

class EngineCallTest {
    @Test
    fun callReachesTrustedPeer() {
        val net = TwoMachines()
        net.connect(); net.pairThem()
        val id = net.a.call(net.bHex, urgent = true)
        net.pump()
        assertEquals(listOf(Triple("Аня", id!!, true)), net.bEvents.incoming)
    }

    @Test
    fun callToUntrustedPeerIsRefused() {
        val net = TwoMachines()
        net.connect()
        assertNull(net.a.call(net.bHex, urgent = true))
    }

    @Test
    fun senderSeesDelivered() {
        val net = TwoMachines()
        net.connect(); net.pairThem()
        val id = net.a.call(net.bHex, urgent = false)
        net.pump()
        assertTrue(net.aEvents.callStates.contains(id!! to CallState.DELIVERED))
    }

    @Test
    fun senderSeesSeenAfterReceiverCloses() {
        val net = TwoMachines()
        net.connect(); net.pairThem()
        val id = net.a.call(net.bHex, urgent = false)
        net.pump()
        net.b.markSeen(net.aHex, id!!)
        net.pump()
        assertTrue(net.aEvents.callStates.contains(id to CallState.SEEN))
    }
}

class EnginePauseTest {
    @Test
    fun pauseSilencesOrdinaryCall() {
        val net = TwoMachines()
        net.connect(); net.pairThem()
        net.b.pauseUntil(net.clock.now + 900); net.pump()
        net.a.call(net.bHex, urgent = false); net.pump()
        assertEquals(emptyList(), net.bEvents.incoming)
    }

    @Test
    fun pauseSilencesUrgentCallToo() {
        val net = TwoMachines()
        net.connect(); net.pairThem()
        net.b.pauseUntil(net.clock.now + 900); net.pump()
        net.a.call(net.bHex, urgent = true); net.pump()
        assertEquals(emptyList(), net.bEvents.incoming)
    }

    @Test
    fun senderStillLearnsItWasDelivered() {
        val net = TwoMachines()
        net.connect(); net.pairThem()
        net.b.pauseUntil(net.clock.now + 900); net.pump()
        val id = net.a.call(net.bHex, urgent = true); net.pump()
        assertTrue(net.aEvents.callStates.contains(id!! to CallState.DELIVERED))
    }

    @Test
    fun pauseIsVisibleToTheSenderBeforeCalling() {
        val net = TwoMachines()
        net.connect(); net.pairThem()
        net.b.pauseUntil(net.clock.now + 900); net.pump()
        assertTrue(net.a.peers().single { it.keyHex == net.bHex }.isPaused)
    }

    @Test
    fun expiredPauseStopsSilencing() {
        val net = TwoMachines()
        net.connect(); net.pairThem()
        net.b.pauseUntil(net.clock.now + 60); net.pump()
        net.clock.now += 120
        net.a.call(net.bHex, urgent = false); net.pump()
        assertEquals(1, net.bEvents.incoming.size)
    }

    @Test
    fun resumingClearsPauseForTheSender() {
        val net = TwoMachines()
        net.connect(); net.pairThem()
        net.b.pauseUntil(net.clock.now + 900); net.pump()
        net.b.pauseUntil(null); net.pump()
        assertFalse(net.a.peers().single { it.keyHex == net.bHex }.isPaused)
    }
}
