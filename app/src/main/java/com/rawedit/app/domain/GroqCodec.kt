package com.rawedit.app.domain

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

data class GroqImageInput(
    val base64Data: String,
    val mimeType: String
)

data class GroqModelOption(
    val prefId: String,
    val shortLabel: String,
    val badgeLabel: String,
    val detail: String
)

enum class GroqFailureCategory {
    FATAL_AUTH,
    QUOTA_OR_CREDITS_EXHAUSTED,
    RATE_OR_OVERLOAD,
    MODEL_UNAVAILABLE,
    PAYLOAD_TOO_LARGE,
    NETWORK_TIMEOUT,
    NETWORK_UNREACHABLE,
    TRANSIENT_SERVER
}

enum class GroqLimitKind {
    NONE,
    COOLDOWN,
    DAILY,
    CREDITS
}

data class GroqLimit(
    val kind: GroqLimitKind,
    val waitSec: Double? = null,
    val code: String? = null
)

data class GroqModelBucket(
    val remainingTokens: Long?,
    val resetTokensAtMs: Long,
    val remainingRequests: Long?,
    val limitTokens: Long? = null,
    val limitRequests: Long? = null,
    val resetRequestsAtMs: Long = 0L
)

sealed class GroqUi {
    object Ready : GroqUi()
    data class Cooling(val untilMs: Long) : GroqUi()
    data class HardBlocked(val kind: GroqLimitKind, val untilMs: Long) : GroqUi()
}

data class GroqQuotaReport(
    val remainingRequests: Int = -1,
    val limitRequests: Int = -1,
    val resetRequests: String = "",
    val remainingTokens: Int = -1,
    val limitTokens: Int = -1,
    val resetTokens: String = "",
    val retryAfterSec: Int = 0,
    val creditsDepleted402: Boolean = false,
    val updatedAtMs: Long = 0L
) {
    val hasTelemetry: Boolean
        get() = limitRequests > 0 ||
            remainingRequests >= 0 ||
            limitTokens > 0 ||
            remainingTokens >= 0 ||
            creditsDepleted402

    val isExhausted: Boolean
        get() = creditsDepleted402 ||
            (limitRequests > 0 && remainingRequests == 0)

    val isCooling: Boolean
        get() = !isExhausted && limitTokens > 0 && remainingTokens == 0

    val requestsFraction: Float
        get() = if (limitRequests > 0 && remainingRequests >= 0) {
            (remainingRequests.toFloat() / limitRequests.toFloat()).coerceIn(0f, 1f)
        } else {
            1f
        }

    val tokensFraction: Float
        get() = if (limitTokens > 0 && remainingTokens >= 0) {
            (remainingTokens.toFloat() / limitTokens.toFloat()).coerceIn(0f, 1f)
        } else {
            1f
        }

    val primaryFraction: Float
        get() {
            if (isExhausted) return 0f
            val hasReq = limitRequests > 0 && remainingRequests >= 0
            val hasTok = limitTokens > 0 && remainingTokens >= 0
            return when {
                hasReq && hasTok -> minOf(requestsFraction, tokensFraction)
                hasReq -> requestsFraction
                hasTok -> tokensFraction
                else -> 1f
            }
        }

    val remainingPercent: Int
        get() {
            if (isExhausted) return 0
            return (primaryFraction * 100f).roundToInt().coerceIn(if (isCooling) 0 else 1, 100)
        }

    val isLow: Boolean
        get() {
            if (isExhausted) return false
            val lowReq = limitRequests > 0 && remainingRequests >= 0 && requestsFraction <= 0.12f
            val lowTok = limitTokens > 0 && remainingTokens >= 0 && tokensFraction <= 0.12f
            return lowReq || lowTok
        }

    fun formattedRequestsSummary(): String {
        val nf = NumberFormat.getIntegerInstance(Locale.US)
        val main = when {
            remainingRequests >= 0 && limitRequests > 0 ->
                "${nf.format(remainingRequests)} / ${nf.format(limitRequests)} req"
            remainingRequests >= 0 ->
                "${nf.format(remainingRequests)} req"
            else -> "-- req"
        }
        return if (resetRequests.isNotBlank()) "$main (resets $resetRequests)" else main
    }

    fun formattedTokensSummary(): String {
        val main = when {
            remainingTokens >= 0 && limitTokens > 0 ->
                "${GroqCodec.formatCompactCount(remainingTokens)} / ${GroqCodec.formatCompactCount(limitTokens)} tok"
            remainingTokens >= 0 ->
                "${GroqCodec.formatCompactCount(remainingTokens)} tok"
            else -> "-- tok"
        }
        return if (resetTokens.isNotBlank()) "$main (resets $resetTokens)" else main
    }

    fun bannerLabel(): String = GroqCodec.formatQuotaBannerLabel(
        remainingRequests = remainingRequests,
        limitRequests = limitRequests,
        remainingTokens = remainingTokens,
        limitTokens = limitTokens,
        exhausted = isExhausted,
        coolingReset = if (isCooling) resetTokens else ""
    )
}

data class GroqParsedError(
    val message: String,
    val type: String?,
    val code: String?
)

class GroqApiException(
    val httpCode: Int?,
    override val message: String,
    val errorCode: String? = null,
    val errorType: String? = null,
    val quota: GroqQuotaReport? = null,
    val headers: Map<String, String> = emptyMap()
) : IllegalStateException(message)

class GroqQuotaExhaustedException(
    override val message: String,
    val httpCode: Int? = 429,
    val quota: GroqQuotaReport? = null
) : IllegalStateException(message)

class GroqCooldownException(
    val waitSec: Int,
    override val message: String = "Rate window cooling (${waitSec}s)"
) : IllegalStateException(message)

class GroqHardLimitException(
    val kind: GroqLimitKind,
    val resetAtMs: Long,
    val quota: GroqQuotaReport? = null,
    override val message: String = if (kind == GroqLimitKind.CREDITS) {
        "Groq credits exhausted. Switch to another Groq API key."
    } else {
        "Groq daily limit reached. Resets in ${GroqCodec.formatDurationShort(resetAtMs - System.currentTimeMillis())}."
    }
) : IllegalStateException(message)

class GroqPromptTooLargeException(
    val estimatedTokens: Int,
    val capTokens: Int,
    override val message: String = "Too many flags to send (~$estimatedTokens tok > $capTokens cap); fix top scenes first."
) : IllegalStateException(message)

data class GroqRootCause(
    val id: String,
    val type: String,
    val timesSec: List<Double>,
    val element: String,
    val evidence: String,
    val fix: String
)

data class GroqPatchEdit(
    val rc: String,
    val find: String,
    val replace: String
)

data class GroqStage1Plan(
    val rootCauses: List<GroqRootCause>,
    val edits: List<GroqPatchEdit>,
    val needsFull: Boolean,
    val reason: String
)

data class GroqReviewIssue(
    val f: Int,
    val type: String,
    val where: String,
    val sev: String,
    val fix: String
)

data class GroqReviewResult(
    val ok: Boolean,
    val issues: List<GroqReviewIssue>,
    val htmlHash: Int,
    val atMs: Long,
    val parseFailed: Boolean = false
)

data class GroqPlanResult(
    val edits: List<GroqPatchEdit>,
    val skippedCount: Int,
    val structural: Boolean
)

data class GroqFailedEdit(
    val edit: GroqPatchEdit,
    val reason: String,
    val matchCount: Int
)

data class GroqPatchApplyOutcome(
    val updatedHtml: String,
    val appliedCount: Int,
    val failedEdits: List<GroqFailedEdit>
)

object GroqCodec {

    const val API_ORIGIN = "https://api.groq.com"
    const val CONSOLE_ORIGIN = "https://console.groq.com"
    const val CHAT_COMPLETIONS_URL = "$API_ORIGIN/openai/v1/chat/completions"
    const val MODELS_URL = "$API_ORIGIN/openai/v1/models"
    const val KEYS_CONSOLE_URL = "$CONSOLE_ORIGIN/keys"
    const val LIMITS_CONSOLE_URL = "$CONSOLE_ORIGIN/settings/limits"

    const val MAX_IMAGES_PER_REQUEST = 5

