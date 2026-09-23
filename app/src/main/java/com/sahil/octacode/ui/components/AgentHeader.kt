package com.sahil.octacode.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sahil.octacode.ui.theme.ChatMuted
import com.sahil.octacode.ui.theme.ElectricPurple
import com.sahil.octacode.ui.theme.GoldLight
import com.sahil.octacode.ui.theme.NeonBlue
import com.sahil.octacode.ui.theme.OnDark
import kotlin.math.cos
import kotlin.math.sin

// M4b app header: menu (real destinations) · own hex mark · title ·
// search/bell honestly disabled (no index/notification system in M4) ·
// profile → Settings (credits live there).
@Composable
fun AgentHeader(
    onOpenProjects: () -> Unit,
    onNewMission: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { menu = true }) {
            Icon(Icons.Filled.Menu, contentDescription = "Menu")
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("Projects") },
                onClick = { menu = false; onOpenProjects() }
            )
            DropdownMenuItem(
                text = { Text("New mission") },
                onClick = { menu = false; onNewMission() }
            )
            DropdownMenuItem(
                text = { Text("Settings") },
                onClick = { menu = false; onOpenSettings() }
            )
        }
        HexMark()
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "Octa Code",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = OnDark
            )
            Text(
                text = "C O D I N G  A S S I S T A N T",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = ChatMuted
            )
        }
        IconButton(onClick = {}, enabled = false) {
            Icon(
                Icons.Filled.Search,
                contentDescription = "Search unavailable — no project index yet",
                tint = ChatMuted.copy(alpha = 0.4f)
            )
        }
        IconButton(onClick = {}, enabled = false) {
            Icon(
                Icons.Filled.Notifications,
                contentDescription = "Notifications unavailable",
                tint = ChatMuted.copy(alpha = 0.4f)
            )
        }
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Filled.Person, contentDescription = "Profile and settings")
        }
    }
}

/** Own hex mark drawn in code — gradient hexagon + node. Not copied artwork. */
@Composable
private fun HexMark(modifier: Modifier = Modifier) {
    Canvas(modifier.size(40.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = size.minDimension / 2f - 3.dp.toPx()
        val hex = Path().apply {
            for (i in 0..5) {
                val a = Math.PI / 3 * i - Math.PI / 6
                val x = cx + (r * cos(a)).toFloat()
                val y = cy + (r * sin(a)).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        drawPath(
            path = hex,
            brush = Brush.linearGradient(
                colors = listOf(NeonBlue, ElectricPurple),
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height)
            ),
            style = Stroke(width = 3.dp.toPx())
        )
        drawCircle(
            brush = Brush.radialGradient(colors = listOf(GoldLight, NeonBlue)),
            radius = r * 0.28f,
            center = Offset(cx, cy)
        )
    }
}
