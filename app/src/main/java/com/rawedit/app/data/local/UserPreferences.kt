package com.rawedit.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rawedit.app.domain.PuterCodec
import com.rawedit.app.domain.PuterUsageReport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "raw_edit_prefs")

data class UserProfile(
    val userName: String = "",
    val profilePhotoPath: String? = null,
    val onboardingDone: Boolean = false,
    val joinDateMillis: Long = System.currentTimeMillis(),
    val puterAuthToken: String = "",
    val puterUsername: String = "",
    val puterModelPref: String = PuterCodec.PREF_AUTO,
    val puterRemainingRaw: Double = 0.0,
    val puterAllowanceRaw: Double = 0.0,
    val puterPurchasedRemainingRaw: Double = 0.0,
    val puterIsCreditsUnit: Boolean = false,
    val puterCreditsExhausted: Boolean = false,
    val puterUsageUpdatedAtMs: Long = 0L,
    val paletteCounter: Int = 1,
    val paletteJson: String = ""
) {
    val isPuterSignedIn: Boolean
        get() = puterAuthToken.isNotBlank()

    fun toUsageReportOrNull(): PuterUsageReport? {
        if (puterUsageUpdatedAtMs <= 0L && !puterCreditsExhausted) return null
        val monthlyRem = (puterRemainingRaw - puterPurchasedRemainingRaw).coerceAtLeast(0.0)
        val monthlyUsed = (puterAllowanceRaw - monthlyRem).coerceAtLeast(0.0)
        return PuterUsageReport(
            monthlyUsedRaw = monthlyUsed,
            monthlyAllowanceRaw = puterAllowanceRaw,
            monthlyRemainingRaw = monthlyRem,
            purchasedCreditsRaw = puterPurchasedRemainingRaw,
            consumedPurchasedRaw = 0.0,
            purchasedRemainingRaw = puterPurchasedRemainingRaw,
            isCreditsUnit = puterIsCreditsUnit
        )
    }

    fun creditsSummaryText(): String {
        if (!isPuterSignedIn) return "Sign in with Google / Puter"
        if (puterCreditsExhausted) return "0 left (Out of credits)"
        if (puterUsageUpdatedAtMs <= 0L) return "Tap refresh for credits"
        return PuterCodec.formatUsageBannerLabel(
            remainingRaw = puterRemainingRaw,
            allowanceRaw = puterAllowanceRaw,
            isCreditsUnit = puterIsCreditsUnit,
            exhausted = puterCreditsExhausted
        )
    }
}

class UserPreferences(private val context: Context) {

    private object Keys {
        val USER_NAME = stringPreferencesKey("user_name")
        val PHOTO_PATH = stringPreferencesKey("profile_photo_path")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val JOIN_DATE = longPreferencesKey("join_date_millis")
        val PUTER_AUTH_TOKEN = stringPreferencesKey("puter_auth_token")
        val PUTER_USERNAME = stringPreferencesKey("puter_username")
        val PUTER_MODEL_PREF = stringPreferencesKey("puter_model_pref")
        val PUTER_REMAINING_RAW = doublePreferencesKey("puter_remaining_raw")
        val PUTER_ALLOWANCE_RAW = doublePreferencesKey("puter_allowance_raw")
        val PUTER_PURCHASED_REM_RAW = doublePreferencesKey("puter_purchased_rem_raw")
        val PUTER_IS_CREDITS_UNIT = booleanPreferencesKey("puter_is_credits_unit")
        val PUTER_CREDITS_EXHAUSTED = booleanPreferencesKey("puter_credits_exhausted")
        val PUTER_USAGE_UPDATED_AT = longPreferencesKey("puter_usage_updated_at")
        val PALETTE_COUNTER = intPreferencesKey("palette_counter")
        val PALETTE_JSON = stringPreferencesKey("palette_json")
    }

