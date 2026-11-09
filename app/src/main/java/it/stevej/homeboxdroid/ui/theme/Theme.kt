package it.stevej.homeboxdroid.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = SoftTeal,
    secondary = SoftTealLight,
    tertiary = SoftAmber,
    background = SoftSand,
    onBackground = SoftInk,
    surface = SoftSand,
    onSurface = SoftInk,
)

private val DarkColors = darkColorScheme(
    primary = SoftTealLight,
    secondary = SoftTeal,
    tertiary = SoftAmber,
    background = SoftSurfaceDark,
    onBackground = SoftSand,
    surface = SoftSurfaceDark,
    onSurface = SoftSand,
)

@Composable
fun HomeboxDroidTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color disattivato di default per mantenere l'identita' morbida.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
