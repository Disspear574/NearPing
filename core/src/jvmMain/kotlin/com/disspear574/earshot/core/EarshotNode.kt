package com.disspear574.earshot.core

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.InetAddress
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Что показывает интерфейс. Плоский снимок, пересобирается целиком. */
data class NodeState(
    val myDisplayName: String = "",
    val myFingerprint: String = "",
    val pausedUntilEpochSeconds: Long? = null,
    val peers: List<PeerView> = emptyList(),
    val pendingRequests: List<Peer> = emptyList(),
    val statuses: Map<String, CallStatus> = emptyMap(),
    val networkInterfaces: List<NetworkInterfaceOption> = emptyList(),
    val selectedInterface: String? = null,
    val activeInterface: String? = null,
    val networkChanging: Boolean = false,
    val networkFailed: Boolean = false,
)

data class NetworkInterfaceOption(val name: String, val addresses: String, val multicast: Boolean)

data class CallStatus(val text: String, val kind: Kind) {
    enum class Kind { NEUTRAL, GOOD, WARN, BAD }
}

/** Входящий вызов, который интерфейс должен показать. */
data class IncomingCall(val keyHex: String, val displayName: String, val callId: String, val urgent: Boolean)

/**
 * Узел под JVM: собирает ядро, сокеты и mDNS.
 *
 * Ядро не потокобезопасно, а события приходят с потоков чтения, приёма соединений
 * и JmDNS. Поэтому всё, что трогает ядро, выполняется на одном потоке.
 */
