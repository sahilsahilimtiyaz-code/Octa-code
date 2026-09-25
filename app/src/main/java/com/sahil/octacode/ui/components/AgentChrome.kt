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
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.KeyboardArrowDown
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

/** Circular glass icon button used in the Agent header. */
@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.03f))
            .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape)
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
                Icon(Icons.Outlined.Menu, contentDescription = null, tint = OnDark, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(14.dp))
            HexLogo(modifier = Modifier.size(50.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "Octa Code",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 26.sp,
                        lineHeight = 30.sp,
                        letterSpacing = 0.1.sp
                    ),
                    color = OnDark
                )
                Text(
                    text = "CODING ASSISTANT",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        letterSpacing = 3.2.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = OnDarkMuted
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassIconButton(onClick = onSearch, contentDescription = "Search") {
                Icon(Icons.Filled.Search, contentDescription = null, tint = OnDark, modifier = Modifier.size(22.dp))
            }
            GlassIconButton(onClick = onNotifications, contentDescription = "Notifications") {
                Icon(Icons.Filled.Notifications, contentDescription = null, tint = OnDark, modifier = Modifier.size(22.dp))
            }
            GlassIconButton(onClick = onProfile, contentDescription = "Profile") {
                Icon(Icons.Filled.Person, contentDescription = null, tint = OnDark, modifier = Modifier.size(22.dp))
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
        val r = w * 0.44f
        fun hexPath(scale: Float): Path = Path().apply {
            for (i in 0 until 6) {
                val angle = Math.toRadians((60.0 * i - 30.0))
                val x = cx + (r * scale * kotlin.math.cos(angle)).toFloat()
                val y = cy + (r * scale * kotlin.math.sin(angle)).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        drawPath(
            path = hexPath(1f),
            brush = Brush.linearGradient(
                colors = listOf(GoldBright, Gold, NeonBlue),
                start = Offset(0f, 0f),
                end = Offset(w, h)
            ),
            style = Stroke(width = w * 0.075f)
        )
        val inner = hexPath(0.58f)
        drawPath(inner, color = Color.Black.copy(alpha = 0.7f))
        drawPath(
            path = inner,
            brush = Brush.linearGradient(listOf(Gold, NeonBlue)),
            style = Stroke(width = w * 0.045f)
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
            .clip(RoundedCornerShape(32.dp))
            .background(Color.White.copy(alpha = 0.035f))
            .border(
                1.dp,
                Brush.horizontalGradient(
                    listOf(
                        Gold.copy(alpha = 0.55f),
                        Color.White.copy(alpha = 0.10f),
                        NeonBlue.copy(alpha = 0.28f)
                    )
                ),
                RoundedCornerShape(32.dp)
            )
            .clickable(onClick = onProjectClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1.2f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Folder,
                contentDescription = null,
                tint = Gold,
                modifier = Modifier.size(26.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = projectLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = OnDark,
                maxLines = 1,
                modifier = Modifier.weight(1f, fill = true)
            )
            Icon(
                Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                tint = OnDarkMuted,
                modifier = Modifier.size(20.dp)
            )
        }
        Box(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .width(1.dp)
                .height(28.dp)
                .background(Color.White.copy(alpha = 0.14f))
        )
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(NeonGreen)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = agentLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = OnDark,
                maxLines = 1
            )
        }
    }
}

/** Decorative planet for the hero (matches reference sphere + rings). */
@Composable
fun HeroPlanet(modifier: Modifier = Modifier) {
    val motion = LocalMotionPolicy.current
    Canvas(modifier = modifier) {
        val cx = size.width * 0.68f
        val cy = size.height * 0.50f
        val r = size.minDimension * 0.34f

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Gold.copy(alpha = 0.20f), NeonBlue.copy(alpha = 0.08f), Color.Transparent),
                center = Offset(cx, cy),
                radius = r * 2.5f
            ),
            radius = r * 2.5f,
            center = Offset(cx, cy)
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF1C2A44), Color(0xFF0A101C), Color(0xFF04060C)),
                center = Offset(cx - r * 0.28f, cy - r * 0.32f),
                radius = r * 1.25f
            ),
            radius = r,
            center = Offset(cx, cy)
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(GoldBright.copy(alpha = 0.70f), Gold.copy(alpha = 0.18f), Color.Transparent),
                center = Offset(cx - r * 0.38f, cy - r * 0.42f),
                radius = r * 1.0f
            ),
            radius = r,
            center = Offset(cx, cy)
        )
        val ringStroke = 2.5.dp.toPx()
        drawOval(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Transparent, Gold.copy(alpha = 0.9f), NeonBlue.copy(alpha = 0.55f), Color.Transparent)
            ),
            topLeft = Offset(cx - r * 1.85f, cy - r * 0.62f),
            size = androidx.compose.ui.geometry.Size(r * 3.7f, r * 1.25f),
            style = Stroke(width = ringStroke)
        )
        drawOval(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Transparent, NeonBlue.copy(alpha = 0.75f), Gold.copy(alpha = 0.45f), Color.Transparent)
            ),
            topLeft = Offset(cx - r * 1.55f, cy - r * 0.18f),
            size = androidx.compose.ui.geometry.Size(r * 3.1f, r * 0.90f),
            style = Stroke(width = ringStroke * 0.85f)
        )
        if (!motion.reducedMotion) {
            drawArc(
                color = GoldBright.copy(alpha = 0.40f),
                startAngle = -50f,
                sweepAngle = 75f,
                useCenter = false,
                topLeft = Offset(cx - r * 1.15f, cy - r * 1.15f),
                size = androidx.compose.ui.geometry.Size(r * 2.3f, r * 2.3f),
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
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.05f),
                        Color.White.copy(alpha = 0.02f)
                    )
                )
            )
            .border(
                1.5.dp,
                Brush.horizontalGradient(
                    listOf(
                        accent.copy(alpha = 0.95f),
                        accent.copy(alpha = 0.70f),
                        accent.copy(alpha = 0.40f)
                    )
                ),
                RoundedCornerShape(22.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.04f))
                .border(1.2.dp, accent.copy(alpha = 0.70f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    lineHeight = 22.sp
                ),
                color = OnDark
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    lineHeight = 19.sp
                ),
                color = OnDarkMuted,
                maxLines = 4
            )
        }
        Spacer(Modifier.width(6.dp))
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = OnDarkMuted,
            modifier = Modifier.size(24.dp)
        )
    }
}

