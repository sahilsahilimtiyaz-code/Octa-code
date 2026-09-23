package com.sahil.octacode.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// M5 wires the PTY terminal here. Until then: honest placeholder, no fake shell.
@Composable
fun TerminalScreen() {
    Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
        Text("Terminal — arrives in M5 (PTY + PRoot).", style = MaterialTheme.typography.bodyMedium)
    }
}
