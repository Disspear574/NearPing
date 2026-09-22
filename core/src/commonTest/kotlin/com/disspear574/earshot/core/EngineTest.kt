package com.disspear574.earshot.core

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
private class TwoMachines(staleTrustedName: String? = null, legacyB: Boolean = false) {
    val clock = TestClock()
    val aKey = ByteArray(32) { 0x11 }
    val bKey = ByteArray(32) { 0x22 }
    val aHex = Peer(aKey, "").keyHex
    val bHex = Peer(bKey, "").keyHex

    val aEvents = Recorded()
    val bEvents = Recorded()
    private val queue = ArrayDeque<Triple<Boolean, String, ByteArray>>()

    val aDials = mutableListOf<String>()
    val bDials = mutableListOf<String>()

    private inner class Wire(private val toB: Boolean) : Transport {
        override fun connect(connectionId: String, endpoint: String) {
            if (toB) aDials += connectionId else bDials += connectionId
        }
        override fun send(connectionId: String, bytes: ByteArray) { queue += Triple(toB, connectionId, bytes) }
        override fun disconnect(connectionId: String) {}
    }

    val a = Engine(Identity(aKey, "Аня"), FakeSigner(aKey), FakeCrypto(1),
        PeerRegistry(MemoryPeers()).also { registry ->
            if (staleTrustedName != null) registry.trust(Peer(bKey, staleTrustedName))
        }, Wire(toB = true), clock, aEvents)
    private val bCrypto = FakeCrypto(2)
    private val compatibleCrypto = object : Crypto by bCrypto {
        override fun seal(key: ByteArray, plaintext: ByteArray): ByteArray {
            // Emulate an older peer's Presence on the actual encrypted session path.
            val payload = if (legacyB) plaintext.decodeToString()
                .replace(",\"heartbeat\":true", "").encodeToByteArray() else plaintext
            return bCrypto.seal(key, payload)
        }
    }
    val b = Engine(Identity(bKey, "Борис"), FakeSigner(bKey), compatibleCrypto,
        PeerRegistry(MemoryPeers()), Wire(toB = false), clock, bEvents)

    // Аня набирает (её ключ меньше), Борис принимает входящее.
    val aConn = "out:$bHex"
    val bConn = "in:1"

    fun connect() {
        a.onPeerFound(Peer(bKey, "Борис"), "b:1")
        b.onPeerFound(Peer(aKey, "Аня"), "a:1")
        a.onConnected(aConn)
        b.onConnected(bConn)
        pump()
    }

    fun pump() {
        var guard = 0
        while (queue.isNotEmpty() && guard++ < 200) {
            val (toB, _, bytes) = queue.removeFirst()
            if (toB) b.onBytes(bConn, bytes) else a.onBytes(aConn, bytes)
        }
    }

    fun pairThem() {
        a.requestPairing(bHex); pump()
        b.acceptPairing(aHex); pump()
    }
}

class EngineConnectionTest {
    @Test fun legacyPeerStaysCompatibleWithoutFalseOfflineStatus() {
        val net = TwoMachines(legacyB = true)
        net.connect(); net.pairThem()
        repeat(60) { net.clock.now++; net.a.tick(); net.pump() }
        assertTrue(net.a.peers().single().isOnline)
        net.a.call(net.bHex, false); net.pump()
        assertEquals(1, net.bEvents.incoming.size)
    }

    @Test fun silentPeerExpiresAfterTwelveSecondsAndCanReconnect() {
        val net = TwoMachines()
        net.connect(); net.pairThem()
        net.clock.now += 11
        net.a.tick()
        assertTrue(net.a.peers().single().isOnline)
        net.clock.now += 1
        net.a.tick()
        assertFalse(net.a.peers().single().isOnline)
        net.a.retryConnections()
        assertEquals(2, net.aDials.size)
        net.b.onDisconnected(net.bConn)
        net.pump() // Discard packets for the closed sessions.
        net.connect()
        assertTrue(net.a.peers().single().isOnline)
        assertTrue(net.b.peers().single().isOnline)
    }

    @Test fun heartbeatsKeepIdlePausedPeersOnlineWithoutAlerts() {
        val net = TwoMachines()
        net.connect(); net.pairThem()
        net.b.pauseUntil(net.clock.now + 120); net.pump()
        repeat(60) {
            net.clock.now++
            net.a.tick(); net.b.tick(); net.pump()
            assertTrue(net.a.peers().single().isOnline)
            assertTrue(net.b.peers().single().isOnline)
        }
        assertTrue(net.a.peers().single().isPaused)
        assertTrue(net.aEvents.incoming.isEmpty())
        assertTrue(net.bEvents.incoming.isEmpty())
        assertEquals(0L, net.a.callCooldownSeconds(net.bHex, false))
    }

    @Test fun oneWayLossExpiresTheSideThatReceivesNothing() {
        val net = TwoMachines()
        net.connect()
        repeat(4) {
            net.clock.now += 3
            net.b.tick(); net.pump()
        }
        assertTrue(net.a.peers().single().isOnline)
        assertFalse(net.b.peers().single().isOnline)
    }

    @Test fun stalledHandshakeDoesNotPreventRetryForever() {
        val net = TwoMachines()
        net.a.onPeerFound(Peer(net.bKey, "Борис"), "b:1")
        net.a.onConnected(net.aConn)
        net.clock.now += 12
        net.a.tick(); net.a.retryConnections()
        assertEquals(2, net.aDials.size)
    }