/** Hero headline block: "Build Better / Together" + supporting copy. */
@Composable
fun AgentHeroCopy(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = "YOUR CODING PARTNER",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 12.sp,
                letterSpacing = 3.5.sp,
                fontWeight = FontWeight.Medium
            ),
            color = OnDarkMuted
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Build Better",
            style = MaterialTheme.typography.displayMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 42.sp,
                lineHeight = 46.sp,
                letterSpacing = (-0.5).sp
            ),
            color = OnDark
        )
        Text(
            text = "Together",
            style = MaterialTheme.typography.displayMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 42.sp,
                lineHeight = 46.sp,
                letterSpacing = (-0.5).sp
            ),
            color = GoldText
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Your coding conversation and\nmission activity will appear here.",
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 15.sp,
                lineHeight = 22.sp
            ),
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
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White.copy(alpha = 0.03f))
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(28.dp))
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
        Spacer(Modifier.width(2.dp))
        Icon(
            Icons.Outlined.KeyboardArrowDown,
            contentDescription = null,
            tint = OnDarkMuted,
            modifier = Modifier.size(18.dp)
        )
    }
}

/** Circular gold send / stop control with glow ring (reference). */
@Composable
fun GoldSendButton(
    enabled: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Invoked when the button is tapped but cannot act. Without it the tap is
     * swallowed outright, so the user gets no explanation for why nothing
     * happened — which reads as the app being broken rather than unconfigured.
     */
    onBlocked: (() -> Unit)? = null
) {
    Box(
        modifier = modifier.size(48.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer glow
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Gold.copy(alpha = if (enabled || busy) 0.45f else 0.22f),
                            Gold.copy(alpha = 0.10f),
                            Color.Transparent
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(
                    if (enabled || busy) {
                        Brush.radialGradient(
                            colors = listOf(GoldBright.copy(alpha = 0.35f), Color(0xFF1A1408), Color(0xFF0C0A06))
                        )
                    } else {
                        Brush.radialGradient(
                            colors = listOf(Color.White.copy(alpha = 0.10f), Color(0xFF0C0C14))
                        )
                    }
                )
                .border(
                    1.8.dp,
                    if (enabled || busy) {
                        Brush.linearGradient(listOf(GoldBright, Gold, GoldDeep))
                    } else {
                        Brush.linearGradient(
                            listOf(
                                Gold.copy(alpha = 0.55f),
                                Gold.copy(alpha = 0.30f)
                            )
                        )
                    },
                    CircleShape
                )
                .clickable(
                    enabled = enabled || busy || onBlocked != null,
                    onClick = { if (enabled || busy) onClick() else onBlocked?.invoke() }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (busy) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .background(GoldBright, RoundedCornerShape(2.dp))
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Send,
                    contentDescription = "Send",
                    tint = if (enabled) GoldBright else OnDarkMuted.copy(alpha = 0.7f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
