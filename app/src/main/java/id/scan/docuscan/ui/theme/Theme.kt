package id.scan.docuscan.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = SlateAccentAzure,
    secondary = ScannerGlowGreen,
    tertiary = CategoryGold,
    background = SlateMainDark,
    surface = SlateCardDark,
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onTertiary = Color.Black,
    onBackground = NeutralTextWhite,
    onSurface = NeutralTextWhite,
    onSurfaceVariant = NeutralTextMutedDark,
    outline = Color(0xFF434750),
    error = UrgentRoseRed
)

private val LightColorScheme = lightColorScheme(
    primary = SlateAccentBlue,
    secondary = ScannerGlowGreen,
    tertiary = CategoryGold,
    background = SlateMainLight,
    surface = SlateCardLight,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onTertiary = Color.Black,
    onBackground = NeutralTextBlack,
    onSurface = NeutralTextBlack,
    onSurfaceVariant = NeutralTextMutedLight,
    outline = Color(0xFFE1E2EC),
    error = UrgentRoseRed
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Set to false to force our corporate Slate identity
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
        content = content
    )
}
