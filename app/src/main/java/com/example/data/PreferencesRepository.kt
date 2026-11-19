package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.i18n.AppLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.streamPulseDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "streampulse_prefs"
)

data class LocalAppPreferences(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val language: AppLanguage = AppLanguage.ENGLISH,
    val preferredResolution: StreamResolution = StreamResolution.HD_720P,
    val backgroundPlaybackEnabled: Boolean = true,
    val notificationsEnabled: Boolean = true
)

class PreferencesRepository(context: Context) {
    private val dataStore = context.applicationContext.streamPulseDataStore

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LANGUAGE = stringPreferencesKey("app_language")
        val RESOLUTION = stringPreferencesKey("preferred_resolution")
        val BG_PLAYBACK = booleanPreferencesKey("background_playback")
        val NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
    }

    val preferencesFlow: Flow<LocalAppPreferences> = dataStore.data.map { prefs ->
        LocalAppPreferences(
            themeMode = ThemeMode.fromCode(prefs[Keys.THEME_MODE]),
            language = AppLanguage.fromCode(prefs[Keys.LANGUAGE]),
            preferredResolution = StreamResolution.fromCode(prefs[Keys.RESOLUTION]),
            backgroundPlaybackEnabled = prefs[Keys.BG_PLAYBACK] ?: true,
            notificationsEnabled = prefs[Keys.NOTIFICATIONS] ?: true
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.code }
    }

    suspend fun setLanguage(language: AppLanguage) {
        dataStore.edit { it[Keys.LANGUAGE] = language.code }
    }

    suspend fun setResolution(resolution: StreamResolution) {
        dataStore.edit { it[Keys.RESOLUTION] = resolution.code }
    }

    suspend fun setBackgroundPlayback(enabled: Boolean) {
        dataStore.edit { it[Keys.BG_PLAYBACK] = enabled }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.NOTIFICATIONS] = enabled }
    }
}
