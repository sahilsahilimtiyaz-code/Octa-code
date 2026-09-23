package com.sahil.octacode.ui.components

import com.sahil.octacode.domain.mission.EventKind
import com.sahil.octacode.domain.mission.MissionPhase
import com.sahil.octacode.domain.mission.PhaseEvent
import org.junit.Assert.assertTrue
import org.junit.Test

// M3d pure-logic tests for stream formatting (no Compose needed).
class StreamFormatTest {

    private fun event(kind: EventKind, payload: String) = PhaseEvent(
        id = 0,
        missionId = "m",
        phase = MissionPhase.IMPLEMENT,
        timestamp = 0,
        kind = kind,
        payloadJson = payload
    )

    @Test
    fun `progress payload pretty-prints message`() {
        val line = formatEventLine(event(EventKind.PHASE_PROGRESS, """{"message":"Writing Foo.kt"}"""))
        assertTrue(line.contains("[07]"))
        assertTrue(line.contains("Writing Foo.kt"))
        assertTrue(!line.contains("\"message\""))
    }

    @Test
    fun `failure payload appends retryable`() {
        val line = formatEventLine(event(EventKind.PHASE_FAILED, """{"reason":"boom","retryable":true}"""))
        assertTrue(line.contains("boom"))
        assertTrue(line.contains("retryable"))
    }

    @Test
    fun `checkpoint payload shows plan and needs`() {
        val line = formatEventLine(
            event(
                EventKind.CHECKPOINT_REQUIRED,
                """{"planSummary":"Review 2 changes","approvalsRequired":["diff-review"]}"""
            )
        )
        assertTrue(line.contains("Review 2 changes"))
        assertTrue(line.contains("diff-review"))
    }

    @Test
    fun `secrets are redacted in stream`() {
        val line = formatEventLine(event(EventKind.PHASE_PROGRESS, """{"message":"key sk-abcdefgh12345678 leaked"}"""))
        assertTrue(!line.contains("sk-abcdefgh12345678"))
        assertTrue(line.contains("sk-***"))
    }

    @Test
    fun `malformed json falls back honestly`() {
        val line = formatEventLine(event(EventKind.PHASE_PROGRESS, "not-json{{{"))
        assertTrue(line.contains("not-json"))
    }
}
