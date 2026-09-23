package com.disspear574.nearping.linux

import java.io.BufferedReader
import java.util.concurrent.TimeUnit

/**
 * Светлая или тёмная схема из системы — через XDG Desktop Portal (gdbus),
 * с запасным путём на gsettings. Без библиотеки D-Bus.
 *
 * Акцент из системы здесь больше не читается: по решению пользователя
 * (2026-09-22, спека nearping-design) обе платформы красятся палитрой «Мох».
 */
object SystemAppearance {

    fun prefersDark(): Boolean {
        val portal = portalValue("color-scheme")
        // 1 — «предпочитаю тёмную», 2 — светлую, 0 — без предпочтения.
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
        // Не Linux, нет портала, нет gsettings — всё это нормально, идём к запасному пути.
        null
    }
}
