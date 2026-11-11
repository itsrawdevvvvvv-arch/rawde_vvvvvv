package com.rawedit.app.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

data class PuterImageInput(
    val base64Data: String,
    val mimeType: String
)

data class PuterModelOption(
    val prefId: String,
    val shortLabel: String,
    val badgeLabel: String,
    val detail: String
)

enum class PuterFailureCategory {
    FATAL_AUTH,
    INSUFFICIENT_CREDITS,
    RATE_OR_OVERLOAD,
    NETWORK_TIMEOUT,
    NETWORK_UNREACHABLE,
    TRANSIENT_SERVER
}

data class PuterUsageReport(
    val monthlyUsedRaw: Double,
    val monthlyAllowanceRaw: Double,
    val monthlyRemainingRaw: Double,
    val purchasedCreditsRaw: Double,
    val consumedPurchasedRaw: Double,
    val purchasedRemainingRaw: Double,
    val isCreditsUnit: Boolean
) {
    val totalRemainingRaw: Double
        get() = (monthlyRemainingRaw + purchasedRemainingRaw).coerceAtLeast(0.0)

    val totalCapacityRaw: Double
        get() = (monthlyAllowanceRaw + purchasedCreditsRaw).coerceAtLeast(0.0)

    val usedPercent: Int
        get() {
            if (monthlyAllowanceRaw <= 0.0) return 0
            val pct = (monthlyUsedRaw / monthlyAllowanceRaw) * 100.0
            return if (pct < 100.0) minOf(99, pct.roundToInt()).coerceAtLeast(0) else 100
        }

    val remainingFraction: Float
        get() {
            val cap = totalCapacityRaw
            if (cap <= 0.0) return 1f
            return (totalRemainingRaw / cap).toFloat().coerceIn(0f, 1f)
        }

    val remainingPercent: Int
        get() {
            val cap = totalCapacityRaw
            if (cap <= 0.0) return 100
            if (totalRemainingRaw <= 0.0) return 0
            val rawPct = ((totalRemainingRaw / cap) * 100.0).roundToInt()
            return rawPct.coerceIn(1, 100)
        }

    val isExhausted: Boolean
        get() = totalCapacityRaw > 0.0 && totalRemainingRaw <= 0.0

    val isLow: Boolean
        get() = !isExhausted && totalCapacityRaw > 0.0 && remainingPercent <= 10

    fun formattedRemaining(): String = PuterCodec.formatBalanceValue(totalRemainingRaw, isCreditsUnit)

    fun formattedMonthlyAllowance(): String = PuterCodec.formatBalanceValue(monthlyAllowanceRaw, isCreditsUnit)

    fun formattedPurchasedRemaining(): String = PuterCodec.formatBalanceValue(purchasedRemainingRaw, isCreditsUnit)
}

data class PuterParsedError(
    val message: String,
    val code: String?,
    val usageLimited: Boolean
)

class PuterApiException(
    val httpCode: Int?,
    override val message: String,
    val errorCode: String? = null,
    val usageLimited: Boolean = false
) : IllegalStateException(message)

class PuterOutOfCreditsException(
    override val message: String,
    val httpCode: Int? = 402
) : IllegalStateException(message)

object PuterCodec {

    const val API_ORIGIN = "https://api.puter.com"
    const val WEB_ORIGIN = "https://puter.com"
    const val DRIVERS_CALL_URL = "$API_ORIGIN/drivers/call"
    const val WHOAMI_URL = "$API_ORIGIN/whoami"
    const val USAGE_URL = "$API_ORIGIN/metering/usage"
    const val AUTHME_URL = "$WEB_ORIGIN/action/authme"
    const val COPY_AUTH_URL = "$WEB_ORIGIN/action/copyauth"

    const val APP_REDIRECT_SCHEME = "rawedit"
    const val APP_REDIRECT_HOST = "puter-auth"
    const val APP_REDIRECT_URI = "$APP_REDIRECT_SCHEME://$APP_REDIRECT_HOST"

