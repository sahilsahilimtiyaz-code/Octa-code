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

// M4 builds the full chat UI here (bubbles, ThinkingOrb, ReasoningPanel, diff viewer).
@Composable
fun ChatScreen() {
    Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
        Text("Agent Chat — arrives in M4. No fake activity until then.", style = MaterialTheme.typography.bodyMedium)
    }
}
