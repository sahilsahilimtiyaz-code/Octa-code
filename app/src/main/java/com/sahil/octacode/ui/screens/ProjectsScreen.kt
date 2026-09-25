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
 * No project index in this build.
 *
 * The bottom bar calls this "Code", so it has to say what is really behind
 * it rather than a milestone with a date on it — the placeholder here used
 * to promise M5, and M5 has since shipped as settings and history work.
 * Nothing was ever built under it, so the screen now says that instead of
 * pointing at a release that has already happened.
 */
@Composable
fun ProjectsScreen() {
    Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
        Text(
            text = "No projects in this build — the system folder picker a " +
                "project would come from is not wired up yet.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}
