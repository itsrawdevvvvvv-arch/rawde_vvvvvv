package com.rawedit.app.domain

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.rawedit.app.data.local.LocalAssetEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

data class SheetIssue(
    val frame: String,
    val time: String,
    val problem: String,
    val fix: String,
    val sampleIndex: Int = -1
)

data class SheetReviewResult(
    val summary: String,
    val issues: List<SheetIssue>,
    val rootCauses: List<GroqRootCause> = emptyList(),
    val cachedReview: GroqReviewResult? = null
)

data class GroqConnectionInfo(
    val latencyMs: Long,
    val availableModelsCount: Int,
    val modelUsed: String,
    val usedFallback: Boolean,
    val quota: GroqQuotaReport?
)

enum class GroqAutoFixStatus {
    APPLIED,
    NOTHING_MATCHED,
    STRUCTURAL,
    NOTHING_TO_FIX
}

data class GroqAutoFixOutcome(
    val status: GroqAutoFixStatus = GroqAutoFixStatus.APPLIED,
    val fixedHtml: String,
    val validation: CodeValidationResult?,
    val rootCauses: List<GroqRootCause> = emptyList(),
    val editsApplied: Int = 0,
    val failedCount: Int = 0,
    val skippedCount: Int = 0,
    val failureSummary: String = "",
    val repairRoundsUsed: Int = 0,
    val modelUsed: String = "",
    val quota: GroqQuotaReport? = null
)

private data class GroqCompletionResult(
    val text: String,
    val modelUsed: String,
    val quota: GroqQuotaReport?
)

object GroqLimiter {
    private val buckets = ConcurrentHashMap<String, GroqModelBucket>()
    private val _ui = MutableStateFlow<GroqUi>(GroqUi.Ready)
    val ui: StateFlow<GroqUi> = _ui.asStateFlow()

    @Volatile
    private var hardBlockUntilMs: Long = 0L

    @Volatile
    private var hardBlockKind: GroqLimitKind = GroqLimitKind.NONE

    @Volatile
    private var persistCallback: ((Long, String) -> Unit)? = null

    fun bindPersistence(
        initialUntilMs: Long,
        initialKindRaw: String,
        onPersist: (untilMs: Long, kind: String) -> Unit
    ) {
        persistCallback = onPersist
        val now = System.currentTimeMillis()
        if (initialUntilMs > now) {
            val parsedKind = runCatching { GroqLimitKind.valueOf(initialKindRaw) }.getOrDefault(GroqLimitKind.DAILY)
            hardBlockUntilMs = initialUntilMs
            hardBlockKind = parsedKind
            _ui.value = GroqUi.HardBlocked(parsedKind, initialUntilMs)
        } else {
            hardBlockUntilMs = 0L
            hardBlockKind = GroqLimitKind.NONE
            if (_ui.value is GroqUi.HardBlocked) {
                _ui.value = GroqUi.Ready
            }
        }
    }

    fun bucket(model: String): GroqModelBucket? = buckets[model]

    fun currentHardBlock(nowMs: Long = System.currentTimeMillis()): Pair<GroqLimitKind, Long>? {
        val until = hardBlockUntilMs
        if (until > nowMs) {
            val kind = if (hardBlockKind == GroqLimitKind.CREDITS) GroqLimitKind.CREDITS else GroqLimitKind.DAILY
            return kind to until
        }
        if (until > 0L) {
            hardBlockUntilMs = 0L
            hardBlockKind = GroqLimitKind.NONE
            persistCallback?.invoke(0L, "")
            if (_ui.value is GroqUi.HardBlocked) {
                _ui.value = GroqUi.Ready
            }
        }
        return null
    }

    fun record(model: String, headerFields: Map<String?, List<String>>?) {
        val now = System.currentTimeMillis()
        val b = GroqCodec.readBucket(headerFields, now) ?: return
        buckets[model] = b
        when {
            b.remainingRequests == 0L && (b.limitRequests == null || b.limitRequests > 0L) -> {
                val resetAt = if (b.resetRequestsAtMs > now) b.resetRequestsAtMs else (now + 3_600_000L)
                hardBlock(GroqLimitKind.DAILY, resetAt)
            }
            b.remainingTokens == 0L && b.resetTokensAtMs > now -> {
                cooling(b.resetTokensAtMs)
            }
        }
    }

    fun hardBlock(kind: GroqLimitKind, untilMs: Long) {
        val safeKind = if (kind == GroqLimitKind.CREDITS) GroqLimitKind.CREDITS else GroqLimitKind.DAILY
        hardBlockUntilMs = untilMs
        hardBlockKind = safeKind
        persistCallback?.invoke(untilMs, safeKind.name)
        _ui.value = GroqUi.HardBlocked(safeKind, untilMs)
    }

    fun cooling(untilMs: Long) {
        if (currentHardBlock() != null) return
        _ui.value = GroqUi.Cooling(untilMs)
    }

    fun markReadyIfNotHardBlocked() {
        val now = System.currentTimeMillis()
        if (hardBlockUntilMs <= now) {
            val current = _ui.value
            if (current is GroqUi.Cooling && current.untilMs > now) {
                return
            }
            _ui.value = GroqUi.Ready
        }
    }

    fun reset() {
        buckets.clear()
        hardBlockUntilMs = 0L
        hardBlockKind = GroqLimitKind.NONE
        persistCallback?.invoke(0L, "")
        _ui.value = GroqUi.Ready
    }
}

object GroqClient {

    private const val MODELS_CACHE_TTL_MS = 5 * 60_000L
    private const val MAX_INLINE_WAIT_MS = 12_000L

