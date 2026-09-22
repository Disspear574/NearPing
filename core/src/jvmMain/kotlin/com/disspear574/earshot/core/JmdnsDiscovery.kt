package com.disspear574.earshot.core

import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.nio.file.Files
import java.nio.file.Path
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceInfo
import javax.jmdns.ServiceListener

interface DiscoveryEvents {
    fun onPeerFound(peer: Peer, address: InetAddress, port: Int)
    fun onPeerLost(keyHex: String)
}

/**
 * mDNS под JVM через JmDNS — тот же протокол, что Bonjour на macOS и Avahi на Linux,
 * поэтому клиенты разных платформ видят друг друга без посредников.
 */
class JmdnsDiscovery(private val events: DiscoveryEvents) {
    private fun debug(text: String) {
        if (System.getProperty("earshot.debug") != null) println("[earshot/mdns] $text")
    }

    @Volatile private var jmdns: JmDNS? = null
    var boundInterface: String? = null
        private set
    private var registered: ServiceInfo? = null

    fun start(identity: Identity, port: Int, preferredInterface: String? = null) {
        val bind = localAddress(preferredInterface)
        val dns = JmDNS.create(bind)
        jmdns = dns

        val info = ServiceInfo.create(
            "${TxtRecord.SERVICE_TYPE}.local.",
            // Имя службы должно быть уникальным в сети, но личностью не является:
            // узнают по ключу из TXT, а не по этой строке.
            "earshot-${identity.fingerprint}",
            port,
            0,
            0,
            TxtRecord.of(identity),
        )
        dns.registerService(info)
        registered = info

        dns.addServiceListener("${TxtRecord.SERVICE_TYPE}.local.", object : ServiceListener {
            override fun serviceAdded(event: ServiceEvent) {
                if (jmdns !== dns) return
                debug("added ${event.name}")
                dns.requestServiceInfo(event.type, event.name, true)
            }

            override fun serviceResolved(event: ServiceEvent) {
                if (jmdns !== dns) return
                debug("resolved ${event.name}")
                val resolved = event.info ?: return debug("no info")
                val txt = buildMap {
                    for (key in listOf("v", "pk", "n")) {
                        resolved.getPropertyString(key)?.let { put(key, it) }
                    }
                }
                debug("txt=$txt")
                val peer = TxtRecord.parse(txt) ?: return debug("txt did not parse")
                if (peer.publicKey.contentEquals(identity.publicKey)) return debug("that is me")
                // Первый отклик часто несёт только link-local IPv6 без области
                // видимости — соединиться по такому нельзя. Ждём отклика с IPv4.
                val address = resolved.inet4Addresses.firstOrNull()
                    ?: return debug("no IPv4 yet, waiting for the next answer")
                debug("found ${peer.displayName} at ${address.hostAddress}:${resolved.port}")
                events.onPeerFound(peer, address, resolved.port)
            }

            override fun serviceRemoved(event: ServiceEvent) {
                if (jmdns !== dns) return
                val txt = event.info?.getPropertyString("pk") ?: return
                val key = Base64Url.decode(txt)
                if (key.size != IDENTITY_KEY_BYTES) return
                events.onPeerLost(Peer(key, "").keyHex)
            }
        })
    }

    private fun localAddress(preferredInterface: String?): InetAddress {
        val candidates = availableInterfaces()
        candidates.forEach { debug("interface ${it.name}: ${it.address}, defaultRouteMetric=${it.routeMetric}, multicast=${it.multicast}") }
        val selected = LanInterfaceSelector.select(candidates, preferredInterface)
            ?: error("Selected network interface is unavailable: ${preferredInterface ?: "automatic"}")
        boundInterface = "${selected.name} · ${selected.address}"
        debug("binding to ${selected.address} on ${selected.name}")
        return InetAddress.getByName(selected.address)
    }

    fun stop() {
        val old = jmdns
        jmdns = null
        registered?.let { runCatching { old?.unregisterService(it) } }
        runCatching { old?.close() }
        registered = null
        boundInterface = null
    }

    companion object {
        private const val IDENTITY_KEY_BYTES = 32
        internal fun availableInterfaces(): List<LanInterfaceCandidate> {
            val routes = runCatching { Files.readString(Path.of("/proc/net/route")) }.getOrDefault("")
            val metrics = LanInterfaceSelector.defaultRouteMetrics(routes)
            return NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { iface ->
                    iface.inetAddresses.toList().filterIsInstance<Inet4Address>()
                        .filter { !it.isLoopbackAddress && !it.isAnyLocalAddress && !it.isMulticastAddress && !it.isLinkLocalAddress }
                        .map { address -> LanInterfaceCandidate(iface.name, address.hostAddress,
                            Files.exists(Path.of("/sys/class/net", iface.name, "device")), metrics[iface.name],
                            iface.supportsMulticast(), iface.isPointToPoint) }
                }
        }
    }
}
