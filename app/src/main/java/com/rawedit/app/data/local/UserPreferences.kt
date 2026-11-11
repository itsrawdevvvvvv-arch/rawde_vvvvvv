package com.rawedit.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "raw_edit_prefs")

data class UserProfile(
    val userName: String = "",
    val profilePhotoPath: String? = null,
    val onboardingDone: Boolean = false,
    val joinDateMillis: Long = System.currentTimeMillis(),
    val geminiApiKey: String = "",
    val paletteCounter: Int = 1,
    val paletteJson: String = ""
)

class UserPreferences(private val context: Context) {

    private object Keys {
        val USER_NAME = stringPreferencesKey("user_name")
        val PHOTO_PATH = stringPreferencesKey("profile_photo_path")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val JOIN_DATE = longPreferencesKey("join_date_millis")
        val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val PALETTE_COUNTER = intPreferencesKey("palette_counter")
        val PALETTE_JSON = stringPreferencesKey("palette_json")
    }

    val userProfile: Flow<UserProfile> = context.dataStore.data.map { prefs ->
        UserProfile(
            userName = prefs[Keys.USER_NAME] ?: "",
            profilePhotoPath = prefs[Keys.PHOTO_PATH]?.takeIf { it.isNotBlank() },
            onboardingDone = prefs[Keys.ONBOARDING_DONE] ?: false,
            joinDateMillis = prefs[Keys.JOIN_DATE] ?: 0L,
            geminiApiKey = prefs[Keys.GEMINI_API_KEY] ?: "",
            paletteCounter = (prefs[Keys.PALETTE_COUNTER] ?: 1).coerceAtLeast(1),
            paletteJson = prefs[Keys.PALETTE_JSON] ?: ""
        )
    }

    suspend fun completeOnboardingAndProfile(name: String, photoPath: String?) {
        context.dataStore.edit { prefs ->
            prefs[Keys.USER_NAME] = name.trim()
            if (photoPath != null) {
                prefs[Keys.PHOTO_PATH] = photoPath
            } else {
                prefs.remove(Keys.PHOTO_PATH)
            }
            prefs[Keys.ONBOARDING_DONE] = true
            if ((prefs[Keys.JOIN_DATE] ?: 0L) == 0L) {
                prefs[Keys.JOIN_DATE] = System.currentTimeMillis()
            }
        }
    }

    suspend fun updateUserName(name: String) {
        val cleaned = name.trim()
        if (cleaned.isEmpty()) return
        context.dataStore.edit { prefs ->
            prefs[Keys.USER_NAME] = cleaned
            if ((prefs[Keys.JOIN_DATE] ?: 0L) == 0L) {
                prefs[Keys.JOIN_DATE] = System.currentTimeMillis()
            }
        }
    }

    suspend fun updateProfilePhoto(photoPath: String?) {
        context.dataStore.edit { prefs ->
            if (photoPath != null) {
                prefs[Keys.PHOTO_PATH] = photoPath
            } else {
                prefs.remove(Keys.PHOTO_PATH)
            }
        }
    }

    suspend fun saveGeminiApiKey(apiKey: String) {
        val cleaned = apiKey.trim()
        context.dataStore.edit { prefs ->
            if (cleaned.isEmpty()) {
                prefs.remove(Keys.GEMINI_API_KEY)
            } else {
                prefs[Keys.GEMINI_API_KEY] = cleaned
            }
        }
    }

    suspend fun savePaletteState(counter: Int, json: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PALETTE_COUNTER] = counter.coerceAtLeast(1)
            prefs[Keys.PALETTE_JSON] = json
        }
    }
}
