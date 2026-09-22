package com.disspear574.earshot.linux

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Оформление по Yaru: плоско, без стекла. На macOS приложение выглядит иначе —
 * так и задумано, каждая платформа должна быть похожа на себя.
 */
object Yaru {
    val cardRadius = 12.dp
    val buttonRadius = 8.dp

    /**
     * Срочный вызов НЕ красится системным акцентом. При оранжевом акценте Ubuntu
     * по умолчанию он слился бы с ним, поэтому цвет фиксированный, а знак
     * дополнительно отличается формой — залитый кружок, не контурный.
     */
    val urgentLight = Color(0xFFC01C28)
    val urgentDark = Color(0xFFE04A40)

    val onlineLight = Color(0xFF1B7F4B)
    val onlineDark = Color(0xFF40D486)
    val pausedLight = Color(0xFF7F5C00)
    val pausedDark = Color(0xFFE8B33D)
    val offline = Color(0xFF8B8B95)
}

data class EarshotColors(
    val accent: Color,
    val urgent: Color,
    val online: Color,
    val paused: Color,
    val offline: Color,
    val isDark: Boolean,
)

val LocalEarshotColors = staticCompositionLocalOf {
    EarshotColors(
        accent = SystemAppearance.fallbackAccent,
        urgent = Yaru.urgentLight,
        online = Yaru.onlineLight,
        paused = Yaru.pausedLight,
        offline = Yaru.offline,
        isDark = false,
    )
}

@Composable
fun EarshotTheme(content: @Composable () -> Unit) {
    val accent = remember { SystemAppearance.accent() }
    val dark = remember { SystemAppearance.prefersDark() } || isSystemInDarkTheme()

    val scheme = if (dark) {
        darkColorScheme(
            primary = accent,
            background = Color(0xFF242424),
            surface = Color(0xFF303030),
            onSurface = Color.White,
            onSurfaceVariant = Color(0xFFB5B3B0),
        )
    } else {
        lightColorScheme(
            primary = accent,
            background = Color(0xFFFAFAFA),
            surface = Color.White,
            onSurface = Color(0xFF1E1E1E),
            onSurfaceVariant = Color(0xFF5E5C64),
        )
    }

    CompositionLocalProvider(
        LocalEarshotColors provides EarshotColors(
            accent = accent,
            urgent = if (dark) Yaru.urgentDark else Yaru.urgentLight,
            online = if (dark) Yaru.onlineDark else Yaru.onlineLight,
            paused = if (dark) Yaru.pausedDark else Yaru.pausedLight,
            offline = Yaru.offline,
            isDark = dark,
        )
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
