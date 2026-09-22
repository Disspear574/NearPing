package com.disspear574.earshot.linux

import com.disspear574.earshot.core.EarshotNode
import java.nio.file.Path

/**
 * Проверка сети без интерфейса: поднимает узел и печатает, кого нашёл.
 * Нужна, чтобы отлаживать обнаружение и рукопожатие, не открывая окно.
 */
fun main(args: Array<String>) {
    val seconds = args.firstOrNull()?.toIntOrNull() ?: 20
    val directory = Path.of(System.getProperty("java.io.tmpdir"), "earshot-probe")
    val node = EarshotNode(directory)
    node.start()

    println("[probe] узел поднят, каталог $directory")
    var lastSummary = ""
    repeat(seconds * 2) {
        Thread.sleep(500)
        val state = node.state.value
        val summary = buildString {
            append("я=${state.myFingerprint} «${state.myDisplayName}»")
            append(" | найдено ${state.peers.size}: ")
            append(state.peers.joinToString("; ") { peer ->
                "${peer.displayName}[${peer.fingerprint}] " +
                    (if (peer.isOnline) "в сети" else "не в сети") +
                    (if (peer.isTrusted) ", доверенный" else "")
            })
        }
        if (summary != lastSummary) {
            println("[probe] $summary")
            lastSummary = summary
        }
    }
    node.stop()
    println("[probe] готово")
}
