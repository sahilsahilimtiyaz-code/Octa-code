package com.sahil.octacode.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sahil.octacode.ui.theme.OnDarkMuted

/**
 * A row on the settings hub that opens a sub-page.
 *
 * It must only ever be given a destination that exists. The whole point of the
 * hub is that every row leads somewhere, so this signature takes no "disabled"
 * flag — a row that cannot navigate has no reason to be on the hub at all.
 */
@Composable
fun SettingsNavRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Card(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = OnDarkMuted,
                )
            }
            Spacer(Modifier.width(12.dp))
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = OnDarkMuted,
            )
        }
    }
}

/**
 * A setting that exists and persists, but whose control is not available yet.
 *
 * This has no click handler and is not a button: it cannot be operated, and it
 * says why in plain words. The alternative — a switch that writes a value
 * nothing reads — is exactly the decorative affordance this project refuses to
 * ship, so the row reports its own limit instead of pretending to be one.
 *
 * @param title the setting's name
 * @param value what it is currently set to, so the row still answers "where am I"
 * @param reason what is missing before it can be changed
 */
@Composable
fun LockedRow(
    title: String,
    value: String,
    reason: String,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.weight(1f))
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = "Not available yet",
                    tint = OnDarkMuted,
                    modifier = Modifier.width(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Not available",
                    style = MaterialTheme.typography.labelSmall,
                    color = OnDarkMuted,
                )
            }
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = OnDarkMuted,
            )
            Text(
                reason,
                style = MaterialTheme.typography.bodySmall,
                color = OnDarkMuted,
            )
        }
    }
}

/**
 * A boolean setting: title, what it does, and the switch itself.
 *
 * The description is required rather than optional on purpose — an unexplained
 * switch is the one kind of control a user has to guess at.
 */
@Composable
fun ToggleCard(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = OnDarkMuted,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

/**
 * A setting on a discrete range: title, its value rendered by the caller,
 * and the track.
 *
 * [display] is a parameter rather than a formatted number because these cards
 * answer to different units — a percentage for type size, a plain count for a
 * chat limit — and neither belongs in a shared component.
 *
 * [description] is nullable: some of these explain themselves only while a
 * parent rule is switched off, and saying so then is the useful case.
 */
@Composable
fun SettingSlider(
    title: String,
    description: String?,
    display: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
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
                    display,
                    style = MaterialTheme.typography.titleSmall,
                    color = OnDarkMuted,
                )
            }
            if (description != null) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = OnDarkMuted,
                )
            }
            Slider(
                value = value,
                onValueChange = onChange,
                valueRange = valueRange,
                steps = steps,
            )
        }
    }
}
