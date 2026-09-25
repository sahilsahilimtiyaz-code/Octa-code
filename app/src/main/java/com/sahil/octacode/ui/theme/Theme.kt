package com.sahil.octacode.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import com.sahil.octacode.data.settings.SettingsRepository
import org.koin.compose.koinInject

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

    // The user's own text-size setting, applied the way Android applies an
    // accessibility font scale: by replacing Density rather than by editing
    // every typographic value. Anything measured in dp is untouched, so the
    // layout holds and only text responds.
    val settings: SettingsRepository = koinInject()
    val current by settings.settings.collectAsState()
    // Read before it is replaced — the system scale is the base this multiplies,
    // so a device already set to "large" stays large and the slider moves it
    // from there rather than discarding it.
    val density = LocalDensity.current

    CompositionLocalProvider(
        LocalDensity provides Density(
            density = density.density,
            fontScale = density.fontScale * current.uiFontScale,
        )
    ) {
        MaterialTheme(
            colorScheme = OctaDarkScheme,
            typography = OctaTypography,
            content = content
        )
    }
}
