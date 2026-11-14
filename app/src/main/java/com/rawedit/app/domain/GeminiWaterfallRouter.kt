package com.rawedit.app.domain

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.random.Random

enum class GeminiRouteMode {
    JSON_WORKHORSE, // 3.5-flash-lite (500 RPD) -> 3.1-flash-lite (500 RPD) -> 3.6-flash (20 RPD) -> 3.8-flash (20 RPD)
    CREATIVE_FLASH  // 3.8-flash (20 RPD) -> 3.6-flash (20 RPD) -> 3.5-flash-lite (500 RPD) -> 3.1-flash-lite (500 RPD)
}

enum class AiProvider(val id: String, val label: String) {
    GEMINI_FREE("GEMINI_FREE", "Gemini Waterfall (Free)"),
    GROQ_CLOUD("GROQ_CLOUD", "Groq Cloud"),
    AUTO_HYBRID("AUTO_HYBRID", "Auto Hybrid (Gemini -> Groq)")
}

enum class GeminiTaskRoute(val id: String, val label: String, val detail: String) {
    BALANCED_AUTO("BALANCED_AUTO", "Balanced Auto", "3.5-Flash-Lite for JSON & EDL, 3.8-Flash for scripts"),
    MAX_QUOTA_SAVER("MAX_QUOTA_SAVER", "Max Quota Saver", "3.5-Flash-Lite 500/day workhorse first across all tasks"),
    MAX_QUALITY("MAX_QUALITY", "Max Quality", "3.8-Flash first with automatic 503 fallback to 3.6 & 3.5-Lite")
}

data class GeminiGenerationResult(
    val text: String,
    val modelUsed: String,
    val fallbackTriggered: Boolean,
    val attemptCount: Int
)

data class ModelHealthStatus(
    val modelId: String,
    val isAvailable: Boolean,
    val statusLabel: String,
    val usedToday: Int,
    val dailyLimit: Int
)

data class GeminiImageInput(
    val base64Data: String,
    val mimeType: String = "image/jpeg"
)

