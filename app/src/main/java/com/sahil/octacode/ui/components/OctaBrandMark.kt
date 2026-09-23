package com.sahil.octacode.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import com.sahil.octacode.ui.theme.BubblePink
import com.sahil.octacode.ui.theme.ElectricPurple
import com.sahil.octacode.ui.theme.NeonBlue

@Composable
fun OctaBrandMark(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val leftLeg = Path().apply {
            moveTo(width * 0.06f, height * 0.92f)
            lineTo(width * 0.42f, height * 0.10f)
            lineTo(width * 0.61f, height * 0.10f)
            lineTo(width * 0.27f, height * 0.92f)
            close()
        }
        val rightLeg = Path().apply {
            moveTo(width * 0.47f, height * 0.10f)
            lineTo(width * 0.66f, height * 0.10f)
            lineTo(width * 0.98f, height * 0.92f)
            lineTo(width * 0.78f, height * 0.92f)
            close()
        }
        drawPath(
            path = leftLeg,
            brush = Brush.linearGradient(listOf(NeonBlue, NeonBlue, ElectricPurple), start = Offset.Zero, end = Offset(width, height)),
        )
        drawPath(
            path = rightLeg,
            brush = Brush.linearGradient(listOf(ElectricPurple, BubblePink), start = Offset(width * 0.4f, 0f), end = Offset(width, height)),
        )
        drawLine(
            color = NeonBlue.copy(alpha = 0.65f),
            start = Offset(width * 0.29f, height * 0.66f),
            end = Offset(width * 0.70f, height * 0.66f),
            strokeWidth = 2.dp.toPx(),
        )
    }
}
