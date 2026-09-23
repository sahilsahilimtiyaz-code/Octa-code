package com.sahil.octacode.ui.motion

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

data class MotionPolicy(
    val reducedMotion: Boolean,
) {
    companion object {
        val Animated = MotionPolicy(reducedMotion = false)
    }
}

val LocalMotionPolicy = compositionLocalOf { MotionPolicy.Animated }

@Composable
fun rememberSystemMotionPolicy(): MotionPolicy {
    val context = LocalContext.current
    return remember(context) {
        val animatorScale = runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
        }.getOrDefault(1f)
        MotionPolicy(reducedMotion = animatorScale == 0f)
    }
}
