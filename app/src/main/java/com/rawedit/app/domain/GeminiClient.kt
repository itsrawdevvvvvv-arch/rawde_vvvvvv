package com.rawedit.app.domain

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.rawedit.app.data.local.LocalAssetEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
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

object GeminiClient {

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

    suspend fun testConnection(apiKey: String): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val t0 = System.currentTimeMillis()
            callInteraction(
                apiKey = apiKey,
                prompt = "Reply with the single word READY.",
                thinkingLevel = "low",
                systemInstruction = "Output only the word READY."
            )
            (System.currentTimeMillis() - t0).coerceAtLeast(1L)
        }
    }

    suspend fun generatePalette(
        apiKey: String,
        nextNumber: Int,
        previousMood: String? = null
    ): Result<PaletteState> = withContext(Dispatchers.IO) {
        runCatching {
            val themeHint = PALETTE_THEMES[Random.nextInt(PALETTE_THEMES.size)]
            val seed = Random.nextInt(100_000, 999_999)
            val prevNote = previousMood?.takeIf { it.isNotBlank() }?.let { "Different from previous vibe: $it." } ?: ""
            val prompt = """
                Generate a completely original, curated 3-color editorial palette for a kinetic-typography video edit (variation seed: $seed, aesthetic direction: "$themeHint"). $prevNote
                Choose 3 distinct harmonious hex colors usable together as background, dominant display type, and accent. Avoid near-black muddy duplicates.
                Respond with JSON matching the requested schema:
                - colors: exactly 3 items, each with "hex" (#XXXXXX) and "name" (2 lowercase words)
                - mood: 2-4 word vibe
                - pairing: one concise sentence explaining background vs headline vs accent usage
            """.trimIndent()

            val rawText = callInteraction(
                apiKey = apiKey,
                prompt = prompt,
                thinkingLevel = "low",
                systemInstruction = "You are a bold editorial color director. Always respond with valid JSON matching the schema.",
                jsonSchema = GeminiInteractionsCodec.PALETTE_SCHEMA
            )
            val obj = JSONObject(GeminiInteractionsCodec.stripJsonFences(rawText))
            val arr = obj.optJSONArray("colors")
                ?: throw IllegalStateException("Gemini palette did not contain colors array.")
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
            PaletteState(
                number = nextNumber.coerceAtLeast(1),
                mood = obj.optString("mood", "Editorial Motion").trim().ifBlank { "Editorial Motion" },
                pairing = obj.optString(
                    "pairing",
                    "Use 01 & 03 for clean contrast with 02 for kinetic highlight pills."
                ).trim(),
                colors = swatches
            )
        }
    }

    suspend fun describeAsset(
        apiKey: String,
        asset: LocalAssetEntity,
        imageFile: File
    ): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        runCatching {
            val (b64, mime) = encodeFileForVision(imageFile, maxDim = 960)
            val isWebShot = asset.description.contains("Interactive UI targets", ignoreCase = true)
            val prompt = when {
                asset.kind == LocalAssetEntity.KIND_CLIP -> {
                    "This 3-frame storyboard is sampled at 10%, 50%, 85% from the video clip ${asset.relativePath} (${asset.width}x${asset.height}, ${String.format(Locale.US, "%.1f", asset.durationSec)}s). Return JSON with keys: \"description\" (1 concise sentence describing visual motion/content across the clip) and \"safeZone\" (1 concise sentence stating where kinetic overlay typography can sit safely with high contrast)."
                }
                isWebShot -> {
                    "Analyze this tutorial UI screenshot (${asset.relativePath}, ${asset.width}x${asset.height}). Extracted UI context: ${asset.description.take(700)}. Return JSON with keys: \"description\" (1-2 concise sentences describing the UI screen AND preserving key target [x,y,wxh] boxes for tutorial cursor/highlight animation) and \"safeZone\" (1 concise sentence stating which region is safest for kinetic titles without covering interactive controls)."
                }
                else -> {
                    val label = if (asset.kind == LocalAssetEntity.KIND_CUTOUT) "PNG cutout" else "image"
                    "Analyze this $label (${asset.relativePath}, ${asset.width}x${asset.height}). Return JSON with keys: \"description\" (1 concise sentence describing visual subject and lighting/palette) and \"safeZone\" (1 concise sentence stating where kinetic overlay text can be placed cleanly without obscuring the subject)."
                }
            }

            val rawText = callInteraction(
                apiKey = apiKey,
                prompt = prompt,
                images = listOf(InteractionImageInput(base64Data = b64, mimeType = mime)),
                thinkingLevel = "low",
                systemInstruction = "Respond strictly with JSON matching the schema.",
                jsonSchema = GeminiInteractionsCodec.DESCRIBE_ASSET_SCHEMA
            )
            val obj = JSONObject(GeminiInteractionsCodec.stripJsonFences(rawText))
            val desc = obj.optString("description", asset.description).trim().ifBlank { asset.description }
            val safe = obj.optString("safeZone", asset.safeZone).trim().ifBlank { asset.safeZone }
            desc to safe
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
        runCatching {
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

                Rules:
                - status: "OK" or "ADJUST"
                - diagnosis: 1 concise honest sentence summarizing whether clipping or accidental erasure was seen and what you fixed (or 'Cutout edges and interior details look clean.')
                - For isSvg=true, keep recommendedTolerance=0 and focus extraPad* (0.04..0.16) if strokes/shadows appear tight at edges.
                - If inner light shapes or text were accidentally removed on a raster logo/icon, lower recommendedTolerance (6..20). Otherwise keep recommendedTolerance=$currentTolerance and extraPad*=0.0.
                - Set keepOriginal=true ONLY if background removal destroyed a complex UI card, photo, or non-transparent scene that looks better un-keyed.
            """.trimIndent()

            val rawText = callInteraction(
                apiKey = apiKey,
                prompt = prompt,
                images = listOf(InteractionImageInput(base64Data = b64, mimeType = mime)),
                thinkingLevel = "low",
                systemInstruction = "Respond strictly with JSON matching the schema.",
                jsonSchema = GeminiInteractionsCodec.AUDIT_CUTOUT_SCHEMA
            )
            val obj = JSONObject(GeminiInteractionsCodec.stripJsonFences(rawText))
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

            CutoutAuditResult(
                status = if (statusRaw == "ADJUST" || hasDelta) "ADJUST" else "OK",
                diagnosis = obj.optString("diagnosis", "Cutout edges and interior details verified by Gemini.")
                    .trim()
                    .ifBlank { "Cutout verified by Gemini." },
                keepOriginal = keepOrig,
                recommendedTolerance = recTol,
                extraPadTop = exT,
                extraPadRight = exR,
                extraPadBottom = exB,
                extraPadLeft = exL,
                needsReprocess = hasDelta
            )
        }
    }

    suspend fun reviewContactSheet(
        apiKey: String,
        sheetImages: List<File>,
        htmlCode: String,
        frameCount: Int
    ): Result<SheetReviewResult> = withContext(Dispatchers.IO) {
        runCatching {
            val prompt = """
                You are inspecting a rendered video contact sheet covering $frameCount frames for a 1920x1080 kinetic typography reel.
                Inspect every frame (#01 to #$frameCount) for: clipped/overflowing text outside 90% safe margins, overlapping text & cards, unreadable low contrast, blank scenes, or out-of-bounds elements.
                Also inspect the HTML source below:
                ${htmlCode.take(65_000)}

                Return JSON with:
                - summary: short overall diagnostic sentence
                - issues: array of visual defects, each with "frame" (e.g. "#09"), "time" (e.g. "t=4.00s"), "problem", and "fix" (exact CSS/JS adjustment needed)
            """.trimIndent()

            val encodedImages = sheetImages.take(2).mapNotNull { file ->
                if (!file.exists()) return@mapNotNull null
                val (b64, mime) = encodeFileForVision(file, maxDim = 1400)
                InteractionImageInput(base64Data = b64, mimeType = mime)
            }
            val rawText = callInteraction(
                apiKey = apiKey,
                prompt = prompt,
                images = encodedImages,
                thinkingLevel = "medium",
                systemInstruction = "Respond strictly with JSON matching the schema.",
                jsonSchema = GeminiInteractionsCodec.REVIEW_SHEET_SCHEMA
            )
            val obj = JSONObject(GeminiInteractionsCodec.stripJsonFences(rawText))
            val summary = obj.optString("summary", "Gemini frame inspection finished.").trim()
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
            SheetReviewResult(summary = summary, issues = issues)
        }
    }

    suspend fun autoFixHtmlWithSheet(
        apiKey: String,
        sheetImages: List<File>,
        htmlCode: String,
        issuesSummary: String,
        durationSec: Double
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
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
                InteractionImageInput(base64Data = b64, mimeType = mime)
            }
            val rawText = callInteraction(
                apiKey = apiKey,
                prompt = prompt,
                images = encodedImages,
                thinkingLevel = "medium",
                systemInstruction = "Output only a complete, valid self-contained HTML document from <!DOCTYPE html> to </html> with no markdown fences or explanation."
            )
            CodeValidator.unwrapMarkdownFences(rawText)
        }
    }

    private fun callInteraction(
        apiKey: String,
        prompt: String,
        images: List<InteractionImageInput> = emptyList(),
        thinkingLevel: String = "medium",
        systemInstruction: String? = null,
        jsonSchema: JsonObject? = null
    ): String {
        val cleanKey = GeminiInteractionsCodec.normalizeApiKey(apiKey)
        if (cleanKey.isEmpty()) throw IllegalStateException("Google AI Studio API key is missing.")

        val payload = GeminiInteractionsCodec.buildRequestJson(
            prompt = prompt,
            images = images,
            thinkingLevel = thinkingLevel,
            systemInstruction = systemInstruction,
            jsonSchema = jsonSchema
        )

        val endpoint = URL(GeminiInteractionsCodec.INTERACTIONS_URL)
        val conn = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("x-goog-api-key", cleanKey)
        }

        try {
            val bytes = payload.toByteArray(Charsets.UTF_8)
            conn.outputStream.use { os -> os.write(bytes) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

            if (code !in 200..299) {
                throw IllegalStateException(GeminiInteractionsCodec.extractErrorMessage(body, code))
            }

            return GeminiInteractionsCodec.extractResponseText(body)
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
