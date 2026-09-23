package com.sahil.octacode

import com.sahil.octacode.navigation.AppDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDestinationTest {
    @Test
    fun destinationsHaveUniqueRoutesAndThreeWorkspaceTabs() {
        val destinations = AppDestination.entries
        assertEquals(6, destinations.size)
        assertEquals(destinations.size, destinations.map { it.route }.toSet().size)
        assertTrue(destinations.all { it.label.isNotBlank() })
        assertEquals(
            setOf(AppDestination.AI, AppDestination.CODE, AppDestination.TERMINAL),
            destinations.filter { it.isWorkspace }.toSet(),
        )
    }
}
