package com.sahil.octacode.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val OctaDarkScheme = darkColorScheme(
    primary = NeonBlue,
    secondary = ElectricPurple,
    tertiary = NeonGreen,
    background = DeepSpaceBlack,
    surface = SurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onBackground = OnDark,
    onSurface = OnDark,
    error = NeonRed,
    onError = OnDark
)

@Composable
fun OctaCodeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(), // M0: always dark; light theme arrives in M6
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = DeepSpaceBlack.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }
    MaterialTheme(
        colorScheme = OctaDarkScheme,
        typography = OctaTypography,
        content = content
    )
}
