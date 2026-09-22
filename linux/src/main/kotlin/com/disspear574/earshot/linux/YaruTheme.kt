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

/** Shared moss palette, with platform-native controls. */
object Yaru {
    val cardRadius = 12.dp
    val buttonRadius = 8.dp

    /**
     * Срочный вызов НЕ красится системным акцентом. При оранжевом акценте Ubuntu
     * по умолчанию он слился бы с ним, поэтому цвет фиксированный, а знак
     * дополнительно отличается формой — залитый кружок, не контурный.
     */
    val accentLight = Color(0xFF4E5A40)
    val accentDark = Color(0xFFB5BEA3)

    val urgentLight = Color(0xFFA53F32)
    val urgentDark = Color(0xFFE9A092)

    val onlineLight = Color(0xFF4E5A40)
    val onlineDark = Color(0xFFB5BEA3)
    val pausedLight = Color(0xFF785917)
    val pausedDark = Color(0xFFD5BD80)
    val offlineLight = Color(0xFF6E6E78)
    val offlineDark = Color(0xFFA0A0AA)
}

data class EarshotColors(
    val accent: Color,
    val urgent: Color,
    val online: Color,
    val paused: Color,
    val offline: Color,
    val isDark: Boolean,
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

val LocalEarshotColors = staticCompositionLocalOf {
    EarshotColors(
        accent = Yaru.accentLight,
        urgent = Yaru.urgentLight,
        online = Yaru.onlineLight,
        paused = Yaru.pausedLight,
        offline = Yaru.offlineLight,
        isDark = false,
    )
}

@Composable
fun EarshotTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val systemDark = remember { SystemAppearance.prefersDark() } || isSystemInDarkTheme()
    val dark = when (mode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val accent = if (dark) Yaru.accentDark else Yaru.accentLight
    val background = if (dark) Color(0xFF141A14) else Color(0xFFF3F2ED)
    val surface = if (dark) Color(0xFF293225) else Color(0xFFE4E7DC)
    val primaryText = if (dark) Color(0xFFF0EEE5) else Color(0xFF232B22)
    val secondaryText = if (dark) Color(0xFFB7BDB0) else Color(0xFF5A6254)

    val scheme = if (dark) {
        darkColorScheme(
            primary = accent,
            onPrimary = background,
            primaryContainer = surface,
            onPrimaryContainer = primaryText,
            secondary = accent,
            onSecondary = background,
            secondaryContainer = surface,
            onSecondaryContainer = primaryText,
            tertiary = accent,
            onTertiary = background,
            tertiaryContainer = surface,
            onTertiaryContainer = primaryText,
            background = background,
            onBackground = primaryText,
            surface = surface,
            onSurface = primaryText,
            surfaceVariant = surface,
            onSurfaceVariant = secondaryText,
            outline = secondaryText,
            outlineVariant = secondaryText.copy(alpha = 0.45f),
            inverseSurface = primaryText,
            inverseOnSurface = background,
            inversePrimary = Color(0xFF4E5A40),
            surfaceTint = accent,
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = Color(0xFFF0EEE5),
            primaryContainer = surface,
            onPrimaryContainer = primaryText,
            secondary = accent,
            onSecondary = Color(0xFFF0EEE5),
            secondaryContainer = surface,
            onSecondaryContainer = primaryText,
            tertiary = accent,
            onTertiary = Color(0xFFF0EEE5),
            tertiaryContainer = surface,
            onTertiaryContainer = primaryText,
            background = background,
            onBackground = primaryText,
            surface = surface,
            onSurface = primaryText,
            surfaceVariant = surface,
            onSurfaceVariant = secondaryText,
            outline = secondaryText,
            outlineVariant = secondaryText.copy(alpha = 0.35f),
            inverseSurface = primaryText,
            inverseOnSurface = background,
            inversePrimary = Color(0xFFB5BEA3),
            surfaceTint = accent,
        )
    }

    CompositionLocalProvider(
        LocalEarshotColors provides EarshotColors(
            accent = accent,
            urgent = if (dark) Yaru.urgentDark else Yaru.urgentLight,
            online = if (dark) Yaru.onlineDark else Yaru.onlineLight,
            paused = if (dark) Yaru.pausedDark else Yaru.pausedLight,
            offline = if (dark) Yaru.offlineDark else Yaru.offlineLight,
            isDark = dark,
        )
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
