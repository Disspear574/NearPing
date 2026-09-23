package com.disspear574.nearping.linux

import java.nio.file.Files
import java.nio.file.Path

/** X11/XWayland helper; no focus or mouse input, independent of the urgent banner. */
internal class ScreenEdges {
    private var process: Process? = null
    private var script: Path? = null
    fun show() {
        close()
        runCatching {
            val path = Files.createTempFile("nearping-edges-", ".py")
            script = path
            checkNotNull(javaClass.getResourceAsStream("/nearping-edges.py")).use {
                Files.copy(it, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            }
            val builder = ProcessBuilder("/usr/bin/python3", path.toString())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
            builder.environment()["GDK_BACKEND"] = "x11"
            process = builder.start()
        }.onFailure { close(); System.err.println("NearPing screen edges unavailable: ${it.message}") }
    }
    fun close() {
        process?.destroy()
        process = null
        script?.let { runCatching { Files.deleteIfExists(it) } }
        script = null
    }
}
