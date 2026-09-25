package com.sahil.octacode.data.settings

import android.content.Context
import android.content.SharedPreferences
import com.sahil.octacode.core.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for app settings.
 *
 * Every screen reads [settings] and writes through [update]; each write is
 * mirrored to disk, so values survive app restart, process death and activity
 * recreation. Compose screens collect the flow, so a change made on one screen
 * is visible on every other one immediately.
 *
 * Not encrypted: these are preferences, not secrets. API keys stay in
 * [com.sahil.octacode.data.security.CredentialStore], which is
 * Keystore-backed EncryptedSharedPreferences.
 */
class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(SettingsCodec.decode(prefs.all))

    val settings: StateFlow<Settings> = _settings.asStateFlow()

    /** Current snapshot for non-composable callers. */
    val value: Settings
        get() = _settings.value

    /**
     * Applies [transform] to the current value, clamps the result into its
     * legal range and persists it. No-op when the value did not change, so
     * repeated identical writes cost nothing.
     */
    fun update(transform: (Settings) -> Settings) {
        val next = transform(_settings.value).sanitized()
        if (next == _settings.value) return
        _settings.value = next
        write(next)
    }

    /** Restores every setting to its default. */
    fun reset() {
        val fresh = Settings()
        if (fresh == _settings.value) return
        _settings.value = fresh
        write(fresh)
    }

    private fun write(settings: Settings) {
        // clear() first keeps this file authoritative: a key removed from the
        // model cannot linger on disk and resurface on the next read.
        val editor = prefs.edit().clear()
        SettingsCodec.encode(settings).forEach { (key, value) ->
            // SharedPreferences is typed at the write side — there is no
            // put(Any) — so dispatch explicitly rather than let a value arrive
            // on disk in a shape decode would have to guess at.
            when (value) {
                is String -> editor.putString(key, value)
                is Float -> editor.putFloat(key, value)
                is Int -> editor.putInt(key, value)
                is Boolean -> editor.putBoolean(key, value)
                null -> editor.remove(key)
                else -> error(
                    "SettingsCodec.encode produced an unsupported type for '$key': " +
                        value::class.qualifiedName
                )
            }
        }
        editor.apply()
    }

    private companion object {
        const val FILE_NAME = "octa_settings"
    }
}
