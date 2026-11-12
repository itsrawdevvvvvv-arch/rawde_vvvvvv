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
import com.rawedit.app.domain.GroqCodec
import com.rawedit.app.domain.GroqQuotaReport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "raw_edit_prefs")

data class UserProfile(
    val userName: String = "",
    val profilePhotoPath: String? = null,
    val onboardingDone: Boolean = false,
    val joinDateMillis: Long = System.currentTimeMillis(),
    val groqApiKey: String = "",
    val groqModelPref: String = GroqCodec.PREF_AUTO,
    val groqRemainingRequests: Int = -1,
    val groqLimitRequests: Int = -1,
    val groqResetRequests: String = "",
    val groqRemainingTokens: Int = -1,
    val groqLimitTokens: Int = -1,
    val groqResetTokens: String = "",
    val groqQuotaExhausted: Boolean = false,
    val groqQuotaUpdatedAtMs: Long = 0L,
    val paletteCounter: Int = 1,
    val paletteJson: String = ""
) {
    val hasGroqKey: Boolean
        get() = groqApiKey.isNotBlank()

    fun toGroqQuotaReportOrNull(): GroqQuotaReport? {
        val hasValues = groqRemainingRequests >= 0 ||
            groqLimitRequests > 0 ||
            groqRemainingTokens >= 0 ||
            groqLimitTokens > 0 ||
            groqQuotaExhausted
        if (groqQuotaUpdatedAtMs <= 0L && !hasValues) return null
        return GroqQuotaReport(
            remainingRequests = groqRemainingRequests,
            limitRequests = groqLimitRequests,
            resetRequests = groqResetRequests,
            remainingTokens = groqRemainingTokens,
            limitTokens = groqLimitTokens,
            resetTokens = groqResetTokens,
            creditsDepleted402 = groqQuotaExhausted && groqRemainingRequests != 0 && groqRemainingTokens != 0,
            updatedAtMs = groqQuotaUpdatedAtMs
        )
    }

    fun quotaSummaryText(): String {
        if (!hasGroqKey) return "Add Groq key"
        if (groqQuotaExhausted) return "Quota 0 \u2022 Switch"
        return GroqCodec.formatQuotaBannerLabel(
            remainingRequests = groqRemainingRequests,
            limitRequests = groqLimitRequests,
            remainingTokens = groqRemainingTokens,
            limitTokens = groqLimitTokens,
            exhausted = groqQuotaExhausted
        )
    }
}

class UserPreferences(private val context: Context) {

    private object Keys {
        val USER_NAME = stringPreferencesKey("user_name")
        val PHOTO_PATH = stringPreferencesKey("profile_photo_path")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val JOIN_DATE = longPreferencesKey("join_date_millis")
        val GROQ_API_KEY = stringPreferencesKey("groq_api_key")
        val GROQ_MODEL_PREF = stringPreferencesKey("groq_model_pref")
        val GROQ_REM_REQ = intPreferencesKey("groq_rem_req")
        val GROQ_LIM_REQ = intPreferencesKey("groq_lim_req")
        val GROQ_RST_REQ = stringPreferencesKey("groq_rst_req")
        val GROQ_REM_TOK = intPreferencesKey("groq_rem_tok")
        val GROQ_LIM_TOK = intPreferencesKey("groq_lim_tok")
        val GROQ_RST_TOK = stringPreferencesKey("groq_rst_tok")
        val GROQ_QUOTA_EXHAUSTED = booleanPreferencesKey("groq_quota_exhausted")
        val GROQ_QUOTA_UPDATED_AT = longPreferencesKey("groq_quota_updated_at")
        val PALETTE_COUNTER = intPreferencesKey("palette_counter")
        val PALETTE_JSON = stringPreferencesKey("palette_json")
    }

