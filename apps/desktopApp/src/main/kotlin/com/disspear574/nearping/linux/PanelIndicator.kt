package com.disspear574.nearping.linux

import kotlinx.coroutines.delay
import java.nio.file.Files

internal enum class PanelEvent { READY, UNAVAILABLE, OPEN, PAUSE, RESUME, QUIT }

/** GTK lives in a helper process so it cannot conflict with Compose's AWT event loop. */
internal class PanelIndicator {
    suspend fun run(strings: Strings, onEvent: (PanelEvent) -> Unit) {
        val directory = Files.createTempDirectory("nearping-indicator-")
        var process: Process? = null
        try {
            for (name in listOf("nearping-indicator.py", "nearping.png")) {
                checkNotNull(javaClass.getResourceAsStream("/$name")).use {
                    Files.copy(it, directory.resolve(name))
                }
            }
            process = ProcessBuilder(
                "/usr/bin/python3", "-u", directory.resolve("nearping-indicator.py").toString(),
                directory.resolve("nearping.png").toString(),
                strings.openApp, "${strings.pause} · ${strings.minutes15}", strings.resume, strings.quit,
            ).apply { environment()["GDK_BACKEND"] = "x11" }
                .redirectError(ProcessBuilder.Redirect.INHERIT).start()
            // Do not block on readLine: cancellation must always destroy the helper.
            process.inputStream.use { input ->
                val pending = StringBuilder()
                while (process.isAlive || input.available() > 0) {
                    while (input.available() > 0) {
                        val byte = input.read()
                        if (byte == 10) {
                            PanelEvent.entries.firstOrNull { it.name == pending.toString() }?.let(onEvent)
                            pending.setLength(0)
                        } else if (byte >= 0 && pending.length < 128) {
                            pending.append(byte.toChar())
                        }
                    }
                    delay(100)
                }
            }
        } finally {
            process?.destroy()
            runCatching { process?.outputStream?.close() }
            Files.deleteIfExists(directory.resolve("nearping-indicator.py"))
            Files.deleteIfExists(directory.resolve("nearping.png"))
            Files.deleteIfExists(directory)
        }
    }
}