class GeminiWaterfallRouter(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("raw_edit_gemini_quota", Context.MODE_PRIVATE)
    private val rateMutex = Mutex()
    private val lastRequestTimeMs = mutableMapOf<String, Long>()

    companion object {
        val WORKHORSE_CHAIN = listOf(
            "gemini-3.5-flash-lite",
            "gemini-3.1-flash-lite",
            "gemini-3.6-flash",
            "gemini-3.8-flash"
        )

        val CREATIVE_CHAIN = listOf(
            "gemini-3.8-flash",
            "gemini-3.6-flash",
            "gemini-3.5-flash-lite",
            "gemini-3.1-flash-lite"
        )

        fun getDailyLimitForModel(modelId: String): Int = when {
            modelId.contains("flash-lite") -> 500
            modelId.contains("flash") -> 20
            else -> 20
        }

        // Minimum spacing to avoid burst 429 RPM errors on single-model loops
        fun getMinIntervalMsForModel(modelId: String): Long = when {
            modelId.contains("flash-lite") -> 4_000L // 15 RPM = 4s spacing
            else -> 12_000L                          // 5 RPM = 12s spacing
        }

        fun maskApiKey(key: String): String {
            val clean = key.trim()
            if (clean.length < 10) return "AIza...****"
            val prefix = clean.take(4)
            val suffix = clean.takeLast(4)
            return "$prefix...$suffix"
        }
    }

    /**
     * Computes epoch millis of the next Midnight Pacific Time (America/Los_Angeles) reset.
     * Corresponds to 1:30 PM Indian Standard Time (IST).
     */
    fun getNextPacificMidnightMillis(): Long {
        val pacificZone = ZoneId.of("America/Los_Angeles")
        val nowPacific = ZonedDateTime.now(pacificZone)
        val nextMidnight = nowPacific.toLocalDate().plusDays(1).atStartOfDay(pacificZone)
        return nextMidnight.toInstant().toEpochMilli()
    }

    fun checkAndResetDailyCountersIfNeeded() {
        val now = System.currentTimeMillis()
        val resetAt = prefs.getLong("next_pacific_reset_ms", 0L)
        if (now >= resetAt) {
            val editor = prefs.edit()
            WORKHORSE_CHAIN.forEach { model ->
                editor.putInt("rpd_used_$model", 0)
                editor.putLong("cooldown_until_$model", 0L)
            }
            editor.putLong("next_pacific_reset_ms", getNextPacificMidnightMillis())
            editor.apply()
        }
    }

    fun isModelInCooldown(model: String): Boolean {
        checkAndResetDailyCountersIfNeeded()
        val until = prefs.getLong("cooldown_until_$model", 0L)
        return System.currentTimeMillis() < until
    }

    fun markModelDailyExhausted(model: String) {
        prefs.edit()
            .putLong("cooldown_until_$model", getNextPacificMidnightMillis())
            .apply()
    }

    fun incrementModelUsage(model: String) {
        checkAndResetDailyCountersIfNeeded()
        val current = prefs.getInt("rpd_used_$model", 0)
        prefs.edit().putInt("rpd_used_$model", current + 1).apply()
    }

    fun getUsedToday(model: String): Int {
        checkAndResetDailyCountersIfNeeded()
        return prefs.getInt("rpd_used_$model", 0)
    }

    fun resetAllCounters() {
        prefs.edit().clear().apply()
    }

    /**
     * Executes a prompt with automatic 503 jittered backoff, 429 PerDay detection,
     * and seamless cascade across the 4-model free-tier waterfall.
     */
    suspend fun generate(
        apiKey: String,
        prompt: String,
        systemInstruction: String? = null,
        routeMode: GeminiRouteMode = GeminiRouteMode.JSON_WORKHORSE,
        expectJson: Boolean = true,
        images: List<GeminiImageInput> = emptyList()
    ): Result<GeminiGenerationResult> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (!cleanKey.startsWith("AIza") || cleanKey.length < 30) {
            return@withContext Result.failure(
                IllegalArgumentException("Invalid Google AI Studio API Key format (must start with AIza).")
            )
        }

        val chain = if (routeMode == GeminiRouteMode.JSON_WORKHORSE) WORKHORSE_CHAIN else CREATIVE_CHAIN
        var totalAttempts = 0
        var lastErrorSummary = "All models in waterfall failed."

        for ((modelIndex, model) in chain.withIndex()) {
            if (isModelInCooldown(model)) {
                continue
            }

            // Cap retries at 2 per model so 503 waves don't drain daily RPD
            for (attempt in 0 until 2) {
                totalAttempts++

                // Respect burst spacing if retrying the same model
                val waitMs = rateMutex.withLock {
                    val lastTs = lastRequestTimeMs[model] ?: 0L
                    val elapsed = System.currentTimeMillis() - lastTs
                    val minGap = if (attempt > 0) 1_500L else 600L
                    val needed = (minGap - elapsed).coerceAtLeast(0L)
                    lastRequestTimeMs[model] = System.currentTimeMillis() + needed
                    needed
                }
                if (waitMs > 0L) delay(waitMs)

                val endpoint =
                    "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
                val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 20_000
                    readTimeout = 90_000 // 90s timeout prevents drops on busy waves
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    setRequestProperty("x-goog-api-key", cleanKey)
                }

                val userParts = JSONArray()
                userParts.put(JSONObject().put("text", prompt))
                for (img in images) {
                    userParts.put(
                        JSONObject().put(
                            "inlineData",
                            JSONObject().apply {
                                put("mimeType", img.mimeType)
                                put("data", img.base64Data)
                            }
                        )
                    )
                }

                val payload = JSONObject().apply {
                    if (!systemInstruction.isNullOrBlank()) {
                        put("systemInstruction", JSONObject().apply {
                            put("parts", JSONArray().put(JSONObject().put("text", systemInstruction)))
                        })
                    }
                    put("contents", JSONArray().put(
                        JSONObject().apply {
                            put("role", "user")
                            put("parts", userParts)
                        }
                    ))
                    put("generationConfig", JSONObject().apply {
                        if (expectJson) {
                            put("responseMimeType", "application/json")
                        }
                        // Minimal/zero thinking prevents 503 scheduling bottlenecks & timeouts
                        put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
                    })
                }

                try {
                    conn.outputStream.use { os ->
                        os.write(payload.toString().toByteArray(Charsets.UTF_8))
                    }
                    val status = conn.responseCode

                    if (status == 200) {
                        val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                        val root = JSONObject(responseText)
                        val candidates = root.optJSONArray("candidates")
                        val firstCandidate = candidates?.optJSONObject(0)
                        val content = firstCandidate?.optJSONObject("content")
                        val parts = content?.optJSONArray("parts")

                        val output = buildString {
                            if (parts != null) {
                                for (i in 0 until parts.length()) {
                                    val part = parts.optJSONObject(i)
                                    val text = part?.optString("text").orEmpty()
                                    if (text.isNotEmpty()) append(text)
                                }
                            }
                        }

                        if (output.isNotEmpty()) {
                            incrementModelUsage(model)
                            return@withContext Result.success(
                                GeminiGenerationResult(
                                    text = output,
                                    modelUsed = model,
                                    fallbackTriggered = modelIndex > 0 || attempt > 0,
                                    attemptCount = totalAttempts
                                )
                            )
                        }
                    }

                    val errBody = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    lastErrorSummary = parseCleanErrorMessage(status, model, errBody)

                    // 1. Check if key itself is invalid (400 API_KEY_INVALID or 403)
                    if (status == 400 && errBody.contains("API_KEY_INVALID", ignoreCase = true)) {
                        return@withContext Result.failure(
                            IllegalStateException("API Key is invalid. Check your key in Google AI Studio.")
                        )
                    }

                    // 2. Check if Daily Quota (RPD) is exhausted or model is restricted (limit: 0 / 404)
                    if (status == 404 ||
                        errBody.contains("PerDay", ignoreCase = true) ||
                        errBody.contains("limit: 0", ignoreCase = true)
                    ) {
                        markModelDailyExhausted(model)
                        break // Immediately cascade to next model in waterfall
                    }

                    // 3. Transient 503 (High Demand / Overloaded) or Per-Minute 429:
                    // Backoff with random jitter ONCE, then cascade to next model
                    if ((status == 503 || status == 429 || status == 500) && attempt == 0) {
                        val jitterMs = Random.nextLong(350L, 1150L)
                        delay(1_250L + jitterMs)
                        continue
                    }

                    // Otherwise move to next model in waterfall
                    break
                } catch (ce: CancellationException) {
                    throw ce
                } catch (e: Exception) {
                    lastErrorSummary = "${e.javaClass.simpleName} on $model: ${e.message}"
                    if (attempt == 0) {
                        delay(1_000L + Random.nextLong(200L, 600L))
                    }
                } finally {
                    conn.disconnect()
                }
            }
        }

        Result.failure(IllegalStateException(lastErrorSummary))
    }

    /**
     * Lightweight 5-token health & key verification ping for the Profile Tab Modal.
     */
    suspend fun testApiKeyHealth(apiKey: String): List<ModelHealthStatus> = withContext(Dispatchers.IO) {
        val testModels = listOf("gemini-3.5-flash-lite", "gemini-3.8-flash")
        val results = mutableListOf<ModelHealthStatus>()

        for (model in testModels) {
            val limit = getDailyLimitForModel(model)
            val used = getUsedToday(model)
            val endpoint =
                "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 12_000
                readTimeout = 20_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("x-goog-api-key", apiKey.trim())
            }

            val pingPayload = JSONObject().apply {
                put("contents", JSONArray().put(
                    JSONObject().put("parts", JSONArray().put(JSONObject().put("text", "Reply OK")))
                ))
                put("generationConfig", JSONObject().apply {
                    put("maxOutputTokens", 5)
                    put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
                })
            }

            try {
                conn.outputStream.use { it.write(pingPayload.toString().toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val err = if (code != 200) {
                    conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                } else ""

                val statusLabel = when {
                    code == 200 -> "READY"
                    code == 503 -> "503 HIGH DEMAND (AUTO-FALLBACK READY)"
                    code == 429 && (err.contains("PerDay") || err.contains("limit: 0")) ->
                        "DAILY QUOTA FULL (RESETS 1:30 PM IST)"
                    code == 429 -> "RPM BUSY (WAIT 10S)"
                    code == 400 && err.contains("API_KEY_INVALID") -> "INVALID API KEY"
                    else -> "HTTP $code"
                }

                results.add(
                    ModelHealthStatus(
                        modelId = model,
                        isAvailable = code == 200 || code == 503,
                        statusLabel = statusLabel,
                        usedToday = used,
                        dailyLimit = limit
                    )
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                results.add(
                    ModelHealthStatus(
                        modelId = model,
                        isAvailable = false,
                        statusLabel = "NETWORK / TIMEOUT",
                        usedToday = used,
                        dailyLimit = limit
                    )
                )
            } finally {
                conn.disconnect()
            }
        }
        results
    }

    private fun parseCleanErrorMessage(status: Int, model: String, rawBody: String): String {
        return try {
            val msg = JSONObject(rawBody).getJSONObject("error").getString("message")
            "[$model HTTP $status] $msg"
        } catch (_: Exception) {
            "[$model HTTP $status] Service busy or unavailable."
        }
    }
}
