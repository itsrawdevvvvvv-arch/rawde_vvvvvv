package com.rawedit.app.domain

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.rawedit.app.data.local.LocalAssetEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
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

        val PALETTE_THEMES = listOf(
            "brutalist swiss",
            "warm desert dusk",
            "retro analog film",
            "deep botanical night",
            "nordic ceramic studio",
            "cyber editorial print",
            "sunbaked clay and slate",
            "midnight jazz lounge",
            "soft espresso bauhaus",
            "coastal slate and saffron",
            "editorial terracotta archive",
            "warm ochre darkroom"
        )

        const val PALETTE_SYSTEM =
            "You are an elite editorial art director specializing in modern kinetic-typography motion design. Respond strictly with raw JSON matching the requested schema."

        const val ASSET_SYSTEM =
            "You are a computer-vision asset analyst for vertical video editing. Respond strictly with raw valid JSON containing the exact keys requested."

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

        fun sanitizeApiKey(raw: String): String {
            return raw.trim()
                .removeSurrounding("\"")
                .removeSurrounding("'")
                .replace("\u200B", "") // zero-width space
                .replace("\uFEFF", "") // zero-width BOM
                .replace("\u00A0", "") // non-breaking space
                .filter { !it.isWhitespace() }
        }

        fun dailyLimitForModel(modelId: String): Int = getDailyLimitForModel(modelId)

        fun isValidApiKey(key: String): Boolean {
            val clean = sanitizeApiKey(key)
            if (clean.startsWith("gsk_")) return false
            if (clean.length < 20 || clean.length > 150) return false
            return clean.startsWith("AIza") || clean.startsWith("AQ") || (clean.length >= 25 && !clean.contains(" "))
        }

        fun maskApiKey(key: String): String {
            val clean = sanitizeApiKey(key)
            if (clean.length < 10) return "••••"
            val prefix = clean.take(4)
            val suffix = clean.takeLast(4)
            return "$prefix...$suffix"
        }

        fun maskKey(key: String): String = maskApiKey(key)

        fun computeNextPacificMidnightMs(nowMs: Long = System.currentTimeMillis()): Long {
            val pacificZone = ZoneId.of("America/Los_Angeles")
            val nowPacific = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(nowMs), pacificZone)
            val nextMidnight = nowPacific.toLocalDate().plusDays(1).atStartOfDay(pacificZone)
            return nextMidnight.toInstant().toEpochMilli()
        }

        fun buildGenerateContentPayload(
            prompt: String,
            systemInstruction: String? = null,
            expectJson: Boolean = true,
            images: List<GeminiImageInput> = emptyList()
        ): String {
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
            return payload.toString()
        }
    }

    /**
     * Computes epoch millis of the next Midnight Pacific Time (America/Los_Angeles) reset.
     * Corresponds to 1:30 PM Indian Standard Time (IST).
     */
    fun getNextPacificMidnightMillis(): Long = computeNextPacificMidnightMs()

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
        val cleanKey = sanitizeApiKey(apiKey)
        if (!isValidApiKey(cleanKey)) {
            return@withContext Result.failure(
                IllegalArgumentException("Invalid Google AI Studio API Key format (must start with AIza or AQ).")
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

                val encodedKey = try {
                    java.net.URLEncoder.encode(cleanKey, "UTF-8")
                } catch (_: Exception) { cleanKey }
                val endpoint =
                    "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$encodedKey"
                val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 20_000
                    readTimeout = 90_000 // 90s timeout prevents drops on busy waves
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    setRequestProperty("x-goog-api-key", cleanKey)
                }

                val payloadString = buildGenerateContentPayload(prompt, systemInstruction, expectJson, images)

                try {
                    conn.outputStream.use { os ->
                        os.write(payloadString.toByteArray(Charsets.UTF_8))
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
     * Lightweight health & key verification ping for the Profile Tab Modal.
     * Uses minimal payload to prevent 400 errors from unsupported generationConfig fields.
     */
    suspend fun testApiKeyHealth(apiKey: String): List<ModelHealthStatus> = withContext(Dispatchers.IO) {
        val cleanKey = sanitizeApiKey(apiKey)
        val testModels = listOf("gemini-2.5-flash", "gemini-3.5-flash-lite", "gemini-3.8-flash")
        val results = mutableListOf<ModelHealthStatus>()

        for (model in testModels) {
            val limit = getDailyLimitForModel(model)
            val used = getUsedToday(model)
            val encodedKey = try {
                java.net.URLEncoder.encode(cleanKey, "UTF-8")
            } catch (_: Exception) { cleanKey }

            val endpoint =
                "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$encodedKey"
            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 12_000
                readTimeout = 20_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("x-goog-api-key", cleanKey)
            }

            // Minimal payload without thinkingConfig or maxOutputTokens
            // so all Gemini models accept the request without 400 INVALID_ARGUMENT
            val pingPayload = JSONObject().apply {
                put("contents", JSONArray().put(
                    JSONObject().put("parts", JSONArray().put(JSONObject().put("text", "ping")))
                ))
            }

            try {
                conn.outputStream.use { it.write(pingPayload.toString().toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val err = if (code != 200) {
                    conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                } else ""

                val errJson = try { JSONObject(err) } catch (_: Exception) { null }
                val errMsg = errJson?.optJSONObject("error")?.optString("message").orEmpty()
                val errReason = errJson?.optJSONObject("error")
                    ?.optJSONArray("details")
                    ?.optJSONObject(0)
                    ?.optString("reason").orEmpty()

                val isKeyInvalid = code == 400 && (
                    errReason == "API_KEY_INVALID" ||
                    errMsg.contains("API key not valid", ignoreCase = true) ||
                    errMsg.contains("API_KEY_INVALID", ignoreCase = true) ||
                    err.contains("API_KEY_INVALID")
                )

                val statusLabel = when {
                    code == 200 -> "READY"
                    code == 503 -> "503 HIGH DEMAND (AUTO-FALLBACK READY)"
                    code == 429 && (err.contains("PerDay") || err.contains("limit: 0")) ->
                        "DAILY QUOTA FULL (RESETS 1:30 PM IST)"
                    code == 429 -> "RPM BUSY (WAIT 10S)"
                    isKeyInvalid -> "INVALID KEY (Check Google AI Studio)"
                    code == 403 && errMsg.contains("disabled", ignoreCase = true) ->
                        "API NOT ENABLED IN GCP"
                    errMsg.isNotBlank() -> errMsg.take(65)
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

    fun encodeFileForVision(file: File, maxDim: Int): Pair<String, String> {
        val raw = file.readBytes()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return Base64.encodeToString(raw, Base64.NO_WRAP) to "image/jpeg"
        }
        var sample = 1
        while (bounds.outWidth / sample > maxDim * 2 || bounds.outHeight / sample > maxDim * 2) {
            sample *= 2
        }
        val decoded = BitmapFactory.decodeByteArray(
            raw,
            0,
            raw.size,
            BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: return Base64.encodeToString(raw, Base64.NO_WRAP) to "image/jpeg"

        val maxSide = maxOf(decoded.width, decoded.height)
        val finalBmp = if (maxSide > maxDim) {
            val scale = maxDim.toFloat() / maxSide.toFloat()
            Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * scale).toInt().coerceAtLeast(1),
                (decoded.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else {
            decoded
        }
        val stream = ByteArrayOutputStream()
        finalBmp.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        val b64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
        return b64 to "image/jpeg"
    }

    suspend fun generatePalette(
        apiKey: String,
        nextNumber: Int,
        previousMood: String? = null
    ): Result<PaletteState> = withContext(Dispatchers.IO) {
        try {
            val themeHint = PALETTE_THEMES[Random.nextInt(PALETTE_THEMES.size)]
            val seed = Random.nextInt(100_000, 999_999)
            val prevNote = previousMood?.takeIf { it.isNotBlank() }?.let { "Different from previous vibe: $it." } ?: ""
            val prompt = """
                Generate a completely original, curated 3-color editorial palette for a kinetic-typography video edit (variation seed: $seed, aesthetic direction: "$themeHint"). $prevNote
                Choose 3 distinct harmonious hex colors usable together as background, dominant display type, and accent. Avoid near-black muddy duplicates.
                Respond strictly with a single JSON object containing:
                - "colors": array of exactly 3 objects, each with "hex" (#XXXXXX) and "name" (2 lowercase words)
                - "mood": 2-4 word vibe
                - "pairing": one concise sentence explaining background vs headline vs accent usage
            """.trimIndent()

            val res = generate(
                apiKey = apiKey,
                prompt = prompt,
                systemInstruction = PALETTE_SYSTEM,
                routeMode = GeminiRouteMode.JSON_WORKHORSE,
                expectJson = true
            ).getOrThrow()

            val obj = JSONObject(GroqCodec.stripJsonFences(res.text))
            val arr = obj.optJSONArray("colors")
                ?: throw IllegalStateException("Gemini palette response did not contain a colors array.")
            val swatches = mutableListOf<PaletteSwatch>()
            for (i in 0 until minOf(3, arr.length())) {
                val cObj = arr.optJSONObject(i) ?: continue
                val hex = PaletteColorMath.normalizeHex(cObj.optString("hex", ""), PaletteState.DEFAULT_SWATCHES[i].hex)
                val name = cObj.optString("name", "tone").trim().lowercase(Locale.US).ifBlank { "tone" }
                swatches.add(PaletteSwatch(hex = hex, name = name))
            }
            while (swatches.size < 3) {
                swatches.add(PaletteState.DEFAULT_SWATCHES[swatches.size])
            }
            val state = PaletteState(
                number = nextNumber.coerceAtLeast(1),
                mood = obj.optString("mood", "Editorial Motion").trim().ifBlank { "Editorial Motion" },
                pairing = obj.optString(
                    "pairing",
                    "Use 01 & 03 for clean contrast with 02 for kinetic highlight pills."
                ).trim(),
                colors = swatches
            )
            Result.success(state)
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun describeAsset(
        apiKey: String,
        asset: LocalAssetEntity,
        imageFile: File
    ): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        try {
            val (b64, mime) = encodeFileForVision(imageFile, maxDim = 960)
            val isWebShot = asset.description.contains("Interactive UI targets", ignoreCase = true)
            val prompt = when {
                asset.kind == LocalAssetEntity.KIND_CLIP -> {
                    "This 3-frame storyboard is sampled at 10%, 50%, 85% from the video clip ${asset.relativePath} (${asset.width}x${asset.height}, ${String.format(Locale.US, "%.1f", asset.durationSec)}s). Respond strictly with raw JSON with keys: \"description\" (1 concise sentence describing visual motion/content across the clip) and \"safeZone\" (1 concise sentence stating where kinetic overlay typography can sit safely with high contrast)."
                }
                isWebShot -> {
                    "Analyze this tutorial UI screenshot (${asset.relativePath}, ${asset.width}x${asset.height}). Extracted UI context: ${asset.description.take(700)}. Respond strictly with raw JSON with keys: \"description\" (1-2 concise sentences describing the UI screen AND preserving key target [x,y,wxh] boxes for tutorial cursor/highlight animation) and \"safeZone\" (1 concise sentence stating which region is safest for kinetic titles without covering interactive controls)."
                }
                asset.kind == LocalAssetEntity.KIND_CUTOUT -> {
                    "Analyze this foreground cutout asset (${asset.relativePath}, ${asset.width}x${asset.height}). Respond strictly with raw JSON with keys: \"description\" (1 concise sentence describing the subject/product) and \"safeZone\" (1 concise sentence stating which quadrant or edge is best for caption typography)."
                }
                else -> {
                    "Analyze this video edit image asset (${asset.relativePath}, ${asset.width}x${asset.height}). Respond strictly with raw JSON with keys: \"description\" (1 concise sentence describing subject, colors, and scene) and \"safeZone\" (1 concise sentence stating where overlay text can sit safely with high contrast)."
                }
            }

            val res = generate(
                apiKey = apiKey,
                prompt = prompt,
                systemInstruction = ASSET_SYSTEM,
                routeMode = GeminiRouteMode.JSON_WORKHORSE,
                expectJson = true,
                images = listOf(GeminiImageInput(base64Data = b64, mimeType = mime))
            ).getOrThrow()

            val obj = JSONObject(GroqCodec.stripJsonFences(res.text))
            val desc = obj.optString("description", "").trim()
            val safe = obj.optString("safeZone", "").trim()
            Result.success(Pair(desc, safe))
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun auditCutout(
        apiKey: String,
        comparisonFile: File,
        currentTolerance: Int,
        currentPad: CutoutPadRatios,
        isSvgSource: Boolean,
        keepOriginalMode: Boolean
    ): Result<CutoutAuditResult> = withContext(Dispatchers.IO) {
        try {
            val (b64, mime) = encodeFileForVision(comparisonFile, maxDim = 1024)
            val padInfo = String.format(
                Locale.US,
                "T=%.2f, R=%.2f, B=%.2f, L=%.2f",
                currentPad.top,
                currentPad.right,
                currentPad.bottom,
                currentPad.left
            )
            val prompt = """
                Inspect this side-by-side PNG cutout diagnostic image carefully.
                LEFT panel shows the SOURCE original. RIGHT panel shows the extracted PNG CUTOUT placed over a dark gray checkerboard.
                Current parameters: isSvg=$isSvgSource, tolerance=$currentTolerance, keepOriginal=$keepOriginalMode, padding=[$padInfo].

                Check for two specific conversion defects:
                1. UNNECESSARY CROPPING: outer vector strokes, rounded corners, drop shadows, circular rings, or thin edges clipped/cut off at the top, right, bottom, or left border of the RIGHT cutout.
                2. OVER-REMOVAL / ACCIDENTAL ERASURE: bright foreground details, inner logo text, eyes, shiny highlights, or anti-aliased edges erased into checkerboard tiles (comparing RIGHT vs LEFT), OR the LEFT image is a full rectangular UI screenshot/scene that should keep its background intact rather than keying out corners.

                Respond ONLY with raw valid JSON containing exact keys:
                - "status": "OK" or "ADJUST"
                - "diagnosis": 1 concise honest sentence summarizing whether clipping or accidental erasure was seen and what you fixed (or "Cutout edges and interior details look clean.")
                - "keepOriginal": boolean (set true ONLY if background removal destroyed a complex UI card, photo, or non-transparent scene)
                - "recommendedTolerance": integer (if isSvg=true, use 0; if inner light shapes/text were erased on a raster logo, lower to 6..20; otherwise $currentTolerance)
                - "extraPadTop": number 0.0..0.16
                - "extraPadRight": number 0.0..0.16
                - "extraPadBottom": number 0.0..0.16
                - "extraPadLeft": number 0.0..0.16
            """.trimIndent()

            val res = generate(
                apiKey = apiKey,
                prompt = prompt,
                systemInstruction = "Respond strictly with raw valid JSON matching the exact schema keys.",
                routeMode = GeminiRouteMode.JSON_WORKHORSE,
                expectJson = true,
                images = listOf(GeminiImageInput(base64Data = b64, mimeType = mime))
            ).getOrThrow()

            val obj = JSONObject(GroqCodec.stripJsonFences(res.text))
            val statusRaw = obj.optString("status", "OK").trim().uppercase(Locale.US)
            val diagnosis = obj.optString("diagnosis", "Cutout edges verified by Gemini Vision.")
            val keepOriginal = obj.optBoolean("keepOriginal", keepOriginalMode)
            val recommendedTolerance = obj.optInt("recommendedTolerance", currentTolerance)
            val extraT = obj.optDouble("extraPadTop", 0.0).toFloat().coerceIn(0f, 0.16f)
            val extraR = obj.optDouble("extraPadRight", 0.0).toFloat().coerceIn(0f, 0.16f)
            val extraB = obj.optDouble("extraPadBottom", 0.0).toFloat().coerceIn(0f, 0.16f)
            val extraL = obj.optDouble("extraPadLeft", 0.0).toFloat().coerceIn(0f, 0.16f)

            val hasDelta = keepOriginal != keepOriginalMode ||
                (!isSvgSource && recommendedTolerance != currentTolerance) ||
                extraT > 0.01f || extraR > 0.01f || extraB > 0.01f || extraL > 0.01f

            Result.success(
                CutoutAuditResult(
                    status = if (statusRaw == "ADJUST" || hasDelta) "ADJUST" else "OK",
                    diagnosis = diagnosis.ifBlank { "Cutout edges and interior details verified by Gemini Vision." },
                    keepOriginal = keepOriginal,
                    recommendedTolerance = recommendedTolerance,
                    extraPadTop = extraT,
                    extraPadRight = extraR,
                    extraPadBottom = extraB,
                    extraPadLeft = extraL,
                    needsReprocess = hasDelta
                )
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun transcribeAudioWordByWord(
        apiKey: String,
        audioFile: File
    ): Result<SrtParseResult> = withContext(Dispatchers.IO) {
        try {
            val cleanKey = sanitizeApiKey(apiKey)
            if (!isValidApiKey(cleanKey)) {
                return@withContext Result.failure(IllegalStateException("Add your Gemini API key in Profile -> AI & Quota first."))
            }
            if (!audioFile.exists() || audioFile.length() == 0L) {
                return@withContext Result.failure(IllegalStateException("Audio file not found or empty: ${audioFile.name}"))
            }

            val rawBytes = audioFile.readBytes()
            val b64 = Base64.encodeToString(rawBytes, Base64.NO_WRAP)
            val ext = audioFile.extension.lowercase(Locale.US)
            val mime = when (ext) {
                "wav" -> "audio/wav"
                "m4a" -> "audio/m4a"
                "ogg" -> "audio/ogg"
                "aac" -> "audio/aac"
                "flac" -> "audio/flac"
                else -> "audio/mp3"
            }

            val prompt = """
                You are a professional audio subtitler.
                Transcribe the spoken words in this audio into standard SubRip (.srt) subtitle format.
                Requirements:
                1. Every entry MUST have a sequential cue number (1, 2, 3...), timestamp line (HH:MM:SS,mmm --> HH:MM:SS,mmm), and the spoken words.
                2. Keep cues short: 1 to 4 words per cue for kinetic typography pacing.
                3. The timing must match the audio timestamps accurately.
                4. Output ONLY the raw SRT text, no explanations, no markdown fences.
            """.trimIndent()

            val res = generate(
                apiKey = cleanKey,
                prompt = prompt,
                routeMode = GeminiRouteMode.JSON_WORKHORSE,
                expectJson = false,
                images = listOf(GeminiImageInput(base64Data = b64, mimeType = mime))
            ).getOrThrow()

            val rawSrt = res.text
                .replace("```srt", "")
                .replace("```", "")
                .trim()

            val parsed = SrtParser.parse(rawSrt)
            Result.success(parsed)
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }
}
