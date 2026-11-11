package com.rawedit.app.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import java.util.Locale

data class InteractionImageInput(
    val base64Data: String,
    val mimeType: String
)

data class GeminiModelOption(
    val prefId: String,
    val shortLabel: String,
    val badgeLabel: String,
    val detail: String
)

enum class GeminiFailureCategory {
    FATAL_CLIENT,
    RATE_OR_OVERLOAD,
    NETWORK_TIMEOUT,
    NETWORK_UNREACHABLE,
    TRANSIENT_SERVER
}

object GeminiInteractionsCodec {

    const val MODEL_ID = "gemini-3.8-flash"
    const val MODEL_FLASH_FALLBACK = "gemini-3.6-flash"
    const val MODEL_LITE_FAST = "gemini-3.5-flash-lite"
    const val MODEL_LITE_BACKUP = "gemini-3.1-flash-lite"
    const val PREF_AUTO = "auto"

    const val INTERACTIONS_URL = "https://generativelanguage.googleapis.com/v1beta/interactions"
    const val MODELS_CHECK_URL = "https://generativelanguage.googleapis.com/v1beta/models?pageSize=1"

    val MODEL_OPTIONS: List<GeminiModelOption> = listOf(
        GeminiModelOption(
            prefId = PREF_AUTO,
            shortLabel = "Auto",
            badgeLabel = "AUTO SMART FALLBACK",
            detail = "Uses low-congestion Flash/Lite first for fast ops & auto-rotates if high traffic or timeout occurs."
        ),
        GeminiModelOption(
            prefId = MODEL_FLASH_FALLBACK,
            shortLabel = "3.6 Flash",
            badgeLabel = "GEMINI 3.6 FLASH",
            detail = "Fast multimodal Flash with minimal thinking; fails over to 3.5 Lite if busy."
        ),
        GeminiModelOption(
            prefId = MODEL_LITE_FAST,
            shortLabel = "3.5 Lite",
            badgeLabel = "GEMINI 3.5 LITE",
            detail = "Lowest latency Flash-Lite route when flagship models hit high traffic."
        ),
        GeminiModelOption(
            prefId = MODEL_ID,
            shortLabel = "3.8 Flash",
            badgeLabel = "GEMINI 3.8 FLASH",
            detail = "Flagship reasoning first, auto-switching to 3.6 Flash or Lite on overload/timeout."
        )
    )

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    val PALETTE_SCHEMA: JsonObject = buildJsonObject {
        put("type", "object")
        put(
            "properties",
            buildJsonObject {
                put(
                    "colors",
                    buildJsonObject {
                        put("type", "array")
                        put(
                            "items",
                            buildJsonObject {
                                put("type", "object")
                                put(
                                    "properties",
                                    buildJsonObject {
                                        put(
                                            "hex",
                                            buildJsonObject {
                                                put("type", "string")
                                                put("description", "6-digit hex color code starting with #")
                                            }
                                        )
                                        put(
                                            "name",
                                            buildJsonObject {
                                                put("type", "string")
                                                put("description", "Two word lowercase editorial color name")
                                            }
                                        )
                                    }
                                )
                                put(
                                    "required",
                                    buildJsonArray {
                                        add(JsonPrimitive("hex"))
                                        add(JsonPrimitive("name"))
                                    }
                                )
                            }
                        )
                    }
                )
                put(
                    "mood",
                    buildJsonObject {
                        put("type", "string")
                        put("description", "2 to 4 word vibe")
                    }
                )
                put(
                    "pairing",
                    buildJsonObject {
                        put("type", "string")
                        put("description", "One concise sentence explaining background vs headline vs accent usage")
                    }
                )
            }
        )
        put(
            "required",
            buildJsonArray {
                add(JsonPrimitive("colors"))
                add(JsonPrimitive("mood"))
                add(JsonPrimitive("pairing"))
            }
        )
    }

    val DESCRIBE_ASSET_SCHEMA: JsonObject = buildJsonObject {
        put("type", "object")
        put(
            "properties",
            buildJsonObject {
                put(
                    "description",
                    buildJsonObject {
                        put("type", "string")
                        put("description", "1-2 concise sentences describing visual content and optional UI target boxes")
                    }
                )
                put(
                    "safeZone",
                    buildJsonObject {
                        put("type", "string")
                        put("description", "1 concise sentence stating where kinetic overlay typography fits safely with high contrast")
                    }
                )
            }
        )
        put(
            "required",
            buildJsonArray {
                add(JsonPrimitive("description"))
                add(JsonPrimitive("safeZone"))
            }
        )
    }

