package com.sahil.octacode.core.capability

import java.io.File

// M1: project type is DETECTED from files, never assumed.
enum class ProjectType { ANDROID, NODE, PYTHON, UNKNOWN }

object ProjectTypeDetector {
    fun detect(root: File): ProjectType {
        if (!root.isDirectory) return ProjectType.UNKNOWN
        val names = (root.list()?.toSet()) ?: return ProjectType.UNKNOWN
        val hasGradle = names.contains("settings.gradle.kts") ||
            names.contains("settings.gradle") ||
            names.contains("build.gradle.kts") ||
            names.contains("build.gradle")
        val hasAndroidManifest = File(root, "app/src/main/AndroidManifest.xml").exists() ||
            File(root, "src/main/AndroidManifest.xml").exists()
        return when {
            hasGradle && hasAndroidManifest -> ProjectType.ANDROID
            names.contains("package.json") -> ProjectType.NODE
            names.contains("requirements.txt") || names.contains("pyproject.toml") ->
                ProjectType.PYTHON
            else -> ProjectType.UNKNOWN
        }
    }
}
