package com.sahil.octacode.core.capability

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProjectTypeDetectorTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun file(name: String, content: String = ""): File =
        tmp.newFile(name).apply { writeText(content) }

    @Test
    fun `android project with gradle and manifest detects ANDROID`() {
        file("settings.gradle.kts")
        val app = File(tmp.root, "app/src/main").apply { mkdirs() }
        File(app, "AndroidManifest.xml").writeText("<manifest/>")
        assertEquals(ProjectType.ANDROID, ProjectTypeDetector.detect(tmp.root))
    }

    @Test
    fun `groovy android project detects ANDROID`() {
        file("build.gradle")
        val src = File(tmp.root, "src/main").apply { mkdirs() }
        File(src, "AndroidManifest.xml").writeText("<manifest/>")
        assertEquals(ProjectType.ANDROID, ProjectTypeDetector.detect(tmp.root))
    }

    @Test
    fun `package json detects NODE`() {
        file("package.json", "{}")
        assertEquals(ProjectType.NODE, ProjectTypeDetector.detect(tmp.root))
    }

    @Test
    fun `requirements txt detects PYTHON`() {
        file("requirements.txt", "requests")
        assertEquals(ProjectType.PYTHON, ProjectTypeDetector.detect(tmp.root))
    }

    @Test
    fun `pyproject toml detects PYTHON`() {
        file("pyproject.toml", "[project]")
        assertEquals(ProjectType.PYTHON, ProjectTypeDetector.detect(tmp.root))
    }

    @Test
    fun `empty dir is UNKNOWN not assumed`() {
        assertEquals(ProjectType.UNKNOWN, ProjectTypeDetector.detect(tmp.root))
    }

    @Test
    fun `missing path is UNKNOWN`() {
        assertEquals(ProjectType.UNKNOWN, ProjectTypeDetector.detect(File(tmp.root, "nope")))
    }

    @Test
    fun `file that is not a directory is UNKNOWN`() {
        val f = file("plain.txt", "x")
        assertEquals(ProjectType.UNKNOWN, ProjectTypeDetector.detect(f))
    }

    @Test
    fun `gradle without manifest is not ANDROID`() {
        file("settings.gradle.kts")
        assertEquals(ProjectType.UNKNOWN, ProjectTypeDetector.detect(tmp.root))
    }
}

class ProviderStatusTest {

    @Test
    fun `only Ready is configured`() {
        assertTrue(ProviderStatus.Ready("ok").configured)
        assertFalse(ProviderStatus.MissingKey().configured)
        assertFalse(ProviderStatus.Misconfigured("bad url").configured)
        assertFalse(ProviderStatus.Unavailable("no adapter").configured)
    }

    @Test
    fun `every status carries a non-blank reason`() {
        val all = listOf(
            ProviderStatus.Ready(),
            ProviderStatus.MissingKey(),
            ProviderStatus.Misconfigured("x"),
            ProviderStatus.Unavailable("y")
        )
        all.forEach { assertTrue(it.reason.isNotBlank()) }
    }
}
