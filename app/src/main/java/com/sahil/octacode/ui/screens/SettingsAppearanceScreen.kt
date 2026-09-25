package com.sahil.octacode.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.sahil.octacode.core.settings.Settings
import com.sahil.octacode.data.settings.SettingsRepository
import com.sahil.octacode.ui.components.SectionHeaderText
import com.sahil.octacode.ui.theme.OnDarkMuted
import com.sahil.octacode.ui.theme.codeTextStyle
import org.koin.compose.koinInject
import kotlin.math.roundToInt

/**
 * Text size, plus the two appearance settings this build cannot honour yet.
 *
 * Both sliders are live: the value goes to disk on the same frame the thumb
 * moves, and every screen rescales immediately, because a text-size control
 * that only takes effect after a restart is indistinguishable from one that
 * does not work.
 */
@Composable
fun SettingsAppearanceScreen(
    onBack: () -> Unit,
    repository: SettingsRepository = koinInject(),
) {
    val settings by repository.settings.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Back") }
            Spacer(Modifier.width(4.dp))
            Text("Appearance", style = MaterialTheme.typography.headlineMedium)
        }

        FontSizeSlider(
            title = "UI font size",
            description = "Labels, chat text and controls. Rows stay the same height, so a " +
                "smaller size fits more on screen.",
            value = settings.uiFontScale,
            onChange = { scale ->
                repository.update { s -> s.copy(uiFontScale = scale) }
            },
        )
        FontSizeSlider(
            title = "Code font size",
            description = "Diffs, phase labels and the event stream. Kept separate — code is " +
                "read differently from the interface around it.",
            value = settings.codeFontScale,
            onChange = { scale ->
                repository.update { s -> s.copy(codeFontScale = scale) }
            },
        )

        SectionHeaderText("Preview")
        Card(Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Interface text follows UI font size.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "val answer = 42  // code font size",
                    style = codeTextStyle(MaterialTheme.typography.bodySmall),
                    fontFamily = FontFamily.Monospace,
                    color = OnDarkMuted,
                )
            }
        }

        SectionHeaderText("Unavailable in this build")

        LockedRow(
            title = "Theme",
            value = "Current: ${settings.theme.label}",
            reason = "This build ships one appearance — dark. Choosing Light would store a " +
                "value nothing reads, so the picker waits for a light scheme to exist.",
        )
        LockedRow(
            title = "Syntax theme",
            value = "Current: ${settings.syntaxTheme.label}",
            reason = "There is no syntax highlighter yet — code renders without token " +
                "coloring — so a palette would change nothing on screen.",
        )

        OutlinedButton(
            onClick = { repository.reset() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Reset all settings to defaults")
        }
    }
}

/**
 * A slider over [Settings]' legal font range, showing the value as a percentage
 * so "1.15" reads as the thing it actually means.
 */
@Composable
private fun FontSizeSlider(
    title: String,
    description: String,
    value: Float,
    onChange: (Float) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.weight(1f))
                Text(
                    "${Math.round(value * 100)}%",
                    style = MaterialTheme.typography.titleSmall,
                    color = OnDarkMuted,
                )
            }
            Text(description, style = MaterialTheme.typography.bodySmall, color = OnDarkMuted)
            // steps = intervals minus the two endpoints the API counts itself.
            // roundToInt, not toInt: 0.75f / 0.05f lands just under 15 in
            // floating point, and truncating that would silently widen the step.
            val steps = ((Settings.MAX_FONT_SCALE - Settings.MIN_FONT_SCALE) / STEP)
                .roundToInt() - 1
            Slider(
                value = value,
                onValueChange = onChange,
                valueRange = Settings.MIN_FONT_SCALE..Settings.MAX_FONT_SCALE,
                steps = steps,
            )
        }
    }
}

private const val STEP = 0.05f
