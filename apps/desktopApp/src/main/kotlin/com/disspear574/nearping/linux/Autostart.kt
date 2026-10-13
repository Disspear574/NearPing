package com.disspear574.nearping.linux

import java.nio.file.Files
import java.nio.file.Path
import java.util.prefs.Preferences

/** Launch at login through the XDG autostart directory. */
class Autostart(private val preferences: Preferences) {
    private val launcher: String? = System.getenv("NEARPING_LAUNCHER")?.takeIf { it.isNotBlank() }
    private val file: Path = Path.of(
        System.getenv("XDG_CONFIG_HOME") ?: (System.getProperty("user.home") + "/.config"),
        "autostart", "nearping.desktop",
    )

    // Only a packaged launch has a launcher path that survives a reboot.
    val isAvailable: Boolean get() = launcher != null

    fun isEnabled(): Boolean = Files.exists(file)

    fun setEnabled(enabled: Boolean): Boolean = runCatching {
        if (enabled) write() else Files.deleteIfExists(file)
    }.isSuccess

    // Rewrites keep the entry pointing at a moved launcher.
    fun onStart() {
        if (!isAvailable) return
        if (!preferences.getBoolean(OFFERED_KEY, false)) {
            preferences.putBoolean(OFFERED_KEY, true)
            setEnabled(true)
        } else if (isEnabled()) {
            setEnabled(true)
        }
    }

    private fun write() {
        val path = checkNotNull(launcher)
        Files.createDirectories(file.parent)
        Files.writeString(
            file,
            """
            [Desktop Entry]
            Type=Application
            Name=NearPing
            Exec=${quote(path)} $BACKGROUND_ARG
            Icon=nearping
            Terminal=false
            X-GNOME-Autostart-enabled=true
            """.trimIndent() + "\n",
        )
    }

    private fun quote(path: String): String =
        "\"" + path.replace("\\", "\\\\").replace("\"", "\\\"").replace("`", "\\`").replace("$", "\\$") + "\""

    companion object {
        const val BACKGROUND_ARG = "--background"
        private const val OFFERED_KEY = "autostartOffered"
    }
}
