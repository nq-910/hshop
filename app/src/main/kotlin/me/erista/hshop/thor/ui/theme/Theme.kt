package me.erista.hshop.thor.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import me.erista.hshop.thor.data.AppTheme

val HShopDarkBg = Color(0xFF0D0E11)
val HShopSurface = Color(0xFF16181D)
val HShopSurfaceVariant = Color(0xFF21252D)

// Predefined Theme Color Schemes
val HShopGreen = Color(0xFF18FF00)
val HShopBlue = Color(0xFF00E5FF)
val HShopPink = Color(0xFFFF4081)
val HShopYellow = Color(0xFFFFD600)

fun getThemeColorScheme(theme: AppTheme) = when (theme) {
    AppTheme.THOR_AMOLED -> darkColorScheme(
        primary = Color(0xFF18FF00),
        onPrimary = Color.Black,
        secondary = Color(0xFF00E5FF),
        onSecondary = Color.Black,
        tertiary = Color(0xFFFFD600),
        background = Color(0xFF0A0A0A),
        surface = Color(0xFF141414),
        surfaceVariant = Color(0xFF202020),
        onSurface = Color.White,
        onSurfaceVariant = Color(0xFFD0D5DD)
    )
    AppTheme.CYBERPUNK_NEON -> darkColorScheme(
        primary = Color(0xFFFF007F),
        onPrimary = Color.White,
        secondary = Color(0xFF00F0FF),
        onSecondary = Color.Black,
        tertiary = Color(0xFFFFE600),
        background = Color(0xFF090B10),
        surface = Color(0xFF121622),
        surfaceVariant = Color(0xFF1D2336),
        onSurface = Color.White,
        onSurfaceVariant = Color(0xFFE2E8F0)
    )
    AppTheme.NINTENDO_RED -> darkColorScheme(
        primary = Color(0xFFE60012),
        onPrimary = Color.White,
        secondary = Color(0xFFFFFFFF),
        onSecondary = Color.Black,
        tertiary = Color(0xFFFF4500),
        background = Color(0xFF0F0F0F),
        surface = Color(0xFF1A1A1A),
        surfaceVariant = Color(0xFF282828),
        onSurface = Color.White,
        onSurfaceVariant = Color(0xFFE0E0E0)
    )
    AppTheme.CITRA_YELLOW -> darkColorScheme(
        primary = Color(0xFFFFCC00),
        onPrimary = Color.Black,
        secondary = Color(0xFFFF8800),
        onSecondary = Color.Black,
        tertiary = Color(0xFF00E5FF),
        background = Color(0xFF0E1015),
        surface = Color(0xFF181C24),
        surfaceVariant = Color(0xFF242A36),
        onSurface = Color.White,
        onSurfaceVariant = Color(0xFFCBD5E1)
    )
}

@Composable
fun HShopThorTheme(
    appTheme: AppTheme = AppTheme.THOR_AMOLED,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = getThemeColorScheme(appTheme),
        content = content
    )
}
