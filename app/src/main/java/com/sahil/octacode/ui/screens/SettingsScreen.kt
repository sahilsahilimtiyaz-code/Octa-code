package com.sahil.octacode.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// M6 builds full settings here. Branding law: credits ONLY on Splash + Settings.
@Composable
fun SettingsScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text("Crafted with ❤ by Sahil (Octavian)", style = MaterialTheme.typography.bodyMedium)
        Text("sahilsahilimtiyaz@gmail.com", style = MaterialTheme.typography.bodySmall)
        Text("TikTok: @mog-octavian", style = MaterialTheme.typography.bodySmall)
        Text("YouTube: CHADFRAMED", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        Text("Octa Code v1.0.0", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(12.dp))
        Text(
            "Provider keys, autonomy level, privacy controls land in M6.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