    @Test fun retriesFailedDialBeforeHandshake() {
        val net = TwoMachines()
        net.a.onPeerFound(Peer(net.bKey, "Борис"), "b:1")
        net.a.retryConnections()
        assertEquals(1, net.aDials.size)
        net.a.onDisconnected(net.aConn)
        net.a.retryConnections()
        assertEquals(2, net.aDials.size)
        net.a.onPeerLost(net.bHex)
        net.a.retryConnections()
        assertEquals(2, net.aDials.size)
    }

    @Test fun retriesDisconnectedPeerWithoutNewDiscovery() {
        val net = TwoMachines()
        net.connect(); net.pairThem()
        net.a.retryConnections()
        assertEquals(1, net.aDials.size)
        net.a.onDisconnected(net.aConn)
        net.a.retryConnections()
        assertEquals(2, net.aDials.size)
        net.b.retryConnections()
        assertTrue(net.bDials.isEmpty())
    }

    @Test
    fun authenticatedReconnectUpdatesTrustedName() {
        val net = TwoMachines(staleTrustedName = "Old name")
        net.a.onPeerFound(Peer(net.bKey, "Unauthenticated name"), "b:1")
        assertEquals("Old name", net.a.peers().single().displayName)
        net.connect()
        assertEquals("Борис", net.a.peers().single().displayName)
        assertTrue(net.a.peers().single().isTrusted)
    }

    @Test
    fun bothSidesComeOnline() {
        val net = TwoMachines()
        net.connect()
        assertTrue(net.a.peers().single { it.keyHex == net.bHex }.isOnline)
        assertTrue(net.b.peers().single { it.keyHex == net.aHex }.isOnline)
    }

    @Test
    fun onlyTheSmallerKeyDials() {
        val net = TwoMachines()
        net.a.onPeerFound(Peer(net.bKey, "Борис"), "b:1")
        net.b.onPeerFound(Peer(net.aKey, "Аня"), "a:1")
        // Иначе обе стороны набирают одновременно и держат два соединения на пару.
        assertEquals(listOf("out:${net.bHex}"), net.aDials)
        assertEquals(emptyList(), net.bDials)
    }

    @Test
    fun repeatedDiscoveryDoesNotDialTwice() {
        val net = TwoMachines()
        repeat(3) { net.a.onPeerFound(Peer(net.bKey, "Борис"), "b:1") }
        assertEquals(1, net.aDials.size)
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
        net.a.onDisconnected(net.aConn)
        assertTrue(net.aEvents.callStates.any { it.second == CallState.FAILED })
    }
}

class EngineCallTest {
    @Test
    fun repeatedPingIsBlockedButUrgentRemainsAvailable() {
        val net = TwoMachines()
        net.connect(); net.pairThem()
        assertTrue(net.a.call(net.bHex, false) != null); net.pump()
        assertNull(net.a.call(net.bHex, false)); net.pump()
        assertEquals(1, net.bEvents.incoming.size)
        assertEquals(3L, net.a.callCooldownSeconds(net.bHex, false))
        assertTrue(net.a.call(net.bHex, true) != null); net.pump()
        assertNull(net.a.call(net.bHex, true))
        assertEquals(2, net.bEvents.incoming.size)
        net.clock.now += 3
        assertTrue(net.a.call(net.bHex, false) != null); net.pump()
        assertEquals(3, net.bEvents.incoming.size)
    }

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

class EngineRepairPairingTest {
    @Test fun oneSidedDeletionCanPairAgain() {
        val net = TwoMachines()
        net.connect(); net.pairThem()
        net.a.forget(net.bHex)
        assertFalse(net.a.peers().single().isTrusted)
        assertTrue(net.b.peers().single().isTrusted)
        net.a.requestPairing(net.bHex); net.pump()
        assertEquals(net.aHex, net.b.pendingPairingRequests().single().keyHex)
        val notifications = net.bEvents.pairingRequests.size
        net.a.requestPairing(net.bHex); net.pump()
        assertEquals(notifications, net.bEvents.pairingRequests.size)
        net.b.acceptPairing(net.aHex); net.pump()
        assertTrue(net.a.peers().single().isTrusted)
        assertTrue(net.b.peers().single().isTrusted)
        assertTrue(net.b.pendingPairingRequests().isEmpty())
        net.a.call(net.bHex, false); net.pump()
        assertEquals(1, net.bEvents.incoming.size)
        net.b.call(net.aHex, false); net.pump()
        assertEquals(1, net.aEvents.incoming.size)
    }
    @Test fun rejectingRepairDoesNotRestoreDeletedTrustAndCanAskAgain() {
        val net = TwoMachines()
        net.connect(); net.pairThem(); net.a.forget(net.bHex)
        net.a.requestPairing(net.bHex); net.pump()
        net.b.rejectPairing(net.aHex); net.pump()
        assertFalse(net.a.peers().single().isTrusted)
        assertEquals(1, net.aEvents.rejections.size)
        net.a.requestPairing(net.bHex); net.pump()
        assertEquals(1, net.b.pendingPairingRequests().size)
    }
    @Test fun deletionInvalidatesAcceptanceAlreadyInFlight() {
        val net = TwoMachines()
        net.connect()
        net.a.requestPairing(net.bHex); net.pump()
        net.b.acceptPairing(net.aHex)
        net.a.forget(net.bHex)
        net.pump()
        assertFalse(net.a.peers().single().isTrusted)
    }
    @Test fun deletionClearsIncomingRequest() {
        val net = TwoMachines()
        net.connect()
        net.a.requestPairing(net.bHex); net.pump()
        net.b.forget(net.aHex)
        assertTrue(net.b.pendingPairingRequests().isEmpty())
        net.b.acceptPairing(net.aHex); net.pump()
        assertFalse(net.a.peers().single().isTrusted)
        assertFalse(net.b.peers().single().isTrusted)
    }
}
