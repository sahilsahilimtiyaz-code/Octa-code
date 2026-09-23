package com.sahil.octacode.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sahil.octacode.ui.motion.LocalMotionPolicy
import com.sahil.octacode.ui.theme.Gold
import com.sahil.octacode.ui.theme.GoldBright
import com.sahil.octacode.ui.theme.GoldDeep
import com.sahil.octacode.ui.theme.GoldText
import com.sahil.octacode.ui.theme.NeonBlue
import com.sahil.octacode.ui.theme.NeonGreen
import com.sahil.octacode.ui.theme.OnDark
import com.sahil.octacode.ui.theme.OnDarkMuted
import com.sahil.octacode.ui.theme.WarningAmber

/** Circular glass icon button used in the Agent header. */
@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = OnDark,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.04f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/** Agent top bar: menu · Octa Code mark · search / bell / profile. */
@Composable
fun AgentTopBar(
    onMenu: () -> Unit,
    onSearch: () -> Unit,
    onNotifications: () -> Unit,
    onProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(onClick = onMenu, contentDescription = "Menu") {
                Icon(Icons.Outlined.Menu, contentDescription = null, tint = OnDark, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            HexLogo(modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = "Octa Code",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 22.sp,
                        letterSpacing = 0.2.sp
                    ),
                    color = OnDark
                )
                Text(
                    text = "CODING ASSISTANT",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.2.sp),
                    color = OnDarkMuted
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassIconButton(onClick = onSearch, contentDescription = "Search") {
                Icon(Icons.Filled.Search, contentDescription = null, tint = OnDark, modifier = Modifier.size(20.dp))
            }
            GlassIconButton(onClick = onNotifications, contentDescription = "Notifications") {
                Icon(Icons.Filled.Notifications, contentDescription = null, tint = OnDark, modifier = Modifier.size(20.dp))
            }
            GlassIconButton(onClick = onProfile, contentDescription = "Profile") {
                Icon(Icons.Filled.Person, contentDescription = null, tint = OnDark, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** Hexagonal brand mark (gold/cyan) used in the Agent header. */
@Composable
fun HexLogo(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val r = w * 0.42f
        val hex = Path().apply {
            for (i in 0 until 6) {
                val angle = Math.toRadians((60.0 * i - 30.0))
                val x = cx + (r * kotlin.math.cos(angle)).toFloat()
                val y = cy + (r * kotlin.math.sin(angle)).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        drawPath(
            path = hex,
            brush = Brush.linearGradient(
                colors = listOf(GoldBright, Gold, NeonBlue),
                start = Offset(0f, 0f),
                end = Offset(w, h)
            ),
            style = Stroke(width = w * 0.08f)
        )
        // Inner cut
        val inner = Path().apply {
            for (i in 0 until 6) {
                val angle = Math.toRadians((60.0 * i - 30.0))
                val x = cx + (r * 0.55f * kotlin.math.cos(angle)).toFloat()
                val y = cy + (r * 0.55f * kotlin.math.sin(angle)).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        drawPath(inner, color = Color.Black.copy(alpha = 0.55f))
        drawPath(
            path = inner,
            brush = Brush.linearGradient(listOf(Gold, NeonBlue)),
            style = Stroke(width = w * 0.05f)
        )
    }
}

/** Pill row: project selector + agent readiness (matches reference status bar). */
@Composable
fun AgentStatusPill(
    projectLabel: String,
    agentLabel: String,
    agentReady: Boolean,
    onProjectClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White.copy(alpha = 0.035f))
            .border(
                1.dp,
                Brush.horizontalGradient(
                    listOf(Gold.copy(alpha = 0.55f), Color.White.copy(alpha = 0.12f), NeonBlue.copy(alpha = 0.35f))
                ),
                RoundedCornerShape(28.dp)
            )
            .clickable(onClick = onProjectClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Folder icon (gold stroke square-ish)
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(1.5.dp, Gold, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(16.dp)) {
                val path = Path().apply {
                    moveTo(size.width * 0.1f, size.height * 0.35f)
                    lineTo(size.width * 0.38f, size.height * 0.35f)
                    lineTo(size.width * 0.48f, size.height * 0.48f)
                    lineTo(size.width * 0.9f, size.height * 0.48f)
                    lineTo(size.width * 0.9f, size.height * 0.78f)
                    lineTo(size.width * 0.1f, size.height * 0.78f)
                    close()
                }
                drawPath(path, color = Gold, style = Stroke(width = 2.dp.toPx()))
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = projectLabel,
            style = MaterialTheme.typography.bodyLarge,
            color = OnDark,
            modifier = Modifier.weight(1f)
        )
        Text(text = "⌄", color = OnDarkMuted, style = MaterialTheme.typography.bodyLarge)
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(28.dp)
                .background(Color.White.copy(alpha = 0.14f))
                .padding(horizontal = 12.dp)
        )
        Spacer(Modifier.width(14.dp))
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (agentReady) NeonGreen else WarningAmber)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = agentLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = if (agentReady) NeonGreen else OnDark
        )
    }
}

/** Decorative planet for the hero (matches reference sphere + rings). */
@Composable
fun HeroPlanet(modifier: Modifier = Modifier) {
    val motion = LocalMotionPolicy.current
    Canvas(modifier = modifier) {
        val cx = size.width * 0.62f
        val cy = size.height * 0.48f
        val r = size.minDimension * 0.28f

        // Soft glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Gold.copy(alpha = 0.22f), NeonBlue.copy(alpha = 0.10f), Color.Transparent),
                center = Offset(cx, cy),
                radius = r * 2.4f
            ),
            radius = r * 2.4f,
            center = Offset(cx, cy)
        )
        // Planet body
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF1A2740),
                    Color(0xFF0B1220),
                    Color(0xFF05070E)
                ),
                center = Offset(cx - r * 0.3f, cy - r * 0.35f),
                radius = r * 1.2f
            ),
            radius = r,
            center = Offset(cx, cy)
        )
        // Terminator highlight
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(GoldBright.copy(alpha = 0.55f), Gold.copy(alpha = 0.15f), Color.Transparent),
                center = Offset(cx - r * 0.35f, cy - r * 0.4f),
                radius = r * 0.95f
            ),
            radius = r,
            center = Offset(cx, cy)
        )
        // Orbital rings
        val ringStroke = 2.5.dp.toPx()
        drawOval(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Transparent, Gold.copy(alpha = 0.85f), NeonBlue.copy(alpha = 0.55f), Color.Transparent)
            ),
            topLeft = Offset(cx - r * 1.7f, cy - r * 0.55f),
            size = androidx.compose.ui.geometry.Size(r * 3.4f, r * 1.15f),
            style = Stroke(width = ringStroke)
        )
        drawOval(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Transparent, NeonBlue.copy(alpha = 0.7f), Gold.copy(alpha = 0.5f), Color.Transparent)
            ),
            topLeft = Offset(cx - r * 1.45f, cy - r * 0.15f),
            size = androidx.compose.ui.geometry.Size(r * 2.9f, r * 0.85f),
            style = Stroke(width = ringStroke * 0.85f)
        )
        if (!motion.reducedMotion) {
            // subtle arc accent
            drawArc(
                color = GoldBright.copy(alpha = 0.35f),
                startAngle = -40f,
                sweepAngle = 70f,
                useCenter = false,
                topLeft = Offset(cx - r * 1.1f, cy - r * 1.1f),
                size = androidx.compose.ui.geometry.Size(r * 2.2f, r * 2.2f),
                style = Stroke(width = ringStroke * 0.7f)
            )
        }
    }
}

