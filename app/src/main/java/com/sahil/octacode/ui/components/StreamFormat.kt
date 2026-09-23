package com.sahil.octacode.ui.components

import com.sahil.octacode.data.security.SecretRedactor
import com.sahil.octacode.domain.mission.CheckpointPayload
import com.sahil.octacode.domain.mission.EventKind
import com.sahil.octacode.domain.mission.PhaseEvent
import com.sahil.octacode.domain.mission.PhaseFailurePayload
import com.sahil.octacode.domain.mission.PhaseProgressPayload
import kotlinx.serialization.json.Json

// M3d: pure formatter for the event stream — pretty-prints JSON payloads,
// redacts secrets before display, never throws (honest fallback to raw).
private val streamJson = Json { ignoreUnknownKeys = true; isLenient = true }

fun formatEventLine(event: PhaseEvent): String {
    val tag = "%02d".format(event.phase.index)
    val body = when (event.kind) {
        EventKind.PHASE_PROGRESS,
        EventKind.PHASE_SUCCEEDED -> decodeProgress(event.payloadJson)
        EventKind.PHASE_FAILED,
        EventKind.MISSION_FAILED -> decodeFailure(event.payloadJson)
        EventKind.CHECKPOINT_REQUIRED -> decodeCheckpoint(event.payloadJson)
        else -> event.payloadJson.takeIf { it.isNotBlank() && it != "{}" }
            ?: event.kind.name
    }
    return "[${tag}] ${event.kind.name}: ${SecretRedactor.redact(body).take(600)}"
}

internal fun decodeProgress(json: String): String = try {
    streamJson.decodeFromString(PhaseProgressPayload.serializer(), json).message
} catch (_: Exception) {
    json.ifBlank { "progress" }
}

internal fun decodeFailure(json: String): String = try {
    val p = streamJson.decodeFromString(PhaseFailurePayload.serializer(), json)
    if (p.retryable) "${p.reason} (retryable)" else p.reason
} catch (_: Exception) {
    json.ifBlank { "failed" }
}

internal fun decodeCheckpoint(json: String): String = try {
    val p = streamJson.decodeFromString(CheckpointPayload.serializer(), json)
    val extra = p.approvalsRequired.takeIf { it.isNotEmpty() }
        ?.let { " [needs: ${it.joinToString()}]" } ?: ""
    p.planSummary.take(300) + extra
} catch (_: Exception) {
    json.ifBlank { "checkpoint" }
}
