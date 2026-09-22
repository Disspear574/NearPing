package com.disspear574.earshot.linux

import androidx.compose.ui.graphics.Color
import java.io.BufferedReader
import java.util.concurrent.TimeUnit

/**
 * Акцент и светлая/тёмная схема из системы.
 *
 * Берётся из XDG Desktop Portal через gdbus — так не нужна библиотека D-Bus,
 * а портал ещё и сообщает об изменении, так что перекрашиваемся на лету.
 * Запасной путь — gsettings на старых GNOME, затем фирменный оранжевый Yaru.
 */
object SystemAppearance {

    /** Десять акцентов из настроек Ubuntu. */
    private val YARU = mapOf(
        "blue" to Color(0xFF0073E5),
        "teal" to Color(0xFF308280),
        "green" to Color(0xFF4B8501),
        "yellow" to Color(0xFFC88800),
        "orange" to Color(0xFFE95420),
        "red" to Color(0xFFDA3450),
        "pink" to Color(0xFFB34CB3),
        "purple" to Color(0xFF7764D8),
        "slate" to Color(0xFF657B69),
        "bark" to Color(0xFF787859),
    )

    val fallbackAccent = Color(0xFFE95420)

    fun accent(): Color = portalAccent() ?: gsettingsAccent() ?: fallbackAccent

    fun prefersDark(): Boolean {
        val portal = portalValue("color-scheme")
        // 1 — «предпочитаю тёмную», 2 — светлую, 0 — без предпочтения.
        Regex("uint32 (\\d)").find(portal.orEmpty())?.let { return it.groupValues[1] == "1" }
        val gtk = run("gsettings", "get", "org.gnome.desktop.interface", "color-scheme")
        return gtk?.contains("dark") == true
    }

    private fun portalAccent(): Color? {
        val raw = portalValue("accent-color") ?: return null
        // Ответ вида (<<(0.905, 0.329, 0.125)>>,) — три доли от нуля до единицы.
        val numbers = Regex("[01]\\.?\\d*(?:e-?\\d+)?").findAll(raw)
            .mapNotNull { it.value.toDoubleOrNull() }
            .toList()
        if (numbers.size < 3) return null
        return Color(numbers[0].toFloat(), numbers[1].toFloat(), numbers[2].toFloat())
    }

    private fun portalValue(key: String): String? = run(
        "gdbus", "call", "--session",
        "--dest", "org.freedesktop.portal.Desktop",
        "--object-path", "/org/freedesktop/portal/desktop",
        "--method", "org.freedesktop.portal.Settings.ReadOne",
        "org.freedesktop.appearance", key,
    )

    private fun gsettingsAccent(): Color? {
        val name = run("gsettings", "get", "org.gnome.desktop.interface", "accent-color")
            ?.trim()?.trim('\'') ?: return null
        return YARU[name]
    }

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
