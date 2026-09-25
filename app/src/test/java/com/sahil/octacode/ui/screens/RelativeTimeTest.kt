package com.sahil.octacode.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class RelativeTimeTest {

    private val now = 1_700_000_000_000L
    private val minute = 60_000L

    @Test
    fun `just now covers the current minute`() {
        assertEquals("just now", relativeTime(now, now))
        assertEquals("just now", relativeTime(now, now - 59 * 1000))
    }

    @Test
    fun `a clock ahead of now reads as just now rather than a negative age`() {
        // Device clocks move backwards. A session stamped in the future must
        // not render "-3d ago" on the card.
        assertEquals("just now", relativeTime(now, now + 40 * 60 * minute))
    }

    @Test
    fun `minutes stop at the hour boundary`() {
        assertEquals("1m ago", relativeTime(now, now - minute))
        assertEquals("59m ago", relativeTime(now, now - 59 * minute))
        assertEquals("1h ago", relativeTime(now, now - 60 * minute))
    }

    @Test
    fun `hours stop at the day boundary`() {
        assertEquals("23h ago", relativeTime(now, now - 23 * 60 * minute))
        assertEquals("1d ago", relativeTime(now, now - 24 * 60 * minute))
    }

    @Test
    fun `days floor rather than round into the next unit`() {
        // 47h is 1.96 days — still yesterday's problem, not two days' worth.
        assertEquals("1d ago", relativeTime(now, now - 47 * 60 * minute))
        assertEquals("30d ago", relativeTime(now, now - 30 * 24 * 60 * minute))
    }
}