    val userProfile: Flow<UserProfile> = context.dataStore.data.map { prefs ->
        UserProfile(
            userName = prefs[Keys.USER_NAME] ?: "",
            profilePhotoPath = prefs[Keys.PHOTO_PATH]?.takeIf { it.isNotBlank() },
            onboardingDone = prefs[Keys.ONBOARDING_DONE] ?: false,
            joinDateMillis = prefs[Keys.JOIN_DATE] ?: 0L,
            puterAuthToken = prefs[Keys.PUTER_AUTH_TOKEN] ?: "",
            puterUsername = prefs[Keys.PUTER_USERNAME] ?: "",
            puterModelPref = PuterCodec.normalizeModelPref(prefs[Keys.PUTER_MODEL_PREF] ?: ""),
            puterRemainingRaw = prefs[Keys.PUTER_REMAINING_RAW] ?: 0.0,
            puterAllowanceRaw = prefs[Keys.PUTER_ALLOWANCE_RAW] ?: 0.0,
            puterPurchasedRemainingRaw = prefs[Keys.PUTER_PURCHASED_REM_RAW] ?: 0.0,
            puterIsCreditsUnit = prefs[Keys.PUTER_IS_CREDITS_UNIT] ?: false,
            puterCreditsExhausted = prefs[Keys.PUTER_CREDITS_EXHAUSTED] ?: false,
            puterUsageUpdatedAtMs = prefs[Keys.PUTER_USAGE_UPDATED_AT] ?: 0L,
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

    suspend fun savePuterSession(
        authToken: String,
        username: String,
        modelPref: String,
        usage: PuterUsageReport?
    ) {
        val cleanedToken = PuterCodec.normalizeAuthToken(authToken)
        val cleanedUser = username.trim()
        val normalizedPref = PuterCodec.normalizeModelPref(modelPref)
        context.dataStore.edit { prefs ->
            if (cleanedToken.isEmpty()) {
                prefs.remove(Keys.PUTER_AUTH_TOKEN)
                prefs.remove(Keys.PUTER_USERNAME)
                prefs.remove(Keys.PUTER_REMAINING_RAW)
                prefs.remove(Keys.PUTER_ALLOWANCE_RAW)
                prefs.remove(Keys.PUTER_PURCHASED_REM_RAW)
                prefs.remove(Keys.PUTER_IS_CREDITS_UNIT)
                prefs.remove(Keys.PUTER_CREDITS_EXHAUSTED)
                prefs.remove(Keys.PUTER_USAGE_UPDATED_AT)
            } else {
                prefs[Keys.PUTER_AUTH_TOKEN] = cleanedToken
                prefs[Keys.PUTER_USERNAME] = cleanedUser
                if (usage != null) {
                    prefs[Keys.PUTER_REMAINING_RAW] = usage.totalRemainingRaw
                    prefs[Keys.PUTER_ALLOWANCE_RAW] = usage.monthlyAllowanceRaw
                    prefs[Keys.PUTER_PURCHASED_REM_RAW] = usage.purchasedRemainingRaw
                    prefs[Keys.PUTER_IS_CREDITS_UNIT] = usage.isCreditsUnit
                    prefs[Keys.PUTER_CREDITS_EXHAUSTED] = usage.isExhausted
                    prefs[Keys.PUTER_USAGE_UPDATED_AT] = System.currentTimeMillis()
                } else {
                    prefs[Keys.PUTER_CREDITS_EXHAUSTED] = false
                }
            }
            prefs[Keys.PUTER_MODEL_PREF] = normalizedPref
        }
    }

    suspend fun savePuterModelPref(modelPref: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PUTER_MODEL_PREF] = PuterCodec.normalizeModelPref(modelPref)
        }
    }

    suspend fun savePuterUsage(username: String?, usage: PuterUsageReport) {
        context.dataStore.edit { prefs ->
            if (!username.isNullOrBlank()) {
                prefs[Keys.PUTER_USERNAME] = username.trim()
            }
            prefs[Keys.PUTER_REMAINING_RAW] = usage.totalRemainingRaw
            prefs[Keys.PUTER_ALLOWANCE_RAW] = usage.monthlyAllowanceRaw
            prefs[Keys.PUTER_PURCHASED_REM_RAW] = usage.purchasedRemainingRaw
            prefs[Keys.PUTER_IS_CREDITS_UNIT] = usage.isCreditsUnit
            prefs[Keys.PUTER_CREDITS_EXHAUSTED] = usage.isExhausted
            prefs[Keys.PUTER_USAGE_UPDATED_AT] = System.currentTimeMillis()
        }
    }

    suspend fun markPuterCreditsExhausted() {
        context.dataStore.edit { prefs ->
            prefs[Keys.PUTER_REMAINING_RAW] = 0.0
            prefs[Keys.PUTER_CREDITS_EXHAUSTED] = true
            prefs[Keys.PUTER_USAGE_UPDATED_AT] = System.currentTimeMillis()
        }
    }

    suspend fun clearPuterSession() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.PUTER_AUTH_TOKEN)
            prefs.remove(Keys.PUTER_USERNAME)
            prefs.remove(Keys.PUTER_REMAINING_RAW)
            prefs.remove(Keys.PUTER_ALLOWANCE_RAW)
            prefs.remove(Keys.PUTER_PURCHASED_REM_RAW)
            prefs.remove(Keys.PUTER_IS_CREDITS_UNIT)
            prefs.remove(Keys.PUTER_CREDITS_EXHAUSTED)
            prefs.remove(Keys.PUTER_USAGE_UPDATED_AT)
        }
    }

    suspend fun savePaletteState(counter: Int, json: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PALETTE_COUNTER] = counter.coerceAtLeast(1)
            prefs[Keys.PALETTE_JSON] = json
        }
    }
}
