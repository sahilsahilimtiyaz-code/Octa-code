package com.sahil.octacode.ui.demo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

class DemoPreviewState {
    var enabled by mutableStateOf(false)

    fun toggle() {
        enabled = !enabled
    }
}

val LocalDemoPreview = compositionLocalOf { DemoPreviewState() }

@Composable
fun rememberDemoPreviewState(): DemoPreviewState = remember { DemoPreviewState() }