    const val PREF_AUTO = "auto"
    const val MODEL_GPT_OSS_120B = "openai/gpt-oss-120b"
    const val MODEL_GPT_OSS_20B = "openai/gpt-oss-20b"
    const val MODEL_QWEN38_27B = "qwen/qwen3.8-27b"
    const val MODEL_MUSE_SPARK = "meta-models/muse-spark-open"
    const val MODEL_LLAMA4_SCOUT = "meta-llama/llama-4-scout-17b-16e-instruct"
    const val MODEL_LLAMA4_MAVERICK = "meta-llama/llama-4-maverick-17b-128e-instruct"
    const val MODEL_LLAMA33_70B = "llama-3.3-70b-versatile"
    const val MODEL_QWEN3_32B = "qwen/qwen3-32b"
    const val MODEL_LLAMA31_8B = "llama-3.1-8b-instant"

    private val VISION_MODELS = setOf(
        MODEL_QWEN38_27B,
        MODEL_MUSE_SPARK,
        MODEL_LLAMA4_SCOUT,
        MODEL_LLAMA4_MAVERICK
    )

    val MODEL_OPTIONS: List<GroqModelOption> = listOf(
        GroqModelOption(
            prefId = PREF_AUTO,
            shortLabel = "Auto",
            badgeLabel = "GROQ AUTO ROUTE",
            detail = "Matches live models from /v1/models: Qwen 3.8 Vision (up to 3 frames) for vision, and GPT-OSS 120B / 20B for palettes & HTML patches."
        ),
        GroqModelOption(
            prefId = MODEL_GPT_OSS_120B,
            shortLabel = "GPT-OSS 120B",
            badgeLabel = "GROQ GPT-OSS 120B",
            detail = "OpenAI flagship 120B reasoning & coding route on Groq (~500 t/s) for Studio Palettes and Stage 2 HTML patch repair."
        ),
        GroqModelOption(
            prefId = MODEL_GPT_OSS_20B,
            shortLabel = "GPT-OSS 20B",
            badgeLabel = "GROQ GPT-OSS 20B",
            detail = "Ultra-fast 1,000 t/s compact OpenAI MoE route to save quota on quick checks, palettes, and JSON tasks."
        ),
        GroqModelOption(
            prefId = MODEL_QWEN38_27B,
            shortLabel = "Qwen 3.8 27B",
            badgeLabel = "GROQ QWEN 3.8 VISION",
            detail = "Multimodal Vision + Text model (max 3 images/call) for Cutout Audit, Asset Notes, and Frame Inspection."
        ),
        GroqModelOption(
            prefId = MODEL_LLAMA33_70B,
            shortLabel = "Llama 3.3 70B",
            badgeLabel = "GROQ LLAMA 3.3 70B",
            detail = "Legacy Llama 3.3 70B text route (enterprise / custom tiers where still active)."
        ),
        GroqModelOption(
            prefId = MODEL_LLAMA4_SCOUT,
            shortLabel = "Llama 4 Scout",
            badgeLabel = "GROQ LLAMA 4 SCOUT",
            detail = "Legacy Llama 4 Scout multimodal route (max 5 images where still active)."
        )
    )

    private val SUPPORTED_MODELS = setOf(
        PREF_AUTO,
        MODEL_GPT_OSS_120B,
        MODEL_GPT_OSS_20B,
        MODEL_QWEN38_27B,
        MODEL_MUSE_SPARK,
        MODEL_LLAMA4_SCOUT,
        MODEL_LLAMA4_MAVERICK,
        MODEL_LLAMA33_70B,
        MODEL_QWEN3_32B,
        MODEL_LLAMA31_8B
    )

    const val IMAGE_TOKENS = 2048

    private val THINK_BLOCK_REGEX = Regex("<think>[\\s\\S]*?</think>", RegexOption.IGNORE_CASE)
    private val SEARCH_REPLACE_BLOCK_REGEX = Regex(
        "<<<SEARCH\\s*\\r?\\n([\\s\\S]*?)\\r?\\n===REPLACE\\s*\\r?\\n([\\s\\S]*?)\\r?\\n>>>"
    )
    private val DURATION_REGEX = Regex("(\\d+(?:\\.\\d+)?)(ms|h|m|s)")
    private val LIMIT_CODE_REGEX = Regex("\\((TPM|RPM|RPD|TPD)\\)")
    private val TRY_AGAIN_REGEX = Regex("try again in\\s+([0-9hms.]+)", RegexOption.IGNORE_CASE)
    private val LINE_NO_PREFIX_REGEX = Regex("^\\s*\\d{1,5}\\s*[:|]\\s?")
    private val FORBIDDEN_EDIT_REGEX = Regex(
        "transition\\s*:|@keyframes|animation\\s*:|requestAnimationFrame|setTimeout|setInterval|Date\\.now|performance\\.now|Math\\.random|\\.play\\(\\)|<script[^>]*\\ssrc=|<link\\b|@import|fetch\\(|XMLHttpRequest|WebSocket|import\\(|window\\.open|https?://(?!www\\.w3\\.org/2000/svg)",
        RegexOption.IGNORE_CASE
    )

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun estTokens(text: String): Int = (text.length / 3.2).toInt() + 8

    fun parseDurationSec(raw: String?): Double? {
        if (raw.isNullOrBlank()) return null
        var total = 0.0
        var any = false
        DURATION_REGEX.findAll(raw.trim()).forEach { m ->
            any = true
            val v = m.groupValues[1].toDoubleOrNull() ?: 0.0
            total += when (m.groupValues[2]) {
                "ms" -> v / 1000.0
                "s" -> v
                "m" -> v * 60.0
                else -> v * 3600.0
            }
        }
        return if (any) total else null
    }

    fun formatDurationShort(msRemaining: Long): String {
        val secTotal = (msRemaining / 1000L).coerceAtLeast(1L)
        return when {
            secTotal < 60L -> "${secTotal}s"
            secTotal < 3600L -> "${secTotal / 60L}m"
            else -> {
                val h = secTotal / 3600L
                val m = (secTotal % 3600L) / 60L
                if (m > 0L) "${h}h${m}m" else "${h}h"
            }
        }
    }

    fun guardTokens(
        images: Int,
        vararg texts: String,
        maxCompletion: Int,
        cap: Int = 6500
    ): Int {
        val est = images * IMAGE_TOKENS + texts.sumOf { estTokens(it) } + maxCompletion
        if (est > cap) throw GroqPromptTooLargeException(est, cap)
        return est
    }

