package com.disspear574.earshot.core

import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
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

    private var jmdns: JmDNS? = null
    private var registered: ServiceInfo? = null

    fun start(identity: Identity, port: Int) {
        val bind = localAddress()
        debug("binding to ${bind?.hostAddress ?: "default"}")
        val dns = if (bind != null) JmDNS.create(bind) else JmDNS.create()
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
                debug("added ${event.name}")
                dns.requestServiceInfo(event.type, event.name, true)
            }

            override fun serviceResolved(event: ServiceEvent) {
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
                val txt = event.info?.getPropertyString("pk") ?: return
                val key = Base64Url.decode(txt)
                if (key.size != IDENTITY_KEY_BYTES) return
                events.onPeerLost(Peer(key, "").keyHex)
            }
        })
    }

    /**
     * InetAddress.getLocalHost() отдаёт адрес по имени хоста, а это может быть
     * петля или адрес чужого интерфейса. Тогда JmDNS отправляет пакеты, но не
     * слушает нужную группу — служба видна всем, а сам он не видит никого.
     */
    private fun localAddress(): InetAddress? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && it.supportsMulticast() }
            .flatMap { iface -> iface.inetAddresses.toList().map { iface to it } }
            .firstOrNull { (_, address) ->
                address is Inet4Address && !address.isLoopbackAddress && address.isSiteLocalAddress
            }
            ?.second
    }.getOrNull()

    fun stop() {
        registered?.let { runCatching { jmdns?.unregisterService(it) } }
        runCatching { jmdns?.close() }
        jmdns = null
    }

    private companion object {
        const val IDENTITY_KEY_BYTES = 32
    }
}
