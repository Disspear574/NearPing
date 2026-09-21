package app.earshot.core

import app.earshot.core.wire.Message
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class MemoryStorage(private var peers: List<Peer> = emptyList()) : PeerStorage {
    override fun load() = peers
    override fun save(peers: List<Peer>) { this.peers = peers }
}

private fun peer(marker: Byte, name: String) = Peer(ByteArray(32) { marker }, name)

class PeerRegistryTest {
    @Test
    fun unknownPeerIsNotTrusted() {
        assertFalse(PeerRegistry(MemoryStorage()).isTrusted(peer(1, "Дима").publicKey))
    }

    @Test
    fun trustSurvivesReload() {
        val storage = MemoryStorage()
        PeerRegistry(storage).trust(peer(1, "Дима"))
        assertTrue(PeerRegistry(storage).isTrusted(peer(1, "Дима").publicKey))
    }

    @Test
    fun trustIsKeyedOnPublicKeyNotName() {
        val registry = PeerRegistry(MemoryStorage())
        registry.trust(peer(1, "Дима"))
        assertTrue(registry.isTrusted(peer(1, "Дмитрий Кравцов").publicKey))
    }

    @Test
    fun retrustUpdatesNameWithoutDuplicating() {
        val registry = PeerRegistry(MemoryStorage())
        registry.trust(peer(1, "Дима"))
        registry.trust(peer(1, "Дмитрий"))
        assertEquals(1, registry.trusted.size)
        assertEquals("Дмитрий", registry.trusted.first().displayName)
    }

    @Test
    fun forgettingRevokesTrust() {
        val registry = PeerRegistry(MemoryStorage())
        registry.trust(peer(1, "Дима"))
        registry.forget(peer(1, "Дима").publicKey)
        assertFalse(registry.isTrusted(peer(1, "Дима").publicKey))
    }

    @Test
    fun forgettingOnePeerKeepsOthers() {
        val registry = PeerRegistry(MemoryStorage())
        registry.trust(peer(1, "Дима"))
        registry.trust(peer(2, "Аня"))
        registry.forget(peer(1, "Дима").publicKey)
        assertEquals(listOf("Аня"), registry.trusted.map { it.displayName })
    }
}

class PairingMachineTest {
    @Test
    fun incomingRequestFromStrangerAsksUser() {
        val machine = PairingMachine(PeerRegistry(MemoryStorage()))
        val decision = machine.onIncoming(Message.PairRequest("Дима"), peer(1, "Дима"))
        assertEquals(PairingDecision.ASK_USER, decision)
    }

    @Test
    fun incomingRequestFromTrustedPeerIsNoop() {
        val registry = PeerRegistry(MemoryStorage())
        registry.trust(peer(1, "Дима"))
        val machine = PairingMachine(registry)
        assertEquals(
            PairingDecision.ALREADY_TRUSTED,
            machine.onIncoming(Message.PairRequest("Дима"), peer(1, "Дима")),
        )
        assertEquals(emptyList(), machine.pendingRequests.map { it.displayName })
    }

    @Test
    fun acceptingMakesPeerTrusted() {
        val registry = PeerRegistry(MemoryStorage())
        val machine = PairingMachine(registry)
        machine.onIncoming(Message.PairRequest("Дима"), peer(1, "Дима"))
        machine.accept(peer(1, "Дима"))
        assertTrue(registry.isTrusted(peer(1, "Дима").publicKey))
        assertEquals(emptyList(), machine.pendingRequests)
    }

    @Test
    fun acceptUsesNameFromRequestNotFromAdvertisement() {
        val registry = PeerRegistry(MemoryStorage())
        val machine = PairingMachine(registry)
        machine.onIncoming(Message.PairRequest("Дмитрий Кравцов"), peer(1, "старое имя"))
        machine.accept(peer(1, "старое имя"))
        assertEquals("Дмитрий Кравцов", registry.trusted.first().displayName)
    }

    @Test
    fun rejectingLeavesPeerUntrustedAndClearsPending() {
        val registry = PeerRegistry(MemoryStorage())
        val machine = PairingMachine(registry)
        machine.onIncoming(Message.PairRequest("Дима"), peer(1, "Дима"))
        machine.reject(peer(1, "Дима"))
        assertFalse(registry.isTrusted(peer(1, "Дима").publicKey))
        assertEquals(emptyList(), machine.pendingRequests)
    }

    @Test
    fun repeatedRequestDoesNotQueueTwice() {
        val machine = PairingMachine(PeerRegistry(MemoryStorage()))
        machine.onIncoming(Message.PairRequest("Дима"), peer(1, "Дима"))
        machine.onIncoming(Message.PairRequest("Дима"), peer(1, "Дима"))
        assertEquals(1, machine.pendingRequests.size)
    }

    @Test
    fun acceptingWithoutPendingRequestStillTrusts() {
        val registry = PeerRegistry(MemoryStorage())
        PairingMachine(registry).accept(peer(1, "Дима"))
        assertTrue(registry.isTrusted(peer(1, "Дима").publicKey))
    }
}
