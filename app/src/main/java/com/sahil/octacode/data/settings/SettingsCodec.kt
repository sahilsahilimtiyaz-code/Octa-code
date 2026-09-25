package com.sahil.octacode.data.settings

import com.sahil.octacode.core.settings.SendBehavior
import com.sahil.octacode.core.settings.Settings
import com.sahil.octacode.core.settings.SyntaxTheme
import com.sahil.octacode.core.settings.ThemeMode
import com.sahil.octacode.core.settings.ToolCallDetail

/**
 * Settings <-> preference-map mapping, kept out of the repository so it is pure
 * and unit-testable without an Android `Context`.
 *
 * Defaults live in exactly one place: [Settings]'s own constructor. Decode
 * starts from `Settings()` and overwrites with whatever the map actually holds,
 * so a missing key, a wrong-typed key and a key written by a future version are
 * all handled the same way — fall back, never throw.
 */
internal object SettingsCodec {

    const val KEY_THEME = "theme"
    const val KEY_UI_FONT_SCALE = "ui_font_scale"
    const val KEY_CODE_FONT_SCALE = "code_font_scale"
    const val KEY_SYNTAX_THEME = "syntax_theme"
    const val KEY_TOOL_CALL_DETAIL = "tool_call_detail"
    const val KEY_AUTO_EXPAND_REASONING = "auto_expand_reasoning"
    const val KEY_SEND_BEHAVIOR = "send_behavior"
    const val KEY_AUTO_ARCHIVE = "auto_archive"
    const val KEY_ARCHIVE_AFTER_DAYS = "archive_after_days"
    const val KEY_LIMIT_ACTIVE_CHATS = "limit_active_chats"
    const val KEY_MAX_ACTIVE_CHATS = "max_active_chats"
    const val KEY_SERVER_ENABLED = "server_enabled"
    const val KEY_SERVER_PORT = "server_port"

    fun encode(settings: Settings): Map<String, Any?> {
        val s = settings.sanitized()
        return mapOf(
            KEY_THEME to s.theme.name,
            KEY_UI_FONT_SCALE to s.uiFontScale,
            KEY_CODE_FONT_SCALE to s.codeFontScale,
            KEY_SYNTAX_THEME to s.syntaxTheme.name,
            KEY_TOOL_CALL_DETAIL to s.toolCallDetail.name,
            KEY_AUTO_EXPAND_REASONING to s.autoExpandReasoning,
            KEY_SEND_BEHAVIOR to s.sendBehavior.name,
            KEY_AUTO_ARCHIVE to s.autoArchive,
            KEY_ARCHIVE_AFTER_DAYS to s.archiveAfterDays,
            KEY_LIMIT_ACTIVE_CHATS to s.limitActiveChats,
            KEY_MAX_ACTIVE_CHATS to s.maxActiveChats,
            KEY_SERVER_ENABLED to s.serverEnabled,
            KEY_SERVER_PORT to s.serverPort,
        )
    }

    fun decode(raw: Map<String, *>): Settings {
        val base = Settings()
        return base.copy(
            theme = raw.enumOr(KEY_THEME, base.theme),
            uiFontScale = raw.floatOr(KEY_UI_FONT_SCALE, base.uiFontScale),
            codeFontScale = raw.floatOr(KEY_CODE_FONT_SCALE, base.codeFontScale),
            syntaxTheme = raw.enumOr(KEY_SYNTAX_THEME, base.syntaxTheme),
            toolCallDetail = raw.enumOr(KEY_TOOL_CALL_DETAIL, base.toolCallDetail),
            autoExpandReasoning = raw.boolOr(KEY_AUTO_EXPAND_REASONING, base.autoExpandReasoning),
            sendBehavior = raw.enumOr(KEY_SEND_BEHAVIOR, base.sendBehavior),
            autoArchive = raw.boolOr(KEY_AUTO_ARCHIVE, base.autoArchive),
            archiveAfterDays = raw.intOr(KEY_ARCHIVE_AFTER_DAYS, base.archiveAfterDays),
            limitActiveChats = raw.boolOr(KEY_LIMIT_ACTIVE_CHATS, base.limitActiveChats),
            maxActiveChats = raw.intOr(KEY_MAX_ACTIVE_CHATS, base.maxActiveChats),
            serverEnabled = raw.boolOr(KEY_SERVER_ENABLED, base.serverEnabled),
            serverPort = raw.intOr(KEY_SERVER_PORT, base.serverPort),
        ).sanitized()
    }

    // --- tolerant readers: any key we cannot read becomes the default ---------

    private inline fun <reified T : Enum<T>> Map<String, *>.enumOr(key: String, fallback: T): T {
        val stored = this[key] as? String ?: return fallback
        return enumValues<T>().firstOrNull { it.name == stored } ?: fallback
    }

    private fun Map<String, *>.floatOr(key: String, fallback: Float): Float =
        (this[key] as? Number)?.toFloat() ?: fallback

    private fun Map<String, *>.intOr(key: String, fallback: Int): Int =
        (this[key] as? Number)?.toInt() ?: fallback

    private fun Map<String, *>.boolOr(key: String, fallback: Boolean): Boolean =
        this[key] as? Boolean ?: fallback
}
