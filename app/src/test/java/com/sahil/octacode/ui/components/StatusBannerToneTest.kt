package com.sahil.octacode.ui.components

import com.sahil.octacode.domain.mission.MissionStatus
import org.junit.Assert.assertEquals
import org.junit.Test

// M3b pure-logic tests for UI kit helpers (no Robolectric needed).
class StatusBannerToneTest {

    @Test
    fun `failed mission maps to Error`() {
        assertEquals(
            BannerTone.Error,
            bannerToneFor(MissionStatus.FAILED, awaiting = false, busy = false, hasError = true)
        )
    }

    @Test
    fun `complete maps to Success`() {
        assertEquals(
            BannerTone.Success,
            bannerToneFor(MissionStatus.COMPLETE, awaiting = false, busy = false, hasError = false)
        )
    }

    @Test
    fun `cancelled maps to Error`() {
        assertEquals(
            BannerTone.Error,
            bannerToneFor(MissionStatus.CANCELLED, awaiting = false, busy = false, hasError = false)
        )
    }

    @Test
    fun `paused maps to Warning`() {
        assertEquals(
            BannerTone.Warning,
            bannerToneFor(MissionStatus.PAUSED, awaiting = false, busy = false, hasError = false)
        )
    }

    @Test
    fun `awaiting checkpoint wins over paused`() {
        assertEquals(
            BannerTone.Warning,
            bannerToneFor(MissionStatus.PAUSED, awaiting = true, busy = false, hasError = false)
        )
    }

    @Test
    fun `running busy maps to Running`() {
        assertEquals(
            BannerTone.Running,
            bannerToneFor(MissionStatus.RUNNING, awaiting = false, busy = true, hasError = false)
        )
    }

    @Test
    fun `draft idle maps to Info`() {
        assertEquals(
            BannerTone.Info,
            bannerToneFor(MissionStatus.DRAFT, awaiting = false, busy = false, hasError = false)
        )
    }
}

class RouteHelperTest {
    @Test
    fun `mission routes hide bottom bar`() {
        assertEquals(true, com.sahil.octacode.ui.navigation.Routes.isMissionRoute("mission/launch"))
        assertEquals(true, com.sahil.octacode.ui.navigation.Routes.isMissionRoute("mission/abc-123"))
        assertEquals(false, com.sahil.octacode.ui.navigation.Routes.isMissionRoute("home"))
        assertEquals(false, com.sahil.octacode.ui.navigation.Routes.isMissionRoute(null))
    }

    @Test
    fun `mission detail builds path route`() {
        assertEquals(
            "mission/xyz",
            com.sahil.octacode.ui.navigation.Routes.missionDetail("xyz")
        )
    }
}
