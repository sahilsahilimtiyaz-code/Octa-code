package com.sahil.octacode.ui.state

enum class MessageAuthor {
    USER,
    AGENT,
}

enum class UiContentSource {
    LIVE,
    DEMO,
    UNAVAILABLE,
}

data class MissionTimelineEventUi(
    val label: String,
    val result: String,
    val state: ActivityStepState,
)

data class MissionFeedUi(
    val source: UiContentSource,
    val messages: List<ChatMessageUi>,
    val mission: MissionProgressUi,
    val progressSteps: List<ActivityStepUi>,
    val result: StatusCardUi,
    val issues: List<String>,
    val changes: List<String>,
    val activity: List<MissionTimelineEventUi>,
)

data class ProjectFileUi(
    val path: String,
    val description: String,
)

data class ProjectWorkspaceUi(
    val source: UiContentSource,
    val name: String?,
    val metrics: ProjectMetricsUi?,
    val files: List<ProjectFileUi>,
)

data class ChatMessageUi(
    val id: String,
    val author: MessageAuthor,
    val text: String,
    val language: String? = null,
    val streaming: Boolean = false,
    val error: String? = null,
)

data class MissionProgressUi(
    val status: String,
    val currentStep: String,
    val progress: Float,
    val startedAtEpochMillis: Long? = null,
)

enum class StatusKind {
    SUCCESS,
    FAILURE,
    WARNING,
    INFO,
}

data class StatusCardUi(
    val kind: StatusKind,
    val title: String,
    val message: String,
)

enum class ActivityStepState {
    COMPLETE,
    ACTIVE,
    PENDING,
}

data class ActivityStepUi(
    val label: String,
    val state: ActivityStepState,
    val progress: Float = 0f,
)

data class ProjectSummaryUi(
    val id: String,
    val name: String,
    val languageBreakdown: String,
    val lastModified: String,
)

data class LanguageSliceUi(
    val label: String,
    val percentage: Float,
)

data class FileTypeCountUi(
    val label: String,
    val count: Int,
)

data class ActivityMonthUi(
    val label: String,
    val value: Float,
)

data class ProjectMetricsUi(
    val linesOfCode: Int,
    val linesProgress: Float,
    val languages: List<LanguageSliceUi>,
    val filesByType: List<FileTypeCountUi>,
    val activity: List<ActivityMonthUi>,
)
