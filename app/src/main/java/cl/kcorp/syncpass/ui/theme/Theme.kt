package cl.kcorp.syncpass.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = BrandPrimary,
    onPrimary = Color.White,
    primaryContainer = PrimarySoftLight,
    onPrimaryContainer = BrandPrimaryDark,
    secondary = BrandSecondary,
    onSecondary = Color.White,
    secondaryContainer = SurfaceAltLight,
    onSecondaryContainer = BrandSecondary,
    tertiary = BrandAccent,
    onTertiary = Color.White,
    tertiaryContainer = InfoSoftLight,
    onTertiaryContainer = Color(0xFF2D5FC4),
    background = BgLight,
    onBackground = TextLight,
    surface = SurfaceLight,
    onSurface = TextLight,
    surfaceVariant = SurfaceAltLight,
    onSurfaceVariant = TextMutedLight,
    surfaceTint = BrandPrimary,
    outline = BorderLight,
    outlineVariant = Color(0xFFDDE0E6),
    error = BrandDanger,
    onError = Color.White,
    errorContainer = DangerSoftLight,
    onErrorContainer = Color(0xFFA72F2F),
)

private val DarkColorScheme = darkColorScheme(
    primary = BrandPrimaryLight,
    onPrimary = Color(0xFF3B0004),
    primaryContainer = Color(0xFF5C1A1A),
    onPrimaryContainer = Color(0xFFFFE3E3),
    secondary = Color(0xFFB8BCC7),
    onSecondary = BrandSecondaryDeep,
    secondaryContainer = SurfaceAltDark,
    onSecondaryContainer = TextDark,
    tertiary = BrandAccent,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF1E3A8A),
    onTertiaryContainer = Color(0xFFDBEAFE),
    background = BgDark,
    onBackground = TextDark,
    surface = SurfaceDark,
    onSurface = TextDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextMutedDark,
    surfaceTint = BrandPrimaryLight,
    outline = BorderDark,
    outlineVariant = Color(0xFF444958),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF5C1A1A),
    onErrorContainer = Color(0xFFFEE2E2),
)

private val SyncPassShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun SyncPassTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = SyncPassShapes,
        content = content,
    )
}