    fun headerFieldsToLowerMap(headerFields: Map<String?, List<String>>?): Map<String, String> {
        if (headerFields.isNullOrEmpty()) return emptyMap()
        val out = LinkedHashMap<String, String>(headerFields.size)
        for ((k, vals) in headerFields) {
            val key = k?.trim()?.lowercase(Locale.US) ?: continue
            val value = vals.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() } ?: continue
            out[key] = value
        }
        return out
    }

    fun readBucket(
        headerFields: Map<String?, List<String>>?,
        nowMs: Long = System.currentTimeMillis()
    ): GroqModelBucket? {
        val h = headerFieldsToLowerMap(headerFields)
        if (h.isEmpty()) return null
        val remTok = h["x-ratelimit-remaining-tokens"]?.toLongOrNull()
        val limTok = h["x-ratelimit-limit-tokens"]?.toLongOrNull()
        val rstTokSec = parseDurationSec(h["x-ratelimit-reset-tokens"]) ?: 0.0
        val remReq = h["x-ratelimit-remaining-requests"]?.toLongOrNull()
        val limReq = h["x-ratelimit-limit-requests"]?.toLongOrNull()
        val rstReqSec = parseDurationSec(h["x-ratelimit-reset-requests"]) ?: 0.0
        if (remTok == null && remReq == null && limTok == null && limReq == null) return null
        return GroqModelBucket(
            remainingTokens = remTok,
            resetTokensAtMs = nowMs + (rstTokSec * 1000.0).toLong(),
            remainingRequests = remReq,
            limitTokens = limTok,
            limitRequests = limReq,
            resetRequestsAtMs = nowMs + (rstReqSec * 1000.0).toLong()
        )
    }

    fun classifyLimit(status: Int?, body: String, headers: Map<String, String>): GroqLimit {
        val b = body.lowercase(Locale.US)
        if (status == 402 ||
            "insufficient_quota" in b ||
            "billing_hard_limit_reached" in b ||
            "spend limit" in b ||
            "spending limit" in b ||
            ("billing" in b && "limit" in b)
        ) {
            return GroqLimit(GroqLimitKind.CREDITS)
        }
        if (status != 429 && "rate_limit_exceeded" !in b && "rate limit" !in b) {
            return GroqLimit(GroqLimitKind.NONE)
        }

        val code = LIMIT_CODE_REGEX.find(body)?.groupValues?.get(1)
        val wait = headers["retry-after"]?.toDoubleOrNull()
            ?: TRY_AGAIN_REGEX.find(body)?.groupValues?.get(1)?.let(::parseDurationSec)
            ?: parseDurationSec(headers["x-ratelimit-reset-tokens"])
            ?: parseDurationSec(headers["x-ratelimit-reset-requests"])
        val dailyWords = "per day" in b ||
            "daily limit" in b ||
            "requests per day" in b ||
            "tokens per day" in b

        return when {
            code == "RPD" || code == "TPD" || dailyWords ->
                GroqLimit(
                    kind = GroqLimitKind.DAILY,
                    waitSec = parseDurationSec(headers["x-ratelimit-reset-requests"]) ?: wait ?: 3600.0,
                    code = code
                )
            code == "TPM" || code == "RPM" ->
                GroqLimit(GroqLimitKind.COOLDOWN, wait ?: 20.0, code)
            wait != null && wait < 120.0 ->
                GroqLimit(GroqLimitKind.COOLDOWN, wait, null)
            else ->
                GroqLimit(GroqLimitKind.DAILY, wait ?: 3600.0, null)
        }
    }

    fun isVisionModel(modelId: String): Boolean {
        if (modelId in VISION_MODELS) return true
        val lower = modelId.lowercase(Locale.US)
        return lower.contains("vision") ||
            lower.contains("qwen3.8") ||
            lower.contains("qwen3.6") ||
            lower.contains("muse-spark") ||
            lower.contains("llama-4")
    }

    fun maxImagesForModel(modelId: String): Int {
        val lower = modelId.lowercase(Locale.US)
        return when {
            lower.contains("qwen") -> 3
            else -> MAX_IMAGES_PER_REQUEST
        }
    }

    fun isGptOssModel(modelId: String): Boolean =
        modelId.lowercase(Locale.US).startsWith("openai/gpt-oss")

    fun isQwen3Model(modelId: String): Boolean =
        modelId.lowercase(Locale.US).startsWith("qwen/qwen3")

    fun parseAvailableChatModelIds(rawBody: String): Set<String> {
        val obj = runCatching { JSONObject(rawBody) }.getOrNull() ?: return emptySet()
        val arr = obj.optJSONArray("data") ?: return emptySet()
        val out = LinkedHashSet<String>()
        for (i in 0 until arr.length()) {
            val id = arr.optJSONObject(i)?.optString("id", "")?.trim().orEmpty()
            if (id.isEmpty()) continue
            val lower = id.lowercase(Locale.US)
            val isNonChat = lower.contains("whisper") ||
                lower.contains("orpheus") ||
                lower.contains("-tts") ||
                lower.contains("prompt-guard") ||
                lower.contains("safeguard") ||
                lower.contains("playai")
            if (!isNonChat) {
                out.add(id)
            }
        }
        return out
    }

    fun normalizeApiKey(rawKey: String): String {
        var t = rawKey.trim().removeSurrounding("\"").removeSurrounding("'")
        if (t.startsWith("Bearer ", ignoreCase = true)) {
            t = t.substring(7).trim()
        }
        return t.filterNot { it.isWhitespace() }
    }

    fun maskApiKey(rawKey: String): String {
        val clean = normalizeApiKey(rawKey)
        if (clean.isEmpty()) return ""
        if (clean.length <= 10) return "gsk_...${clean.takeLast(4)}"
        val prefix = clean.take(4)
        val suffix = clean.takeLast(4)
        return "$prefix...$suffix"
    }

    fun openUrlInChromeOrBrowser(context: Context, url: String) {
        val uri = Uri.parse(url)
        val chromeIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.android.chrome")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val openedChrome = runCatching {
            context.startActivity(chromeIntent)
        }.isSuccess
        if (!openedChrome) {
            val genericIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(genericIntent) }
        }
    }

    fun normalizeModelPref(rawPref: String): String {
        val cleaned = rawPref.trim().lowercase(Locale.US)
        return if (cleaned in SUPPORTED_MODELS) cleaned else PREF_AUTO
    }

    fun optionForPref(rawPref: String): GroqModelOption {
        val norm = normalizeModelPref(rawPref)
        return MODEL_OPTIONS.firstOrNull { it.prefId == norm } ?: MODEL_OPTIONS.first()
    }

    fun routeDisplayLabel(rawPref: String): String {
        return when (normalizeModelPref(rawPref)) {
            PREF_AUTO -> "Auto (Qwen 3.8 + GPT-OSS)"
            MODEL_GPT_OSS_120B -> "GPT-OSS 120B"
            MODEL_GPT_OSS_20B -> "GPT-OSS 20B"
            MODEL_QWEN38_27B -> "Qwen 3.8 27B"
            MODEL_MUSE_SPARK -> "Muse Spark"
            MODEL_LLAMA4_SCOUT -> "Llama 4 Scout"
            MODEL_LLAMA4_MAVERICK -> "Llama 4 Maverick"
            MODEL_LLAMA33_70B -> "Llama 3.3 70B"
            MODEL_QWEN3_32B -> "Qwen3 32B"
            MODEL_LLAMA31_8B -> "Llama 3.1 8B"
            else -> "Auto (Qwen 3.8 + GPT-OSS)"
        }
    }

    fun buildCandidateModels(
        modelPref: String,
        requiresVision: Boolean,
        heavyTask: Boolean,
        cooledDownModels: Set<String> = emptySet(),
        availableModels: Set<String> = emptySet()
    ): List<String> {
        val pref = normalizeModelPref(modelPref)
        val defaults = if (requiresVision) {
            when (pref) {
                MODEL_MUSE_SPARK -> listOf(
                    MODEL_MUSE_SPARK,
                    MODEL_QWEN38_27B,
                    MODEL_LLAMA4_SCOUT,
                    MODEL_LLAMA4_MAVERICK
                )
                MODEL_LLAMA4_SCOUT -> listOf(
                    MODEL_LLAMA4_SCOUT,
                    MODEL_QWEN38_27B,
                    MODEL_LLAMA4_MAVERICK,
                    MODEL_MUSE_SPARK
                )
                MODEL_LLAMA4_MAVERICK -> listOf(
                    MODEL_LLAMA4_MAVERICK,
                    MODEL_QWEN38_27B,
                    MODEL_LLAMA4_SCOUT,
                    MODEL_MUSE_SPARK
                )
                else -> listOf(
                    MODEL_QWEN38_27B,
                    MODEL_MUSE_SPARK,
                    MODEL_LLAMA4_SCOUT,
                    MODEL_LLAMA4_MAVERICK
                )
            }
        } else {
            when (pref) {
                MODEL_GPT_OSS_120B -> listOf(
                    MODEL_GPT_OSS_120B,
                    MODEL_GPT_OSS_20B,
                    MODEL_QWEN38_27B,
                    MODEL_LLAMA33_70B,
                    MODEL_LLAMA31_8B
                )
                MODEL_GPT_OSS_20B -> listOf(
                    MODEL_GPT_OSS_20B,
                    MODEL_GPT_OSS_120B,
                    MODEL_QWEN38_27B,
                    MODEL_LLAMA31_8B,
                    MODEL_LLAMA33_70B
                )
                MODEL_QWEN38_27B -> listOf(
                    MODEL_QWEN38_27B,
                    MODEL_GPT_OSS_120B,
                    MODEL_GPT_OSS_20B,
                    MODEL_LLAMA33_70B
                )
                MODEL_LLAMA33_70B -> listOf(
                    MODEL_LLAMA33_70B,
                    MODEL_GPT_OSS_120B,
                    MODEL_GPT_OSS_20B,
                    MODEL_QWEN38_27B,
                    MODEL_LLAMA31_8B
                )
                MODEL_LLAMA4_SCOUT -> listOf(
                    MODEL_LLAMA4_SCOUT,
                    MODEL_QWEN38_27B,
                    MODEL_GPT_OSS_120B,
                    MODEL_GPT_OSS_20B
                )
                MODEL_LLAMA4_MAVERICK -> listOf(
                    MODEL_LLAMA4_MAVERICK,
                    MODEL_QWEN38_27B,
                    MODEL_GPT_OSS_120B,
                    MODEL_GPT_OSS_20B
                )
                MODEL_QWEN3_32B -> listOf(
                    MODEL_QWEN3_32B,
                    MODEL_QWEN38_27B,
                    MODEL_GPT_OSS_120B,
                    MODEL_GPT_OSS_20B
                )
                MODEL_LLAMA31_8B -> listOf(
                    MODEL_LLAMA31_8B,
                    MODEL_GPT_OSS_20B,
                    MODEL_GPT_OSS_120B,
                    MODEL_QWEN38_27B
                )
                else -> if (heavyTask) {
                    listOf(
                        MODEL_GPT_OSS_120B,
                        MODEL_QWEN38_27B,
                        MODEL_GPT_OSS_20B,
                        MODEL_LLAMA33_70B,
                        MODEL_LLAMA31_8B
                    )
                } else {
                    listOf(
                        MODEL_GPT_OSS_20B,
                        MODEL_GPT_OSS_120B,
                        MODEL_QWEN38_27B,
                        MODEL_LLAMA31_8B,
                        MODEL_LLAMA33_70B
                    )
                }
            }
        }

        val discoveredExtras = if (availableModels.isEmpty()) {
            emptyList()
        } else if (requiresVision) {
            availableModels.filter { it !in defaults && isVisionModel(it) }
        } else {
            availableModels.filter { it !in defaults && !it.contains("compound", ignoreCase = true) }
        }

        val combined = if (availableModels.isEmpty()) {
            defaults
        } else {
            val activeDefaults = defaults.filter { it in availableModels }
            val inactiveDefaults = defaults.filter { it !in availableModels }
            val merged = (activeDefaults + discoveredExtras).distinct()
            if (merged.isNotEmpty()) merged else inactiveDefaults
        }

        if (cooledDownModels.isEmpty()) return combined
        return if (pref == PREF_AUTO) {
            combined.filter { it !in cooledDownModels } + combined.filter { it in cooledDownModels }
        } else {
            val primary = combined.first()
            val rest = combined.drop(1)
            listOf(primary) + rest.filter { it !in cooledDownModels } + rest.filter { it in cooledDownModels }
        }
    }

    fun buildChatCompletionJson(
        prompt: String,
        images: List<GroqImageInput> = emptyList(),
        systemInstruction: String? = null,
        model: String = MODEL_GPT_OSS_120B,
        jsonObjectMode: Boolean = false,
        maxCompletionTokens: Int? = null,
        temperature: Double = 0.2,
        heavyTask: Boolean = false,
        reasoningEffortOverride: String? = null
    ): String {
        val imageCap = maxImagesForModel(model)
        val boundedImages = images.take(imageCap)
        val baseSystem = systemInstruction?.trim().orEmpty()
        val finalSystem = if (jsonObjectMode && !baseSystem.contains("json", ignoreCase = true) && !prompt.contains("json", ignoreCase = true)) {
            if (baseSystem.isNotEmpty()) "$baseSystem\nRespond with valid JSON." else "Respond with valid JSON."
        } else {
            baseSystem
        }

        val messagesArray = buildJsonArray {
            if (finalSystem.isNotEmpty()) {
                add(
                    buildJsonObject {
                        put("role", "system")
                        put("content", finalSystem)
                    }
                )
            }
            if (boundedImages.isEmpty()) {
                add(
                    buildJsonObject {
                        put("role", "user")
                        put("content", prompt)
                    }
                )
            } else {
                add(
                    buildJsonObject {
                        put("role", "user")
                        put(
                            "content",
                            buildJsonArray {
                                add(
                                    buildJsonObject {
                                        put("type", "text")
                                        put("text", prompt)
                                    }
                                )
                                boundedImages.forEach { img ->
                                    val dataUri = "data:${img.mimeType};base64,${img.base64Data}"
                                    add(
                                        buildJsonObject {
                                            put("type", "image_url")
                                            put(
                                                "image_url",
                                                buildJsonObject {
                                                    put("url", dataUri)
                                                }
                                            )
                                        }
                                    )
                                }
                            }
                        )
                    }
                )
            }
        }

        val isOss = isGptOssModel(model)
        val isQwen = isQwen3Model(model)
        val effectiveMaxTokens = when {
            maxCompletionTokens == null -> null
            isOss -> maxOf(maxCompletionTokens, 256)
            isQwen && heavyTask -> maxOf(maxCompletionTokens, 512)
            else -> maxCompletionTokens
        }

        val root = buildJsonObject {
            put("model", model)
            put("messages", messagesArray)
            put("temperature", temperature)
            if (effectiveMaxTokens != null && effectiveMaxTokens > 0) {
                put("max_completion_tokens", effectiveMaxTokens)
            }
            if (jsonObjectMode) {
                put(
                    "response_format",
                    buildJsonObject {
                        put("type", "json_object")
                    }
                )
            }
            if (isOss) {
                val eff = reasoningEffortOverride?.takeIf { it in setOf("low", "medium", "high") }
                    ?: if (heavyTask) "medium" else "low"
                put("reasoning_effort", eff)
            } else if (isQwen) {
                if (model == MODEL_QWEN3_32B) {
                    val eff = reasoningEffortOverride?.takeIf { it in setOf("none", "default") }
                        ?: if (heavyTask) "default" else "none"
                    put("reasoning_effort", eff)
                    if (!jsonObjectMode) {
                        put("reasoning_format", "hidden")
                    }
                } else {
                    // Qwen 3.8 27B supports "none", "low", "medium", "high"
                    val eff = reasoningEffortOverride?.takeIf { it in setOf("none", "low", "medium", "high") }
                        ?: if (heavyTask) "low" else "none"
                    put("reasoning_effort", eff)
                }
            }
        }
        return root.toString()
    }

    fun parseQuotaHeaders(
        headerFields: Map<String?, List<String>>?,
        httpCode: Int = 200,
        nowMs: Long = System.currentTimeMillis()
    ): GroqQuotaReport? {
        if (headerFields.isNullOrEmpty()) {
            return if (httpCode == 402) {
                GroqQuotaReport(creditsDepleted402 = true, updatedAtMs = nowMs)
            } else null
        }

        fun headerValue(target: String): String? {
            for ((k, vals) in headerFields) {
                if (k != null && k.equals(target, ignoreCase = true)) {
                    return vals.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
                }
            }
            return null
        }

        val remReq = headerValue("x-ratelimit-remaining-requests")?.toIntOrNull() ?: -1
        val limReq = headerValue("x-ratelimit-limit-requests")?.toIntOrNull() ?: -1
        val rstReq = headerValue("x-ratelimit-reset-requests").orEmpty()
        val remTok = headerValue("x-ratelimit-remaining-tokens")?.toIntOrNull() ?: -1
        val limTok = headerValue("x-ratelimit-limit-tokens")?.toIntOrNull() ?: -1
        val rstTok = headerValue("x-ratelimit-reset-tokens").orEmpty()
        val retryAfter = headerValue("retry-after")?.toDoubleOrNull()?.roundToInt()?.coerceAtLeast(0) ?: 0
        val depleted402 = httpCode == 402

        if (remReq < 0 && limReq < 0 && remTok < 0 && limTok < 0 && retryAfter <= 0 && !depleted402) {
            return null
        }
        return GroqQuotaReport(
            remainingRequests = remReq,
            limitRequests = limReq,
            resetRequests = rstReq,
            remainingTokens = remTok,
            limitTokens = limTok,
            resetTokens = rstTok,
            retryAfterSec = retryAfter,
            creditsDepleted402 = depleted402,
            updatedAtMs = nowMs
        )
    }

    fun formatCompactCount(value: Int): String {
        val safe = value.coerceAtLeast(0)
        return when {
            safe >= 1_000_000 -> String.format(Locale.US, "%.1fM", safe / 1_000_000.0)
            safe >= 10_000 -> String.format(Locale.US, "%.1fk", safe / 1_000.0)
            else -> NumberFormat.getIntegerInstance(Locale.US).format(safe)
        }
    }

    fun formatQuotaBannerLabel(
        remainingRequests: Int,
        limitRequests: Int,
        remainingTokens: Int,
        limitTokens: Int,
        exhausted: Boolean,
        coolingReset: String = ""
    ): String {
        if (exhausted || (limitRequests > 0 && remainingRequests == 0)) {
            return "Quota 0 left"
        }
        if (limitTokens > 0 && remainingTokens == 0) {
            val suffix = coolingReset.ifBlank { "shortly" }
            return "Cooling $suffix"
        }
        if (remainingRequests >= 0) {
            return "${formatCompactCount(remainingRequests)} req left"
        }
        if (remainingTokens >= 0) {
            return "${formatCompactCount(remainingTokens)} tok left"
        }
        return "Groq ready"
    }

    fun extractResponseText(rawBody: String, quota: GroqQuotaReport? = null, modelId: String = ""): String {
        val root = runCatching { jsonParser.parseToJsonElement(rawBody) as? JsonObject }.getOrNull()
            ?: throw GroqApiException(null, "Groq API returned an unreadable response.", quota = quota)

        val rootErrEl = root["error"]
        if (rootErrEl != null && rootErrEl !is JsonNull) {
            val detail = extractErrorFromJsonObject(root)
            throw GroqApiException(
                httpCode = null,
                message = detail.message.ifBlank { "Groq API request failed." },
                errorCode = detail.code,
                errorType = detail.type,
                quota = quota
            )
        }

        val collected = StringBuilder()
        val choices = root["choices"] as? JsonArray
        val firstChoice = choices?.firstOrNull() as? JsonObject
        val finishReason = (firstChoice?.get("finish_reason") as? JsonPrimitive)?.contentOrNull.orEmpty()
        val choiceMsg = firstChoice?.get("message") as? JsonObject
        val contentEl = choiceMsg?.get("content")
        when (contentEl) {
            is JsonPrimitive -> collected.append(contentEl.contentOrNull.orEmpty())
            is JsonArray -> {
                for (item in contentEl) {
                    val obj = item as? JsonObject ?: continue
                    val t = (obj["text"] as? JsonPrimitive)?.contentOrNull
                        ?: (obj["content"] as? JsonPrimitive)?.contentOrNull
                    collected.append(t.orEmpty())
                }
            }
            else -> Unit
        }

        val textOut = THINK_BLOCK_REGEX.replace(collected.toString(), "").trim()
        if (textOut.isEmpty()) {
            val detail = extractErrorFromJsonObject(root)
            val fallbackMsg = when {
                detail.message.isNotBlank() -> detail.message
                finishReason.equals("length", ignoreCase = true) -> {
                    val m = modelId.ifBlank { "model" }
                    "$m hit max_completion_tokens before answering (finish_reason=length)."
                }
                finishReason.isNotBlank() -> {
                    val m = modelId.ifBlank { "model" }
                    "$m returned empty content (finish_reason=$finishReason)."
                }
                else -> "Groq API returned empty output."
            }
            throw GroqApiException(
                httpCode = 200,
                message = fallbackMsg,
                errorCode = detail.code ?: if (finishReason == "length") "length_truncated" else null,
                errorType = detail.type,
                quota = quota
            )
        }
        return textOut
    }

    fun extractErrorDetail(rawBody: String, httpCode: Int): GroqParsedError {
        val trimmed = rawBody.trim()
        if (trimmed.isNotEmpty()) {
            val parsed = runCatching { jsonParser.parseToJsonElement(trimmed) }.getOrNull()
            if (parsed is JsonObject) {
                val detail = extractErrorFromJsonObject(parsed)
                if (detail.message.isNotBlank() || !detail.code.isNullOrBlank() || !detail.type.isNullOrBlank()) {
                    return GroqParsedError(
                        message = detail.message.ifBlank { "Groq HTTP $httpCode" },
                        type = detail.type,
                        code = detail.code
                    )
                }
            }
        }
        return GroqParsedError(
            message = "Groq HTTP $httpCode",
            type = null,
            code = null
        )
    }

    private fun extractErrorFromJsonObject(obj: JsonObject): GroqParsedError {
        val errEl = obj["error"]
        val errObj = errEl as? JsonObject
        val code = (errObj?.get("code") as? JsonPrimitive)?.contentOrNull
            ?: (obj["code"] as? JsonPrimitive)?.contentOrNull
        val type = (errObj?.get("type") as? JsonPrimitive)?.contentOrNull
            ?: (obj["type"] as? JsonPrimitive)?.contentOrNull
        val msg = when (errEl) {
            is JsonObject -> (errEl["message"] as? JsonPrimitive)?.contentOrNull
            is JsonPrimitive -> errEl.contentOrNull
            else -> null
        } ?: (obj["message"] as? JsonPrimitive)?.contentOrNull ?: ""
        return GroqParsedError(
            message = msg.trim(),
            type = type?.trim()?.ifBlank { null },
            code = code?.trim()?.ifBlank { null }
        )
    }

    fun looksLikeQuotaOrCreditsDepleted(
        httpCode: Int? = null,
        errorCode: String? = null,
        errorType: String? = null,
        rawMessage: String? = null,
        quota: GroqQuotaReport? = null
    ): Boolean {
        if (httpCode == 402 || quota?.creditsDepleted402 == true) return true
        val c = errorCode?.lowercase(Locale.US).orEmpty()
        val t = errorType?.lowercase(Locale.US).orEmpty()
        if (c == "insufficient_quota" || c == "billing_hard_limit_reached" || t == "insufficient_quota") return true
        val m = rawMessage?.lowercase(Locale.US).orEmpty()
        if (m.contains("spend limit") ||
            m.contains("spending limit") ||
            ("billing" in m && "limit" in m) ||
            m.contains("requests per day") ||
            m.contains("tokens per day") ||
            m.contains("(rpd)") ||
            m.contains("(tpd)") ||
            m.contains("daily limit") ||
            m.contains("per day")
        ) {
            return true
        }
        if (httpCode == 429 && quota != null && (quota.creditsDepleted402 || (quota.limitRequests > 0 && quota.remainingRequests == 0))) {
            return true
        }
        return false
    }

    fun classifyFailure(
        httpCode: Int?,
        rawMessage: String,
        errorCode: String? = null,
        errorType: String? = null,
        quota: GroqQuotaReport? = null,
        isTimeout: Boolean = false,
        isDnsOrConnectError: Boolean = false
    ): GroqFailureCategory {
        val msg = rawMessage.lowercase(Locale.US)
        val codeLower = errorCode?.lowercase(Locale.US).orEmpty()
        if (codeLower == "model_decommissioned" ||
            codeLower == "model_not_found" ||
            codeLower == "model_permission_blocked_org" ||
            httpCode == 404 ||
            msg.contains("model_decommissioned") ||
            msg.contains("decommissioned") ||
            msg.contains("does not exist") ||
            msg.contains("blocked at the organization level")
        ) {
            return GroqFailureCategory.MODEL_UNAVAILABLE
        }
        if (httpCode == 401 ||
            (httpCode == 403 && !codeLower.startsWith("model_")) ||
            codeLower == "invalid_api_key" ||
            codeLower == "unauthorized" ||
            msg.contains("invalid api key") ||
            msg.contains("invalid_api_key") ||
            msg.contains("unauthorized") ||
            msg.contains("authentication failed")
        ) {
            return GroqFailureCategory.FATAL_AUTH
        }
        if (looksLikeQuotaOrCreditsDepleted(httpCode, errorCode, errorType, rawMessage, quota)) {
            return GroqFailureCategory.QUOTA_OR_CREDITS_EXHAUSTED
        }
        if (httpCode == 413 || msg.contains("request entity too large") || msg.contains("context_length_exceeded")) {
            return GroqFailureCategory.PAYLOAD_TOO_LARGE
        }
        if (isDnsOrConnectError) return GroqFailureCategory.NETWORK_UNREACHABLE
        if (isTimeout || msg.contains("timed out") || msg.contains("timeout")) {
            return GroqFailureCategory.NETWORK_TIMEOUT
        }
        if (httpCode == 429 ||
            httpCode == 503 ||
            msg.contains("rate limit") ||
            msg.contains("rate_limit_exceeded") ||
            msg.contains("too many requests") ||
            msg.contains("over capacity") ||
            msg.contains("temporarily unavailable")
        ) {
            return GroqFailureCategory.RATE_OR_OVERLOAD
        }
        return GroqFailureCategory.TRANSIENT_SERVER
    }

    fun buildFriendlyErrorMessage(
        lastCategory: GroqFailureCategory,
        lastRawMessage: String,
        attemptedModels: List<String>,
        sawAnyHttpResponse: Boolean,
        quota: GroqQuotaReport? = null,
        modelErrors: List<String> = emptyList()
    ): String {
        val tried = attemptedModels.distinct().joinToString(" -> ").ifBlank { MODEL_GPT_OSS_120B }
        val cleanRaw = lastRawMessage.trim()
        val detailSuffix = modelErrors.distinct().take(3).joinToString(" | ").takeIf { it.isNotBlank() }?.let { " [$it]" }.orEmpty()
        val resetHint = quota?.let {
            when {
                it.resetRequests.isNotBlank() -> " (resets in ${it.resetRequests})"
                it.resetTokens.isNotBlank() -> " (resets in ${it.resetTokens})"
                it.retryAfterSec > 0 -> " (retry in ${it.retryAfterSec}s)"
                else -> ""
            }
        }.orEmpty()
        return when {
            lastCategory == GroqFailureCategory.QUOTA_OR_CREDITS_EXHAUSTED -> {
                "Groq quota or credits exhausted$resetHint. Switch to another Groq API key or wait for quota reset."
            }
            lastCategory == GroqFailureCategory.FATAL_AUTH -> {
                "Groq API key is invalid or revoked. Open Groq Settings to paste a valid gsk_... key."
            }
            lastCategory == GroqFailureCategory.MODEL_UNAVAILABLE -> {
                "Selected Groq model is deprecated or blocked ($tried). Switch Route to Auto.$detailSuffix"
            }
            lastCategory == GroqFailureCategory.PAYLOAD_TOO_LARGE -> {
                "Request exceeded Groq model token/size limit ($tried). Try Flagged-only or fewer scenes."
            }
            !sawAnyHttpResponse &&
                (lastCategory == GroqFailureCategory.NETWORK_TIMEOUT || lastCategory == GroqFailureCategory.NETWORK_UNREACHABLE) -> {
                "Could not reach api.groq.com ($tried). Check your Wi-Fi or mobile data and try again."
            }
            lastCategory == GroqFailureCategory.NETWORK_TIMEOUT -> {
                "Groq API timed out across $tried. Try switching model route or retry in a moment."
            }
            lastCategory == GroqFailureCategory.RATE_OR_OVERLOAD -> {
                "Groq rate limit hit across $tried$resetHint. Wait briefly or switch API key.$detailSuffix"
            }
            else -> {
                val base = cleanRaw.ifBlank { "Groq API request failed" }
                "$base (tried $tried)$detailSuffix"
            }
        }
    }

    fun stripJsonFences(raw: String): String {
        var t = THINK_BLOCK_REGEX.replace(raw, "").trim()
        if (t.startsWith("```")) {
            val firstNewline = t.indexOf('\n')
            t = if (firstNewline >= 0) t.substring(firstNewline + 1) else t.removePrefix("```")
            if (t.trimEnd().endsWith("```")) {
                t = t.trimEnd().removeSuffix("```")
            }
        }
        val firstBrace = t.indexOf('{')
        val lastBrace = t.lastIndexOf('}')
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            t = t.substring(firstBrace, lastBrace + 1)
        }
        return t.trim()
    }

    fun parseReviewJson(
        rawResponse: String,
        htmlHash: Int,
        nowMs: Long = System.currentTimeMillis()
    ): GroqReviewResult {
        val cleaned = stripJsonFences(rawResponse)
        val obj = runCatching { JSONObject(cleaned) }.getOrNull()
            ?: return GroqReviewResult(ok = false, issues = emptyList(), htmlHash = htmlHash, atMs = nowMs, parseFailed = true)
        val arr = obj.optJSONArray("issues") ?: JSONArray()
        val issues = ArrayList<GroqReviewIssue>(arr.length())
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val fIdx = item.optInt("f", 1).coerceAtLeast(1)
            val typeStr = item.optString("type", "layout").trim().ifBlank { "layout" }
            val whereStr = item.optString("where", "").trim()
            val sevStr = item.optString("sev", "med").trim().ifBlank { "med" }
            val fixStr = item.optString("fix", "").trim()
            if (whereStr.isNotBlank() || fixStr.isNotBlank()) {
                issues.add(GroqReviewIssue(f = fIdx, type = typeStr, where = whereStr, sev = sevStr, fix = fixStr))
            }
        }
        val okFlag = obj.optBoolean("ok", issues.isEmpty())
        return GroqReviewResult(ok = okFlag, issues = issues.take(6), htmlHash = htmlHash, atMs = nowMs, parseFailed = false)
    }

    fun parseLeanPlanJson(rawResponse: String): GroqPlanResult {
        val cleaned = stripJsonFences(rawResponse)
        val obj = JSONObject(cleaned)
        val editsArr = obj.optJSONArray("edits") ?: JSONArray()
        val edits = ArrayList<GroqPatchEdit>(editsArr.length())
        for (i in 0 until editsArr.length()) {
            val eObj = editsArr.optJSONObject(i) ?: continue
            val rcId = when {
                eObj.has("rc") -> eObj.optString("rc", "")
                eObj.has("i") -> eObj.opt("i")?.toString().orEmpty()
                else -> "E${i + 1}"
            }.trim().ifBlank { "E${i + 1}" }
            val findStr = eObj.optString("find", "")
            val replaceStr = eObj.optString("replace", "")
            if (findStr.isNotEmpty() || replaceStr.isNotEmpty()) {
                edits.add(GroqPatchEdit(rc = rcId, find = findStr, replace = replaceStr))
            }
        }
        val skippedCount = obj.optJSONArray("skipped")?.length() ?: 0
        val structural = obj.optBoolean("structural", false) || obj.optBoolean("needsFull", false)
        return GroqPlanResult(edits = edits.take(8), skippedCount = skippedCount, structural = structural)
    }

    fun parseStage1Plan(rawResponse: String): GroqStage1Plan {
        val cleaned = stripJsonFences(rawResponse)
        val obj = JSONObject(cleaned)
        val rcArr = obj.optJSONArray("rootCauses") ?: JSONArray()
        val rootCauses = ArrayList<GroqRootCause>(rcArr.length())
        for (i in 0 until rcArr.length()) {
            val item = rcArr.optJSONObject(i) ?: continue
            val tArr = item.optJSONArray("t")
            val times = mutableListOf<Double>()
            if (tArr != null) {
                for (j in 0 until tArr.length()) {
                    val v = tArr.optDouble(j, Double.NaN)
                    if (!v.isNaN() && v.isFinite()) times.add(v)
                }
            } else {
                val singleT = item.optDouble("t", Double.NaN)
                if (!singleT.isNaN() && singleT.isFinite()) times.add(singleT)
            }
            rootCauses.add(
                GroqRootCause(
                    id = item.optString("id", "R${i + 1}").ifBlank { "R${i + 1}" },
                    type = item.optString("type", "layout").ifBlank { "layout" },
                    timesSec = times,
                    element = item.optString("el", "").trim(),
                    evidence = item.optString("evidence", "").trim(),
                    fix = item.optString("fix", "").trim()
                )
            )
        }

        val editsArr = obj.optJSONArray("edits") ?: JSONArray()
        val edits = ArrayList<GroqPatchEdit>(editsArr.length())
        for (i in 0 until editsArr.length()) {
            val eObj = editsArr.optJSONObject(i) ?: continue
            val findStr = eObj.optString("find", "")
            val replaceStr = eObj.optString("replace", "")
            if (findStr.isNotEmpty() || replaceStr.isNotEmpty()) {
                edits.add(
                    GroqPatchEdit(
                        rc = eObj.optString("rc", eObj.opt("i")?.toString().orEmpty()).trim(),
                        find = findStr,
                        replace = replaceStr
                    )
                )
            }
        }

        val needsFull = obj.optBoolean("needsFull", false) || obj.optBoolean("structural", false)
        val reason = obj.optString("reason", "").trim()
        return GroqStage1Plan(
            rootCauses = rootCauses,
            edits = edits.take(12),
            needsFull = needsFull,
            reason = reason
        )
    }

    fun parseStage2Repair(rawResponse: String): Pair<List<GroqPatchEdit>, String?> {
        val cleaned = stripJsonFences(rawResponse)
        val obj = JSONObject(cleaned)
        val fullHtml = obj.optString("html", "").trim().takeIf { it.isNotEmpty() }
        val editsArr = obj.optJSONArray("edits") ?: JSONArray()
        val edits = ArrayList<GroqPatchEdit>(editsArr.length())
        for (i in 0 until editsArr.length()) {
            val eObj = editsArr.optJSONObject(i) ?: continue
            val findStr = eObj.optString("find", "")
            val replaceStr = eObj.optString("replace", "")
            if (findStr.isNotEmpty() || replaceStr.isNotEmpty()) {
                edits.add(
                    GroqPatchEdit(
                        rc = eObj.optString("rc", eObj.opt("i")?.toString().orEmpty()).trim(),
                        find = findStr,
                        replace = replaceStr
                    )
                )
            }
        }
        return edits.take(12) to fullHtml
    }

    fun parseClaudeSearchReplacePatches(patchText: String): List<GroqPatchEdit> {
        val edits = mutableListOf<GroqPatchEdit>()
        for (m in SEARCH_REPLACE_BLOCK_REGEX.findAll(patchText)) {
            val search = m.groupValues[1]
            val replace = m.groupValues[2]
            if (search.isNotBlank()) {
                edits.add(GroqPatchEdit(rc = "P${edits.size + 1}", find = search, replace = replace))
            }
        }
        return edits
    }

    fun stripLineNumbers(s: String): String {
        val nonBlank = s.lines().filter { it.isNotBlank() }
        return if (nonBlank.isNotEmpty() && nonBlank.all { LINE_NO_PREFIX_REGEX.containsMatchIn(it) }) {
            s.lines().joinToString("\n") { it.replace(LINE_NO_PREFIX_REGEX, "") }
        } else {
            s
        }
    }

    private class NormHtml(val text: String, val map: IntArray)

    private fun normalizeWhitespaceWithMap(src: String): NormHtml {
        val sb = StringBuilder(src.length)
        val map = IntArray(src.length + 1)
        var prevWs = false
        for (idx in src.indices) {
            val ch = src[idx]
            if (ch.isWhitespace()) {
                if (!prevWs) {
                    map[sb.length] = idx
                    sb.append(' ')
                }
                prevWs = true
            } else {
                map[sb.length] = idx
                sb.append(ch)
                prevWs = false
            }
        }
        map[sb.length] = src.length
        return NormHtml(sb.toString(), map.copyOf(sb.length + 1))
    }

    fun countOccurrences(haystack: String, needle: String): Int {
        if (needle.isEmpty()) return 0
        var count = 0
        var startIdx = 0
        while (true) {
            val found = haystack.indexOf(needle, startIdx)
            if (found < 0) break
            count++
            startIdx = found + needle.length
        }
        return count
    }

    fun applyExactOnceEdits(
        html: String,
        edits: List<GroqPatchEdit>
    ): GroqPatchApplyOutcome {
        var current = html
        var applied = 0
        val failed = mutableListOf<GroqFailedEdit>()

        for (edit in edits) {
            val cleanedFind = stripLineNumbers(edit.find)
            val cleanedEdit = if (cleanedFind != edit.find) edit.copy(find = cleanedFind) else edit
            if (cleanedFind.isBlank() || cleanedFind.trim().length < 4) {
                failed.add(GroqFailedEdit(cleanedEdit, "empty", 0))
                continue
            }
            if (FORBIDDEN_EDIT_REGEX.containsMatchIn(edit.replace) && !FORBIDDEN_EDIT_REGEX.containsMatchIn(cleanedFind)) {
                failed.add(GroqFailedEdit(cleanedEdit, "forbidden", 0))
                continue
            }

            val exactCount = countOccurrences(current, cleanedFind)
            when {
                exactCount == 1 -> {
                    val idx = current.indexOf(cleanedFind)
                    current = current.substring(0, idx) + edit.replace + current.substring(idx + cleanedFind.length)
                    applied++
                }
                exactCount > 1 -> {
                    failed.add(GroqFailedEdit(cleanedEdit, "ambiguous($exactCount)", exactCount))
                }
                else -> {
                    val lfFind = cleanedFind.replace("\r\n", "\n")
                    val crlfFind = lfFind.replace("\n", "\r\n")
                    val crlfCount = if (crlfFind != cleanedFind) countOccurrences(current, crlfFind) else 0
                    if (crlfCount == 1) {
                        val idx = current.indexOf(crlfFind)
                        val replacement = if (current.contains("\r\n")) edit.replace.replace("\r\n", "\n").replace("\n", "\r\n") else edit.replace
                        current = current.substring(0, idx) + replacement + current.substring(idx + crlfFind.length)
                        applied++
                    } else {
                        // Tier 2: whitespace-normalized unique match
                        val normSource = normalizeWhitespaceWithMap(current)
                        val normNeedle = normalizeWhitespaceWithMap(cleanedFind).text.trim()
                        val n2 = if (normNeedle.length >= 4) countOccurrences(normSource.text, normNeedle) else 0
                        when {
                            n2 == 1 -> {
                                val s = normSource.text.indexOf(normNeedle)
                                val en = s + normNeedle.length
                                val os = normSource.map[s]
                                val oe = normSource.map[en - 1] + 1
                                current = current.substring(0, os) + edit.replace + current.substring(oe)
                                applied++
                            }
                            n2 > 1 -> failed.add(GroqFailedEdit(cleanedEdit, "ambiguous($n2)", n2))
                            else -> failed.add(GroqFailedEdit(cleanedEdit, "not_found", 0))
                        }
                    }
                }
            }
        }

        return GroqPatchApplyOutcome(
            updatedHtml = current,
            appliedCount = applied,
            failedEdits = failed
        )
    }

    suspend fun applyEditsValidated(
        html0: String,
        edits: List<GroqPatchEdit>,
        isValid: suspend (String) -> Boolean
    ): GroqPatchApplyOutcome {
        val batch = applyExactOnceEdits(html0, edits)
        if (batch.appliedCount == 0 || isValid(batch.updatedHtml)) {
            return batch
        }
        var cur = html0
        var applied = 0
        val failed = mutableListOf<GroqFailedEdit>()
        for (edit in edits) {
            val single = applyExactOnceEdits(cur, listOf(edit))
            if (single.appliedCount == 1) {
                if (isValid(single.updatedHtml)) {
                    cur = single.updatedHtml
                    applied++
                } else {
                    failed.add(GroqFailedEdit(edit, "breaks_validation", 1))
                }
            } else {
                failed.addAll(single.failedEdits)
            }
        }
        return GroqPatchApplyOutcome(updatedHtml = cur, appliedCount = applied, failedEdits = failed)
    }

    fun buildCleanExcerpt(
        html: String,
        tokens: List<String>,
        maxChars: Int = 6000,
        radius: Int = 10
    ): String {
        val lines = html.lines()
        if (lines.isEmpty()) return ""
        val toks = tokens.map { it.trim() }.filter { it.length >= 3 }.distinct()
        val hits = sortedSetOf<Int>()
        if (toks.isNotEmpty()) {
            lines.forEachIndexed { idx, line ->
                if (toks.any { tok -> line.contains(tok, ignoreCase = true) }) {
                    hits.add(idx)
                }
            }
        }
        if (hits.isEmpty()) {
            val end = minOf(lines.size, 65)
            return lines.subList(0, end).joinToString("\n").take(maxChars)
        }
        val ranges = mutableListOf<IntRange>()
        for (hit in hits) {
            val r = maxOf(0, hit - radius)..minOf(lines.lastIndex, hit + radius)
            if (ranges.isNotEmpty() && r.first <= ranges.last().last + 1) {
                ranges[ranges.lastIndex] = ranges.last().first..maxOf(ranges.last().last, r.last)
            } else {
                ranges.add(r)
            }
        }
        val sb = StringBuilder()
        for (r in ranges) {
            val chunk = "@@ window @@\n" + lines.subList(r.first, r.last + 1).joinToString("\n") + "\n"
            if (sb.length + chunk.length > maxChars) {
                sb.append(chunk.take(maxOf(0, maxChars - sb.length)))
                break
            }
            sb.append(chunk)
        }
        return sb.toString().trimEnd()
    }

    fun buildHtmlExcerpt(
        fullHtml: String,
        flaggedTimesSec: List<Double>,
        extraHints: List<String> = emptyList(),
        maxChars: Int = 6000
    ): String {
        val lines = fullHtml.lines()
        if (lines.isEmpty()) return ""
        val keep = BooleanArray(lines.size)

        fun markRange(startLineZero: Int, endLineZero: Int) {
            val s = startLineZero.coerceIn(0, lines.lastIndex)
            val e = endLineZero.coerceIn(s, lines.lastIndex)
            for (i in s..e) keep[i] = true
        }

        // 1. Include <style> CSS rules (up to first 60 lines)
        var inStyle = false
        var styleCount = 0
        for (i in lines.indices) {
            val lower = lines[i].lowercase(Locale.US)
            if (!inStyle && lower.contains("<style")) inStyle = true
            if (inStyle) {
                if (styleCount < 60) {
                    keep[i] = true
                    styleCount++
                }
                if (lower.contains("</style>")) inStyle = false
            }
        }

        // 2. Include core engine/RAW contract & seek(t) definitions
        for (i in lines.indices) {
            val l = lines[i]
            if (l.contains("window.RAW") ||
                l.contains("window.PROJECT") ||
                l.contains("function seek") ||
                l.contains("seek(t)") ||
                l.contains("seek:") ||
                l.contains("const build") ||
                l.contains("var build")
            ) {
                markRange(i - 2, i + 14)
            }
        }

        // 3. Find scene / timing blocks near flagged timestamps
        val startNumRegex = Regex("""start\s*:\s*([0-9]+(?:\.[0-9]+)?)""")
        val endNumRegex = Regex("""end\s*:\s*([0-9]+(?:\.[0-9]+)?)""")
        val sceneStarts = mutableListOf<Triple<Int, Double, Double>>()
        for (i in lines.indices) {
            val windowText = (i..minOf(lines.lastIndex, i + 2)).joinToString(" ") { lines[it] }
            val sm = startNumRegex.find(windowText)
            val em = endNumRegex.find(windowText)
            if (sm != null && em != null) {
                val s = sm.groupValues[1].toDoubleOrNull()
                val e = em.groupValues[1].toDoubleOrNull()
                if (s != null && e != null && e >= s) {
                    sceneStarts.add(Triple(i, s, e))
                }
            }
        }

        for (ft in flaggedTimesSec) {
            var matchedScene = false
            for ((lineIdx, s, e) in sceneStarts) {
                if (ft >= s - 0.15 && ft <= e + 0.15) {
                    markRange(lineIdx - 2, lineIdx + 16)
                    matchedScene = true
                }
            }
            if (!matchedScene && sceneStarts.isNotEmpty()) {
                val nearest = sceneStarts.minByOrNull { (_, s, e) ->
                    val mid = (s + e) * 0.5
                    kotlin.math.abs(mid - ft)
                }
                if (nearest != null) {
                    markRange(nearest.first - 2, nearest.first + 14)
                }
            }
        }

        // 4. Match text tokens or selectors mentioned in DOM flags / errors
        val tokens = extraHints
            .flatMap { Regex("[A-Za-z0-9_#-]{4,}").findAll(it).map { m -> m.value }.toList() }
            .filter { it !in setOf("Edge", "clip", "near", "Card", "overlap", "Wrapped", "Resized", "Lifted", "element", "above", "progress") }
            .distinct()
            .take(16)
        for (tok in tokens) {
            var hits = 0
            for (i in lines.indices) {
                if (lines[i].contains(tok, ignoreCase = true)) {
                    markRange(i - 4, i + 6)
                    hits++
                    if (hits >= 3) break
                }
            }
        }

        if (keep.count { it } < 20) {
            markRange(0, minOf(lines.lastIndex, 55))
            if (lines.size > 70) {
                markRange(maxOf(0, lines.lastIndex - 40), lines.lastIndex)
            }
        }

        val sb = StringBuilder()
        var inWindow = false
        for (i in lines.indices) {
            if (!keep[i]) {
                inWindow = false
                continue
            }
            if (!inWindow) {
                if (sb.length + 14 > maxChars) break
                if (sb.isNotEmpty()) sb.append("\n")
                sb.append("@@ window @@\n")
                inWindow = true
            }
            val row = "${lines[i]}\n"
            if (sb.length + row.length > maxChars) {
                sb.append(row.take(maxOf(0, maxChars - sb.length)))
                break
            }
            sb.append(row)
        }
        return sb.toString().trimEnd()
    }

    fun buildRepairWindows(
        fullHtml: String,
        failedEdits: List<GroqFailedEdit>,
        errorMessage: String,
        radiusLines: Int = 8,
        maxChars: Int = 2500
    ): String {
        val lines = fullHtml.lines()
        if (lines.isEmpty()) return ""
        val keep = BooleanArray(lines.size)

        fun markAround(centerZero: Int) {
            val s = (centerZero - radiusLines).coerceIn(0, lines.lastIndex)
            val e = (centerZero + radiusLines).coerceIn(s, lines.lastIndex)
            for (i in s..e) keep[i] = true
        }

        val lineMatch = Regex("line\\s+(\\d+)", RegexOption.IGNORE_CASE).find(errorMessage)
        lineMatch?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { ln ->
            markAround((ln - 1).coerceIn(0, lines.lastIndex))
        }

        for (fe in failedEdits) {
            val findLines = fe.edit.find.lines().map { it.trim() }.filter { it.length >= 4 }
            var foundAnchor = false
            for (needle in findLines) {
                for (i in lines.indices) {
                    if (lines[i].contains(needle)) {
                        markAround(i)
                        foundAnchor = true
                        break
                    }
                }
                if (foundAnchor) break
            }
            if (!foundAnchor) {
                val words = Regex("[A-Za-z0-9_#-]{4,}").findAll(fe.edit.find).map { it.value }.distinct().take(4).toList()
                for (w in words) {
                    val hitIdx = lines.indexOfFirst { it.contains(w) }
                    if (hitIdx >= 0) {
                        markAround(hitIdx)
                        break
                    }
                }
            }
        }

        if (keep.none { it }) {
            markAround(minOf(lines.lastIndex, 20))
            val seekIdx = lines.indexOfFirst { it.contains("seek") }
            if (seekIdx >= 0) markAround(seekIdx)
        }

        val sb = StringBuilder()
        var inWindow = false
        for (i in lines.indices) {
            if (!keep[i]) {
                inWindow = false
                continue
            }
            if (!inWindow) {
                if (sb.length + 14 > maxChars) break
                if (sb.isNotEmpty()) sb.append("\n")
                sb.append("@@ window @@\n")
                inWindow = true
            }
            val row = "${lines[i]}\n"
            if (sb.length + row.length > maxChars) {
                sb.append(row.take(maxOf(0, maxChars - sb.length)))
                break
            }
            sb.append(row)
        }
        return sb.toString().trimEnd()
    }
}
