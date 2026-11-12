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
    val issues: List<SheetIssue>,
    val rootCauses: List<GroqRootCause> = emptyList()
)

data class GroqConnectionInfo(
    val latencyMs: Long,
    val availableModelsCount: Int,
    val modelUsed: String,
    val usedFallback: Boolean,
    val quota: GroqQuotaReport?
)

data class GroqAutoFixOutcome(
    val fixedHtml: String,
    val validation: CodeValidationResult,
    val rootCauses: List<GroqRootCause>,
    val editsApplied: Int,
    val repairRoundsUsed: Int,
    val modelUsed: String,
    val quota: GroqQuotaReport?
)

private data class GroqCompletionResult(
    val text: String,
    val modelUsed: String,
    val quota: GroqQuotaReport?
)

object GroqClient {

    private const val COOLDOWN_MS = 3 * 60_000L
    private const val SMALL_HTML_FULL_REWRITE_BYTES = 12 * 1024

    private val modelCooldownUntilMs = ConcurrentHashMap<String, Long>()
    private val latestQuotaByKey = ConcurrentHashMap<String, GroqQuotaReport>()

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
                    val err = GroqCodec.extractErrorDetail(body, code)
                    throw GroqApiException(code, err.message, err.code, err.type, quota)
                }
                val count = runCatching { JSONObject(body).optJSONArray("data")?.length() ?: 0 }.getOrDefault(0)
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

    suspend fun refreshQuota(
        apiKey: String,
        modelPref: String = GroqCodec.PREF_AUTO
    ): Result<GroqQuotaReport> = withContext(Dispatchers.IO) {
        try {
            val clean = GroqCodec.normalizeApiKey(apiKey)
            if (clean.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("Groq API key is missing."))
            }
            val res = callChatWithFallback(
                apiKey = clean,
                modelPref = modelPref,
                prompt = "Reply with OK.",
                requiresVision = false,
                heavyTask = false,
                systemInstruction = "Output OK.",
                jsonObjectMode = false,
                maxCompletionTokens = 2,
                temperature = 0.0
            )
            val quota = res.quota ?: getLatestQuota(clean) ?: GroqQuotaReport(updatedAtMs = System.currentTimeMillis())
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

            val t0 = System.currentTimeMillis()
            val modelsCount = verifyModelsEndpoint(cleanKey).getOrElse { err ->
                throw err
            }

            val expectedPrimary = GroqCodec.buildCandidateModels(
                modelPref = modelPref,
                requiresVision = false,
                heavyTask = false
            ).first()

            val probeRes = callChatWithFallback(
                apiKey = cleanKey,
                modelPref = modelPref,
                prompt = "Reply with the single word READY.",
                requiresVision = false,
                heavyTask = false,
                systemInstruction = "Output only the word READY.",
                jsonObjectMode = false,
                maxCompletionTokens = 4,
                temperature = 0.0
            )

            val totalMs = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
            Result.success(
                GroqConnectionInfo(
                    latencyMs = totalMs,
                    availableModelsCount = modelsCount,
                    modelUsed = probeRes.modelUsed,
                    usedFallback = probeRes.modelUsed != expectedPrimary,
                    quota = probeRes.quota ?: getLatestQuota(cleanKey)
                )
            )
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
                maxCompletionTokens = 320,
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
                maxCompletionTokens = 360,
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
                maxCompletionTokens = 360,
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
        flaggedFrameFiles: List<Pair<KeyframeSample, File>>,
        htmlCode: String,
        durationSec: Double,
        validationError: String = "",
        modelPref: String = GroqCodec.PREF_AUTO
    ): Result<SheetReviewResult> = withContext(Dispatchers.IO) {
        try {
            val (plan, _) = runStage1Inspector(
                apiKey = apiKey,
                samples = samples,
                flaggedFrameFiles = flaggedFrameFiles,
                htmlCode = htmlCode,
                durationSec = durationSec,
                validationError = validationError,
                modelPref = modelPref
            )
            val issues = plan.rootCauses.map { rc ->
                val primaryT = rc.timesSec.firstOrNull()
                val matchedSample = if (primaryT != null && samples.isNotEmpty()) {
                    samples.minByOrNull { kotlin.math.abs(it.timeSec - primaryT) }
                } else null
                val frameTag = matchedSample?.let { "#${it.index + 1}" } ?: rc.id
                val timeTag = if (rc.timesSec.isNotEmpty()) {
                    rc.timesSec.joinToString(", ") { String.format(Locale.US, "t=%.2fs", it) }
                } else {
                    matchedSample?.let { String.format(Locale.US, "t=%.2fs", it.timeSec) }.orEmpty()
                }
                SheetIssue(
                    frame = frameTag,
                    time = timeTag,
                    problem = "[${rc.type}] ${rc.element}: ${rc.evidence}".trim(),
                    fix = rc.fix
                )
            }
            val summary = when {
                plan.needsFull -> "Broad issue (${plan.reason.ifBlank { "structural fix needed" }}); found ${issues.size} root cause(s)."
                issues.isEmpty() -> "Groq Vision checked ${flaggedFrameFiles.size} key frame(s): layout and readability look clean."
                else -> "Found ${issues.size} root cause(s) across ${plan.edits.size} targeted patch(es)."
            }
            Result.success(
                SheetReviewResult(
                    summary = summary,
                    issues = issues,
                    rootCauses = plan.rootCauses
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
        flaggedFrameFiles: List<Pair<KeyframeSample, File>>,
        htmlCode: String,
        durationSec: Double,
        validationError: String = "",
        modelPref: String = GroqCodec.PREF_AUTO,
        validator: suspend (String) -> CodeValidationResult
    ): Result<GroqAutoFixOutcome> = withContext(Dispatchers.IO) {
        val originalHtml = htmlCode
        try {
            val cleanKey = GroqCodec.normalizeApiKey(apiKey)

            // === STAGE 1: INSPECTOR (Vision + Excerpt -> Root Causes + Targeted Edits JSON) ===
            val (stage1Plan, stage1Res) = runStage1Inspector(
                apiKey = cleanKey,
                samples = samples,
                flaggedFrameFiles = flaggedFrameFiles,
                htmlCode = originalHtml,
                durationSec = durationSec,
                validationError = validationError,
                modelPref = modelPref
            )

            var workingHtml = originalHtml
            var lastModelUsed = stage1Res.modelUsed
            var lastQuota = stage1Res.quota
            var totalEditsApplied = 0
            var repairRoundsUsed = 0

            val initialApply = GroqCodec.applyExactOnceEdits(workingHtml, stage1Plan.edits)
            workingHtml = initialApply.updatedHtml
            totalEditsApplied += initialApply.appliedCount
            var pendingFailedEdits = initialApply.failedEdits

            if (stage1Plan.needsFull && stage1Plan.edits.isEmpty() &&
                originalHtml.toByteArray(Charsets.UTF_8).size > SMALL_HTML_FULL_REWRITE_BYTES
            ) {
                return@withContext Result.failure(
                    IllegalStateException(
                        "Structural rewrite needed (${stage1Plan.reason.ifBlank { "too broad for patch" }}). Copy Prompt B for Cloud AI Full Mode."
                    )
                )
            }

            if (totalEditsApplied == 0 && pendingFailedEdits.isEmpty() && stage1Plan.rootCauses.isEmpty()) {
                return@withContext Result.failure(
                    IllegalStateException("Groq Inspector found no broken elements or patches to apply.")
                )
            }

            var currentValidation: CodeValidationResult? = if (totalEditsApplied > 0) {
                validator(workingHtml)
            } else null

            var needsRepair = pendingFailedEdits.isNotEmpty() ||
                totalEditsApplied == 0 ||
                currentValidation == null ||
                !currentValidation.isValid ||
                !currentValidation.warning.isNullOrBlank()

            // === STAGE 2: PATCH REPAIR (max 2 rounds, text-only JSON + +-40 line windows) ===
            for (round in 1..2) {
                if (!needsRepair) break
                repairRoundsUsed = round

                val failedText = buildFailedEditsText(
                    failedEdits = pendingFailedEdits,
                    validation = currentValidation,
                    rootCauses = stage1Plan.rootCauses,
                    noEditsAtAll = stage1Plan.edits.isEmpty()
                )
                val targetHtmlForWindows = if (currentValidation != null && !currentValidation.isValid && totalEditsApplied > 0) {
                    // If applied patches caused a syntax error, show broken workingHtml windows so model can fix or see context
                    workingHtml
                } else {
                    workingHtml
                }
                val windowsText = GroqCodec.buildRepairWindows(
                    fullHtml = targetHtmlForWindows,
                    failedEdits = pendingFailedEdits,
                    errorMessage = currentValidation?.summaryMessage.orEmpty(),
                    radiusLines = 40,
                    maxChars = 13_000
                )
                val remainingIssues = buildRemainingIssuesText(
                    validation = currentValidation,
                    rootCauses = stage1Plan.rootCauses,
                    fallbackReason = stage1Plan.reason
                )

                val stage2User = """
                    FAILED: $failedText
                    WINDOWS (plus or minus 40 lines around each target):
                    $windowsText
                    REMAINING ISSUES: $remainingIssues
                """.trimIndent()

                val stage2Res = callChatWithFallback(
                    apiKey = cleanKey,
                    modelPref = modelPref,
                    prompt = stage2User,
                    requiresVision = false,
                    heavyTask = true,
                    systemInstruction = PROMPT_C2_SYSTEM,
                    jsonObjectMode = true,
                    maxCompletionTokens = 3_200,
                    temperature = 0.1
                )
                lastModelUsed = stage2Res.modelUsed
                if (stage2Res.quota != null) lastQuota = stage2Res.quota

                val (repairEdits, repairFullHtml) = GroqCodec.parseStage2Repair(stage2Res.text)

                if (!repairFullHtml.isNullOrBlank() &&
                    workingHtml.toByteArray(Charsets.UTF_8).size <= SMALL_HTML_FULL_REWRITE_BYTES
                ) {
                    val fullVal = validator(repairFullHtml)
                    if (fullVal.isValid && fullVal.warning.isNullOrBlank()) {
                        return@withContext Result.success(
                            GroqAutoFixOutcome(
                                fixedHtml = fullVal.cleanedHtml,
                                validation = fullVal,
                                rootCauses = stage1Plan.rootCauses,
                                editsApplied = maxOf(1, totalEditsApplied),
                                repairRoundsUsed = repairRoundsUsed,
                                modelUsed = lastModelUsed,
                                quota = lastQuota ?: getLatestQuota(cleanKey)
                            )
                        )
                    } else {
                        currentValidation = fullVal
                        pendingFailedEdits = emptyList()
                        continue
                    }
                }

                val repairOutcome = GroqCodec.applyExactOnceEdits(workingHtml, repairEdits)
                workingHtml = repairOutcome.updatedHtml
                totalEditsApplied += repairOutcome.appliedCount
                pendingFailedEdits = repairOutcome.failedEdits

                currentValidation = if (totalEditsApplied > 0) {
                    validator(workingHtml)
                } else null

                needsRepair = pendingFailedEdits.isNotEmpty() ||
                    totalEditsApplied == 0 ||
                    currentValidation == null ||
                    !currentValidation.isValid ||
                    !currentValidation.warning.isNullOrBlank()
            }

            // === SNAPSHOT VERIFY & ROLLBACK ON FAILURE ===
            val finalVal = currentValidation
            val isDeterminismBroken = finalVal?.warning?.contains("alter", ignoreCase = true) == true
            if (totalEditsApplied == 0 || finalVal == null || !finalVal.isValid || isDeterminismBroken) {
                // Guaranteed rollback: never return corrupted HTML; caller keeps originalHtml untouched.
                val reason = when {
                    isDeterminismBroken -> "Rolled back: patched seek(t) failed determinism check (${finalVal?.warning})."
                    finalVal != null && !finalVal.isValid -> "Rolled back: ${finalVal.summaryMessage}"
                    pendingFailedEdits.isNotEmpty() -> "Rolled back: could not match ${pendingFailedEdits.size} patch target(s) uniquely."
                    else -> "Rolled back: no valid patches could be applied safely."
                }
                return@withContext Result.failure(IllegalStateException(reason))
            }

            Result.success(
                GroqAutoFixOutcome(
                    fixedHtml = finalVal.cleanedHtml,
                    validation = finalVal,
                    rootCauses = stage1Plan.rootCauses,
                    editsApplied = totalEditsApplied,
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

    private suspend fun runStage1Inspector(
        apiKey: String,
        samples: List<KeyframeSample>,
        flaggedFrameFiles: List<Pair<KeyframeSample, File>>,
        htmlCode: String,
        durationSec: Double,
        validationError: String,
        modelPref: String
    ): Pair<GroqStage1Plan, GroqCompletionResult> {
        val boundedFrames = flaggedFrameFiles.take(GroqCodec.MAX_IMAGES_PER_REQUEST)
        val encodedImages = boundedFrames.mapNotNull { (_, file) ->
            if (!file.exists()) return@mapNotNull null
            val (b64, mime) = encodeFileForVision(file, maxDim = 960)
            GroqImageInput(base64Data = b64, mimeType = mime)
        }

        val flaggedSamples = samples.filter { it.warnings.isNotEmpty() || it.autoFitCount > 0 }
        val flaggedTimes = (boundedFrames.map { it.first.timeSec } + flaggedSamples.map { it.timeSec })
            .distinct()
            .take(16)

        val domFlagsBlock = buildString {
            if (flaggedSamples.isEmpty()) {
                append("None flagged by DOM check")
            } else {
                flaggedSamples.take(24).forEach { s ->
                    val ts = String.format(Locale.US, "%.2fs", s.timeSec)
                    val issueParts = (s.warnings + s.autoFitNotes).joinToString("; ").ifBlank { "auto-fit triggered" }
                    appendLine("- t=$ts (${s.sceneLabel.ifBlank { "scene" }}): $issueParts")
                }
            }
        }.trim()

        val framesSummaryBlock = if (boundedFrames.isEmpty()) {
            "No frame images attached"
        } else {
            boundedFrames.mapIndexed { idx, (s, _) ->
                val tag = when {
                    s.warnings.isNotEmpty() -> "WARN"
                    s.autoFitCount > 0 -> "FIT"
                    else -> "SAMPLE"
                }
                val ts = String.format(Locale.US, "%.2fs", s.timeSec)
                "Image ${idx + 1}: Frame #${s.index + 1} t=$ts ${s.sceneLabel} [$tag]"
            }.joinToString(" | ")
        }

        val hints = mutableListOf<String>()
        if (validationError.isNotBlank()) hints.add(validationError)
        flaggedSamples.take(12).forEach { s ->
            hints.addAll(s.warnings)
            hints.addAll(s.autoFitNotes)
        }

        val excerpt = GroqCodec.buildHtmlExcerpt(
            fullHtml = htmlCode,
            flaggedTimesSec = flaggedTimes,
            extraHints = hints,
            maxChars = 14_000
        )

        val errBlock = validationError.ifBlank { "None" }
        val durStr = String.format(Locale.US, "%.2f", durationSec)

        val userPrompt = """
            DURATION: $durStr
            ERRORS: $errBlock
            DOM FLAGS (t, element, rect, issue):
            $domFlagsBlock
            FLAGGED FRAMES: $framesSummaryBlock (images attached)
            EXCERPT (line-numbered; only the scene data, CSS and functions referenced by the flags):
            $excerpt
        """.trimIndent()

        val res = callChatWithFallback(
            apiKey = apiKey,
            modelPref = modelPref,
            prompt = userPrompt,
            images = encodedImages,
            requiresVision = encodedImages.isNotEmpty(),
            heavyTask = true,
            systemInstruction = PROMPT_C1_SYSTEM,
            jsonObjectMode = true,
            maxCompletionTokens = 2_800,
            temperature = 0.15
        )
        val plan = GroqCodec.parseStage1Plan(res.text)
        return plan to res
    }

    private fun buildFailedEditsText(
        failedEdits: List<GroqFailedEdit>,
        validation: CodeValidationResult?,
        rootCauses: List<GroqRootCause>,
        noEditsAtAll: Boolean
    ): String {
        val parts = mutableListOf<String>()
        if (noEditsAtAll && rootCauses.isNotEmpty()) {
            parts.add("Stage 1 produced 0 edits despite ${rootCauses.size} root cause(s): " + rootCauses.joinToString("; ") { "${it.id}(${it.element}): ${it.fix}" })
        }
        failedEdits.forEach { fe ->
            parts.add("rc=${fe.edit.rc.ifBlank { "?" }} (${fe.reason}): find=${fe.edit.find.take(160)}")
        }
        if (validation != null && !validation.isValid) {
            parts.add("CodeValidator: ${validation.summaryMessage}")
        }
        if (validation != null && !validation.warning.isNullOrBlank()) {
            parts.add("CodeValidator warning: ${validation.warning}")
        }
        return parts.joinToString("\n").ifBlank { "Validation incomplete after initial edits." }
    }

    private fun buildRemainingIssuesText(
        validation: CodeValidationResult?,
        rootCauses: List<GroqRootCause>,
        fallbackReason: String
    ): String {
        val parts = mutableListOf<String>()
        if (validation != null) {
            parts.add(validation.summaryMessage)
            validation.warning?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        }
        if (rootCauses.isNotEmpty()) {
            parts.add("Root causes: " + rootCauses.joinToString("; ") { "${it.id} [${it.type}] ${it.element}: ${it.evidence}" })
        }
        if (fallbackReason.isNotBlank()) parts.add("Note: $fallbackReason")
        return parts.joinToString(" | ").ifBlank { "Ensure exact-once unique patch targets and valid deterministic HTML." }
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
        temperature: Double = 0.2
    ): GroqCompletionResult {
        val cleanKey = GroqCodec.normalizeApiKey(apiKey)
        if (cleanKey.isEmpty()) {
            throw IllegalStateException("Please add your Groq API key first.")
        }

        val candidates = GroqCodec.buildCandidateModels(
            modelPref = modelPref,
            requiresVision = requiresVision,
            heavyTask = heavyTask,
            cooledDownModels = activeCooledModels()
        )
        val attemptedModels = mutableListOf<String>()
        var sawAnyHttpResponse = false
        var lastCategory = GroqFailureCategory.TRANSIENT_SERVER
        var lastRawMessage = ""
        var lastQuota: GroqQuotaReport? = null
        var lastCause: Throwable? = null

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
                val (text, quota) = callSingleChatCompletion(
                    cleanKey = cleanKey,
                    model = model,
                    prompt = prompt,
                    images = if (requiresVision) images else emptyList(),
                    systemInstruction = systemInstruction,
                    jsonObjectMode = jsonObjectMode,
                    maxCompletionTokens = maxCompletionTokens,
                    temperature = temperature,
                    connectTimeoutMs = connectTimeoutMs,
                    readTimeoutMs = readTimeoutMs
                )
                modelCooldownUntilMs.remove(model)
                recordQuota(cleanKey, quota)
                return GroqCompletionResult(text = text, modelUsed = model, quota = quota)
            } catch (ce: CancellationException) {
                throw ce
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
                    throw GroqQuotaExhaustedException(
                        GroqCodec.buildFriendlyErrorMessage(
                            lastCategory = category,
                            lastRawMessage = rawMsg,
                            attemptedModels = attemptedModels,
                            sawAnyHttpResponse = sawAnyHttpResponse,
                            quota = lastQuota
                        ),
                        httpCode = httpCode ?: 429,
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
                            quota = lastQuota
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

        val friendly = GroqCodec.buildFriendlyErrorMessage(
            lastCategory = lastCategory,
            lastRawMessage = lastRawMessage,
            attemptedModels = attemptedModels,
            sawAnyHttpResponse = sawAnyHttpResponse,
            quota = lastQuota
        )
        throw IllegalStateException(friendly, lastCause)
    }

    private fun callSingleChatCompletion(
        cleanKey: String,
        model: String,
        prompt: String,
        images: List<GroqImageInput>,
        systemInstruction: String?,
        jsonObjectMode: Boolean,
        maxCompletionTokens: Int?,
        temperature: Double,
        connectTimeoutMs: Int,
        readTimeoutMs: Int
    ): Pair<String, GroqQuotaReport?> {
        val payload = GroqCodec.buildChatCompletionJson(
            prompt = prompt,
            images = images,
            systemInstruction = systemInstruction,
            model = model,
            jsonObjectMode = jsonObjectMode,
            maxCompletionTokens = maxCompletionTokens,
            temperature = temperature
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
            val quota = GroqCodec.parseQuotaHeaders(conn.headerFields, code)
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

            return GroqCodec.extractResponseText(body, quota) to quota
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

    private const val PROMPT_C1_SYSTEM = """You are a QA inspector for deterministic 1920x1080 HTML reels rendered frame by frame. Reply with ONE JSON object only. No prose, no markdown.

Renderer rules: everything is a pure function of t inside RAW.seek(t). Forbidden: CSS transition/@keyframes/animation, rAF, setTimeout, setInterval, Date.now, performance.now, Math.random, video.play(), external URLs, innerHTML or append inside seek. Safe area: x 120..1800, y 70..930. Columns: left x 140..1040, right x 1160..1760. Card brackets extend 34 px outside the card. The last scene must be inclusive of t = duration. Engine types: ghost, kicker, sub, pill, headline, chips, card, row, phone, buttons, comment, round.

Output schema:
{"rootCauses":[{"id":"R1","type":"overflow|collision|contrast|blank|runtime|determinism|forbidden|asset|sync","t":[seconds],"el":"scene id or selector","evidence":"max 20 words","fix":"max 20 words"}],
 "edits":[{"rc":"R1","find":"verbatim substring from EXCERPT, max 300 chars, unique","replace":"new text"}],
 "needsFull":false,"reason":""}

Rules: max 12 edits. Prefer fixing a shared cause once. Never edit working scenes. `find` must be copied exactly from the excerpt without line numbers. Use measured numbers from the excerpt, not guesses. If edits cannot fix it, set needsFull true and give a one-line reason."""

    private const val PROMPT_C2_SYSTEM = """You repair failed patches for a deterministic 1920x1080 reel. Reply with ONE JSON object only.

Schema: {"edits":[{"rc":"R1","find":"verbatim, unique","replace":"..."}]}
Or, only if the whole HTML is under 12 KB and no patch can work: {"html":"<!DOCTYPE html>..."}

Rules: copy `find` exactly from the WINDOWS below without line numbers (whitespace included); make each one unique by adding neighbouring text. Keep every replacement minimal. Do not introduce any forbidden API, external URL or non-bundled font. Do not touch anything not named in FAILED."""
}
