package com.rawedit.app.domain

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
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
    CREATIVE_FLASH, // 3.8-flash (20 RPD) -> 3.6-flash (20 RPD) -> 3.5-flash-lite (500 RPD) -> 3.1-flash-lite (500 RPD)
    FAST_CHAT       // Instant low-latency chain: gemini-2.5-flash -> 3.5-flash-lite -> 2.5-flash-lite
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

        val FAST_CHAIN = listOf(
            "gemini-2.5-flash",
            "gemini-2.5-flash-lite",
            "gemini-2.0-flash",
            "gemini-3.5-flash-lite"
        )

        val PALETTE_THEMES = listOf(
            "brutalist editorial",
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

        fun getMinIntervalMsForModel(modelId: String): Long = when {
            modelId.contains("flash-lite") -> 4_000L
            else -> 12_000L
        }

        fun sanitizeApiKey(raw: String): String {
            return raw.trim()
                .removeSurrounding("\"")
                .removeSurrounding("'")
                .replace("\u200B", "")
                .replace("\uFEFF", "")
                .replace("\u00A0", "")
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
            images: List<GeminiImageInput> = emptyList(),
            maxOutputTokens: Int = 8192
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
                    put("maxOutputTokens", maxOutputTokens.coerceIn(512, 16384))
                })
            }
            return payload.toString()
        }
    }

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

    suspend fun generate(
        apiKey: String,
        prompt: String,
        systemInstruction: String? = null,
        routeMode: GeminiRouteMode = GeminiRouteMode.JSON_WORKHORSE,
        expectJson: Boolean = true,
        images: List<GeminiImageInput> = emptyList(),
        maxOutputTokens: Int = 8192
    ): Result<GeminiGenerationResult> = withContext(Dispatchers.IO) {
        val cleanKey = sanitizeApiKey(apiKey)
        if (!isValidApiKey(cleanKey)) {
            return@withContext Result.failure(
                IllegalArgumentException("Invalid Google AI Studio API Key format (must start with AIza or AQ).")
            )
        }

        val chain = when (routeMode) {
            GeminiRouteMode.FAST_CHAT -> FAST_CHAIN
            GeminiRouteMode.JSON_WORKHORSE -> (WORKHORSE_CHAIN + "gemini-2.5-flash").distinct()
            GeminiRouteMode.CREATIVE_FLASH -> (listOf("gemini-2.5-flash") + CREATIVE_CHAIN).distinct()
        }
        var totalAttempts = 0
        var lastErrorSummary = "All models in waterfall failed."

        for ((modelIndex, model) in chain.withIndex()) {
            if (isModelInCooldown(model)) {
                continue
            }

            // Single fast attempt per model for chat/lightweight prompts; 2 attempts only for heavy media/SRT
            val maxAttempts = if (images.isEmpty() && prompt.length < 12_000) 1 else 2
            val isQuick = images.isEmpty() && prompt.length < 12_000
            for (attempt in 0 until maxAttempts) {
                totalAttempts++

                if (attempt > 0) {
                    delay(450L)
                }

                val encodedKey = try {
                    java.net.URLEncoder.encode(cleanKey, "UTF-8")
                } catch (_: Exception) { cleanKey }
                val endpoint =
                    "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$encodedKey"
                val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 8_000
                    readTimeout = if (isQuick) 15_000 else 50_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    setRequestProperty("x-goog-api-key", cleanKey)
                }

                val payloadString = buildGenerateContentPayload(
                    prompt = prompt,
                    systemInstruction = systemInstruction,
                    expectJson = expectJson,
                    images = images,
                    maxOutputTokens = maxOutputTokens
                )

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

                    if (status == 400 && errBody.contains("API_KEY_INVALID", ignoreCase = true)) {
                        return@withContext Result.failure(
                            IllegalStateException("API Key is invalid. Check your key in Google AI Studio.")
                        )
                    }

                    if (status == 404 ||
                        errBody.contains("PerDay", ignoreCase = true) ||
                        errBody.contains("limit: 0", ignoreCase = true)
                    ) {
                        markModelDailyExhausted(model)
                        break
                    }

                    if ((status == 503 || status == 429 || status == 500) && attempt == 0 && maxAttempts > 1) {
                        delay(Random.nextLong(300L, 650L))
                        continue
                    }

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

    /**
     * Full 100% Audio-to-SRT Transcription with duration verification, automatic multi-pass
     * continuation if a model stops early (at 25% or 50%), and full script alignment.
     */
    suspend fun transcribeAudioWordByWord(
        apiKey: String,
        audioFile: File,
        knownDurationSec: Double = 0.0,
        scriptHint: String = ""
    ): Result<SrtParseResult> = withContext(Dispatchers.IO) {
        try {
            val cleanKey = sanitizeApiKey(apiKey)
            if (!isValidApiKey(cleanKey)) {
                return@withContext Result.failure(IllegalStateException("Add your Gemini API key in Profile -> AI & Quota first."))
            }
            if (!audioFile.exists() || audioFile.length() == 0L) {
                return@withContext Result.failure(IllegalStateException("Audio file not found or empty: ${audioFile.name}"))
            }

            val actualDurationSec = if (knownDurationSec > 0.5) {
                knownDurationSec
            } else {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(audioFile.absolutePath)
                    val ms = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    (ms / 1000.0).coerceAtLeast(1.0)
                } catch (_: Exception) {
                    15.0
                } finally {
                    runCatching { retriever.release() }
                }
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

            val endTsStr = SrtParser.formatTimestamp(actualDurationSec)
            val durStr = String.format(Locale.US, "%.2f", actualDurationSec)
            val cleanScript = scriptHint.trim()
            val scriptSection = if (cleanScript.isNotBlank()) {
                "\nREFERENCE FULL SCRIPT (You MUST cover 100% of these words from start to end without stopping early):\n$cleanScript\n"
            } else {
                ""
            }

            val prompt = """
                You are an exact, complete audio-to-SRT subtitle transcriber.
                TOTAL AUDIO DURATION: $durStr seconds (00:00:00,000 to $endTsStr).
                $scriptSection
                CRITICAL INSTRUCTIONS — DO NOT STOP AT 25% OR 50%:
                1. You MUST transcribe 100% of the spoken words across the ENTIRE $durStr seconds from 00:00:00,000 all the way until $endTsStr.
                2. Every entry MUST use standard SubRip (.srt) format:
                   - Sequential cue number (1, 2, 3...)
                   - Full timestamp line in exact HH:MM:SS,mmm --> HH:MM:SS,mmm format
                   - Strictly 1 spoken word per cue (word-by-word) for frame-accurate kinetic typography pacing.
                3. Never summarize, never skip the second half, and never stop before the audio ends at $endTsStr.
                4. Output ONLY raw .srt text — no markdown code fences, no commentary.
            """.trimIndent()

            val initialRes = generate(
                apiKey = cleanKey,
                prompt = prompt,
                routeMode = GeminiRouteMode.JSON_WORKHORSE,
                expectJson = false,
                images = listOf(GeminiImageInput(base64Data = b64, mimeType = mime)),
                maxOutputTokens = 16384
            ).getOrThrow()

            val collectedCues = mutableListOf<SrtCue>()
            val firstParsed = SrtParser.parse(initialRes.text)
            if (firstParsed.valid) {
                collectedCues.addAll(firstParsed.cues)
            }

            // Multi-pass continuation loop if transcription stopped before covering at least 86% of the audio duration
            var pass = 0
            while (pass < 3 && actualDurationSec > 4.0) {
                val coveredUntil = collectedCues.maxOfOrNull { it.endSec } ?: 0.0
                if (coveredUntil >= actualDurationSec * 0.86 || (actualDurationSec - coveredUntil) <= 2.2) {
                    break
                }
                pass++
                val resumeTs = SrtParser.formatTimestamp(coveredUntil)
                val tailContext = collectedCues.takeLast(4).joinToString("\n") { c ->
                    "${SrtParser.formatTimestamp(c.startSec)} --> ${SrtParser.formatTimestamp(c.endSec)}: ${c.text}"
                }
                val contPrompt = """
                    The SRT transcription of this $durStr-second audio stopped early at $resumeTs (${String.format(Locale.US, "%.1f", (coveredUntil / actualDurationSec) * 100)}% of the audio).
                    Last transcribed cues:
                    $tailContext
                    $scriptSection
                    CONTINUE transcribing the remaining spoken audio starting strictly from $resumeTs all the way to the end of the audio at $endTsStr ($durStr seconds).
                    Use exact HH:MM:SS,mmm --> HH:MM:SS,mmm format, strictly 1 word per cue (word-by-word). Output ONLY the remaining raw SRT cues.
                """.trimIndent()

                val contRes = generate(
                    apiKey = cleanKey,
                    prompt = contPrompt,
                    routeMode = GeminiRouteMode.JSON_WORKHORSE,
                    expectJson = false,
                    images = listOf(GeminiImageInput(base64Data = b64, mimeType = mime)),
                    maxOutputTokens = 16384
                ).getOrNull() ?: break

                val contParsed = SrtParser.parse(contRes.text)
                if (!contParsed.valid || contParsed.cues.isEmpty()) break

                // If the model returned timestamps starting near 00:00:00 instead of offset `coveredUntil`, shift them by `coveredUntil`
                val minNewStart = contParsed.cues.minOf { it.startSec }
                val shouldOffset = minNewStart < coveredUntil * 0.5 && coveredUntil > 3.0
                val adjustedNewCues = contParsed.cues.map { c ->
                    if (shouldOffset) {
                        c.copy(
                            startSec = (c.startSec + coveredUntil).coerceAtMost(actualDurationSec),
                            endSec = (c.endSec + coveredUntil).coerceAtMost(actualDurationSec)
                        )
                    } else {
                        c
                    }
                }.filter { it.endSec > coveredUntil + 0.15 }

                if (adjustedNewCues.isEmpty()) break
                collectedCues.addAll(adjustedNewCues)
            }

            // If user provided a full script and the audio transcription still missed the tail words, align the remaining script words across the remaining duration!
            if (cleanScript.isNotBlank()) {
                val scriptWords = cleanScript.split(Regex("\\s+")).filter { it.isNotBlank() }
                val transcribedWordsCount = collectedCues.sumOf { it.text.split(Regex("\\s+")).count { w -> w.isNotBlank() } }
                val coveredUntil = collectedCues.maxOfOrNull { it.endSec } ?: 0.0
                if (transcribedWordsCount < (scriptWords.size * 0.85).toInt() && coveredUntil < actualDurationSec - 1.0) {
                    val remainingWords = scriptWords.drop(transcribedWordsCount).joinToString(" ")
                    if (remainingWords.isNotBlank()) {
                        val tailSrt = SrtParser.generateFullScriptSrt(
                            scriptText = remainingWords,
                            totalDurationSec = actualDurationSec,
                            startOffsetSec = coveredUntil,
                            startingCueIndex = collectedCues.size + 1
                        )
                        if (tailSrt.valid) {
                            collectedCues.addAll(tailSrt.cues)
                        }
                    }
                }
            }

            if (collectedCues.isEmpty() && cleanScript.isNotBlank()) {
                return@withContext Result.success(
                    SrtParser.generateFullScriptSrt(cleanScript, actualDurationSec)
                )
            }

            val finalParsed = SrtParser.buildResultFromCues(collectedCues)
            if (!finalParsed.valid) {
                return@withContext Result.failure(IllegalStateException(finalParsed.error ?: "Could not parse SRT output."))
            }
            Result.success(finalParsed)
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    private fun bitmapToJpegBase64(bmp: Bitmap, maxDim: Int = 768, quality: Int = 85): String {
        val maxSide = maxOf(bmp.width, bmp.height)
        val scaled = if (maxSide > maxDim) {
            val scale = maxDim.toFloat() / maxSide.toFloat()
            Bitmap.createScaledBitmap(
                bmp,
                (bmp.width * scale).toInt().coerceAtLeast(1),
                (bmp.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else {
            bmp
        }
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun extractJsonObject(raw: String): String {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        return if (start >= 0 && end > start) raw.substring(start, end + 1) else raw.trim()
    }

    suspend fun refineCutoutWithCircles(
        apiKey: String,
        cutoutBitmap: Bitmap,
        circles: List<IssueCircle>,
        currentTolerance: Int
    ): Result<Pair<Int, String>> = withContext(Dispatchers.IO) {
        runCatching {
            val circleDesc = if (circles.isEmpty()) {
                "No specific circle drawn; perform a general edge & background-residue audit."
            } else {
                circles.joinToString("; ") { c ->
                    "circle at x=%.2f, y=%.2f, r=%.2f".format(Locale.US, c.centerXNorm, c.centerYNorm, c.radiusNorm)
                }
            }
            val prompt = buildString {
                appendLine("You are refining a background-removed PNG cutout.")
                appendLine("Current color-distance tolerance: $currentTolerance (valid range 8..90).")
                appendLine("User circled error area(s) in normalized 0..1 coords: $circleDesc")
                appendLine("If leftover background remains inside the circle, increase tolerance slightly (e.g. +8..+20).")
                appendLine("If foreground subject edges got eaten inside the circle, decrease tolerance slightly (e.g. -6..-15).")
                appendLine("Return ONLY valid JSON: {\"recommendedTolerance\": <int 8..90>, \"note\": \"<short 1-line fix note>\"}")
            }
            val b64 = bitmapToJpegBase64(cutoutBitmap, maxDim = 768, quality = 85)
            val res = generate(
                apiKey = apiKey,
                routeMode = GeminiRouteMode.FAST_CHAT,
                prompt = prompt,
                systemInstruction = "Output strict JSON only.",
                expectJson = true,
                images = listOf(GeminiImageInput(b64, "image/jpeg"))
            ).getOrThrow()
            val obj = JSONObject(extractJsonObject(res.text))
            val recTol = obj.optInt("recommendedTolerance", currentTolerance).coerceIn(8, 90)
            val note = obj.optString("note", "AI refined tolerance to $recTol").ifBlank { "AI refined tolerance to $recTol" }
            recTol to note
        }
    }

}