    const val PREF_AUTO = "auto"
    const val MODEL_GPT_4O_MINI = "gpt-4o-mini"
    const val MODEL_GPT_5_NANO = "gpt-5-nano"
    const val MODEL_CLAUDE_HAIKU = "claude-haiku-5-5"
    const val MODEL_GPT_5_MINI = "gpt-5-mini"
    const val MODEL_CLAUDE_SONNET = "claude-sonnet-4-6"
    const val MODEL_GPT_4_1_MINI = "gpt-4.1-mini"

    val MODEL_OPTIONS: List<PuterModelOption> = listOf(
        PuterModelOption(
            prefId = PREF_AUTO,
            shortLabel = "Auto",
            badgeLabel = "PUTER AUTO ROUTE",
            detail = "Routes across GPT-4o Mini, GPT-5 Nano, Claude Haiku, GPT-5 Mini & Claude Sonnet with automatic congestion failover."
        ),
        PuterModelOption(
            prefId = MODEL_GPT_4O_MINI,
            shortLabel = "GPT-4o Mini",
            badgeLabel = "PUTER GPT-4O MINI",
            detail = "Ultra-fast vision + JSON route for palette generation, asset notes, and cutout checks."
        ),
        PuterModelOption(
            prefId = MODEL_GPT_5_NANO,
            shortLabel = "GPT-5 Nano",
            badgeLabel = "PUTER GPT-5 NANO",
            detail = "Lightweight low-cost OpenAI/Azure model to stretch free Puter credits further."
        ),
        PuterModelOption(
            prefId = MODEL_CLAUDE_HAIKU,
            shortLabel = "Claude Haiku",
            badgeLabel = "PUTER CLAUDE HAIKU",
            detail = "Fast Anthropic vision & UI layout inspection via Puter AI."
        ),
        PuterModelOption(
            prefId = MODEL_GPT_5_MINI,
            shortLabel = "GPT-5 Mini",
            badgeLabel = "PUTER GPT-5 MINI",
            detail = "Strong multimodal reasoning & structured output route."
        ),
        PuterModelOption(
            prefId = MODEL_CLAUDE_SONNET,
            shortLabel = "Claude Sonnet",
            badgeLabel = "PUTER CLAUDE SONNET",
            detail = "Deepest code/layout review route for Contact Sheet HTML auto-fixes."
        )
    )

    private val SUPPORTED_MODELS = setOf(
        PREF_AUTO,
        MODEL_GPT_4O_MINI,
        MODEL_GPT_5_NANO,
        MODEL_CLAUDE_HAIKU,
        MODEL_GPT_5_MINI,
        MODEL_CLAUDE_SONNET,
        MODEL_GPT_4_1_MINI
    )

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun normalizeAuthToken(rawToken: String): String {
        var t = rawToken.trim().removeSurrounding("\"").removeSurrounding("'")
        if (t.startsWith("Bearer ", ignoreCase = true)) {
            t = t.substring(7).trim()
        }
        return t.filterNot { it.isWhitespace() }
    }

    fun normalizeModelPref(rawPref: String): String {
        val cleaned = rawPref.trim().lowercase(Locale.US)
        return if (cleaned in SUPPORTED_MODELS) cleaned else PREF_AUTO
    }

    fun optionForPref(rawPref: String): PuterModelOption {
        val norm = normalizeModelPref(rawPref)
        return MODEL_OPTIONS.firstOrNull { it.prefId == norm } ?: MODEL_OPTIONS.first()
    }

    fun routeDisplayLabel(rawPref: String): String {
        return when (normalizeModelPref(rawPref)) {
            PREF_AUTO -> "Auto (Smart Fallback)"
            MODEL_GPT_4O_MINI -> "GPT-4o Mini"
            MODEL_GPT_5_NANO -> "GPT-5 Nano"
            MODEL_CLAUDE_HAIKU -> "Claude Haiku"
            MODEL_GPT_5_MINI -> "GPT-5 Mini"
            MODEL_CLAUDE_SONNET -> "Claude Sonnet"
            MODEL_GPT_4_1_MINI -> "GPT-4.1 Mini"
            else -> "Auto (Smart Fallback)"
        }
    }

