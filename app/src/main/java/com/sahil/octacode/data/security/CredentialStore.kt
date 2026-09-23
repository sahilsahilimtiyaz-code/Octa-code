package com.sahil.octacode.data.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.sahil.octacode.core.provider.ProviderId

// Credentials live ONLY in EncryptedSharedPreferences (Keystore-backed MasterKey).
// No key/token is ever logged — callers must pass values through SecretRedactor first.
class CredentialStore(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "octa_credentials",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun getApiKey(id: ProviderId): String? =
        prefs.getString(keyFor(id), null)?.takeIf { it.isNotBlank() }

    fun setApiKey(id: ProviderId, value: String) {
        prefs.edit().putString(keyFor(id), value.trim()).apply()
    }

    fun clearApiKey(id: ProviderId) {
        prefs.edit().remove(keyFor(id)).apply()
    }

    fun hasApiKey(id: ProviderId): Boolean = !getApiKey(id).isNullOrBlank()

    fun getCustomBaseUrl(): String? =
        prefs.getString(KEY_CUSTOM_BASE_URL, null)?.takeIf { it.isNotBlank() }

    fun setCustomBaseUrl(url: String) {
        prefs.edit().putString(KEY_CUSTOM_BASE_URL, url.trim().trimEnd('/')).apply()
    }

    fun getCustomModel(): String =
        prefs.getString(KEY_CUSTOM_MODEL, "default") ?: "default"

    fun setCustomModel(model: String) {
        prefs.edit().putString(KEY_CUSTOM_MODEL, model.trim()).apply()
    }

    private fun keyFor(id: ProviderId): String = "api_key_" + id.name.lowercase()

    private companion object {
        const val KEY_CUSTOM_BASE_URL = "custom_base_url"
        const val KEY_CUSTOM_MODEL = "custom_model"
    }
}
