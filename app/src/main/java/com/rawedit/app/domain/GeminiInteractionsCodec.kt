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

object GeminiInteractionsCodec {

    const val MODEL_ID = "gemini-3.8-flash"
    const val INTERACTIONS_URL = "https://generativelanguage.googleapis.com/v1beta/interactions"

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

    fun normalizeThinkingLevel(requested: String): String {
        // gemini-3.8-flash supports low, medium, and high; minimal returns an API error.
        return when (requested.trim().lowercase(Locale.US)) {
            "low", "medium", "high" -> requested.trim().lowercase(Locale.US)
            else -> "medium"
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
                    put("thinking_level", normalizeThinkingLevel(thinkingLevel))
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
