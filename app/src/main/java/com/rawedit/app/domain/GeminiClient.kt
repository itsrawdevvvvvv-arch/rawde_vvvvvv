package com.rawedit.app.domain

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.rawedit.app.data.local.LocalAssetEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
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

    private const val MODEL_ID = "gemini-2.0-flash"

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
            val parts = JSONArray().apply {
                put(JSONObject().put("text", "Reply with the single word READY"))
            }
            callGenerateContent(
                apiKey = apiKey,
                parts = parts,
                temperature = 0.0,
                jsonMode = false
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
                Generate a completely original, curated 3-color editorial palette for a kinetic-typography video edit (seed: $seed, direction: "$themeHint"). $prevNote
                Choose 3 distinct harmonious hex colors usable together as background, dominant display type, and accent. Avoid near-black muddy duplicates.
                Return strict JSON only:
                {
                  "colors": [
                    {"hex": "#XXXXXX", "name": "two word lowercase name"},
                    {"hex": "#XXXXXX", "name": "two word lowercase name"},
                    {"hex": "#XXXXXX", "name": "two word lowercase name"}
                  ],
                  "mood": "2-4 word vibe",
                  "pairing": "One concise sentence explaining background vs headline vs accent usage"
                }
            """.trimIndent()

            val parts = JSONArray().apply {
                put(JSONObject().put("text", prompt))
            }
            val rawText = callGenerateContent(
                apiKey = apiKey,
                parts = parts,
                temperature = 1.35,
                jsonMode = true
            )
            val obj = JSONObject(stripJsonFences(rawText))
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
                    "This 3-frame storyboard is sampled at 10%, 50%, 85% from the video clip ${asset.relativePath} (${asset.width}x${asset.height}, ${String.format(Locale.US, "%.1f", asset.durationSec)}s). Return strict JSON with keys: \"description\" (1 concise sentence describing visual motion/content across the clip) and \"safeZone\" (1 concise sentence stating where kinetic overlay typography can sit safely with high contrast)."
                }
                isWebShot -> {
                    "Analyze this tutorial UI screenshot (${asset.relativePath}, ${asset.width}x${asset.height}). Extracted UI context: ${asset.description.take(700)}. Return strict JSON with keys: \"description\" (1-2 concise sentences describing the UI screen AND preserving key target [x,y,wxh] boxes for tutorial cursor/highlight animation) and \"safeZone\" (1 concise sentence stating which region is safest for kinetic titles without covering interactive controls)."
                }
                else -> {
                    val label = if (asset.kind == LocalAssetEntity.KIND_CUTOUT) "PNG cutout" else "image"
                    "Analyze this $label (${asset.relativePath}, ${asset.width}x${asset.height}). Return strict JSON with keys: \"description\" (1 concise sentence describing visual subject and lighting/palette) and \"safeZone\" (1 concise sentence stating where kinetic overlay text can be placed cleanly without obscuring the subject)."
                }
            }

            val parts = JSONArray().apply {
                put(JSONObject().put("text", prompt))
                put(
                    JSONObject().put(
                        "inlineData",
                        JSONObject().apply {
                            put("mimeType", mime)
                            put("data", b64)
                        }
                    )
                )
            }
            val rawText = callGenerateContent(
                apiKey = apiKey,
                parts = parts,
                temperature = 0.35,
                jsonMode = true
            )
            val obj = JSONObject(stripJsonFences(rawText))
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

                Return strict JSON only:
                {
                  "status": "OK" or "ADJUST",
                  "diagnosis": "1 concise honest sentence summarizing whether clipping or accidental erasure was seen and what you fixed (or 'Cutout edges and interior details look clean.')",
                  "keepOriginal": false,
                  "recommendedTolerance": $currentTolerance,
                  "extraPadTop": 0.0,
                  "extraPadRight": 0.0,
                  "extraPadBottom": 0.0,
                  "extraPadLeft": 0.0
                }
                Rules:
                - For isSvg=true, keep tolerance=0 and focus extraPad* (0.04..0.16) if strokes/shadows appear tight at edges.
                - If inner light shapes or text were accidentally removed on a raster logo/icon, lower recommendedTolerance (6..20).
                - Set keepOriginal=true ONLY if background removal destroyed a complex UI card, photo, or non-transparent scene that looks better un-keyed.
            """.trimIndent()

            val parts = JSONArray().apply {
                put(JSONObject().put("text", prompt))
                put(
                    JSONObject().put(
                        "inlineData",
                        JSONObject().apply {
                            put("mimeType", mime)
                            put("data", b64)
                        }
                    )
                )
            }
            val rawText = callGenerateContent(
                apiKey = apiKey,
                parts = parts,
                temperature = 0.2,
                jsonMode = true
            )
            val obj = JSONObject(stripJsonFences(rawText))
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

                Return strict JSON only:
                {
                  "summary": "Short overall diagnostic sentence",
                  "issues": [
                    {
                      "frame": "#09",
                      "time": "t=4.00s",
                      "problem": "Concise visual defect found",
                      "fix": "Exact CSS/JS adjustment needed"
                    }
                  ]
                }
            """.trimIndent()

            val parts = JSONArray().apply {
                put(JSONObject().put("text", prompt))
                sheetImages.take(2).forEach { file ->
                    if (file.exists()) {
                        val (b64, mime) = encodeFileForVision(file, maxDim = 1400)
                        put(
                            JSONObject().put(
                                "inlineData",
                                JSONObject().apply {
                                    put("mimeType", mime)
                                    put("data", b64)
                                }
                            )
                        )
                    }
                }
            }
            val rawText = callGenerateContent(
                apiKey = apiKey,
                parts = parts,
                temperature = 0.25,
                jsonMode = true
            )
            val obj = JSONObject(stripJsonFences(rawText))
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

            val parts = JSONArray().apply {
                put(JSONObject().put("text", prompt))
                sheetImages.take(2).forEach { file ->
                    if (file.exists()) {
                        val (b64, mime) = encodeFileForVision(file, maxDim = 1400)
                        put(
                            JSONObject().put(
                                "inlineData",
                                JSONObject().apply {
                                    put("mimeType", mime)
                                    put("data", b64)
                                }
                            )
                        )
                    }
                }
            }
            val rawText = callGenerateContent(
                apiKey = apiKey,
                parts = parts,
                temperature = 0.2,
                jsonMode = false
            )
            CodeValidator.unwrapMarkdownFences(rawText)
        }
    }

    private fun callGenerateContent(
        apiKey: String,
        parts: JSONArray,
        temperature: Double,
        jsonMode: Boolean
    ): String {
        val cleanKey = apiKey.trim()
        if (cleanKey.isEmpty()) throw IllegalStateException("Google AI Studio API key is missing.")

        val encodedKey = URLEncoder.encode(cleanKey, "UTF-8")
        val endpoint = URL("https://generativelanguage.googleapis.com/v1beta/models/$MODEL_ID:generateContent?key=$encodedKey")

        val genConfig = JSONObject().apply {
            put("temperature", temperature)
            if (jsonMode) {
                put("responseMimeType", "application/json")
            }
        }
        val payload = JSONObject().apply {
            put(
                "contents",
                JSONArray().apply {
                    put(JSONObject().put("parts", parts))
                }
            )
            put("generationConfig", genConfig)
        }

        val conn = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 45_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        }

        try {
            val bytes = payload.toString().toByteArray(Charsets.UTF_8)
            conn.outputStream.use { os -> os.write(bytes) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

            if (code !in 200..299) {
                val errMsg = runCatching {
                    JSONObject(body).optJSONObject("error")?.optString("message")
                }.getOrNull()
                throw IllegalStateException(errMsg?.takeIf { it.isNotBlank() } ?: "Gemini HTTP $code")
            }

            val root = JSONObject(body)
            val candidates = root.optJSONArray("candidates")
                ?: throw IllegalStateException("Gemini returned no candidates.")
            val first = candidates.optJSONObject(0)
                ?: throw IllegalStateException("Empty Gemini response.")
            val respParts = first.optJSONObject("content")?.optJSONArray("parts")
                ?: throw IllegalStateException("Gemini returned empty parts.")

            val textOut = buildString {
                for (i in 0 until respParts.length()) {
                    append(respParts.optJSONObject(i)?.optString("text", "").orEmpty())
                }
            }.trim()
            if (textOut.isEmpty()) throw IllegalStateException("Gemini returned empty text.")
            return textOut
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

    private fun stripJsonFences(raw: String): String {
        val t = raw.trim()
        return when {
            t.startsWith("```json", ignoreCase = true) ->
                t.removePrefix("```json").removePrefix("```JSON").removeSuffix("```").trim()
            t.startsWith("```") ->
                t.removePrefix("```").removeSuffix("```").trim()
            else -> t
        }
    }
}
