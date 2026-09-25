package com.sahil.octacode.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * No shell in this build.
 *
 * The runtime work downloaded and verified a prefix (R1/R2), but nothing
 * has been wired to execute inside it. The placeholder that used to sit
 * here promised "M5 (PTY + PRoot)" — M5 shipped as settings and history
 * instead, so that sentence had outlived the truth it described. A prompt
 * that accepted input and did nothing would be worse than an empty screen,
 * so this one simply states what is and is not running.
 */
@Composable
fun TerminalScreen() {
    Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
        Text(
            text = "No shell in this build — the runtime prefix is downloaded " +
                "and verified, but nothing executes inside it yet.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}
