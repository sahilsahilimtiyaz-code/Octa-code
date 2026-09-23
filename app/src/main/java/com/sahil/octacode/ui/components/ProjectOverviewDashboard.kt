package com.sahil.octacode.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.sahil.octacode.ui.motion.LocalMotionPolicy
import com.sahil.octacode.ui.state.ProjectMetricsUi
import com.sahil.octacode.ui.theme.ElectricPurple
import com.sahil.octacode.ui.theme.NeonAmber
import com.sahil.octacode.ui.theme.NeonBlue
import com.sahil.octacode.ui.theme.NeonGreen
import com.sahil.octacode.ui.theme.NeonTeal
import com.sahil.octacode.ui.theme.TextSecondary

@Composable
fun ProjectOverviewDashboard(
    metrics: ProjectMetricsUi?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            LinesCard(metrics, Modifier.weight(1f))
            LanguagesCard(metrics, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            FilesCard(metrics, Modifier.weight(1f))
            ActivityCard(metrics, Modifier.weight(1f))
        }
    }
}

@Composable
private fun LinesCard(metrics: ProjectMetricsUi?, modifier: Modifier) {
    val reducedMotion = LocalMotionPolicy.current.reducedMotion
    GlassSurface(modifier = modifier) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Lines of code", style = MaterialTheme.typography.titleSmall)
            if (metrics == null) {
                MetricUnavailable()
            } else {
                val animated by animateFloatAsState(
                    targetValue = metrics.linesProgress.coerceIn(0f, 1f),
                    animationSpec = tween(if (reducedMotion) 0 else 800),
                    label = "lines-ring",
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgressRing(animated, NeonTeal, Modifier.size(64.dp))
                    Text(
                        metrics.linesOfCode.toString(),
                        modifier = Modifier.padding(start = 10.dp),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguagesCard(metrics: ProjectMetricsUi?, modifier: Modifier) {
    GlassSurface(modifier = modifier) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Language distribution", style = MaterialTheme.typography.titleSmall)
            if (metrics == null || metrics.languages.isEmpty()) {
                MetricUnavailable()
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DonutChart(metrics.languages.map { it.percentage }, Modifier.size(64.dp))
                    Column(Modifier.padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        metrics.languages.take(3).forEach { slice ->
                            Text(
                                "${slice.label} ${slice.percentage.toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilesCard(metrics: ProjectMetricsUi?, modifier: Modifier) {
    val reducedMotion = LocalMotionPolicy.current.reducedMotion
    GlassSurface(modifier = modifier) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("Files by type", style = MaterialTheme.typography.titleSmall)
            if (metrics == null || metrics.filesByType.isEmpty()) {
                MetricUnavailable()
            } else {
                val max = metrics.filesByType.maxOf { it.count }.coerceAtLeast(1)
                val barColors = listOf(NeonTeal, ElectricPurple, NeonAmber, NeonBlue, NeonGreen)
                metrics.filesByType.take(4).forEachIndexed { index, fileType ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(fileType.label, style = MaterialTheme.typography.labelSmall)
                            Text(
                                fileType.count.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                            )
                        }
                        val fraction by animateFloatAsState(
                            targetValue = fileType.count.toFloat() / max,
                            animationSpec = tween(if (reducedMotion) 0 else 700),
                            label = "file-bar",
                        )
                        Box(
                            Modifier.fillMaxWidth().height(5.dp).backgroundTrack(),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(fraction)
                                    .height(5.dp)
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                barColors[index % barColors.size],
                                                barColors[(index + 1) % barColors.size],
                                            ),
                                        ),
                                        RoundedCornerShape(3.dp),
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityCard(metrics: ProjectMetricsUi?, modifier: Modifier) {
    GlassSurface(modifier = modifier) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Activity", style = MaterialTheme.typography.titleSmall)
            if (metrics == null || metrics.activity.isEmpty()) {
                MetricUnavailable()
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().height(70.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    metrics.activity.takeLast(6).forEach { month ->
                        val height = (month.value.coerceIn(0f, 1f) * 56f + 4f).dp
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier
                                    .size(width = 12.dp, height = height)
                                    .background(
                                        Brush.verticalGradient(listOf(NeonTeal, NeonBlue)),
                                        RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
                                    ),
                            )
                            Text(
                                month.label.take(3),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricUnavailable() {
    Text(
        "Unavailable until a project is indexed.",
        color = TextSecondary,
        style = MaterialTheme.typography.labelSmall,
    )
}

@Composable
private fun ProgressRing(progress: Float, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        drawArc(
            color = Color.White.copy(alpha = 0.12f),
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            style = Stroke(6.dp.toPx()),
        )
        drawArc(
            brush = Brush.sweepGradient(listOf(color, NeonBlue, color)),
            startAngle = -90f,
            sweepAngle = progress.coerceIn(0f, 1f) * 360f,
            useCenter = false,
            style = Stroke(6.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

@Composable
private fun DonutChart(values: List<Float>, modifier: Modifier) {
    val colors = listOf(NeonTeal, ElectricPurple, NeonAmber, NeonBlue, NeonGreen)
    Canvas(modifier) {
        var start = -90f
        values.forEachIndexed { index, value ->
            val sweep = value.coerceAtLeast(0f) / values.sum().coerceAtLeast(1f) * 360f
            drawArc(colors[index % colors.size], start, sweep, false, style = Stroke(8.dp.toPx()))
            start += sweep
        }
    }
}

private fun Modifier.backgroundTrack(): Modifier =
    background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(3.dp))
