package com.disspear574.nearping.linux

import com.disspear574.nearping.core.NearPingNode
import java.nio.file.Path

fun main(args: Array<String>) {
    val seconds = args.firstOrNull()?.toIntOrNull() ?: 20
    val directory = Path.of(System.getProperty("java.io.tmpdir"), "nearping-probe")
    val node = NearPingNode(directory)
    node.start()

    println("[probe] node started, directory $directory")
    var lastSummary = ""
    repeat(seconds * 2) {
        Thread.sleep(500)
        val state = node.state.value
        val summary = buildString {
            append("me=${state.myFingerprint} «${state.myDisplayName}»")
            append(" | found ${state.peers.size}: ")
            append(state.peers.joinToString("; ") { peer ->
                "${peer.displayName}[${peer.fingerprint}] " +
                    (if (peer.isOnline) "online" else "offline") +
                    (if (peer.isTrusted) ", trusted" else "")
            })
        }
        if (summary != lastSummary) {
            println("[probe] $summary")
            lastSummary = summary
        }
    }
    node.stop()
    println("[probe] done")
}
