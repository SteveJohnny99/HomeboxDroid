package it.stevej.homeboxdroid.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Archivio sicuro per URL server, credenziale (token Bearer o API Key hb_),
 * modalita' auth e flag TLS self-signed. Usa Android Keystore +
 * EncryptedSharedPreferences: i segreti non finiscono mai in chiaro.
 */
@Singleton
class SecureSettings @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences = createPrefs(context)

    private val _configured = MutableStateFlow(isConfigured())
    /** true quando c'e' sia URL che credenziale: l'app puo' entrare. */
    val configured: StateFlow<Boolean> = _configured.asStateFlow()

    var serverUrl: String?
        get() = prefs.getString(KEY_URL, null)
        set(value) = prefs.edit().putStringOrRemove(KEY_URL, value).apply().also { refresh() }

    /** Credenziale normalizzata (senza prefisso "Bearer "). */
    var credential: String?
        get() = prefs.getString(KEY_CRED, null)
        set(value) = prefs.edit().putStringOrRemove(KEY_CRED, value).apply().also { refresh() }

    /** "apikey" oppure "password". */
    var authMode: String
        get() = prefs.getString(KEY_MODE, MODE_APIKEY) ?: MODE_APIKEY
        set(value) = prefs.edit().putString(KEY_MODE, value).apply()

    var attachmentToken: String?
        get() = prefs.getString(KEY_ATTACH, null)
        set(value) = prefs.edit().putStringOrRemove(KEY_ATTACH, value).apply()

    var allowSelfSigned: Boolean
        get() = prefs.getBoolean(KEY_SELF_SIGNED, false)
        set(value) = prefs.edit().putBoolean(KEY_SELF_SIGNED, value).apply()

    var userName: String?
        get() = prefs.getString(KEY_USER_NAME, null)
        set(value) = prefs.edit().putStringOrRemove(KEY_USER_NAME, value).apply()

    var userEmail: String?
        get() = prefs.getString(KEY_USER_EMAIL, null)
        set(value) = prefs.edit().putStringOrRemove(KEY_USER_EMAIL, value).apply()

    fun isConfigured(): Boolean = !serverUrl.isNullOrBlank() && !credential.isNullOrBlank()

    fun clearSession() {
        prefs.edit()
            .remove(KEY_CRED)
            .remove(KEY_ATTACH)
            .remove(KEY_USER_NAME)
            .remove(KEY_USER_EMAIL)
            .apply()
        refresh()
    }

    private fun refresh() { _configured.value = isConfigured() }

    private fun SharedPreferences.Editor.putStringOrRemove(key: String, value: String?) =
        if (value == null) remove(key) else putString(key, value)

    companion object {
        private const val FILE = "homebox_secure_prefs"
        private const val KEY_URL = "server_url"
        private const val KEY_CRED = "credential"
        private const val KEY_MODE = "auth_mode"
        private const val KEY_ATTACH = "attachment_token"
        private const val KEY_SELF_SIGNED = "allow_self_signed"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_EMAIL = "user_email"
        const val MODE_APIKEY = "apikey"
        const val MODE_PASSWORD = "password"

        private fun createPrefs(context: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return try {
                build(context, masterKey)
            } catch (e: Exception) {
                // Keystore/prefs corrotti (es. chiave ruotata): azzera e ricrea.
                context.deleteSharedPreferences(FILE)
                build(context, masterKey)
            }
        }

        private fun build(context: Context, masterKey: MasterKey): SharedPreferences =
            EncryptedSharedPreferences.create(
                context,
                FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
    }
}