    val AUDIT_CUTOUT_SCHEMA: JsonObject = buildJsonObject {
        put("type", "object")
        put(
            "properties",
            buildJsonObject {
                put(
                    "status",
                    buildJsonObject {
                        put("type", "string")
                        put(
                            "enum",
                            buildJsonArray {
                                add(JsonPrimitive("OK"))
                                add(JsonPrimitive("ADJUST"))
                            }
                        )
                    }
                )
                put(
                    "diagnosis",
                    buildJsonObject {
                        put("type", "string")
                    }
                )
                put(
                    "keepOriginal",
                    buildJsonObject {
                        put("type", "boolean")
                    }
                )
                put(
                    "recommendedTolerance",
                    buildJsonObject {
                        put("type", "integer")
                    }
                )
                put(
                    "extraPadTop",
                    buildJsonObject {
                        put("type", "number")
                    }
                )
                put(
                    "extraPadRight",
                    buildJsonObject {
                        put("type", "number")
                    }
                )
                put(
                    "extraPadBottom",
                    buildJsonObject {
                        put("type", "number")
                    }
                )
                put(
                    "extraPadLeft",
                    buildJsonObject {
                        put("type", "number")
                    }
                )
            }
        )
        put(
            "required",
            buildJsonArray {
                add(JsonPrimitive("status"))
                add(JsonPrimitive("diagnosis"))
                add(JsonPrimitive("keepOriginal"))
                add(JsonPrimitive("recommendedTolerance"))
                add(JsonPrimitive("extraPadTop"))
                add(JsonPrimitive("extraPadRight"))
                add(JsonPrimitive("extraPadBottom"))
                add(JsonPrimitive("extraPadLeft"))
            }
        )
    }

    val REVIEW_SHEET_SCHEMA: JsonObject = buildJsonObject {
        put("type", "object")
        put(
            "properties",
            buildJsonObject {
                put(
                    "summary",
                    buildJsonObject {
                        put("type", "string")
                    }
                )
                put(
                    "issues",
                    buildJsonObject {
                        put("type", "array")
                        put(
                            "items",
                            buildJsonObject {
                                put("type", "object")
                                put(
                                    "properties",
                                    buildJsonObject {
                                        put("frame", buildJsonObject { put("type", "string") })
                                        put("time", buildJsonObject { put("type", "string") })
                                        put("problem", buildJsonObject { put("type", "string") })
                                        put("fix", buildJsonObject { put("type", "string") })
                                    }
                                )
                                put(
                                    "required",
                                    buildJsonArray {
                                        add(JsonPrimitive("frame"))
                                        add(JsonPrimitive("time"))
                                        add(JsonPrimitive("problem"))
                                        add(JsonPrimitive("fix"))
                                    }
                                )
                            }
                        )
                    }
                )
            }
        )
        put(
            "required",
            buildJsonArray {
                add(JsonPrimitive("summary"))
                add(JsonPrimitive("issues"))
            }
        )
    }

    fun normalizeApiKey(rawKey: String): String {
        return rawKey.filterNot { it.isWhitespace() }
            .removeSurrounding("\"")
            .removeSurrounding("'")
    }

    fun normalizeModelPref(rawPref: String): String {
        return when (rawPref.trim().lowercase(Locale.US)) {
            PREF_AUTO -> PREF_AUTO
            MODEL_ID -> MODEL_ID
            MODEL_FLASH_FALLBACK -> MODEL_FLASH_FALLBACK
            MODEL_LITE_FAST -> MODEL_LITE_FAST
            MODEL_LITE_BACKUP -> MODEL_LITE_BACKUP
            else -> PREF_AUTO
        }
    }

    fun optionForPref(rawPref: String): GeminiModelOption {
        val norm = normalizeModelPref(rawPref)
        return MODEL_OPTIONS.firstOrNull { it.prefId == norm } ?: MODEL_OPTIONS.first()
    }

