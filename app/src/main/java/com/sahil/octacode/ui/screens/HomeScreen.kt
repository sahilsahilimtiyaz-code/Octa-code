package com.sahil.octacode.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(onOpenChat: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Octa Code", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text("Mobile AI Coding Agent — M1 + M2", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        Text(
            "Capability registry + OpenAI/Custom adapters live in Settings. " +
                "Missions land in M3.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onOpenChat) { Text("Open Agent Chat") }
    }
}