    fun buildCandidateModels(
        modelPref: String,
        heavyTask: Boolean,
        cooledDownModels: Set<String> = emptySet()
    ): List<String> {
        val pref = normalizeModelPref(modelPref)
        val base = when (pref) {
            MODEL_GPT_4O_MINI -> listOf(
                MODEL_GPT_4O_MINI,
                MODEL_GPT_5_NANO,
                MODEL_CLAUDE_HAIKU,
                MODEL_GPT_4_1_MINI,
                MODEL_GPT_5_MINI
            )
            MODEL_GPT_5_NANO -> listOf(
                MODEL_GPT_5_NANO,
                MODEL_GPT_4O_MINI,
                MODEL_CLAUDE_HAIKU,
                MODEL_GPT_4_1_MINI,
                MODEL_GPT_5_MINI
            )
            MODEL_CLAUDE_HAIKU -> listOf(
                MODEL_CLAUDE_HAIKU,
                MODEL_GPT_4O_MINI,
                MODEL_GPT_5_NANO,
                MODEL_GPT_5_MINI,
                MODEL_GPT_4_1_MINI
            )
            MODEL_GPT_5_MINI -> listOf(
                MODEL_GPT_5_MINI,
                MODEL_CLAUDE_SONNET,
                MODEL_GPT_4O_MINI,
                MODEL_GPT_4_1_MINI,
                MODEL_CLAUDE_HAIKU
            )
            MODEL_CLAUDE_SONNET -> listOf(
                MODEL_CLAUDE_SONNET,
                MODEL_GPT_5_MINI,
                MODEL_GPT_4_1_MINI,
                MODEL_GPT_4O_MINI,
                MODEL_CLAUDE_HAIKU
            )
            MODEL_GPT_4_1_MINI -> listOf(
                MODEL_GPT_4_1_MINI,
                MODEL_GPT_4O_MINI,
                MODEL_GPT_5_MINI,
                MODEL_GPT_5_NANO,
                MODEL_CLAUDE_HAIKU
            )
            else -> if (heavyTask) {
                listOf(
                    MODEL_CLAUDE_SONNET,
                    MODEL_GPT_5_MINI,
                    MODEL_GPT_4_1_MINI,
                    MODEL_GPT_4O_MINI,
                    MODEL_CLAUDE_HAIKU
                )
            } else {
                listOf(
                    MODEL_GPT_4O_MINI,
                    MODEL_GPT_5_NANO,
                    MODEL_CLAUDE_HAIKU,
                    MODEL_GPT_4_1_MINI,
                    MODEL_GPT_5_MINI
                )
            }
        }
        if (cooledDownModels.isEmpty()) return base
        return if (pref == PREF_AUTO) {
            base.filter { it !in cooledDownModels } + base.filter { it in cooledDownModels }
        } else {
            val primary = base.first()
            val rest = base.drop(1)
            listOf(primary) + rest.filter { it !in cooledDownModels } + rest.filter { it in cooledDownModels }
        }
    }

    fun buildAuthMeUrl(redirectUrl: String): String {
        val encoded = URLEncoder.encode(redirectUrl, "UTF-8")
        return "$AUTHME_URL?redirectURL=$encoded"
    }