    fun routeDisplayLabel(rawPref: String): String {
        return when (normalizeModelPref(rawPref)) {
            PREF_AUTO -> "Auto (Smart Fallback)"
            MODEL_FLASH_FALLBACK -> "Gemini 3.6 Flash"
            MODEL_LITE_FAST -> "Gemini 3.5 Flash-Lite"
            MODEL_LITE_BACKUP -> "Gemini 3.1 Flash-Lite"
            MODEL_ID -> "Gemini 3.8 Flash"
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
            MODEL_FLASH_FALLBACK -> listOf(MODEL_FLASH_FALLBACK, MODEL_LITE_FAST, MODEL_LITE_BACKUP, MODEL_ID)
            MODEL_LITE_FAST -> listOf(MODEL_LITE_FAST, MODEL_LITE_BACKUP, MODEL_FLASH_FALLBACK, MODEL_ID)
            MODEL_LITE_BACKUP -> listOf(MODEL_LITE_BACKUP, MODEL_LITE_FAST, MODEL_FLASH_FALLBACK, MODEL_ID)
            MODEL_ID -> listOf(MODEL_ID, MODEL_FLASH_FALLBACK, MODEL_LITE_FAST, MODEL_LITE_BACKUP)
            else -> if (heavyTask) {
                listOf(MODEL_ID, MODEL_FLASH_FALLBACK, MODEL_LITE_FAST, MODEL_LITE_BACKUP)
            } else {
                listOf(MODEL_FLASH_FALLBACK, MODEL_LITE_FAST, MODEL_ID, MODEL_LITE_BACKUP)
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

    fun supportsMinimalThinking(model: String): Boolean {
        return when (model.trim().lowercase(Locale.US)) {
            MODEL_FLASH_FALLBACK, MODEL_LITE_FAST, "gemini-3-flash-preview" -> true
            else -> false
        }
    }

    fun normalizeThinkingLevelForModel(requested: String, model: String = MODEL_ID): String {
        val norm = requested.trim().lowercase(Locale.US)
        return when (norm) {
            "minimal" -> if (supportsMinimalThinking(model)) "minimal" else "low"
            "low", "medium", "high" -> norm
            else -> if (supportsMinimalThinking(model)) "low" else "medium"
        }
    }

    fun normalizeThinkingLevel(requested: String): String {
        return normalizeThinkingLevelForModel(requested, MODEL_ID)
    }

    fun classifyFailure(
        httpCode: Int?,
        rawMessage: String,
        isTimeout: Boolean = false,
        isDnsOrConnectError: Boolean = false
    ): GeminiFailureCategory {
        val msg = rawMessage.lowercase(Locale.US)
        if (isDnsOrConnectError) return GeminiFailureCategory.NETWORK_UNREACHABLE
        if (isTimeout ||
            msg.contains("timed out") ||
            msg.contains("timeout") ||
            msg.contains("deadline_exceeded") ||
            msg.contains("deadline exceeded")
        ) {
            return GeminiFailureCategory.NETWORK_TIMEOUT
        }
        if (httpCode == 429 ||
            httpCode == 503 ||
            msg.contains("high traffic") ||
            msg.contains("high demand") ||
            msg.contains("overloaded") ||
            msg.contains("capacity") ||
            msg.contains("temporarily unavailable") ||
            msg.contains("resource_exhausted") ||
            msg.contains("rate limit") ||
            msg.contains("too many requests") ||
            msg.contains("quota")
        ) {
            return GeminiFailureCategory.RATE_OR_OVERLOAD
        }
        if (httpCode != null && httpCode in 500..599) {
            return GeminiFailureCategory.TRANSIENT_SERVER
        }
        if (httpCode == 400 || httpCode == 401 || httpCode == 403 ||
            msg.contains("api key") ||
            msg.contains("api_key") ||
            msg.contains("permission denied") ||
            msg.contains("unauthorized") ||
            msg.contains("billing")
        ) {
            return GeminiFailureCategory.FATAL_CLIENT
        }
        if (httpCode == 404) {
            // Treat 404 model unavailability as transient across fallbacks
            return GeminiFailureCategory.RATE_OR_OVERLOAD
        }
        return GeminiFailureCategory.TRANSIENT_SERVER
    }

    fun buildFriendlyErrorMessage(
        lastCategory: GeminiFailureCategory,
        lastRawMessage: String,
        attemptedModels: List<String>,
        sawAnyHttpResponse: Boolean
    ): String {
        val tried = attemptedModels.distinct().joinToString(" -> ").ifBlank { MODEL_ID }
        val cleanRaw = lastRawMessage.trim()
        return when {
            lastCategory == GeminiFailureCategory.FATAL_CLIENT -> {
                cleanRaw.ifBlank { "Google AI Studio rejected the API key or request." }
            }
            !sawAnyHttpResponse && (lastCategory == GeminiFailureCategory.NETWORK_TIMEOUT || lastCategory == GeminiFailureCategory.NETWORK_UNREACHABLE) -> {
                "Connection to Google AI timed out before reaching Gemini ($tried). Try switching Wi-Fi <-> 4G/5G or disabling VPN / Private DNS, then retry."
            }
            lastCategory == GeminiFailureCategory.NETWORK_TIMEOUT -> {
                "Gemini inference timed out under heavy server load (tried $tried). Try selecting 3.5 Lite or 3.6 Flash in Key settings or retry shortly."
            }
            lastCategory == GeminiFailureCategory.RATE_OR_OVERLOAD -> {
                "High traffic / rate limit across tried models ($tried). Try selecting 3.5 Lite in Key settings or retry in ~30 seconds."
            }
            else -> {
                val base = cleanRaw.ifBlank { "Gemini request failed" }
                "$base (tried $tried)"
            }
        }
    }

    fun buildRequestJson(
        prompt: String,
        images: List<InteractionImageInput> = emptyList(),
        thinkingLevel: String = "medium",
        systemInstruction: String? = null,
        jsonSchema: JsonObject? = null,
        model: String = MODEL_ID
    ): String {
        val inputArray = buildJsonArray {
            add(
                buildJsonObject {
                    put("type", "text")
                    put("text", prompt)
                }
            )
            images.forEach { img ->
                add(
                    buildJsonObject {
                        put("type", "image")
                        put("data", img.base64Data)
                        put("mime_type", img.mimeType)
                    }
                )
            }
        }

        val payload = buildJsonObject {
            put("model", model)
            put("input", inputArray)
            put(
                "generation_config",
                buildJsonObject {
                    put("thinking_level", normalizeThinkingLevelForModel(thinkingLevel, model))
                }
            )
            if (!systemInstruction.isNullOrBlank()) {
                put("system_instruction", systemInstruction.trim())
            }
            if (jsonSchema != null) {
                put(
                    "response_format",
                    buildJsonObject {
                        put("type", "text")
                        put("mime_type", "application/json")
                        put("schema", jsonSchema)
                    }
                )
            }
        }
        return payload.toString()
    }

    fun extractResponseText(rawBody: String): String {
        val root = runCatching { jsonParser.parseToJsonElement(rawBody) as? JsonObject }.getOrNull()
            ?: throw IllegalStateException("Gemini returned an unreadable response.")

        val status = (root["status"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        if (status == "failed" || status == "cancelled") {
            val statusErr = extractErrorFromJsonObject(root)
            throw IllegalStateException(statusErr ?: "Gemini interaction $status.")
        }

        val collected = StringBuilder()

        // Current Interactions API schema (steps[].type == "model_output" -> content[].type == "text")
        val steps = root["steps"] as? JsonArray
        if (steps != null) {
            for (stepEl in steps) {
                val stepObj = stepEl as? JsonObject ?: continue
                val stepType = (stepObj["type"] as? JsonPrimitive)?.contentOrNull
                if (stepType != null && stepType != "model_output") continue
                val content = stepObj["content"] as? JsonArray ?: continue
                for (itemEl in content) {
                    val itemObj = itemEl as? JsonObject ?: continue
                    val itemType = (itemObj["type"] as? JsonPrimitive)?.contentOrNull
                    if (itemType == null || itemType == "text") {
                        collected.append((itemObj["text"] as? JsonPrimitive)?.contentOrNull.orEmpty())
                    }
                }
            }
        }

        // Defensive fallback for legacy flat outputs array if ever returned
        if (collected.isEmpty()) {
            val outputs = root["outputs"] as? JsonArray
            if (outputs != null) {
                for (outEl in outputs) {
                    val outObj = outEl as? JsonObject ?: continue
                    val outType = (outObj["type"] as? JsonPrimitive)?.contentOrNull
                    if (outType == null || outType == "text") {
                        collected.append((outObj["text"] as? JsonPrimitive)?.contentOrNull.orEmpty())
                    }
                }
            }
        }

        val textOut = collected.toString().trim()
        if (textOut.isEmpty()) {
            val errMsg = extractErrorFromJsonObject(root)
            throw IllegalStateException(errMsg ?: "Gemini returned no text output.")
        }
        return textOut
    }

    fun extractErrorMessage(rawBody: String, httpCode: Int): String {
        val trimmed = rawBody.trim()
        if (trimmed.isNotEmpty()) {
            val parsed = runCatching { jsonParser.parseToJsonElement(trimmed) }.getOrNull()
            val msg = when (parsed) {
                is JsonObject -> extractErrorFromJsonObject(parsed)
                is JsonArray -> parsed.firstNotNullOfOrNull { el ->
                    (el as? JsonObject)?.let { extractErrorFromJsonObject(it) }
                }
                else -> null
            }
            if (!msg.isNullOrBlank()) return msg.trim()
        }
        return "Gemini HTTP $httpCode"
    }

    private fun extractErrorFromJsonObject(obj: JsonObject): String? {
        val errObj = obj["error"] as? JsonObject
        val nested = (errObj?.get("message") as? JsonPrimitive)?.contentOrNull
        if (!nested.isNullOrBlank()) return nested.trim()
        val direct = (obj["message"] as? JsonPrimitive)?.contentOrNull
        if (!direct.isNullOrBlank()) return direct.trim()
        return null
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
        return t.trim()
    }
}
