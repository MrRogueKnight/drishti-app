package io.github.mrroguekknight.drishti.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "drishti_prefs")

object PreferenceKeys {
    val DARK_THEME = booleanPreferencesKey("dark_theme")
    val VISUAL_ALERTS = booleanPreferencesKey("visual_alerts")
    val AUDIO_ALERTS = booleanPreferencesKey("audio_alerts")
    val VIBRATION_ALERTS = booleanPreferencesKey("vibration_alerts")
    val IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
    val LOGIN_EXPIRATION_TIME = androidx.datastore.preferences.core.longPreferencesKey("login_expiration_time")
    
    // Network/System Configuration
    val SENSITIVITY_LEVEL = stringPreferencesKey("sensitivity_level")
    val SAFE_DISTANCE = floatPreferencesKey("safe_distance")
}

class PreferenceStore(private val context: Context) {
    val isDarkTheme: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[PreferenceKeys.DARK_THEME] ?: false }
    val visualAlerts: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[PreferenceKeys.VISUAL_ALERTS] ?: true }
    val audioAlerts: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[PreferenceKeys.AUDIO_ALERTS] ?: true }
    val vibrationAlerts: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[PreferenceKeys.VIBRATION_ALERTS] ?: true }

    val isLoggedIn: Flow<Boolean> = context.dataStore.data.map { prefs ->
        val loggedIn = prefs[PreferenceKeys.IS_LOGGED_IN] ?: false
        val expiration = prefs[PreferenceKeys.LOGIN_EXPIRATION_TIME] ?: 0L
        val currentTime = System.currentTimeMillis()
        loggedIn && (currentTime < expiration)
    }

    val sensitivityLevel: Flow<String> = context.dataStore.data.map { prefs -> prefs[PreferenceKeys.SENSITIVITY_LEVEL] ?: "Medium" }
    val safeDistance: Flow<Float> = context.dataStore.data.map { prefs -> prefs[PreferenceKeys.SAFE_DISTANCE] ?: 50f }

    suspend fun setDarkTheme(value: Boolean) { context.dataStore.edit { it[PreferenceKeys.DARK_THEME] = value } }
    suspend fun setVisualAlerts(value: Boolean) { context.dataStore.edit { it[PreferenceKeys.VISUAL_ALERTS] = value } }
    suspend fun setAudioAlerts(value: Boolean) { context.dataStore.edit { it[PreferenceKeys.AUDIO_ALERTS] = value } }
    suspend fun setVibrationAlerts(value: Boolean) { context.dataStore.edit { it[PreferenceKeys.VIBRATION_ALERTS] = value } }

    suspend fun setLoginState(isLoggedIn: Boolean, rememberMe: Boolean = false) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.IS_LOGGED_IN] = isLoggedIn
            if (isLoggedIn) {
                val duration = if (rememberMe) 30L * 24 * 60 * 60 * 1000 else 24 * 60 * 60 * 1000L // 30 days or 1 day default
                prefs[PreferenceKeys.LOGIN_EXPIRATION_TIME] = System.currentTimeMillis() + duration
            } else {
                prefs[PreferenceKeys.LOGIN_EXPIRATION_TIME] = 0L
            }
        }
    }

    suspend fun setSensitivityLevel(value: String) { context.dataStore.edit { it[PreferenceKeys.SENSITIVITY_LEVEL] = value } }
    suspend fun setSafeDistance(value: Float) { context.dataStore.edit { it[PreferenceKeys.SAFE_DISTANCE] = value } }
}