    fun extractTokenFromCallbackUri(uriString: String): String? {
        val raw = uriString.trim()
        if (raw.isEmpty()) return null
        val parsed = runCatching { URI(raw) }.getOrNull() ?: return null
        val candidatePairs = mutableListOf<String>()
        parsed.rawQuery?.takeIf { it.isNotBlank() }?.let { candidatePairs.addAll(it.split('&')) }
        parsed.rawFragment?.takeIf { it.isNotBlank() }?.let { candidatePairs.addAll(it.split('&')) }
        for (pair in candidatePairs) {
            if (pair.isBlank()) continue
            val eqIdx = pair.indexOf('=')
            val key = if (eqIdx >= 0) pair.substring(0, eqIdx) else pair
            val decodedKey = runCatching { URLDecoder.decode(key, "UTF-8") }.getOrDefault(key)
            if (decodedKey == "token" || decodedKey == "puter.auth_token" || decodedKey == "auth_token") {
                val rawVal = if (eqIdx >= 0) pair.substring(eqIdx + 1) else ""
                val decodedVal = runCatching { URLDecoder.decode(rawVal, "UTF-8") }.getOrDefault(rawVal)
                val norm = normalizeAuthToken(decodedVal)
                if (norm.isNotEmpty()) return norm
            }
        }
        return null
    }