class EarshotNode(directory: Path) {
    private val worker = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "earshot-engine").apply { isDaemon = true }
    }

    private val keyStore = FileIdentityStore(directory)
    private val peers = JvmPeerStorage(directory)
    private val crypto = JvmCrypto()
    private val clock = JvmClock()

    private val transport = SocketTransport(TransportBridge())
    private var discoveryEpoch = 0
    private var discovery = JmdnsDiscovery(DiscoveryBridge(discoveryEpoch))
    private val discovered = LinkedHashSet<String>()
    private val networkPreference = NetworkPreference(directory)
    private var selectedInterface = networkPreference.read()
    private var networkInterfaces: List<NetworkInterfaceOption> = emptyList()
    private var networkChanging = false
    private var networkFailed = false

    private val _state = MutableStateFlow(NodeState())
    val state: StateFlow<NodeState> = _state.asStateFlow()

    private val _calls = MutableSharedFlow<IncomingCall>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val calls: SharedFlow<IncomingCall> = _calls.asSharedFlow()

    private lateinit var engine: Engine
    private lateinit var identity: Identity
    private val statuses = LinkedHashMap<String, CallStatus>()

    fun start() = onWorker {
        identity = keyStore.loadOrCreate()
        engine = Engine(
            identity = identity,
            signer = keyStore.signer,
            crypto = crypto,
            registry = PeerRegistry(peers),
            transport = transport,
            clock = clock,
            listener = Events(),
        )
        transport.start()
        restartDiscovery()
        publish()
        var ticks = 0
        worker.scheduleWithFixedDelay({
            runCatching {
                engine.tick()
                if (++ticks % 5 == 0) engine.retryConnections()
            }.onFailure { it.printStackTrace() }
        }, 1, 1, TimeUnit.SECONDS)
    }

    fun stop() {
        discovery.stop()
        transport.stop()
        worker.shutdownNow()
    }

    fun refreshNetworkInterfaces() = onWorker {
        updateNetworkInterfaces()
        publish()
    }

    fun selectNetworkInterface(name: String?) = onWorker {
        // Save first: a write failure leaves the previous selection and live discovery intact.
        try { networkPreference.write(name) }
        catch (error: Exception) { networkFailed = true; publish(); return@onWorker }
        selectedInterface = name
        restartDiscovery()
    }

    private fun updateNetworkInterfaces() {
        networkInterfaces = runCatching {
            JmdnsDiscovery.availableInterfaces().groupBy { it.name }.map { (name, values) ->
                NetworkInterfaceOption(name, values.map { it.address }.distinct().sorted().joinToString(", "), values.all { it.multicast })
            }.sortedBy { it.name }
        }.getOrDefault(emptyList())
    }

    private fun restartDiscovery() {
        networkChanging = true
        networkFailed = false
        discoveryEpoch++
        publish()
        discovery.stop()
        discovered.toList().forEach { key -> transport.forgetEndpoint(key); engine.onPeerLost(key) }
        discovered.clear()
        discovery = JmdnsDiscovery(DiscoveryBridge(discoveryEpoch))
        updateNetworkInterfaces()
        try { discovery.start(identity, transport.port, selectedInterface) }
        catch (error: Exception) {
            discovery.stop()
            networkFailed = true
            System.err.println("NearPing discovery: ${error.message}")
        }
        networkChanging = false
        publish()
    }

    // MARK: действия пользователя

    fun rename(name: String) = onWorker {
        keyStore.setDisplayName(name)
        // Имя едет в hello и в TXT-запись: сессии переподнимаются, служба
        // перерегистрируется. Иначе соседи видят старое имя до перезапуска.
        engine.rename(name)
        restartDiscovery()
    }

    fun call(keyHex: String, urgent: Boolean) = onWorker {
        val id = engine.call(keyHex, urgent)
        status(keyHex, if (id == null) CallStatus("not delivered", CallStatus.Kind.BAD)
                       else CallStatus("sent", CallStatus.Kind.NEUTRAL))
    }

    fun requestPairing(keyHex: String) = onWorker {
        engine.requestPairing(keyHex)
        status(keyHex, CallStatus("request sent", CallStatus.Kind.WARN))
    }

    fun acceptPairing(keyHex: String) = onWorker { engine.acceptPairing(keyHex); publish() }
    fun rejectPairing(keyHex: String) = onWorker { engine.rejectPairing(keyHex); publish() }
    fun forget(keyHex: String) = onWorker { engine.forget(keyHex); publish() }
    fun markSeen(keyHex: String, callId: String) = onWorker { engine.markSeen(keyHex, callId) }

    fun pause(minutes: Int?) = onWorker {
        engine.pauseUntil(minutes?.let { clock.nowEpochSeconds() + it * 60 })
        publish()
    }

    // MARK: внутреннее

    private fun onWorker(body: () -> Unit) {
        worker.execute {
            runCatching(body).onFailure { it.printStackTrace() }
        }
    }

    private fun status(keyHex: String, value: CallStatus) {
        statuses[keyHex] = value
        publish()
    }

    private fun publish() {
        _state.value = NodeState(
            myDisplayName = identity.displayName,
            myFingerprint = identity.fingerprint,
            pausedUntilEpochSeconds = engine.pausedUntilEpochSeconds,
            peers = engine.peers(),
            pendingRequests = engine.pendingPairingRequests(),
            statuses = statuses.toMap(),
            networkInterfaces = networkInterfaces,
            selectedInterface = selectedInterface,
            activeInterface = discovery.boundInterface,
            networkChanging = networkChanging,
            networkFailed = networkFailed,
        )
    }

    private inner class Events : EngineListener {
        override fun onPeersChanged() = publish()

        override fun onPairingRequest(peer: Peer) = publish()

        override fun onPairingRejected(peer: Peer) =
            status(peer.keyHex, CallStatus("declined", CallStatus.Kind.BAD))

        override fun onIncomingCall(peer: Peer, callId: String, urgent: Boolean) {
            _calls.tryEmit(IncomingCall(peer.keyHex, peer.displayName, callId, urgent))
        }

        override fun onCallState(keyHex: String, callId: String, state: CallState) {
            when (state) {
                CallState.DELIVERED -> status(keyHex, CallStatus("delivered", CallStatus.Kind.NEUTRAL))
                CallState.SEEN -> status(keyHex, CallStatus("seen it", CallStatus.Kind.GOOD))
                CallState.FAILED -> status(keyHex, CallStatus("not delivered", CallStatus.Kind.BAD))
                CallState.SENT -> Unit
            }
        }
    }

    private inner class TransportBridge : TransportEvents {
        override fun onConnected(connectionId: String) = onWorker { engine.onConnected(connectionId) }
        override fun onBytes(connectionId: String, chunk: ByteArray) = onWorker { engine.onBytes(connectionId, chunk) }
        override fun onDisconnected(connectionId: String) = onWorker { engine.onDisconnected(connectionId) }
    }

    private inner class DiscoveryBridge(private val epoch: Int) : DiscoveryEvents {
        override fun onPeerFound(peer: Peer, address: InetAddress, port: Int) = onWorker {
            if (epoch != discoveryEpoch) return@onWorker
            discovered.add(peer.keyHex)
            transport.rememberEndpoint(peer.keyHex, address, port)
            engine.onPeerFound(peer, "${address.hostAddress}:$port")
        }

        override fun onPeerLost(keyHex: String) = onWorker {
            if (epoch != discoveryEpoch) return@onWorker
            discovered.remove(keyHex)
            transport.forgetEndpoint(keyHex)
            engine.onPeerLost(keyHex)
        }
    }
}
