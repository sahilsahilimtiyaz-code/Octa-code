package com.sahil.octacode.ui.demo

import com.sahil.octacode.ui.state.ActivityMonthUi
import com.sahil.octacode.ui.state.ActivityStepState
import com.sahil.octacode.ui.state.ActivityStepUi
import com.sahil.octacode.ui.state.ChatMessageUi
import com.sahil.octacode.ui.state.FileTypeCountUi
import com.sahil.octacode.ui.state.LanguageSliceUi
import com.sahil.octacode.ui.state.MessageAuthor
import com.sahil.octacode.ui.state.MissionFeedUi
import com.sahil.octacode.ui.state.MissionProgressUi
import com.sahil.octacode.ui.state.MissionTimelineEventUi
import com.sahil.octacode.ui.state.ProjectFileUi
import com.sahil.octacode.ui.state.ProjectMetricsUi
import com.sahil.octacode.ui.state.ProjectWorkspaceUi
import com.sahil.octacode.ui.state.StatusCardUi
import com.sahil.octacode.ui.state.StatusKind
import com.sahil.octacode.ui.state.UiContentSource

/** Presentation-only samples. Callers must keep the visible Demo Preview label with these values. */
object DemoSampleData {
    const val projectName = "SmartNotes · Demo"

    val metrics = ProjectMetricsUi(
        linesOfCode = 1_384,
        linesProgress = 0.76f,
        languages = listOf(
            LanguageSliceUi("Kotlin", 72f),
            LanguageSliceUi("Java", 14f),
            LanguageSliceUi("XML", 9f),
            LanguageSliceUi("Other", 5f),
        ),
        filesByType = listOf(
            FileTypeCountUi("Kotlin", 24),
            FileTypeCountUi("Java", 10),
            FileTypeCountUi("XML", 7),
            FileTypeCountUi("Gradle", 4),
        ),
        activity = listOf(
            ActivityMonthUi("Apr", 0.48f),
            ActivityMonthUi("May", 0.92f),
            ActivityMonthUi("Jun", 0.66f),
            ActivityMonthUi("Jul", 0.35f),
            ActivityMonthUi("Aug", 0.28f),
            ActivityMonthUi("Sep", 0.61f),
        ),
    )

    val project = ProjectWorkspaceUi(
        source = UiContentSource.DEMO,
        name = projectName,
        metrics = metrics,
        files = listOf(
            ProjectFileUi("app/src/main/SmartNotes.kt", "Kotlin · sample file"),
            ProjectFileUi("app/src/main/NotesScreen.kt", "Kotlin · sample file"),
            ProjectFileUi("README.md", "Markdown · sample file"),
        ),
    )

    val missionFeed = MissionFeedUi(
        source = UiContentSource.DEMO,
        messages = listOf(
            ChatMessageUi(
                id = "demo-user",
                author = MessageAuthor.USER,
                text = "Review this project and show me what the build workflow would look like.",
            ),
            ChatMessageUi(
                id = "demo-agent",
                author = MessageAuthor.AGENT,
                text = "This is a sample mission view. No provider, terminal, tests, or build was run.",
            ),
        ),
        mission = MissionProgressUi(
            status = "SIMULATED",
            currentStep = "Code Edit preview · no files changed",
            progress = 0.62f,
        ),
        progressSteps = listOf(
            ActivityStepUi("Project Scan", ActivityStepState.COMPLETE, 1f),
            ActivityStepUi("File Read", ActivityStepState.COMPLETE, 1f),
            ActivityStepUi("Code Edit", ActivityStepState.ACTIVE, 0.62f),
            ActivityStepUi("Build & Install", ActivityStepState.PENDING, 0f),
        ),
        result = StatusCardUi(
            kind = StatusKind.SUCCESS,
            title = "Build preview · simulated",
            message = "Example result only. No tests, build, or install ran.",
        ),
        issues = listOf("Missing dependency", "Unused imports", "Manifest needs review"),
        changes = listOf("Dependency update", "Import cleanup", "Manifest review"),
        activity = listOf(
            MissionTimelineEventUi("Project scan preview", "Sampled", ActivityStepState.COMPLETE),
            MissionTimelineEventUi("Code edit preview", "Preview", ActivityStepState.ACTIVE),
            MissionTimelineEventUi("Tests", "Not run", ActivityStepState.PENDING),
            MissionTimelineEventUi("Build and install", "Not run", ActivityStepState.PENDING),
        ),
    )
}