    private data class CachedModels(
        val ids: Set<String>,
        val fetchedAtMs: Long
    )

    private val latestQuotaByKey = ConcurrentHashMap<String, GroqQuotaReport>()
    private val availableModelsByKey = ConcurrentHashMap<String, CachedModels>()

    private val PALETTE_THEMES = listOf(
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

    fun getLatestQuota(apiKey: String): GroqQuotaReport? {
        val clean = GroqCodec.normalizeApiKey(apiKey)
        if (clean.isEmpty()) return null
        return latestQuotaByKey[clean]
    }

    private fun recordQuota(apiKey: String, quota: GroqQuotaReport?) {
        if (quota != null && quota.hasTelemetry) {
            latestQuotaByKey[apiKey] = quota
        }
    }

    suspend fun verifyModelsEndpoint(apiKey: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val clean = GroqCodec.normalizeApiKey(apiKey)
            if (clean.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("Groq API key is missing."))
            }
            val conn = (URL(GroqCodec.MODELS_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 9_000
                readTimeout = 9_000
                setRequestProperty("Authorization", "Bearer $clean")
                setRequestProperty("Accept", "application/json")
            }
            try {
                val code = conn.responseCode
                val quota = GroqCodec.parseQuotaHeaders(conn.headerFields, code)
                recordQuota(clean, quota)
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (code !in 200..299) {
                    val lowerHeaders = GroqCodec.headerFieldsToLowerMap(conn.headerFields)
                    val lim = GroqCodec.classifyLimit(code, body, lowerHeaders)
                    if (lim.kind == GroqLimitKind.CREDITS || lim.kind == GroqLimitKind.DAILY) {
                        val until = System.currentTimeMillis() + ((lim.waitSec ?: 3600.0) * 1000.0).toLong().coerceAtLeast(60_000L)
                        GroqLimiter.hardBlock(lim.kind, until)
                        throw GroqHardLimitException(kind = lim.kind, resetAtMs = until, quota = quota)
                    }
                    val err = GroqCodec.extractErrorDetail(body, code)
                    throw GroqApiException(code, err.message, err.code, err.type, quota)
                }
                val chatIds = GroqCodec.parseAvailableChatModelIds(body)
                if (chatIds.isNotEmpty()) {
                    availableModelsByKey[clean] = CachedModels(
                        ids = chatIds,
                        fetchedAtMs = System.currentTimeMillis()
                    )
                }
                val count = runCatching { JSONObject(body).optJSONArray("data")?.length() ?: 0 }.getOrDefault(chatIds.size)
                Result.success(count)
            } finally {
                conn.disconnect()
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    private suspend fun resolveAvailableChatModels(cleanKey: String): Set<String> {
        val now = System.currentTimeMillis()
        val cached = availableModelsByKey[cleanKey]
        if (cached != null && (now - cached.fetchedAtMs) <= MODELS_CACHE_TTL_MS && cached.ids.isNotEmpty()) {
            return cached.ids
        }
        verifyModelsEndpoint(cleanKey)
        return availableModelsByKey[cleanKey]?.ids ?: emptySet()
    }

    suspend fun refreshQuota(
        apiKey: String,
        modelPref: String = GroqCodec.PREF_AUTO
    ): Result<GroqQuotaReport> = withContext(Dispatchers.IO) {
        try {
            val clean = GroqCodec.normalizeApiKey(apiKey)
            if (clean.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("Groq API key is missing."))
            }
            verifyModelsEndpoint(clean)
            val quota = getLatestQuota(clean) ?: GroqQuotaReport(updatedAtMs = System.currentTimeMillis())
            Result.success(quota)
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun testConnectionDetailed(
        apiKey: String,
        modelPref: String = GroqCodec.PREF_AUTO
    ): Result<GroqConnectionInfo> = withContext(Dispatchers.IO) {
        try {
            val cleanKey = GroqCodec.normalizeApiKey(apiKey)
            if (cleanKey.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("Paste your Groq API key (gsk_...) first."))
            }

            GroqLimiter.reset()
            val t0 = System.currentTimeMillis()
            val modelsCount = verifyModelsEndpoint(cleanKey).getOrElse { err ->
                throw err
            }
            val liveModels = availableModelsByKey[cleanKey]?.ids ?: emptySet()
            val latestQuota = getLatestQuota(cleanKey)

            val expectedPrimary = GroqCodec.buildCandidateModels(
                modelPref = modelPref,
                requiresVision = false,
                heavyTask = false,
                availableModels = liveModels
            ).firstOrNull() ?: GroqCodec.MODEL_LLAMA33_70B

            val probeOutcome = runCatching {
                callChatWithFallback(
                    apiKey = cleanKey,
                    modelPref = modelPref,
                    prompt = "Say OK",
                    requiresVision = false,
                    heavyTask = false,
                    systemInstruction = null,
                    jsonObjectMode = false,
                    maxCompletionTokens = 64,
                    temperature = 0.7
                )
            }

            val totalMs = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
            val modelUsed = probeOutcome.map { it.modelUsed }.getOrDefault(expectedPrimary)
            val finalQuota = probeOutcome.getOrNull()?.quota ?: latestQuota

            Result.success(
                GroqConnectionInfo(
                    latencyMs = totalMs,
                    availableModelsCount = modelsCount,
                    modelUsed = modelUsed,
                    usedFallback = modelUsed != expectedPrimary,
                    quota = finalQuota
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
            val cleanKey = GroqCodec.normalizeApiKey(apiKey)
            if (cleanKey.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("Add your Groq API key first."))
            }
            if (!audioFile.exists() || audioFile.length() == 0L) {
                return@withContext Result.failure(IllegalStateException("Audio file not found or empty: ${audioFile.name}"))
            }

            val boundary = "----GroqBoundary" + System.currentTimeMillis()
            val lineEnd = "\r\n"
            val twoHyphens = "--"

            val url = URL(GroqCodec.TRANSCRIPTIONS_URL)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 60_000
                doOutput = true
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                setRequestProperty("Authorization", "Bearer $cleanKey")
                setRequestProperty("Accept", "application/json")
            }

            try {
                conn.outputStream.use { os ->
                    fun writeField(name: String, value: String) {
                        os.write((twoHyphens + boundary + lineEnd).toByteArray(Charsets.UTF_8))
                        os.write(("Content-Disposition: form-data; name=\"$name\"$lineEnd$lineEnd").toByteArray(Charsets.UTF_8))
                        os.write((value + lineEnd).toByteArray(Charsets.UTF_8))
                    }

                    writeField("model", GroqCodec.MODEL_WHISPER_LARGE_V3)
                    writeField("response_format", "verbose_json")
                    writeField("timestamp_granularities[]", "word")

                    val ext = audioFile.extension.lowercase(Locale.US)
                    val mime = when (ext) {
                        "wav" -> "audio/wav"
                        "m4a" -> "audio/m4a"
                        "ogg" -> "audio/ogg"
                        "aac" -> "audio/aac"
                        "flac" -> "audio/flac"
                        else -> "audio/mpeg"
                    }
                    val fileName = audioFile.name.ifBlank { "audio.mp3" }

                    os.write((twoHyphens + boundary + lineEnd).toByteArray(Charsets.UTF_8))
                    os.write(("Content-Disposition: form-data; name=\"file\"; filename=\"$fileName\"$lineEnd").toByteArray(Charsets.UTF_8))
                    os.write(("Content-Type: $mime$lineEnd$lineEnd").toByteArray(Charsets.UTF_8))

                    audioFile.inputStream().use { input ->
                        input.copyTo(os)
                    }
                    os.write(lineEnd.toByteArray(Charsets.UTF_8))

                    os.write((twoHyphens + boundary + twoHyphens + lineEnd).toByteArray(Charsets.UTF_8))
                    os.flush()
                }

                val code = conn.responseCode
                val rawHeaders = conn.headerFields
                val quota = GroqCodec.parseQuotaHeaders(rawHeaders, code)
                recordQuota(cleanKey, quota)
                GroqLimiter.record(GroqCodec.MODEL_WHISPER_LARGE_V3, rawHeaders)

                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

                if (code !in 200..299) {
                    val parsed = GroqCodec.extractErrorDetail(body, code)
                    throw GroqApiException(
                        httpCode = code,
                        message = parsed.message,
                        errorCode = parsed.code,
                        errorType = parsed.type,
                        quota = quota
                    )
                }

                val parsedResult = GroqCodec.buildSrtFromGroqVerboseJson(body)
                Result.success(parsedResult)
            } finally {
                conn.disconnect()
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun generatePalette(
        apiKey: String,
        nextNumber: Int,
        previousMood: String? = null,
        modelPref: String = GroqCodec.PREF_AUTO
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

            val res = callChatWithFallback(
                apiKey = apiKey,
                modelPref = modelPref,
                prompt = prompt,
                requiresVision = false,
                heavyTask = false,
                systemInstruction = "You are a bold editorial color director. Respond ONLY with raw valid JSON.",
                jsonObjectMode = true,
                maxCompletionTokens = 1024,
                temperature = 0.7
            )
            val obj = JSONObject(GroqCodec.stripJsonFences(res.text))
            val arr = obj.optJSONArray("colors")
                ?: throw IllegalStateException("Groq palette response did not contain a colors array.")
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
            Result.success(
                PaletteState(
                    number = nextNumber.coerceAtLeast(1),
                    mood = obj.optString("mood", "Editorial Motion").trim().ifBlank { "Editorial Motion" },
                    pairing = obj.optString(
                        "pairing",
                        "Use 01 & 03 for clean contrast with 02 for kinetic highlight pills."
                    ).trim(),
                    colors = swatches
                )
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun describeAsset(
        apiKey: String,
        asset: LocalAssetEntity,
        imageFile: File,
        modelPref: String = GroqCodec.PREF_AUTO
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
                else -> {
                    val label = if (asset.kind == LocalAssetEntity.KIND_CUTOUT) "PNG cutout" else "image"
                    "Analyze this $label (${asset.relativePath}, ${asset.width}x${asset.height}). Respond strictly with raw JSON with keys: \"description\" (1 concise sentence describing visual subject and lighting/palette) and \"safeZone\" (1 concise sentence stating where kinetic overlay text can be placed cleanly without obscuring the subject)."
                }
            }

            val res = callChatWithFallback(
                apiKey = apiKey,
                modelPref = modelPref,
                prompt = prompt,
                images = listOf(GroqImageInput(base64Data = b64, mimeType = mime)),
                requiresVision = true,
                heavyTask = false,
                systemInstruction = "Respond strictly with raw valid JSON containing keys \"description\" and \"safeZone\".",
                jsonObjectMode = true,
                maxCompletionTokens = 1024,
                temperature = 0.2
            )
            val obj = JSONObject(GroqCodec.stripJsonFences(res.text))
            val desc = obj.optString("description", asset.description).trim().ifBlank { asset.description }
            val safe = obj.optString("safeZone", asset.safeZone).trim().ifBlank { asset.safeZone }
            Result.success(desc to safe)
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
        keepOriginalMode: Boolean,
        modelPref: String = GroqCodec.PREF_AUTO
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

            val res = callChatWithFallback(
                apiKey = apiKey,
                modelPref = modelPref,
                prompt = prompt,
                images = listOf(GroqImageInput(base64Data = b64, mimeType = mime)),
                requiresVision = true,
                heavyTask = false,
                systemInstruction = "Respond strictly with raw valid JSON matching the exact schema keys.",
                jsonObjectMode = true,
                maxCompletionTokens = 1024,
                temperature = 0.15
            )
            val obj = JSONObject(GroqCodec.stripJsonFences(res.text))
            val statusRaw = obj.optString("status", "OK").trim().uppercase(Locale.US)
            val keepOrig = obj.optBoolean("keepOriginal", false)
            val recTol = if (isSvgSource) {
                0
            } else {
                obj.optInt("recommendedTolerance", currentTolerance).coerceIn(4, 60)
            }
            val exT = obj.optDouble("extraPadTop", 0.0).toFloat().coerceIn(0f, 0.20f)
            val exR = obj.optDouble("extraPadRight", 0.0).toFloat().coerceIn(0f, 0.20f)
            val exB = obj.optDouble("extraPadBottom", 0.0).toFloat().coerceIn(0f, 0.20f)
            val exL = obj.optDouble("extraPadLeft", 0.0).toFloat().coerceIn(0f, 0.20f)
            val hasDelta = keepOrig != keepOriginalMode ||
                (!isSvgSource && recTol != currentTolerance) ||
                exT > 0.01f || exR > 0.01f || exB > 0.01f || exL > 0.01f

            Result.success(
                CutoutAuditResult(
                    status = if (statusRaw == "ADJUST" || hasDelta) "ADJUST" else "OK",
                    diagnosis = obj.optString("diagnosis", "Cutout edges and interior details verified by Groq Vision.")
                        .trim()
                        .ifBlank { "Cutout verified by Groq Vision." },
                    keepOriginal = keepOrig,
                    recommendedTolerance = recTol,
                    extraPadTop = exT,
                    extraPadRight = exR,
                    extraPadBottom = exB,
                    extraPadLeft = exL,
                    needsReprocess = hasDelta
                )
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun reviewContactSheet(
        apiKey: String,
        samples: List<KeyframeSample>,
        durationSec: Double,
        htmlHash: Int = 0,
        modelPref: String = GroqCodec.PREF_AUTO
    ): Result<SheetReviewResult> = withContext(Dispatchers.IO) {
        try {
            val bundle = KeyframeSheetGenerator.buildReviewCollageBundle(samples, durationSec)
            if (bundle.jpegBytes.isEmpty() || bundle.pickedSamples.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("No keyframe thumbnails available to inspect."))
            }
            val flagsText = if (bundle.flagLines.isEmpty()) {
                "None"
            } else {
                bundle.flagLines.joinToString("\n")
            }
            val userPrompt = (
                "DURATION: ${String.format(Locale.US, "%.1f", durationSec)}s\n" +
                    "FLAGS (f|t|selector|x,y,wxh|issue):\n$flagsText"
                ).take(1500)
            val estTokens = GroqCodec.guardTokens(
                images = 1,
                REVIEW_SYSTEM,
                userPrompt,
                maxCompletion = 700
            )
            val b64 = Base64.encodeToString(bundle.jpegBytes, Base64.NO_WRAP)
            val res = callChatWithFallback(
                apiKey = apiKey,
                modelPref = modelPref,
                prompt = userPrompt,
                images = listOf(GroqImageInput(base64Data = b64, mimeType = "image/jpeg")),
                requiresVision = true,
                heavyTask = false,
                systemInstruction = REVIEW_SYSTEM,
                jsonObjectMode = true,
                maxCompletionTokens = 700,
                temperature = 0.1,
                reasoningEffortOverride = "none",
                estimatedTokens = estTokens
            )
            val parsed = GroqCodec.parseReviewJson(res.text, htmlHash)
            if (parsed.parseFailed) {
                return@withContext Result.success(
                    SheetReviewResult(
                        summary = "Unreadable review response — try again",
                        issues = emptyList(),
                        cachedReview = null
                    )
                )
            }
            val mappedIssues = parsed.issues.map { issue ->
                val picked = bundle.pickedSamples.getOrNull(
                    (issue.f - 1).coerceIn(0, (bundle.pickedSamples.size - 1).coerceAtLeast(0))
                )
                SheetIssue(
                    frame = picked?.let { "#${it.index + 1}" } ?: "F${issue.f}",
                    time = picked?.let { String.format(Locale.US, "t=%.2fs", it.timeSec) }.orEmpty(),
                    problem = "[${issue.type}] ${issue.where}".trim(),
                    fix = issue.fix,
                    sampleIndex = picked?.index ?: -1
                )
            }
            val summary = if (mappedIssues.isEmpty()) {
                "Groq Vision checked ${bundle.pickedSamples.size}-frame collage: layout and contrast look clean."
            } else {
                "Found ${mappedIssues.size} visual issue(s) across ${bundle.pickedSamples.size}-frame collage."
            }
            Result.success(
                SheetReviewResult(
                    summary = summary,
                    issues = mappedIssues,
                    cachedReview = parsed
                )
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun autoFixHtmlTwoStage(
        apiKey: String,
        samples: List<KeyframeSample>,
        htmlCode: String,
        durationSec: Double,
        validationError: String = "",
        modelPref: String = GroqCodec.PREF_AUTO,
        lastReview: GroqReviewResult? = null,
        validator: suspend (String) -> CodeValidationResult
    ): Result<GroqAutoFixOutcome> = withContext(Dispatchers.IO) {
        val originalHtml = htmlCode
        try {
            val cleanKey = GroqCodec.normalizeApiKey(apiKey)
            val hash = originalHtml.hashCode()
            val now = System.currentTimeMillis()
            val domBundle = KeyframeSheetGenerator.extractDomFlagBundle(samples)

            var review = lastReview?.takeIf {
                !it.parseFailed && it.htmlHash == hash && (now - it.atMs) < 15 * 60_000L
            }
            if (review == null && domBundle.flagLines.isEmpty()) {
                val freshReview = reviewContactSheet(
                    apiKey = cleanKey,
                    samples = samples,
                    durationSec = durationSec,
                    htmlHash = hash,
                    modelPref = modelPref
                ).getOrElse { err -> throw err }
                review = freshReview.cachedReview
            }

            if ((review?.ok == true && review.issues.isEmpty()) &&
                domBundle.flagLines.isEmpty() &&
                validationError.isBlank()
            ) {
                return@withContext Result.success(
                    GroqAutoFixOutcome(
                        status = GroqAutoFixStatus.NOTHING_TO_FIX,
                        fixedHtml = originalHtml,
                        validation = null,
                        failureSummary = "No visual or DOM issues found."
                    )
                )
            }

            val findings = buildString {
                if (validationError.isNotBlank()) {
                    append("ERR|").append(validationError.trim()).append('\n')
                }
                review?.issues?.forEach { issue ->
                    append("R|f").append(issue.f)
                        .append('|').append(issue.type)
                        .append('|').append(issue.where)
                        .append('|').append(issue.sev)
                        .append('|').append(issue.fix)
                        .append('\n')
                }
                if (domBundle.flagLines.isNotEmpty()) {
                    append("DOM (f|t|selector|x,y,wxh|issue):\n")
                    append(domBundle.flagLines.joinToString("\n"))
                }
            }.trim().take(2600)

            val issueTokens = review?.issues?.flatMap { issue ->
                Regex("[A-Za-z0-9_#.-]{3,}").findAll("${issue.where} ${issue.fix}")
                    .map { it.value }
                    .toList()
            }.orEmpty()
            val errTokens = Regex("[A-Za-z0-9_#.-]{4,}").findAll(validationError)
                .map { it.value }
                .take(6)
                .toList()
            val excerptTokens = (domBundle.flagTokens + issueTokens + errTokens).distinct()
            val excerpt = GroqCodec.buildCleanExcerpt(
                html = originalHtml,
                tokens = excerptTokens,
                maxChars = 6000,
                radius = 10
            )
            val durStr = String.format(Locale.US, "%.1f", durationSec)
            val userPrompt = "DURATION: $durStr\nFINDINGS:\n$findings\nEXCERPT:\n$excerpt"
            val est1 = GroqCodec.guardTokens(
                images = 0,
                PROMPT_C1_SYSTEM,
                userPrompt,
                maxCompletion = 1400
            )

            val planRes = callChatWithFallback(
                apiKey = cleanKey,
                modelPref = modelPref,
                prompt = userPrompt,
                images = emptyList(),
                requiresVision = false,
                heavyTask = true,
                systemInstruction = PROMPT_C1_SYSTEM,
                jsonObjectMode = true,
                maxCompletionTokens = 1400,
                temperature = 0.1,
                reasoningEffortOverride = "low",
                estimatedTokens = est1
            )
            var lastModelUsed = planRes.modelUsed
            var lastQuota = planRes.quota
            val plan = GroqCodec.parseLeanPlanJson(planRes.text)

            var applyOutcome = GroqCodec.applyEditsValidated(originalHtml, plan.edits) { candidate ->
                validator(candidate).isValid
            }
            var totalApplied = applyOutcome.appliedCount
            var repairRoundsUsed = 0

            if (applyOutcome.failedEdits.isNotEmpty() && applyOutcome.appliedCount < plan.edits.size) {
                repairRoundsUsed = 1
                val failedTokens = applyOutcome.failedEdits.map { it.edit.find.take(40) }
                val windows = GroqCodec.buildCleanExcerpt(
                    html = applyOutcome.updatedHtml,
                    tokens = failedTokens,
                    maxChars = 2500,
                    radius = 6
                )
                val failedText = applyOutcome.failedEdits.joinToString("\n") { fe ->
                    "${fe.edit.rc}|${fe.reason}|find=${fe.edit.find.take(120)}|replace=${fe.edit.replace.take(160)}"
                }.take(1000)
                val repairPrompt = "FAILED:\n$failedText\nWINDOWS:\n$windows"
                val est2 = GroqCodec.guardTokens(
                    images = 0,
                    PROMPT_C2_SYSTEM,
                    repairPrompt,
                    maxCompletion = 900
                )
                val repairRes = callChatWithFallback(
                    apiKey = cleanKey,
                    modelPref = modelPref,
                    prompt = repairPrompt,
                    images = emptyList(),
                    requiresVision = false,
                    heavyTask = true,
                    systemInstruction = PROMPT_C2_SYSTEM,
                    jsonObjectMode = true,
                    maxCompletionTokens = 900,
                    temperature = 0.0,
                    reasoningEffortOverride = "low",
                    estimatedTokens = est2
                )
                lastModelUsed = repairRes.modelUsed
                if (repairRes.quota != null) lastQuota = repairRes.quota
                val repairEdits = GroqCodec.parseLeanPlanJson(repairRes.text).edits
                val secondPass = GroqCodec.applyEditsValidated(applyOutcome.updatedHtml, repairEdits) { candidate ->
                    validator(candidate).isValid
                }
                totalApplied += secondPass.appliedCount
                applyOutcome = secondPass
            }

            if (totalApplied > 0) {
                val finalVal = validator(applyOutcome.updatedHtml)
                val isDeterminismBroken = finalVal.warning?.contains("alter", ignoreCase = true) == true
                if (!finalVal.isValid || isDeterminismBroken) {
                    val rollbackReason = if (isDeterminismBroken) {
                        "Rolled back: patched seek(t) failed determinism check (${finalVal.warning})."
                    } else {
                        "Rolled back: ${finalVal.summaryMessage}"
                    }
                    return@withContext Result.success(
                        GroqAutoFixOutcome(
                            status = GroqAutoFixStatus.NOTHING_MATCHED,
                            fixedHtml = originalHtml,
                            validation = null,
                            editsApplied = 0,
                            failedCount = applyOutcome.failedEdits.size + 1,
                            skippedCount = plan.skippedCount,
                            failureSummary = rollbackReason,
                            repairRoundsUsed = repairRoundsUsed,
                            modelUsed = lastModelUsed,
                            quota = lastQuota ?: getLatestQuota(cleanKey)
                        )
                    )
                }
                val failNote = applyOutcome.failedEdits.joinToString(", ") { "${it.edit.rc}:${it.reason}" }
                return@withContext Result.success(
                    GroqAutoFixOutcome(
                        status = GroqAutoFixStatus.APPLIED,
                        fixedHtml = finalVal.cleanedHtml,
                        validation = finalVal,
                        editsApplied = totalApplied,
                        failedCount = applyOutcome.failedEdits.size,
                        skippedCount = plan.skippedCount,
                        failureSummary = failNote,
                        repairRoundsUsed = repairRoundsUsed,
                        modelUsed = lastModelUsed,
                        quota = lastQuota ?: getLatestQuota(cleanKey)
                    )
                )
            }

            if (plan.structural) {
                return@withContext Result.success(
                    GroqAutoFixOutcome(
                        status = GroqAutoFixStatus.STRUCTURAL,
                        fixedHtml = originalHtml,
                        validation = null,
                        skippedCount = plan.skippedCount,
                        failureSummary = "Needs structural changes. Use the copyable Cloud AI fix prompt.",
                        repairRoundsUsed = repairRoundsUsed,
                        modelUsed = lastModelUsed,
                        quota = lastQuota ?: getLatestQuota(cleanKey)
                    )
                )
            }

            val reasons = applyOutcome.failedEdits.joinToString("; ") { "${it.edit.rc}: ${it.reason}" }
                .ifBlank { "No matching code fragments found." }
            Result.success(
                GroqAutoFixOutcome(
                    status = GroqAutoFixStatus.NOTHING_MATCHED,
                    fixedHtml = originalHtml,
                    validation = null,
                    failedCount = applyOutcome.failedEdits.size,
                    skippedCount = plan.skippedCount,
                    failureSummary = reasons,
                    repairRoundsUsed = repairRoundsUsed,
                    modelUsed = lastModelUsed,
                    quota = lastQuota ?: getLatestQuota(cleanKey)
                )
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    private suspend fun groqCall(
        model: String,
        estimatedTokens: Int,
        post: suspend () -> Triple<Int, String, Map<String?, List<String>>?>
    ): Pair<String, GroqQuotaReport?> {
        GroqLimiter.currentHardBlock()?.let { (kind, until) ->
            throw GroqHardLimitException(kind = kind, resetAtMs = until)
        }
        GroqLimiter.bucket(model)?.let { b ->
            val now = System.currentTimeMillis()
            if (b.remainingTokens != null && b.remainingTokens < estimatedTokens && b.resetTokensAtMs > now) {
                val waitMs = b.resetTokensAtMs - now
                GroqLimiter.cooling(b.resetTokensAtMs)
                if (waitMs <= MAX_INLINE_WAIT_MS) {
                    delay(waitMs + 250L)
                } else {
                    throw GroqCooldownException(((waitMs / 1000L).toInt() + 1).coerceAtLeast(1))
                }
            }
        }
        var attempt = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            val (status, body, rawHeaders) = post()
            GroqLimiter.record(model, rawHeaders)
            val quota = GroqCodec.parseQuotaHeaders(rawHeaders, status)
            if (status in 200..299) {
                GroqLimiter.markReadyIfNotHardBlocked()
                return body to quota
            }
            val lowerHeaders = GroqCodec.headerFieldsToLowerMap(rawHeaders)
            val lim = GroqCodec.classifyLimit(status, body, lowerHeaders)
            when (lim.kind) {
                GroqLimitKind.CREDITS, GroqLimitKind.DAILY -> {
                    val until = System.currentTimeMillis() + ((lim.waitSec ?: 3600.0) * 1000.0).toLong().coerceAtLeast(60_000L)
                    GroqLimiter.hardBlock(lim.kind, until)
                    throw GroqHardLimitException(kind = lim.kind, resetAtMs = until, quota = quota)
                }
                GroqLimitKind.COOLDOWN -> {
                    val waitMs = ((lim.waitSec ?: 20.0) * 1000.0).toLong().coerceAtLeast(1_000L)
                    GroqLimiter.cooling(System.currentTimeMillis() + waitMs)
                    if (attempt == 0 && waitMs <= MAX_INLINE_WAIT_MS) {
                        attempt++
                        delay(waitMs + 250L)
                        continue
                    }
                    throw GroqCooldownException(((waitMs / 1000L).toInt() + 1).coerceAtLeast(1))
                }
                GroqLimitKind.NONE -> {
                    val parsed = GroqCodec.extractErrorDetail(body, status)
                    throw GroqApiException(
                        httpCode = status,
                        message = parsed.message,
                        errorCode = parsed.code,
                        errorType = parsed.type,
                        quota = quota,
                        headers = lowerHeaders
                    )
                }
            }
        }
    }

    private suspend fun callChatWithFallback(
        apiKey: String,
        modelPref: String,
        prompt: String,
        images: List<GroqImageInput> = emptyList(),
        requiresVision: Boolean = images.isNotEmpty(),
        heavyTask: Boolean = false,
        systemInstruction: String? = null,
        jsonObjectMode: Boolean = false,
        maxCompletionTokens: Int? = null,
        temperature: Double = 0.2,
        reasoningEffortOverride: String? = null,
        estimatedTokens: Int? = null
    ): GroqCompletionResult {
        val cleanKey = GroqCodec.normalizeApiKey(apiKey)
        if (cleanKey.isEmpty()) {
            throw IllegalStateException("Please add your Groq API key first.")
        }

        GroqLimiter.currentHardBlock()?.let { (kind, until) ->
            throw GroqHardLimitException(kind = kind, resetAtMs = until)
        }

        val liveAvailable = resolveAvailableChatModels(cleanKey)
        val candidates = GroqCodec.buildCandidateModels(
            modelPref = modelPref,
            requiresVision = requiresVision,
            heavyTask = heavyTask,
            availableModels = liveAvailable
        )
        val attemptedModels = mutableListOf<String>()
        val modelErrors = mutableListOf<String>()
        var sawAnyHttpResponse = false
        var lastCategory = GroqFailureCategory.TRANSIENT_SERVER
        var lastRawMessage = ""
        var lastQuota: GroqQuotaReport? = null
        var lastCause: Throwable? = null

        val estTok = estimatedTokens ?: (
            images.size * GroqCodec.IMAGE_TOKENS +
                GroqCodec.estTokens(systemInstruction.orEmpty()) +
                GroqCodec.estTokens(prompt) +
                (maxCompletionTokens ?: 512)
            )

        for ((index, model) in candidates.withIndex()) {
            currentCoroutineContext().ensureActive()
            attemptedModels.add(model)

            val connectTimeoutMs = if (heavyTask) 12_000 else 10_000
            val readTimeoutMs = when {
                heavyTask -> 55_000
                index == 0 -> 18_000
                else -> 24_000
            }

            try {
                val (rawBody, quota) = groqCall(model, estTok) {
                    postSingleChatCompletion(
                        cleanKey = cleanKey,
                        model = model,
                        prompt = prompt,
                        images = if (requiresVision) images else emptyList(),
                        systemInstruction = systemInstruction,
                        jsonObjectMode = jsonObjectMode,
                        maxCompletionTokens = maxCompletionTokens,
                        temperature = temperature,
                        heavyTask = heavyTask,
                        reasoningEffortOverride = reasoningEffortOverride,
                        connectTimeoutMs = connectTimeoutMs,
                        readTimeoutMs = readTimeoutMs
                    )
                }
                recordQuota(cleanKey, quota)
                val text = GroqCodec.extractResponseText(rawBody, quota, modelId = model)
                return GroqCompletionResult(text = text, modelUsed = model, quota = quota)
            } catch (ce: CancellationException) {
                throw ce
            } catch (cool: GroqCooldownException) {
                throw cool
            } catch (hard: GroqHardLimitException) {
                throw hard
            } catch (tooBig: GroqPromptTooLargeException) {
                throw tooBig
            } catch (t: UnknownHostException) {
                throw IllegalStateException(
                    "Cannot reach api.groq.com (DNS lookup failed). Check internet connection or VPN/Private DNS and retry.",
                    t
                )
            } catch (t: Throwable) {
                lastCause = t
                val apiEx = t as? GroqApiException
                val httpCode = apiEx?.httpCode
                if (apiEx?.quota != null) {
                    lastQuota = apiEx.quota
                    recordQuota(cleanKey, apiEx.quota)
                }
                if (httpCode != null || apiEx != null) {
                    sawAnyHttpResponse = true
                }
                val isTimeout = t is SocketTimeoutException ||
                    (t is InterruptedIOException && (t.message ?: "").contains("time", ignoreCase = true))
                val isConnectErr = t is ConnectException
                val rawMsg = (t.message ?: t.javaClass.simpleName).trim()
                val shortCode = apiEx?.errorCode?.takeIf { it.isNotBlank() } ?: httpCode?.toString() ?: ""
                val errSummary = if (shortCode.isNotEmpty()) "$model: $shortCode ($rawMsg)" else "$model: $rawMsg"
                modelErrors.add(errSummary.take(120))
                val category = GroqCodec.classifyFailure(
                    httpCode = httpCode,
                    rawMessage = rawMsg,
                    errorCode = apiEx?.errorCode,
                    errorType = apiEx?.errorType,
                    quota = apiEx?.quota,
                    isTimeout = isTimeout,
                    isDnsOrConnectError = isConnectErr
                )
                lastCategory = category
                lastRawMessage = rawMsg

                if (category == GroqFailureCategory.QUOTA_OR_CREDITS_EXHAUSTED) {
                    val resetAt = System.currentTimeMillis() + 3_600_000L
                    GroqLimiter.hardBlock(GroqLimitKind.DAILY, resetAt)
                    throw GroqHardLimitException(
                        kind = GroqLimitKind.DAILY,
                        resetAtMs = resetAt,
                        quota = lastQuota
                    )
                }

                if (category == GroqFailureCategory.FATAL_AUTH) {
                    throw IllegalStateException(
                        GroqCodec.buildFriendlyErrorMessage(
                            lastCategory = category,
                            lastRawMessage = rawMsg,
                            attemptedModels = attemptedModels,
                            sawAnyHttpResponse = sawAnyHttpResponse,
                            quota = lastQuota,
                            modelErrors = modelErrors
                        ),
                        t
                    )
                }

                if (index < candidates.lastIndex) {
                    delay(Random.nextLong(220L, 450L))
                }
            }
        }

        val friendly = GroqCodec.buildFriendlyErrorMessage(
            lastCategory = lastCategory,
            lastRawMessage = lastRawMessage,
            attemptedModels = attemptedModels,
            sawAnyHttpResponse = sawAnyHttpResponse,
            quota = lastQuota,
            modelErrors = modelErrors
        )
        throw IllegalStateException(friendly, lastCause)
    }

    private fun postSingleChatCompletion(
        cleanKey: String,
        model: String,
        prompt: String,
        images: List<GroqImageInput>,
        systemInstruction: String?,
        jsonObjectMode: Boolean,
        maxCompletionTokens: Int?,
        temperature: Double,
        heavyTask: Boolean,
        reasoningEffortOverride: String?,
        connectTimeoutMs: Int,
        readTimeoutMs: Int
    ): Triple<Int, String, Map<String?, List<String>>?> {
        val payload = GroqCodec.buildChatCompletionJson(
            prompt = prompt,
            images = images,
            systemInstruction = systemInstruction,
            model = model,
            jsonObjectMode = jsonObjectMode,
            maxCompletionTokens = maxCompletionTokens,
            temperature = temperature,
            heavyTask = heavyTask,
            reasoningEffortOverride = reasoningEffortOverride
        )

        val endpoint = URL(GroqCodec.CHAT_COMPLETIONS_URL)
        val conn = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $cleanKey")
            setRequestProperty("Accept", "application/json")
        }

        try {
            val bytes = payload.toByteArray(Charsets.UTF_8)
            conn.outputStream.use { os -> os.write(bytes) }

            val code = conn.responseCode
            val headers = conn.headerFields
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            return Triple(code, body, headers)
        } finally {
            conn.disconnect()
        }
    }

    private fun encodeFileForVision(file: File, maxDim: Int): Pair<String, String> {
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
            ).also { if (it !== decoded) decoded.recycle() }
        } else {
            decoded
        }
        val baos = ByteArrayOutputStream()
        finalBmp.compress(Bitmap.CompressFormat.JPEG, 84, baos)
        finalBmp.recycle()
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP) to "image/jpeg"
    }

    private const val REVIEW_SYSTEM = """You review frames of a 1920x1080 explainer reel. The image is a 2x2 collage of 4 frames, each labeled "#n t=seconds FLAG" in a strip below it. Frames are at 1/3 scale. Cyan dashed line = safe bottom (nothing may cross it; the progress bar sits below). Red boxes = flagged elements.
Report ONLY defects you can see or that the flag list states: clip (text/element cut at an edge), overlap (text or cards colliding), bar (content below the cyan line), blank (empty/black frame), contrast (low-contrast text or icon), broken (missing image).
Reply with ONE JSON object, no prose:
{"ok":false,"issues":[{"f":1,"type":"clip|overlap|bar|blank|contrast|broken","where":"max 8 words","sev":"high|med|low","fix":"max 12 words"}]}
Max 6 issues. Omit clean frames. If everything is fine: {"ok":true,"issues":[]}."""

    private const val PROMPT_C1_SYSTEM = """You patch a deterministic 1920x1080 HTML reel (everything is a pure function of t inside RAW.seek(t)).
Rules of the renderer: safe area x120..1800, y70..930 (progress bar below 930). Columns: left x140..1040, right x1160..1760, full x140..1780. Card brackets extend 34px outside the card. Font size = floor(min(max, W/(chars*k))), k: Bebas/Anton/Oswald .45, Poppins/Montserrat/Inter/Space Grotesk .62 (.66 at 800-900), Playfair .58, JetBrains Mono .60. The last scene must use t<=end so t=duration is not blank.
FORBIDDEN in any replacement: CSS transition/@keyframes/animation, requestAnimationFrame, setTimeout, setInterval, Date.now, performance.now, Math.random, video.play(), external URLs, innerHTML/append inside seek.
Input: findings (what is wrong, with rects) and EXCERPT windows (verbatim code, no line numbers; "@@ window @@" separates windows).
Reply with ONE JSON object, no prose:
{"edits":[{"i":1,"find":"...","replace":"..."}],"skipped":[{"t":12.5,"why":"max 10 words"}],"structural":false}
Edit rules:
- `find` = ONE line fragment (no newline), 12-160 chars, copied EXACTLY from the excerpt, distinctive enough to appear once (include the selector or property name).
- `replace` keeps the surrounding syntax valid. Change numbers, widths, positions or comparison operators. Fix a shared class once instead of many scenes.
- Use real numbers from the rects, never guesses. Max 8 edits.
- If the defect needs new elements, a new renderer, or seek() rewrites: "structural":true and no edits for it."""

    private const val PROMPT_C2_SYSTEM = """You fix failed patches for a deterministic 1920x1080 reel. Reply with ONE JSON object only: {"edits":[{"i":1,"find":"...","replace":"..."}]}.
Each failed edit has a reason: not_found (text differs from the file), ambiguous(n) (matches n places), forbidden, breaks_validation.
`find` must be ONE line fragment copied exactly from WINDOWS, unique (add neighbouring text on the same line). Keep the same intent. Never use transition, @keyframes, rAF, setTimeout, setInterval, Date.now, Math.random, external URLs. Do not touch anything not named in FAILED."""
}
