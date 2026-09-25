package com.sahil.octacode.core.settings

/**
 * Typed app settings.
 *
 * Every field here is real: if it exists, the Settings screen shows it, the
 * change is written to disk, and it is still there after the process dies.
 *
 * Language is deliberately absent. The app ships exactly one locale
 * (`res/values`) with 15 files of hardcoded `Text("…")` literals, so a language
 * picker would persist a value that never changes anything visible — a
 * decorative control, which this project does not ship. Adding it means
 * extracting those strings and translating them first.
 *
 * Choice-valued settings are enums rather than booleans so the UI cannot use a
 * switch where the setting actually has several answers.
 */

/** Enumerated settings carry their own labels so screens never hardcode option text. */
enum class ThemeMode(val label: String) {
    AUTO("System default"),
    LIGHT("Light"),
    DARK("Dark"),
}

enum class ToolCallDetail(val label: String) {
    COMPACT("Compact"),
    NORMAL("Normal"),
    DETAILED("Detailed"),
}

enum class SendBehavior(val label: String) {
    IMMEDIATELY("Send immediately"),
    QUEUE("Queue while streaming"),
}

enum class SyntaxTheme(val label: String) {
    OCTA_DARK("Octa Dark"),
    MONOKAI("Monokai"),
    DRACULA("Dracula"),
    LIGHT("Light"),
}

data class Settings(
    val theme: ThemeMode = ThemeMode.AUTO,
    val uiFontScale: Float = 1.0f,
    val codeFontScale: Float = 1.0f,
    val syntaxTheme: SyntaxTheme = SyntaxTheme.OCTA_DARK,
    val toolCallDetail: ToolCallDetail = ToolCallDetail.NORMAL,
    val autoExpandReasoning: Boolean = false,
    val sendBehavior: SendBehavior = SendBehavior.IMMEDIATELY,
    val autoArchive: Boolean = false,
    val archiveAfterDays: Int = 30,
    val limitActiveChats: Boolean = false,
    val maxActiveChats: Int = 20,
    /**
     * The local server this build does not run yet.
     *
     * Stored now because it is a real value with a real future reader, and
     * because a port the user picked should not be forgotten by the release
     * that arrives before the server does. The control is shown locked until
     * there is a process to bind it to — a switch over a service that does not
     * exist would be a setting that writes and never acts.
     */
    val serverEnabled: Boolean = false,
    val serverPort: Int = DEFAULT_SERVER_PORT,
) {

    /**
     * Clamps every value into its legal range. Persistence can hand back
     * anything — a hand-edited preference, a value written by a future version
     * — and the UI must never render a slider at 4.0x or a 0-day archive window.
     */
    fun sanitized(): Settings = copy(
        uiFontScale = uiFontScale.clampedFontScale(),
        codeFontScale = codeFontScale.clampedFontScale(),
        archiveAfterDays = archiveAfterDays.takeIf { it in ARCHIVE_CHOICES }
            ?: DEFAULT_ARCHIVE_DAYS,
        maxActiveChats = maxActiveChats.coerceIn(MIN_ACTIVE_CHATS, MAX_ACTIVE_CHATS),
        serverPort = serverPort.coerceIn(MIN_SERVER_PORT, MAX_SERVER_PORT),
    )

    private fun Float.clampedFontScale(): Float =
        if (isNaN()) 1.0f else coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE)

    companion object {
        const val MIN_FONT_SCALE = 0.85f
        const val MAX_FONT_SCALE = 1.60f

        /** The only archive windows offered; anything else is repaired to 30. */
        val ARCHIVE_CHOICES = listOf(7, 14, 30, 60, 90)
        const val DEFAULT_ARCHIVE_DAYS = 30

        const val MIN_ACTIVE_CHATS = 1
        const val MAX_ACTIVE_CHATS = 200

        const val DEFAULT_SERVER_PORT = 8080

        /** Ports below 1024 need privileges this app does not have. */
        const val MIN_SERVER_PORT = 1024
        const val MAX_SERVER_PORT = 65535
    }
}
