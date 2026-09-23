package com.disspear574.nearping.linux

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit

/** Native desktop notifications, independent of the panel helper process. */
internal object DesktopNotifications {
    /**
     * Иконка кладётся из ресурсов в кэш пользователя. Путь `/usr/share/icons/...`
     * существует только после установки из .deb; в portable-сборке его нет,
     * и уведомление приходило без иконки.
     */
    private val iconPath: String? by lazy {
        runCatching {
            val cache = Path.of(System.getenv("XDG_CACHE_HOME") ?: (System.getProperty("user.home") + "/.cache"), "nearping")
            Files.createDirectories(cache)
            val target = cache.resolve("nearping.png")
            checkNotNull(javaClass.getResourceAsStream("/nearping.png")).use {
                Files.copy(it, target, StandardCopyOption.REPLACE_EXISTING)
            }
            target.toAbsolutePath().toString()
        }.getOrElse {
            val packaged = Path.of("/usr/share/icons/hicolor/256x256/apps/nearping.png")
            if (Files.exists(packaged)) packaged.toString() else null
        }
    }

    suspend fun show(name: String, body: String, urgent: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        var process: Process? = null
        try {
            process = ProcessBuilder(
                "/usr/bin/notify-send", "--app-name=NearPing",
                "--hint=string:desktop-entry:nearping",
                *(iconPath?.let { arrayOf("--icon=$it") } ?: emptyArray()),
                "--urgency=${if (urgent) "critical" else "normal"}",
                "--expire-time=${if (urgent) "10000" else "5000"}",
                "--", name, body,
            ).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.INHERIT).start()
            process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0
        } catch (error: Exception) {
            System.err.println("NearPing notification failed: ${error.message}")
            false
        } finally {
            process?.destroy()
        }
    }
}
