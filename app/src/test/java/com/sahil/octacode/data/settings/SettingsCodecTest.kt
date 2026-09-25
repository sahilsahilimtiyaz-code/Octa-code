package com.sahil.octacode.data.settings

import com.sahil.octacode.core.settings.SendBehavior
import com.sahil.octacode.core.settings.Settings
import com.sahil.octacode.core.settings.SyntaxTheme
import com.sahil.octacode.core.settings.ThemeMode
import com.sahil.octacode.core.settings.ToolCallDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Settings are worthless if a restart loses them or a bad value reaches a
 * slider. These pin the two guarantees: round-trip fidelity, and repair rather
 * than crash when the stored map is missing, mistyped or hostile.
 */
class SettingsCodecTest {

    @Test
    fun `a fully specified settings object round trips exactly`() {
        val original = Settings(
            theme = ThemeMode.DARK,
            uiFontScale = 1.25f,
            codeFontScale = 1.4f,
            syntaxTheme = SyntaxTheme.DRACULA,
            toolCallDetail = ToolCallDetail.DETAILED,
            autoExpandReasoning = true,
            sendBehavior = SendBehavior.QUEUE,
            autoArchive = true,
            archiveAfterDays = 60,
            limitActiveChats = true,
            maxActiveChats = 7,
        )

        assertEquals(original, SettingsCodec.decode(SettingsCodec.encode(original)))
    }

    @Test
    fun `every enum constant round trips so a new value cannot be dropped`() {
        ThemeMode.entries.forEach { theme ->
            val encoded = SettingsCodec.encode(Settings(theme = theme))
            assertEquals(theme, SettingsCodec.decode(encoded).theme)
        }
        ToolCallDetail.entries.forEach { detail ->
            val encoded = SettingsCodec.encode(Settings(toolCallDetail = detail))
            assertEquals(detail, SettingsCodec.decode(encoded).toolCallDetail)
        }
        SyntaxTheme.entries.forEach { syntax ->
            val encoded = SettingsCodec.encode(Settings(syntaxTheme = syntax))
            assertEquals(syntax, SettingsCodec.decode(encoded).syntaxTheme)
        }
        SendBehavior.entries.forEach { behavior ->
            val encoded = SettingsCodec.encode(Settings(sendBehavior = behavior))
            assertEquals(behavior, SettingsCodec.decode(encoded).sendBehavior)
        }
    }

    @Test
    fun `an empty store yields the documented defaults`() {
        assertEquals(Settings(), SettingsCodec.decode(emptyMap<String, Any?>()))
        assertEquals(ThemeMode.AUTO, SettingsCodec.decode(emptyMap<String, Any?>()).theme)
        assertEquals(30, SettingsCodec.decode(emptyMap<String, Any?>()).archiveAfterDays)
        assertEquals(20, SettingsCodec.decode(emptyMap<String, Any?>()).maxActiveChats)
    }

    @Test
    fun `a mistyped value falls back instead of throwing`() {
        val raw = mapOf<String, Any?>(
            SettingsCodec.KEY_THEME to 42,
            SettingsCodec.KEY_UI_FONT_SCALE to "big",
            SettingsCodec.KEY_AUTO_ARCHIVE to "yes",
            SettingsCodec.KEY_ARCHIVE_AFTER_DAYS to true,
        )
        val decoded = SettingsCodec.decode(raw)
        assertEquals(ThemeMode.AUTO, decoded.theme)
        assertEquals(1.0f, decoded.uiFontScale, 0f)
        assertEquals(false, decoded.autoArchive)
        assertEquals(Settings.DEFAULT_ARCHIVE_DAYS, decoded.archiveAfterDays)
    }

    @Test
    fun `an enum name written by a future version falls back to the default`() {
        val raw = mapOf<String, Any?>(
            SettingsCodec.KEY_THEME to "TRANSLUCENT",
            SettingsCodec.KEY_SEND_BEHAVIOR to "MAYBE",
        )
        assertEquals(ThemeMode.AUTO, SettingsCodec.decode(raw).theme)
        assertEquals(SendBehavior.IMMEDIATELY, SettingsCodec.decode(raw).sendBehavior)
    }

    @Test
    fun `an out of range font scale is clamped on decode`() {
        val decoded = SettingsCodec.decode(
            mapOf<String, Any?>(
                SettingsCodec.KEY_UI_FONT_SCALE to 4.0f,
                SettingsCodec.KEY_CODE_FONT_SCALE to 0.1f,
            )
        )
        assertEquals(Settings.MAX_FONT_SCALE, decoded.uiFontScale, 0f)
        assertEquals(Settings.MIN_FONT_SCALE, decoded.codeFontScale, 0f)
    }

    @Test
    fun `an archive window outside the offered choices is repaired to 30`() {
        Settings.ARCHIVE_CHOICES.forEach { days ->
            assertEquals(
                days,
                SettingsCodec.decode(
                    mapOf<String, Any?>(SettingsCodec.KEY_ARCHIVE_AFTER_DAYS to days)
                ).archiveAfterDays
            )
        }
        assertEquals(
            Settings.DEFAULT_ARCHIVE_DAYS,
            SettingsCodec.decode(
                mapOf<String, Any?>(SettingsCodec.KEY_ARCHIVE_AFTER_DAYS to 3)
            ).archiveAfterDays
        )
    }

    @Test
    fun `an absurd chat limit is clamped`() {
        val decoded = SettingsCodec.decode(
            mapOf<String, Any?>(SettingsCodec.KEY_MAX_ACTIVE_CHATS to 99_999)
        )
        assertEquals(Settings.MAX_ACTIVE_CHATS, decoded.maxActiveChats)
    }

    @Test
    fun `encode sanitizes so the disk never holds an illegal value`() {
        val encoded = SettingsCodec.encode(Settings(uiFontScale = 99f, archiveAfterDays = 1))
        assertEquals(Settings.MAX_FONT_SCALE, encoded[SettingsCodec.KEY_UI_FONT_SCALE])
        assertEquals(Settings.DEFAULT_ARCHIVE_DAYS, encoded[SettingsCodec.KEY_ARCHIVE_AFTER_DAYS])
    }

    @Test
    fun `keys written by a future version are ignored rather than crashing`() {
        val withFutureKeys = SettingsCodec.encode(Settings()) +
            mapOf<String, Any?>("some_future_setting" to 1, "another" to "x")
        assertEquals(Settings(), SettingsCodec.decode(withFutureKeys))
        assertNull(withFutureKeys["missing_key"])
    }

    @Test
    fun `decode then encode is stable`() {
        val once = SettingsCodec.decode(SettingsCodec.encode(Settings(theme = ThemeMode.LIGHT)))
        assertEquals(SettingsCodec.encode(once), SettingsCodec.encode(SettingsCodec.decode(SettingsCodec.encode(once))))
    }
}
