package io.github.mrroguekknight.drishti.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = GovBluePrimary,
    onPrimary = GovWhite,
    secondary = GovBlueDark,
    onSecondary = GovWhite,
    tertiary = GovSuccess,
    background = GovDarkGray,
    onBackground = GovTextWhite,
    surface = GovSurfaceDark,
    onSurface = GovTextWhite,
    error = GovError,
    onError = GovWhite
)

private val LightColorScheme = lightColorScheme(
    primary = GovBluePrimary,
    onPrimary = GovWhite,
    secondary = GovBlueDark,
    onSecondary = GovWhite,
    tertiary = GovSuccess,
    background = GovLightGray,
    onBackground = GovTextBlack,
    surface = GovWhite,
    onSurface = GovTextBlack,
    error = GovError,
    onError = GovWhite
)

@Composable
fun DRISHTITheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false, // Disabled for consistent Gov App look
    content: @Composable () -> Unit
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