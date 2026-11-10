package com.rawedit.app.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class RawLayer(
    val id: String,
    val name: String = "",
    val type: String = "text",
    val x: Float = 0f,
    val y: Float = 0f,
    val w: Float = 400f,
    val h: Float = 120f,
    val opacity: Float = 1f,
    val z: Int = 1,
    val start: Double = 0.0,
    val end: Double = 5.0,
    val text: String? = null,
    val src: String? = null,
    val locked: Boolean = false,
    val hidden: Boolean = false
) {
    fun toStageExport(): RawLayerExport = RawLayerExport(
        id = id,
        name = name,
        type = type,
        x = x,
        y = y,
        w = w,
        h = h,
        opacity = if (hidden) 0f else opacity.coerceIn(0f, 1f),
        z = z,
        start = start,
        end = end,
        text = text,
        src = src
    )
}

@Serializable
data class RawLayerExport(
    val id: String,
    val name: String,
    val type: String,
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val opacity: Float,
    val z: Int,
    val start: Double,
    val end: Double,
    val text: String? = null,
    val src: String? = null
)

object LayerJson {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    fun decode(raw: String): List<RawLayer> {
        if (raw.isBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<RawLayer>>(raw) }.getOrDefault(emptyList())
    }

    fun encode(layers: List<RawLayer>): String = json.encodeToString(layers)

    fun encodeForStage(layers: List<RawLayer>): String =
        json.encodeToString(layers.map { it.toStageExport() })
}
