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
import kotlinx.coroutines.withContext
import org.json.JSONArray
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
    val fix: String
)

data class SheetReviewResult(
    val summary: String,
    val issues: List<SheetIssue>
)

data class PuterConnectionInfo(
    val latencyMs: Long,
    val username: String,
    val modelUsed: String,
    val usedFallback: Boolean,
    val usage: PuterUsageReport?
)

private data class PuterCompletionResult(
    val text: String,
    val modelUsed: String
)

object PuterClient {

    private const val COOLDOWN_MS = 4 * 60_000L

    private val modelCooldownUntilMs = ConcurrentHashMap<String, Long>()

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

    fun activeCooledModels(nowMs: Long = System.currentTimeMillis()): Set<String> {
        val expired = mutableListOf<String>()
        val active = HashSet<String>()
        for ((model, until) in modelCooldownUntilMs) {
            if (until > nowMs) {
                active.add(model)
            } else {
                expired.add(model)
            }
        }
        expired.forEach { modelCooldownUntilMs.remove(it) }
        return active
    }

    fun clearModelCooldowns() {
        modelCooldownUntilMs.clear()
    }

    suspend fun fetchWhoami(authToken: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val clean = PuterCodec.normalizeAuthToken(authToken)
            if (clean.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("Puter session token is missing."))
            }
            val conn = (URL(PuterCodec.WHOAMI_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 9_000
                readTimeout = 9_000
                setRequestProperty("Authorization", "Bearer $clean")
                setRequestProperty("Accept", "application/json")
            }
            try {
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (code !in 200..299) {
                    val err = PuterCodec.extractErrorDetail(body, code)
                    throw PuterApiException(code, err.message, err.code, err.usageLimited)
                }
                val username = PuterCodec.parseWhoamiUsername(body)
                    ?: throw IllegalStateException("Signed in, but username could not be read.")
                Result.success(username)
            } finally {
                conn.disconnect()
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun fetchUsage(authToken: String): Result<PuterUsageReport> = withContext(Dispatchers.IO) {
        try {
            val clean = PuterCodec.normalizeAuthToken(authToken)
            if (clean.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("Puter session token is missing."))
            }
            val conn = (URL(PuterCodec.USAGE_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 9_000
                readTimeout = 9_000
                setRequestProperty("Authorization", "Bearer $clean")
                setRequestProperty("Accept", "application/json")
            }
            try {
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (code !in 200..299) {
                    val err = PuterCodec.extractErrorDetail(body, code)
                    throw PuterApiException(code, err.message, err.code, err.usageLimited)
                }
                Result.success(PuterCodec.parseUsageReport(body))
            } finally {
                conn.disconnect()
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun testConnectionDetailed(
        authToken: String,
        modelPref: String = PuterCodec.PREF_AUTO
    ): Result<PuterConnectionInfo> = withContext(Dispatchers.IO) {
        try {
            val cleanToken = PuterCodec.normalizeAuthToken(authToken)
            if (cleanToken.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("Not signed in to Puter.js yet."))
            }

            val t0 = System.currentTimeMillis()
            val username = fetchWhoami(cleanToken).getOrElse { err ->
                throw err
            }
            val usage = fetchUsage(cleanToken).getOrNull()
            if (usage != null && usage.isExhausted) {
                throw PuterOutOfCreditsException(
                    "Signed in as @$username, but Puter.js credits ran out. Switch account to continue.",
                    httpCode = 402
                )
            }

            val expectedPrimary = PuterCodec.buildCandidateModels(
                modelPref = modelPref,
                heavyTask = false
            ).first()

            val probeRes = callChatWithFallback(
                authToken = cleanToken,
                modelPref = modelPref,
                prompt = "Reply with the single word READY.",
                heavyTask = false,
                systemInstruction = "Output only the word READY."
            )

            val refreshedUsage = fetchUsage(cleanToken).getOrNull() ?: usage
            val totalMs = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
            Result.success(
                PuterConnectionInfo(
                    latencyMs = totalMs,
                    username = username,
                    modelUsed = probeRes.modelUsed,
                    usedFallback = probeRes.modelUsed != expectedPrimary,
                    usage = refreshedUsage
                )
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun generatePalette(
        authToken: String,
        nextNumber: Int,
        previousMood: String? = null,
        modelPref: String = PuterCodec.PREF_AUTO
    ): Result<PaletteState> = withContext(Dispatchers.IO) {
        try {
            val themeHint = PALETTE_THEMES[Random.nextInt(PALETTE_THEMES.size)]
            val seed = Random.nextInt(100_000, 999_999)
            val prevNote = previousMood?.takeIf { it.isNotBlank() }?.let { "Different from previous vibe: $it." } ?: ""
            val prompt = """
                Generate a completely original, curated 3-color editorial palette for a kinetic-typography video edit (variation seed: $seed, aesthetic direction: "$themeHint"). $prevNote
                Choose 3 distinct harmonious hex colors usable together as background, dominant display type, and accent. Avoid near-black muddy duplicates.
                Respond strictly with a single JSON object (no markdown fences, no prose) containing:
                - "colors": array of exactly 3 objects, each with "hex" (#XXXXXX) and "name" (2 lowercase words)
                - "mood": 2-4 word vibe
                - "pairing": one concise sentence explaining background vs headline vs accent usage
            """.trimIndent()

            val res = callChatWithFallback(
                authToken = authToken,
                modelPref = modelPref,
                prompt = prompt,
                heavyTask = false,
                systemInstruction = "You are a bold editorial color director. Respond ONLY with raw valid JSON."
            )
            val obj = JSONObject(PuterCodec.stripJsonFences(res.text))
            val arr = obj.optJSONArray("colors")
                ?: throw IllegalStateException("Puter AI palette response did not contain a colors array.")
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
        authToken: String,
        asset: LocalAssetEntity,
        imageFile: File,
        modelPref: String = PuterCodec.PREF_AUTO
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
                authToken = authToken,
                modelPref = modelPref,
                prompt = prompt,
                images = listOf(PuterImageInput(base64Data = b64, mimeType = mime)),
                heavyTask = false,
                systemInstruction = "Respond strictly with raw valid JSON containing keys \"description\" and \"safeZone\"."
            )
            val obj = JSONObject(PuterCodec.stripJsonFences(res.text))
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
        authToken: String,
        comparisonFile: File,
        currentTolerance: Int,
        currentPad: CutoutPadRatios,
        isSvgSource: Boolean,
        keepOriginalMode: Boolean,
        modelPref: String = PuterCodec.PREF_AUTO
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
                authToken = authToken,
                modelPref = modelPref,
                prompt = prompt,
                images = listOf(PuterImageInput(base64Data = b64, mimeType = mime)),
                heavyTask = false,
                systemInstruction = "Respond strictly with raw valid JSON matching the exact schema keys."
            )
            val obj = JSONObject(PuterCodec.stripJsonFences(res.text))
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
                    diagnosis = obj.optString("diagnosis", "Cutout edges and interior details verified by Puter AI.")
                        .trim()
                        .ifBlank { "Cutout verified by Puter AI." },
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
        authToken: String,
        sheetImages: List<File>,
        htmlCode: String,
        frameCount: Int,
        modelPref: String = PuterCodec.PREF_AUTO
    ): Result<SheetReviewResult> = withContext(Dispatchers.IO) {
        try {
            val prompt = """
                You are inspecting a rendered video contact sheet covering $frameCount frames for a 1920x1080 kinetic typography reel.
                Inspect every frame (#01 to #$frameCount) for: clipped/overflowing text outside 90% safe margins, overlapping text & cards, unreadable low contrast, blank scenes, or out-of-bounds elements.
                Also inspect the HTML source below:
                ${htmlCode.take(65_000)}

                Respond ONLY with raw valid JSON containing:
                - "summary": short overall diagnostic sentence
                - "issues": array of visual defects, each with "frame" (e.g. "#09"), "time" (e.g. "t=4.00s"), "problem", and "fix" (exact CSS/JS adjustment needed)
            """.trimIndent()

            val encodedImages = sheetImages.take(2).mapNotNull { file ->
                if (!file.exists()) return@mapNotNull null
                val (b64, mime) = encodeFileForVision(file, maxDim = 1400)
                PuterImageInput(base64Data = b64, mimeType = mime)
            }
            val res = callChatWithFallback(
                authToken = authToken,
                modelPref = modelPref,
                prompt = prompt,
                images = encodedImages,
                heavyTask = true,
                systemInstruction = "Respond strictly with raw valid JSON containing \"summary\" and \"issues\"."
            )
            val obj = JSONObject(PuterCodec.stripJsonFences(res.text))
            val summary = obj.optString("summary", "Puter AI frame inspection finished.").trim()
            val arr = obj.optJSONArray("issues") ?: JSONArray()
            val issues = mutableListOf<SheetIssue>()
            for (i in 0 until arr.length()) {
                val it = arr.optJSONObject(i) ?: continue
                issues.add(
                    SheetIssue(
                        frame = it.optString("frame", "Frame"),
                        time = it.optString("time", ""),
                        problem = it.optString("problem", ""),
                        fix = it.optString("fix", "")
                    )
                )
            }
            Result.success(SheetReviewResult(summary = summary, issues = issues))
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun autoFixHtmlWithSheet(
        authToken: String,
        sheetImages: List<File>,
        htmlCode: String,
        issuesSummary: String,
        durationSec: Double,
        modelPref: String = PuterCodec.PREF_AUTO
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val durStr = String.format(Locale.US, "%.2f", durationSec)
            val prompt = """
                Fix all visual overflow, overlapping, clipping, contrast, and timing glitches shown on the attached contact sheet images while keeping working scenes untouched.
                Reported notes / issues:
                $issuesSummary

                MANDATORY CONSTRAINTS:
                - Self-contained single HTML file from <!DOCTYPE html> to </html> (NO markdown code fences, NO prose).
                - Keep window.RAW = { duration: $durStr, width: 1920, height: 1080, fps: 30, seek(t){...} } with deterministic seek(t).
                - Zero external http(s) URLs or CDN script/font links.
                - Keep any relative "assets/..." references and RAW.syncVideo(...) calls intact.
                - Keep all text strictly inside the 90% safe area (margins >= 110px horizontal, >= 80px top, clear of bottom progress bar).

                CURRENT HTML:
                $htmlCode
            """.trimIndent()

            val encodedImages = sheetImages.take(2).mapNotNull { file ->
                if (!file.exists()) return@mapNotNull null
                val (b64, mime) = encodeFileForVision(file, maxDim = 1400)
                PuterImageInput(base64Data = b64, mimeType = mime)
            }
            val res = callChatWithFallback(
                authToken = authToken,
                modelPref = modelPref,
                prompt = prompt,
                images = encodedImages,
                heavyTask = true,
                systemInstruction = "Output only a complete, valid self-contained HTML document from <!DOCTYPE html> to </html> with no markdown fences or explanation."
            )
            Result.success(CodeValidator.unwrapMarkdownFences(res.text))
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    private suspend fun callChatWithFallback(
        authToken: String,
        modelPref: String,
        prompt: String,
        images: List<PuterImageInput> = emptyList(),
        heavyTask: Boolean = false,
        systemInstruction: String? = null
    ): PuterCompletionResult {
        val cleanToken = PuterCodec.normalizeAuthToken(authToken)
        if (cleanToken.isEmpty()) {
            throw IllegalStateException("Please sign in with Puter / Google first.")
        }

        val candidates = PuterCodec.buildCandidateModels(
            modelPref = modelPref,
            heavyTask = heavyTask,
            cooledDownModels = activeCooledModels()
        )
        val attemptedModels = mutableListOf<String>()
        var sawAnyHttpResponse = false
        var lastCategory = PuterFailureCategory.TRANSIENT_SERVER
        var lastRawMessage = ""
        var lastCause: Throwable? = null

        for ((index, model) in candidates.withIndex()) {
            currentCoroutineContext().ensureActive()
            attemptedModels.add(model)

            val connectTimeoutMs = if (heavyTask) 12_000 else 10_000
            val readTimeoutMs = when {
                heavyTask -> 55_000
                index == 0 -> 18_000
                else -> 22_000
            }

            try {
                val text = callSingleDriverCompletion(
                    cleanToken = cleanToken,
                    model = model,
                    prompt = prompt,
                    images = images,
                    systemInstruction = systemInstruction,
                    connectTimeoutMs = connectTimeoutMs,
                    readTimeoutMs = readTimeoutMs
                )
                modelCooldownUntilMs.remove(model)
                return PuterCompletionResult(text = text, modelUsed = model)
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: UnknownHostException) {
                throw IllegalStateException(
                    "Cannot reach api.puter.com (DNS lookup failed). Check internet connection or VPN/Private DNS and retry.",
                    t
                )
            } catch (t: Throwable) {
                lastCause = t
                val apiEx = t as? PuterApiException
                val httpCode = apiEx?.httpCode
                if (httpCode != null || apiEx != null) {
                    sawAnyHttpResponse = true
                }
                val isTimeout = t is SocketTimeoutException ||
                    (t is InterruptedIOException && (t.message ?: "").contains("time", ignoreCase = true))
                val isConnectErr = t is ConnectException
                val rawMsg = (t.message ?: t.javaClass.simpleName).trim()
                val category = PuterCodec.classifyFailure(
                    httpCode = httpCode,
                    rawMessage = rawMsg,
                    errorCode = apiEx?.errorCode,
                    usageLimited = apiEx?.usageLimited == true,
                    isTimeout = isTimeout,
                    isDnsOrConnectError = isConnectErr
                )
                lastCategory = category
                lastRawMessage = rawMsg

                if (category == PuterFailureCategory.INSUFFICIENT_CREDITS) {
                    throw PuterOutOfCreditsException(
                        PuterCodec.buildFriendlyErrorMessage(
                            lastCategory = category,
                            lastRawMessage = rawMsg,
                            attemptedModels = attemptedModels,
                            sawAnyHttpResponse = sawAnyHttpResponse
                        ),
                        httpCode = httpCode ?: 402
                    )
                }

                if (category == PuterFailureCategory.FATAL_AUTH) {
                    throw IllegalStateException(
                        PuterCodec.buildFriendlyErrorMessage(
                            lastCategory = category,
                            lastRawMessage = rawMsg,
                            attemptedModels = attemptedModels,
                            sawAnyHttpResponse = sawAnyHttpResponse
                        ),
                        t
                    )
                }

                modelCooldownUntilMs[model] = System.currentTimeMillis() + COOLDOWN_MS

                if (index < candidates.lastIndex) {
                    delay(Random.nextLong(220L, 450L))
                }
            }
        }

        val friendly = PuterCodec.buildFriendlyErrorMessage(
            lastCategory = lastCategory,
            lastRawMessage = lastRawMessage,
            attemptedModels = attemptedModels,
            sawAnyHttpResponse = sawAnyHttpResponse
        )
        throw IllegalStateException(friendly, lastCause)
    }

    private fun callSingleDriverCompletion(
        cleanToken: String,
        model: String,
        prompt: String,
        images: List<PuterImageInput>,
        systemInstruction: String?,
        connectTimeoutMs: Int,
        readTimeoutMs: Int
    ): String {
        val payload = PuterCodec.buildDriversCallJson(
            authToken = cleanToken,
            prompt = prompt,
            images = images,
            systemInstruction = systemInstruction,
            model = model
        )

        val endpoint = URL(PuterCodec.DRIVERS_CALL_URL)
        val conn = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            doOutput = true
            setRequestProperty("Content-Type", "text/plain;actually=json")
            setRequestProperty("Authorization", "Bearer $cleanToken")
        }

        try {
            val bytes = payload.toByteArray(Charsets.UTF_8)
            conn.outputStream.use { os -> os.write(bytes) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

            if (code !in 200..299) {
                val parsed = PuterCodec.extractErrorDetail(body, code)
                throw PuterApiException(
                    httpCode = code,
                    message = parsed.message,
                    errorCode = parsed.code,
                    usageLimited = parsed.usageLimited
                )
            }

            return PuterCodec.extractResponseText(body)
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
}
