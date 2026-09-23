package com.disspear574.nearping.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class FixedClock(var now: Long = 1_000) : Clock {
    override fun nowEpochSeconds(): Long = now
}

private class Quiet : EngineListener {
    val rejections = mutableListOf<String>()
    val pairingRequests = mutableListOf<String>()
    override fun onPeersChanged() {}
    override fun onPairingRequest(peer: Peer) { pairingRequests += peer.displayName }
    override fun onPairingRejected(peer: Peer) { rejections += peer.displayName }
    override fun onIncomingCall(peer: Peer, callId: String, urgent: Boolean) {}
    override fun onCallState(keyHex: String, callId: String, state: CallState) {}
}

/**
 * Две машины, где сторону А можно «перезапустить»: новый движок на том же ключе
 * и том же хранилище доверия, как это бывает после закрытия приложения.
 */
private class Restartable {
    val clock = FixedClock()
    val aKey = ByteArray(32) { 0x11 }
    val bKey = ByteArray(32) { 0x22 }
    val aHex = Peer(aKey, "").keyHex
    val bHex = Peer(bKey, "").keyHex
    val aStorage = MemoryPeers()
    val bStorage = MemoryPeers()
    val aEvents = Quiet()
    val bEvents = Quiet()
    val aConn = "out:$bHex"
    val bConn = "in:1"
    private val queue = ArrayDeque<Pair<Boolean, ByteArray>>()

    private inner class Wire(private val toB: Boolean) : Transport {
        override fun connect(connectionId: String, endpoint: String) {}
        override fun send(connectionId: String, bytes: ByteArray) { queue += toB to bytes }
        override fun disconnect(connectionId: String) {}
    }

    var a = newA("Аня")
    val b = Engine(Identity(bKey, "Борис"), FakeSigner(bKey), FakeCrypto(2),
        PeerRegistry(bStorage), Wire(toB = false), clock, bEvents)

    private fun newA(name: String) = Engine(Identity(aKey, name), FakeSigner(aKey), FakeCrypto(1),
        PeerRegistry(aStorage), Wire(toB = true), clock, aEvents)

    fun connect() {
        a.onPeerFound(Peer(bKey, "Борис"), "b:1")
        b.onPeerFound(Peer(aKey, "Аня"), "a:1")
        a.onConnected(aConn)
        b.onConnected(bConn)
        pump()
    }

    /** Приложение А закрылось и открылось: новый движок, та же личность, то же доверие. */
    fun restartA() {
        b.onDisconnected(bConn)
        a = newA("Аня")
        queue.clear()
        connect()
    }

    /** Обе стороны потеряли соединение (например, после rename) и переподнялись. */
    fun reconnect() {
        b.onDisconnected(bConn)
        queue.clear()
        a.onConnected(aConn)
        b.onConnected(bConn)
        pump()
    }

    fun pump() {
        var guard = 0
        while (queue.isNotEmpty() && guard++ < 200) {
            val (toB, bytes) = queue.removeFirst()
            if (toB) b.onBytes(bConn, bytes) else a.onBytes(aConn, bytes)
        }
    }
}

class EnginePairingAfterRestartTest {
    @Test
    fun acceptanceThatArrivesAfterRestartIsNotTrustedBlindly() {
        val net = Restartable()
        net.connect()
        net.a.requestPairing(net.bHex); net.pump()
        assertEquals(listOf("Аня"), net.bEvents.pairingRequests)

        // Борис думал десять минут, Аня за это время перезапустила приложение.
        // Согласие без живого запроса не принимается: иначе любой в сети
        // прислал бы pair_accept и оказался в доверенных.
        net.restartA()
        net.b.acceptPairing(net.aHex); net.pump()

        assertFalse(net.a.peers().single { it.keyHex == net.bHex }.isTrusted)
        assertTrue(net.b.peers().single { it.keyHex == net.aHex }.isTrusted)
    }

    @Test
    fun oneMoreAddAfterRestartRepairsThePair() {
        val net = Restartable()
        net.connect()
        net.a.requestPairing(net.bHex); net.pump()
        net.restartA()
        net.b.acceptPairing(net.aHex); net.pump()

        // Аня жмёт «Добавить» ещё раз. Бориса спрашивают повторно — у него
        // есть право отказать, — он соглашается, и пара восстановлена.
        net.a.requestPairing(net.bHex); net.pump()
        assertEquals(2, net.bEvents.pairingRequests.size)
        net.b.acceptPairing(net.aHex); net.pump()

        assertTrue(net.a.peers().single { it.keyHex == net.bHex }.isTrusted)
        assertTrue(net.b.peers().single { it.keyHex == net.aHex }.isTrusted)
    }

    @Test
    fun trustedPeerThatReinstalledCanPairAgain() {
        val net = Restartable()
        net.connect()
        net.a.requestPairing(net.bHex); net.pump()
        net.b.acceptPairing(net.aHex); net.pump()
        // Аня переустановила приложение и потеряла список контактов, ключ тот же.
        net.aStorage.save(emptyList())
        net.restartA()
        net.a.requestPairing(net.bHex); net.pump()
        net.b.acceptPairing(net.aHex); net.pump()
        assertTrue(net.a.peers().single { it.keyHex == net.bHex }.isTrusted)
    }
}

class EngineRenameTest {
    @Test
    fun renameReachesTrustedPeerAfterReconnect() {
        val net = Restartable()
        net.connect()
        net.a.requestPairing(net.bHex); net.pump()
        net.b.acceptPairing(net.aHex); net.pump()

        net.a.rename("Анна Петрова")
        net.reconnect()

        val seenByB = net.b.peers().single { it.keyHex == net.aHex }
        assertEquals("Анна Петрова", seenByB.displayName)
        assertTrue(seenByB.isTrusted, "переименование не должно трогать доверие")
    }

    @Test
    fun renameKeepsPendingIncomingRequest() {
        val net = Restartable()
        net.connect()
        net.b.requestPairing(net.aHex); net.pump()
        assertEquals(1, net.a.pendingPairingRequests().size)
        net.a.rename("Анна")
        assertEquals(1, net.a.pendingPairingRequests().size, "запрос не должен пропасть при смене имени")
    }

    @Test
    fun renameKeepsPause() {
        val net = Restartable()
        net.connect()
        net.a.pauseUntil(net.clock.now + 600)
        net.a.rename("Анна")
        assertTrue(net.a.isPaused)
        assertEquals(net.clock.now + 600, net.a.pausedUntilEpochSeconds)
    }

    @Test
    fun blankNameFallsBackAndTrimsOnRename() {
        val net = Restartable()
        net.a.rename("   ")
        assertEquals(Identity.FALLBACK_NAME, net.a.myDisplayName)
        net.a.rename("  Аня  ")
        assertEquals("Аня", net.a.myDisplayName)
    }
}