    fun buildDriversCallJson(
        authToken: String,
        prompt: String,
        images: List<PuterImageInput> = emptyList(),
        systemInstruction: String? = null,
        model: String = MODEL_GPT_4O_MINI
    ): String {
        val messagesArray = buildJsonArray {
            if (!systemInstruction.isNullOrBlank()) {
                add(
                    buildJsonObject {
                        put("role", "system")
                        put("content", systemInstruction.trim())
                    }
                )
            }
            if (images.isEmpty()) {
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
                                add(JsonPrimitive(prompt))
                                images.forEach { img ->
                                    val dataUri = "data:${img.mimeType};base64,${img.base64Data}"
                                    add(
                                        buildJsonObject {
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

        val argsObject = buildJsonObject {
            put("messages", messagesArray)
            put("model", model)
            if (images.isNotEmpty()) {
                put("vision", true)
            }
        }

        val root = buildJsonObject {
            put("interface", "puter-chat-completion")
            put("driver", "ai-chat")
            put("test_mode", false)
            put("method", "complete")
            put("auth_token", normalizeAuthToken(authToken))
            put("args", argsObject)
        }
        return root.toString()
    }

    fun parseWhoamiUsername(rawBody: String): String? {
        val root = runCatching { jsonParser.parseToJsonElement(rawBody) as? JsonObject }.getOrNull()
            ?: return null
        val direct = (root["username"] as? JsonPrimitive)?.contentOrNull?.trim()
        if (!direct.isNullOrEmpty()) return direct
        val nested = ((root["user"] as? JsonObject)?.get("username") as? JsonPrimitive)?.contentOrNull?.trim()
        if (!nested.isNullOrEmpty()) return nested
        val uuid = (root["uuid"] as? JsonPrimitive)?.contentOrNull?.trim()
        if (!uuid.isNullOrEmpty()) return "user_${uuid.take(6)}"
        return null
    }

    fun parseUsageReport(rawBody: String): PuterUsageReport {
        val root = runCatching { jsonParser.parseToJsonElement(rawBody) as? JsonObject }.getOrNull()
            ?: throw IllegalStateException("Could not parse Puter usage response.")
        val usage = root["usage"] as? JsonObject
            ?: throw IllegalStateException(
                (root["message"] as? JsonPrimitive)?.contentOrNull ?: "Puter usage report unavailable."
            )
        val allowanceInfo = root["allowanceInfo"] as? JsonObject

        val capacity = ((allowanceInfo?.get("monthUsageAllowance") as? JsonPrimitive)?.doubleOrNull ?: 0.0)
            .coerceAtLeast(0.0)
        val usageTotal = ((usage["total"] as? JsonPrimitive)?.doubleOrNull ?: 0.0)
            .coerceAtLeast(0.0)
        val rawAllowanceUsed = (usage["allowanceUsed"] as? JsonPrimitive)?.doubleOrNull
            ?: (allowanceInfo?.get("allowanceUsed") as? JsonPrimitive)?.doubleOrNull
        val used = minOf(
            rawAllowanceUsed?.coerceAtLeast(0.0) ?: minOf(usageTotal, capacity),
            usageTotal
        ).coerceAtLeast(0.0)

        val explicitRemaining = (allowanceInfo?.get("remaining") as? JsonPrimitive)?.doubleOrNull
        val monthlyRemaining = (explicitRemaining ?: (capacity - used)).coerceAtLeast(0.0)

        val addons = allowanceInfo?.get("addons") as? JsonObject
        val purchasedCredits = ((addons?.get("purchasedCredits") as? JsonPrimitive)?.doubleOrNull ?: 0.0)
            .coerceAtLeast(0.0)
        val consumedPurchased = ((addons?.get("consumedPurchaseCredits") as? JsonPrimitive)?.doubleOrNull ?: 0.0)
            .coerceAtLeast(0.0)
        val purchasedRemaining = if (purchasedCredits > 0.0) {
            (purchasedCredits - consumedPurchased).coerceAtLeast(0.0)
        } else {
            0.0
        }

        val unit = (allowanceInfo?.get("unit") as? JsonPrimitive)?.contentOrNull.orEmpty()
        return PuterUsageReport(
            monthlyUsedRaw = used,
            monthlyAllowanceRaw = capacity,
            monthlyRemainingRaw = monthlyRemaining,
            purchasedCreditsRaw = purchasedCredits,
            consumedPurchasedRaw = consumedPurchased,
            purchasedRemainingRaw = purchasedRemaining,
            isCreditsUnit = unit.equals("credits", ignoreCase = true)
        )
    }

    fun formatBalanceValue(rawValue: Double, isCreditsUnit: Boolean): String {
        val safe = if (rawValue.isFinite()) rawValue.coerceAtLeast(0.0) else 0.0
        if (isCreditsUnit) {
            if (safe <= 0.0) return "0 cr"
            if (safe < 0.01) return "<0.01 cr"
            return if (safe >= 100.0) {
                val nf = NumberFormat.getIntegerInstance(Locale.US)
                "${nf.format(safe.roundToInt())} cr"
            } else {
                String.format(Locale.US, "%.2f cr", safe)
            }
        }
        val usd = safe / 100_000_000.0
        if (usd <= 0.0) return "$0.00"
        if (abs(usd) < 0.005) return "<$0.01"
        return String.format(Locale.US, "$%.2f", usd)
    }

    fun formatUsageBannerLabel(
        remainingRaw: Double,
        allowanceRaw: Double,
        isCreditsUnit: Boolean,
        exhausted: Boolean
    ): String {
        if (exhausted || (allowanceRaw > 0.0 && remainingRaw <= 0.0)) {
            return if (isCreditsUnit) "0 cr left" else "$0.00 left"
        }
        if (allowanceRaw <= 0.0 && remainingRaw <= 0.0) {
            return "Credits ready"
        }
        return "${formatBalanceValue(remainingRaw, isCreditsUnit)} left"
    }

    fun extractResponseText(rawBody: String): String {
        val root = runCatching { jsonParser.parseToJsonElement(rawBody) as? JsonObject }.getOrNull()
            ?: throw PuterApiException(null, "Puter AI returned an unreadable response.")

        val success = (root["success"] as? JsonPrimitive)?.booleanOrNull
        if (success == false || root["error"] != null) {
            val detail = extractErrorFromJsonObject(root)
            throw PuterApiException(
                httpCode = null,
                message = detail.message.ifBlank { "Puter AI request failed." },
                errorCode = detail.code,
                usageLimited = detail.usageLimited
            )
        }

        val resultObj = root["result"] as? JsonObject ?: root
        val collected = StringBuilder()

        val msgObj = resultObj["message"] as? JsonObject
        val contentEl = msgObj?.get("content")
        if (contentEl != null) {
            appendContentElement(collected, contentEl)
        }

        if (collected.isEmpty()) {
            val choices = resultObj["choices"] as? JsonArray
            val firstChoice = choices?.firstOrNull() as? JsonObject
            val choiceMsg = firstChoice?.get("message") as? JsonObject
            val choiceContent = choiceMsg?.get("content")
            if (choiceContent != null) {
                appendContentElement(collected, choiceContent)
            }
        }

        if (collected.isEmpty()) {
            val textDirect = (resultObj["text"] as? JsonPrimitive)?.contentOrNull
            if (!textDirect.isNullOrBlank()) {
                collected.append(textDirect)
            }
        }

        val textOut = collected.toString().trim()
        if (textOut.isEmpty()) {
            val detail = extractErrorFromJsonObject(root)
            throw PuterApiException(
                httpCode = null,
                message = detail.message.ifBlank { "Puter AI returned empty output." },
                errorCode = detail.code,
                usageLimited = detail.usageLimited
            )
        }
        return textOut
    }

    private fun appendContentElement(out: StringBuilder, el: JsonElement) {
        when (el) {
            is JsonPrimitive -> out.append(el.contentOrNull.orEmpty())
            is JsonArray -> {
                for (item in el) {
                    when (item) {
                        is JsonPrimitive -> out.append(item.contentOrNull.orEmpty())
                        is JsonObject -> {
                            val type = (item["type"] as? JsonPrimitive)?.contentOrNull
                            if (type == null || type == "text" || type == "output_text") {
                                val t = (item["text"] as? JsonPrimitive)?.contentOrNull
                                    ?: (item["content"] as? JsonPrimitive)?.contentOrNull
                                out.append(t.orEmpty())
                            }
                        }
                        else -> Unit
                    }
                }
            }
            else -> Unit
        }
    }

    fun extractErrorDetail(rawBody: String, httpCode: Int): PuterParsedError {
        val trimmed = rawBody.trim()
        if (trimmed.isNotEmpty()) {
            val parsed = runCatching { jsonParser.parseToJsonElement(trimmed) }.getOrNull()
            if (parsed is JsonObject) {
                val detail = extractErrorFromJsonObject(parsed)
                if (detail.message.isNotBlank() || !detail.code.isNullOrBlank() || detail.usageLimited) {
                    return PuterParsedError(
                        message = detail.message.ifBlank { "Puter HTTP $httpCode" },
                        code = detail.code,
                        usageLimited = detail.usageLimited
                    )
                }
            }
        }
        return PuterParsedError(
            message = "Puter HTTP $httpCode",
            code = null,
            usageLimited = httpCode == 402
        )
    }

    private fun extractErrorFromJsonObject(obj: JsonObject): PuterParsedError {
        val errEl = obj["error"]
        val errObj = errEl as? JsonObject
        val metadata = (errObj?.get("metadata") as? JsonObject) ?: (obj["metadata"] as? JsonObject)
        val usageLimited = (metadata?.get("usage_limited") as? JsonPrimitive)?.booleanOrNull == true
        val code = (errObj?.get("code") as? JsonPrimitive)?.contentOrNull
            ?: (obj["code"] as? JsonPrimitive)?.contentOrNull
        val msg = when (errEl) {
            is JsonObject -> (errEl["message"] as? JsonPrimitive)?.contentOrNull
            is JsonPrimitive -> errEl.contentOrNull
            else -> null
        } ?: (obj["message"] as? JsonPrimitive)?.contentOrNull ?: ""
        return PuterParsedError(
            message = msg.trim(),
            code = code?.trim()?.ifBlank { null },
            usageLimited = usageLimited
        )
    }

    fun looksLikeOutOfCredits(
        httpCode: Int? = null,
        errorCode: String? = null,
        rawMessage: String? = null,
        usageLimited: Boolean = false
    ): Boolean {
        if (httpCode == 402 || usageLimited) return true
        val c = errorCode?.lowercase(Locale.US).orEmpty()
        if (c == "insufficient_funds" || c == "usage_limited" || c == "quota_exceeded") return true
        val m = rawMessage?.lowercase(Locale.US).orEmpty()
        return m.contains("insufficient_funds") ||
            m.contains("not enough funding") ||
            m.contains("out of credits") ||
            m.contains("out of puter credits") ||
            m.contains("credit limit") ||
            m.contains("usage_limited") ||
            m.contains("usage limit reached") ||
            m.contains("allowance exceeded")
    }

    fun classifyFailure(
        httpCode: Int?,
        rawMessage: String,
        errorCode: String? = null,
        usageLimited: Boolean = false,
        isTimeout: Boolean = false,
        isDnsOrConnectError: Boolean = false
    ): PuterFailureCategory {
        if (looksLikeOutOfCredits(httpCode, errorCode, rawMessage, usageLimited)) {
            return PuterFailureCategory.INSUFFICIENT_CREDITS
        }
        val msg = rawMessage.lowercase(Locale.US)
        val codeLower = errorCode?.lowercase(Locale.US).orEmpty()
        if (httpCode == 401 ||
            httpCode == 403 ||
            codeLower == "token_missing" ||
            codeLower == "invalid_token" ||
            codeLower == "unauthorized" ||
            msg.contains("token_missing") ||
            msg.contains("token missing") ||
            msg.contains("invalid token") ||
            msg.contains("token expired") ||
            msg.contains("unauthorized") ||
            msg.contains("authentication failed") ||
            msg.contains("sign in required")
        ) {
            return PuterFailureCategory.FATAL_AUTH
        }
        if (isDnsOrConnectError) return PuterFailureCategory.NETWORK_UNREACHABLE
        if (isTimeout ||
            msg.contains("timed out") ||
            msg.contains("timeout")
        ) {
            return PuterFailureCategory.NETWORK_TIMEOUT
        }
        if (httpCode == 429 ||
            httpCode == 503 ||
            httpCode == 404 ||
            msg.contains("rate limit") ||
            msg.contains("too many requests") ||
            msg.contains("overloaded") ||
            msg.contains("high traffic") ||
            msg.contains("temporarily unavailable") ||
            msg.contains("not found")
        ) {
            return PuterFailureCategory.RATE_OR_OVERLOAD
        }
        if (httpCode != null && httpCode in 500..599) {
            return PuterFailureCategory.TRANSIENT_SERVER
        }
        return PuterFailureCategory.TRANSIENT_SERVER
    }

    fun buildFriendlyErrorMessage(
        lastCategory: PuterFailureCategory,
        lastRawMessage: String,
        attemptedModels: List<String>,
        sawAnyHttpResponse: Boolean
    ): String {
        val tried = attemptedModels.distinct().joinToString(" -> ").ifBlank { MODEL_GPT_4O_MINI }
        val cleanRaw = lastRawMessage.trim()
        return when {
            lastCategory == PuterFailureCategory.INSUFFICIENT_CREDITS -> {
                "Your Puter.js credits have run out. Switch to another Puter or Google account to continue."
            }
            lastCategory == PuterFailureCategory.FATAL_AUTH -> {
                "Puter session expired or is missing. Sign in again with Google / Puter."
            }
            !sawAnyHttpResponse &&
                (lastCategory == PuterFailureCategory.NETWORK_TIMEOUT || lastCategory == PuterFailureCategory.NETWORK_UNREACHABLE) -> {
                "Could not reach api.puter.com ($tried). Check your Wi-Fi or mobile data and try again."
            }
            lastCategory == PuterFailureCategory.NETWORK_TIMEOUT -> {
                "Puter AI timed out across $tried. Try switching model route or retry in a moment."
            }
            lastCategory == PuterFailureCategory.RATE_OR_OVERLOAD -> {
                "Selected Puter AI routes ($tried) are busy right now. Try switching route or retry shortly."
            }
            else -> {
                val base = cleanRaw.ifBlank { "Puter AI request failed" }
                "$base (tried $tried)"
            }
        }
    }

    fun stripJsonFences(raw: String): String {
        var t = raw.trim()
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
}
