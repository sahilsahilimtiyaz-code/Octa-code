package com.sahil.octacode.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sahil.octacode.ui.theme.ChatCardBottom
import com.sahil.octacode.ui.theme.ChatCardTop
import com.sahil.octacode.ui.theme.GoldBright
import com.sahil.octacode.ui.theme.GoldDeep
import com.sahil.octacode.ui.theme.IceBlue
import com.sahil.octacode.ui.theme.TealGlow

// M4b reference card: navy vertical-gradient fill + glowing horizontal
// gradient edge (bright center, fading sides) like the reference cards.
@Composable
fun GlowCard(
    edge: List<Color>,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.verticalGradient(listOf(ChatCardTop, ChatCardBottom))
            )
            .border(
                1.dp,
                Brush.horizontalGradient(edge),
                RoundedCornerShape(18.dp)
            )
            .padding(16.dp)
    ) {
        content()
    }
}

/** Gold edge for the agent card (bright champagne center). */
fun goldEdge(): List<Color> = listOf(
    Color.Transparent,
    GoldDeep.copy(alpha = 0.55f),
    GoldBright,
    GoldDeep.copy(alpha = 0.55f),
    Color.Transparent
)

/** Teal-blue edge for mission/composer cards. */
fun tealEdge(): List<Color> = listOf(
    Color.Transparent,
    TealGlow.copy(alpha = 0.45f),
    IceBlue,
    TealGlow.copy(alpha = 0.45f),
    Color.Transparent
)
