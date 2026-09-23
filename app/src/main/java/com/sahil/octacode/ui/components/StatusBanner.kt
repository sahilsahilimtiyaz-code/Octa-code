package com.sahil.octacode.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sahil.octacode.ui.theme.NeonBlue
import com.sahil.octacode.ui.theme.NeonGreen
import com.sahil.octacode.ui.theme.NeonRed
import com.sahil.octacode.ui.theme.OnDark
import com.sahil.octacode.ui.theme.OnDarkMuted
import com.sahil.octacode.ui.theme.SurfaceVariantDark
import com.sahil.octacode.ui.theme.WarningAmber

enum class BannerTone { Info, Success, Warning, Error, Running }

// M3b UI kit: full-width status banner with tone colors.
@Composable
fun StatusBanner(
    tone: BannerTone,
    title: String,
    message: String? = null,
    modifier: Modifier = Modifier
) {
    val accent: Color = when (tone) {
        BannerTone.Info -> NeonBlue
        BannerTone.Success -> NeonGreen
        BannerTone.Warning -> WarningAmber
        BannerTone.Error -> NeonRed
        BannerTone.Running -> NeonBlue
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceVariantDark)
            .border(1.dp, accent.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(text = title, style = MaterialTheme.typography.titleSmall, color = accent)
        if (message != null) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = OnDarkMuted,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

internal fun bannerToneFor(
    status: com.sahil.octacode.domain.mission.MissionStatus,
    awaiting: Boolean,
    busy: Boolean,
    hasError: Boolean
): BannerTone = when {
    awaiting -> BannerTone.Warning
    hasError && status == com.sahil.octacode.domain.mission.MissionStatus.FAILED -> BannerTone.Error
    status == com.sahil.octacode.domain.mission.MissionStatus.COMPLETE -> BannerTone.Success
    status == com.sahil.octacode.domain.mission.MissionStatus.CANCELLED -> BannerTone.Error
    status == com.sahil.octacode.domain.mission.MissionStatus.FAILED -> BannerTone.Error
    status == com.sahil.octacode.domain.mission.MissionStatus.PAUSED -> BannerTone.Warning
    busy || status == com.sahil.octacode.domain.mission.MissionStatus.RUNNING -> BannerTone.Running
    else -> BannerTone.Info
}
