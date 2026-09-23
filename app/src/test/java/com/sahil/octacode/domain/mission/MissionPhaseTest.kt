package com.sahil.octacode.domain.mission

import com.sahil.octacode.core.capability.AutonomyLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MissionPhaseTest {

    @Test
    fun `exactly 16 phases in spec order`() {
        assertEquals(16, MissionPhase.entries.size)
        assertEquals(16, MissionPhase.TOTAL)
        assertEquals(16, MissionPhase.ORDERED.size)
        assertEquals(1, MissionPhase.ORDERED.first().index)
        assertEquals(16, MissionPhase.ORDERED.last().index)
        MissionPhase.ORDERED.forEachIndexed { i, phase ->
            assertEquals(i + 1, phase.index)
        }
    }

    @Test
    fun `spec pipeline titles match milestone definition`() {
        assertEquals("Understand task", MissionPhase.UNDERSTAND_TASK.title)
        assertEquals("Detect project type", MissionPhase.DETECT_PROJECT.title)
        assertEquals("Select agent team", MissionPhase.SELECT_TEAM.title)
        assertEquals("Select provider/runtime", MissionPhase.SELECT_PROVIDER.title)
        assertEquals("Plan approach", MissionPhase.PLAN_APPROACH.title)
        assertEquals("User checkpoint", MissionPhase.USER_CHECKPOINT.title)
        assertEquals("Implement changes", MissionPhase.IMPLEMENT.title)
        assertEquals("Review diff", MissionPhase.REVIEW_DIFF.title)
        assertEquals("Format code", MissionPhase.FORMAT.title)
        assertEquals("Analyze impact", MissionPhase.ANALYZE_IMPACT.title)
        assertEquals("Test changes", MissionPhase.TEST.title)
        assertEquals("Build", MissionPhase.BUILD.title)
        assertEquals("Install APK", MissionPhase.INSTALL.title)
        assertEquals("Verify success", MissionPhase.VERIFY.title)
        assertEquals("Rollback if failed", MissionPhase.ROLLBACK.title)
        assertEquals("Mission complete", MissionPhase.COMPLETE.title)
    }

    @Test
    fun `next and prev walk the chain without gaps`() {
        var phase: MissionPhase? = MissionPhase.UNDERSTAND_TASK
        val seen = mutableListOf<MissionPhase>()
        while (phase != null) {
            seen += phase
            phase = phase.next()
        }
        assertEquals(MissionPhase.ORDERED, seen)
        assertEquals(MissionPhase.COMPLETE, seen.last())
        assertNull(MissionPhase.COMPLETE.next())
        assertNull(MissionPhase.UNDERSTAND_TASK.prev())
        assertEquals(
            MissionPhase.PLAN_APPROACH,
            MissionPhase.USER_CHECKPOINT.prev()
        )
        assertEquals(
            MissionPhase.IMPLEMENT,
            MissionPhase.USER_CHECKPOINT.next()
        )
    }

    @Test
    fun `fromIndex round-trips every phase`() {
        MissionPhase.entries.forEach { p ->
            assertEquals(p, MissionPhase.fromIndex(p.index))
        }
        assertNull(MissionPhase.fromIndex(0))
        assertNull(MissionPhase.fromIndex(17))
    }

    @Test
    fun `only install build and checkpoint require approval by default flags`() {
        assertTrue(MissionPhase.USER_CHECKPOINT.requiresApproval)
        assertTrue(MissionPhase.BUILD.requiresApproval)
        assertTrue(MissionPhase.INSTALL.requiresApproval)
        assertFalse(MissionPhase.IMPLEMENT.requiresApproval)
        assertFalse(MissionPhase.COMPLETE.requiresApproval)
    }

    @Test
    fun `skippable phases are format test build install rollback only`() {
        val skippable = MissionPhase.entries.filter { it.maySkip }.map { it.index }
        assertEquals(listOf(9, 11, 12, 13, 15), skippable)
    }

    @Test
    fun `ASK autonomy requires approval on all approval phases`() {
        MissionPhase.entries.filter { it.requiresApproval }.forEach { phase ->
            assertTrue(
                "ASK should gate $phase",
                MissionPhase.approvalRequired(phase, AutonomyLevel.ASK)
            )
        }
    }

    @Test
    fun `HIGH_AUTONOMY auto-approves everything`() {
        MissionPhase.entries.forEach { phase ->
            assertFalse(
                "HIGH should not gate $phase",
                MissionPhase.approvalRequired(phase, AutonomyLevel.HIGH_AUTONOMY)
            )
        }
    }

    @Test
    fun `BALANCED gates checkpoint and install only`() {
        assertTrue(MissionPhase.approvalRequired(MissionPhase.USER_CHECKPOINT, AutonomyLevel.BALANCED))
        assertTrue(MissionPhase.approvalRequired(MissionPhase.INSTALL, AutonomyLevel.BALANCED))
        assertFalse(MissionPhase.approvalRequired(MissionPhase.BUILD, AutonomyLevel.BALANCED))
        assertFalse(MissionPhase.approvalRequired(MissionPhase.IMPLEMENT, AutonomyLevel.BALANCED))
    }

    @Test
    fun `GUIDED gates install only`() {
        assertTrue(MissionPhase.approvalRequired(MissionPhase.INSTALL, AutonomyLevel.GUIDED))
        assertFalse(MissionPhase.approvalRequired(MissionPhase.USER_CHECKPOINT, AutonomyLevel.GUIDED))
        assertFalse(MissionPhase.approvalRequired(MissionPhase.BUILD, AutonomyLevel.GUIDED))
    }

    @Test
    fun `non-approval phases never gate regardless of autonomy`() {
        AutonomyLevel.entries.forEach { auto ->
            assertFalse(MissionPhase.approvalRequired(MissionPhase.IMPLEMENT, auto))
            assertFalse(MissionPhase.approvalRequired(MissionPhase.SELECT_PROVIDER, auto))
        }
    }
}

class MissionStatusTest {

    @Test
    fun `terminal states are failed complete cancelled`() {
        assertTrue(MissionStatus.FAILED.isTerminal)
        assertTrue(MissionStatus.COMPLETE.isTerminal)
        assertTrue(MissionStatus.CANCELLED.isTerminal)
        assertFalse(MissionStatus.DRAFT.isTerminal)
        assertFalse(MissionStatus.RUNNING.isTerminal)
        assertFalse(MissionStatus.PAUSED.isTerminal)
    }

    @Test
    fun `phase and mission enums have stable names for Room strings`() {
        assertEquals("RUNNING", MissionStatus.RUNNING.name)
        assertEquals("SUCCEEDED", PhaseStatus.SUCCEEDED.name)
        assertEquals("PHASE_STARTED", EventKind.PHASE_STARTED.name)
        assertEquals("ACCEPTED", DiffDecision.ACCEPTED.name)
        assertNotNull(MissionPhase.fromIndex(7))
    }
}
