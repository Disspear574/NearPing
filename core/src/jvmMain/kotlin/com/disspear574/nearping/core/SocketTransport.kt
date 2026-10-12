package com.disspear574.nearping.core

import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

interface TransportEvents {
    fun onConnected(connectionId: String)
    fun onBytes(connectionId: String, chunk: ByteArray)
    fun onDisconnected(connectionId: String)
}

/** JVM TCP: a listening socket plus outgoing connections, one reader thread each. */
class SocketTransport(private val events: TransportEvents) : Transport {
    private val connections = ConcurrentHashMap<String, Socket>()
    private val endpoints = ConcurrentHashMap<String, Pair<InetAddress, Int>>()
    private val incoming = AtomicInteger()

    @Volatile private var server: ServerSocket? = null

    val port: Int get() = server?.localPort ?: 0

    fun start() {
        val socket = ServerSocket(0)
        server = socket
        thread("nearping-accept") {
            while (!socket.isClosed) {
                val client = try {
                    socket.accept()
                } catch (e: IOException) {
                    return@thread
                }
                adopt(client, "in:${incoming.incrementAndGet()}")
            }
        }
    }

    fun stop() {
        runCatching { server?.close() }
        connections.values.forEach { runCatching { it.close() } }
        connections.clear()
    }

    fun rememberEndpoint(keyHex: String, address: InetAddress, port: Int) {
        endpoints[keyHex] = address to port
    }

    fun forgetEndpoint(keyHex: String) {
        endpoints.remove(keyHex)
    }

    override fun connect(connectionId: String, endpoint: String) {
        val keyHex = connectionId.removePrefix(OUTGOING_PREFIX)
        val target = endpoints[keyHex] ?: return
        if (connections.containsKey(connectionId)) return
        thread("nearping-dial") {
            val socket = Socket()
            try {
                socket.connect(InetSocketAddress(target.first, target.second), 5000)
            } catch (e: IOException) {
                runCatching { socket.close() }
                if (System.getProperty("nearping.debug") != null) println("[nearping/tcp] connect failed: ${e.message}")
                events.onDisconnected(connectionId)
                return@thread
            }
            adopt(socket, connectionId)
        }
    }

    override fun send(connectionId: String, bytes: ByteArray) {
        val socket = connections[connectionId] ?: return
        try {
            val stream = socket.getOutputStream()
            synchronized(socket) {
                stream.write(bytes)
                stream.flush()
            }
        } catch (e: IOException) {
            close(connectionId, socket)
        }
    }

    override fun disconnect(connectionId: String) = close(connectionId)

    private fun adopt(socket: Socket, connectionId: String) {
        socket.tcpNoDelay = true
        connections[connectionId] = socket
        events.onConnected(connectionId)
        thread("nearping-read-$connectionId") {
            val buffer = ByteArray(BUFFER_BYTES)
            try {
                val stream = socket.getInputStream()
                while (true) {
                    val read = stream.read(buffer)
                    if (read <= 0) break
                    events.onBytes(connectionId, buffer.copyOf(read))
                }
            } catch (e: IOException) {
                // A drop is normal: sleeping machine, network blip.
            } finally {
                close(connectionId, socket)
            }
        }
    }

    private fun close(connectionId: String, expected: Socket? = null) {
        val socket = expected ?: connections[connectionId] ?: return
        // An old reader must never close a replacement connection with the same ID.
        if (!connections.remove(connectionId, socket)) return
        runCatching { socket.close() }
        events.onDisconnected(connectionId)
    }

    private fun thread(name: String, body: () -> Unit) {
        Thread(body, name).apply { isDaemon = true }.start()
    }

    companion object {
        const val OUTGOING_PREFIX = "out:"
        private const val BUFFER_BYTES = 64 * 1024
    }
}