/** Gold or cyan bordered info card from the reference empty state. */
@Composable
fun AccentInfoCard(
    title: String,
    message: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.045f),
                        Color.White.copy(alpha = 0.02f)
                    )
                )
            )
            .border(
                1.2.dp,
                Brush.horizontalGradient(
                    listOf(
                        accent.copy(alpha = 0.85f),
                        accent.copy(alpha = 0.35f),
                        Color.White.copy(alpha = 0.08f)
                    )
                ),
                RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.04f))
                .border(1.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = OnDark
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = OnDarkMuted
            )
        }
        Text(text = "›", style = MaterialTheme.typography.headlineSmall, color = OnDarkMuted)
    }
}

/** Hero headline block: "Build Better / Together" + supporting copy. */
@Composable
fun AgentHeroCopy(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = "YOUR CODING PARTNER",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.5.sp),
            color = OnDarkMuted
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Build Better",
            style = MaterialTheme.typography.displayMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 42.sp,
                lineHeight = 48.sp
            ),
            color = OnDark
        )
        Text(
            text = "Together",
            style = MaterialTheme.typography.displayMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 42.sp,
                lineHeight = 48.sp
            ),
            color = GoldText
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Your coding conversation and\nmission activity will appear here.",
            style = MaterialTheme.typography.bodyLarge,
            color = OnDarkMuted
        )
    }
}

/** Pill dropdown used in the composer (Model / Project). */
@Composable
fun ComposerPill(
    label: String,
    leadingIcon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = NeonBlue
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .border(
                1.dp,
                Brush.horizontalGradient(listOf(accent.copy(alpha = 0.45f), Color.White.copy(alpha = 0.1f))),
                RoundedCornerShape(24.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leadingIcon()
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = OnDark,
            maxLines = 1
        )
        Spacer(Modifier.width(6.dp))
        Text(text = "⌄", color = OnDarkMuted, style = MaterialTheme.typography.bodySmall)
    }
}

/** Circular gold send / stop control. */
@Composable
fun GoldSendButton(
    enabled: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(
                if (enabled || busy) {
                    Brush.radialGradient(
                        colors = listOf(GoldBright, Gold, GoldDeep)
                    )
                } else {
                    Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.12f),
                            Color.White.copy(alpha = 0.06f)
                        )
                    )
                }
            )
            .border(
                1.5.dp,
                if (enabled || busy) GoldBright.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.2f),
                CircleShape
            )
            .clickable(enabled = enabled || busy, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (busy) {
            // Stop square
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(Color(0xFF1A1208), RoundedCornerShape(2.dp))
            )
        } else {
            Canvas(Modifier.size(22.dp)) {
                val path = Path().apply {
                    moveTo(size.width * 0.18f, size.height * 0.5f)
                    lineTo(size.width * 0.78f, size.height * 0.22f)
                    lineTo(size.width * 0.78f, size.height * 0.78f)
                    close()
                }
                drawPath(path, color = if (enabled) Color(0xFF1A1208) else OnDarkMuted.copy(alpha = 0.6f))
            }
        }
    }
}