    val userProfile: Flow<UserProfile> = context.dataStore.data.map { prefs ->
        UserProfile(
            userName = prefs[Keys.USER_NAME] ?: "",
            profilePhotoPath = prefs[Keys.PHOTO_PATH]?.takeIf { it.isNotBlank() },
            onboardingDone = prefs[Keys.ONBOARDING_DONE] ?: false,
            joinDateMillis = prefs[Keys.JOIN_DATE] ?: 0L,
            groqApiKey = prefs[Keys.GROQ_API_KEY] ?: "",
            groqModelPref = GroqCodec.normalizeModelPref(prefs[Keys.GROQ_MODEL_PREF] ?: ""),
            groqRemainingRequests = prefs[Keys.GROQ_REM_REQ] ?: -1,
            groqLimitRequests = prefs[Keys.GROQ_LIM_REQ] ?: -1,
            groqResetRequests = prefs[Keys.GROQ_RST_REQ] ?: "",
            groqRemainingTokens = prefs[Keys.GROQ_REM_TOK] ?: -1,
            groqLimitTokens = prefs[Keys.GROQ_LIM_TOK] ?: -1,
            groqResetTokens = prefs[Keys.GROQ_RST_TOK] ?: "",
            groqQuotaExhausted = prefs[Keys.GROQ_QUOTA_EXHAUSTED] ?: false,
            groqQuotaUpdatedAtMs = prefs[Keys.GROQ_QUOTA_UPDATED_AT] ?: 0L,
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

    suspend fun saveGroqSession(
        apiKey: String,
        modelPref: String,
        quota: GroqQuotaReport?
    ) {
        val cleanedKey = GroqCodec.normalizeApiKey(apiKey)
        val normalizedPref = GroqCodec.normalizeModelPref(modelPref)
        context.dataStore.edit { prefs ->
            if (cleanedKey.isEmpty()) {
                clearGroqKeysFromPrefs(prefs)
            } else {
                prefs[Keys.GROQ_API_KEY] = cleanedKey
                if (quota != null) {
                    writeQuotaToPrefs(prefs, quota)
                } else {
                    prefs[Keys.GROQ_QUOTA_EXHAUSTED] = false
                }
            }
            prefs[Keys.GROQ_MODEL_PREF] = normalizedPref
        }
    }

    suspend fun saveGroqModelPref(modelPref: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.GROQ_MODEL_PREF] = GroqCodec.normalizeModelPref(modelPref)
        }
    }

    suspend fun saveGroqQuota(quota: GroqQuotaReport) {
        context.dataStore.edit { prefs ->
            writeQuotaToPrefs(prefs, quota)
        }
    }

    suspend fun markGroqQuotaExhausted(quota: GroqQuotaReport? = null) {
        context.dataStore.edit { prefs ->
            if (quota != null) {
                writeQuotaToPrefs(prefs, quota)
            }
            prefs[Keys.GROQ_QUOTA_EXHAUSTED] = true
            prefs[Keys.GROQ_QUOTA_UPDATED_AT] = System.currentTimeMillis()
        }
    }

    suspend fun clearGroqSession() {
        context.dataStore.edit { prefs ->
            clearGroqKeysFromPrefs(prefs)
        }
    }

    private fun writeQuotaToPrefs(
        prefs: androidx.datastore.preferences.core.MutablePreferences,
        quota: GroqQuotaReport
    ) {
        if (quota.remainingRequests >= 0) prefs[Keys.GROQ_REM_REQ] = quota.remainingRequests
        if (quota.limitRequests > 0) prefs[Keys.GROQ_LIM_REQ] = quota.limitRequests
        if (quota.resetRequests.isNotBlank()) prefs[Keys.GROQ_RST_REQ] = quota.resetRequests
        if (quota.remainingTokens >= 0) prefs[Keys.GROQ_REM_TOK] = quota.remainingTokens
        if (quota.limitTokens > 0) prefs[Keys.GROQ_LIM_TOK] = quota.limitTokens
        if (quota.resetTokens.isNotBlank()) prefs[Keys.GROQ_RST_TOK] = quota.resetTokens
        prefs[Keys.GROQ_QUOTA_EXHAUSTED] = quota.isExhausted
        prefs[Keys.GROQ_QUOTA_UPDATED_AT] = if (quota.updatedAtMs > 0L) quota.updatedAtMs else System.currentTimeMillis()
    }

    private fun clearGroqKeysFromPrefs(prefs: androidx.datastore.preferences.core.MutablePreferences) {
        prefs.remove(Keys.GROQ_API_KEY)
        prefs.remove(Keys.GROQ_REM_REQ)
        prefs.remove(Keys.GROQ_LIM_REQ)
        prefs.remove(Keys.GROQ_RST_REQ)
        prefs.remove(Keys.GROQ_REM_TOK)
        prefs.remove(Keys.GROQ_LIM_TOK)
        prefs.remove(Keys.GROQ_RST_TOK)
        prefs.remove(Keys.GROQ_QUOTA_EXHAUSTED)
        prefs.remove(Keys.GROQ_QUOTA_UPDATED_AT)
    }

    suspend fun savePaletteState(counter: Int, json: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PALETTE_COUNTER] = counter.coerceAtLeast(1)
            prefs[Keys.PALETTE_JSON] = json
        }
    }
}
