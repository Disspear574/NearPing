package com.disspear574.nearping.linux

import java.io.BufferedReader
import java.util.concurrent.TimeUnit

/** Light or dark scheme from the XDG Desktop Portal (gdbus), falling back to gsettings; no D-Bus library. */
object SystemAppearance {

    fun prefersDark(): Boolean {
        val portal = portalValue("color-scheme")
        // 1 means prefer dark, 2 light, 0 no preference.
        Regex("uint32 (\\d)").find(portal.orEmpty())?.let { return it.groupValues[1] == "1" }
        val gtk = run("gsettings", "get", "org.gnome.desktop.interface", "color-scheme")
        return gtk?.contains("dark") == true
    }

    private fun portalValue(key: String): String? = run(
        "gdbus", "call", "--session",
        "--dest", "org.freedesktop.portal.Desktop",
        "--object-path", "/org/freedesktop/portal/desktop",
        "--method", "org.freedesktop.portal.Settings.ReadOne",
        "org.freedesktop.appearance", key,
    )

    private fun run(vararg command: String): String? = try {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use(BufferedReader::readText)
        if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly()
        if (process.exitValue() == 0) output else null
    } catch (e: Exception) {
        // Not Linux, no portal or no gsettings is normal: fall through to the fallback.
        null
    }
}
