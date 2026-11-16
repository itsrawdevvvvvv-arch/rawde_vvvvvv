package com.rawedit.app.domain

import com.rawedit.app.data.CacheManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

data class AspectPreset(
    val id: String,
    val label: String,
    val width: Int,
    val height: Int
) {
    val ratioFloat: Float get() = width.toFloat() / height.coerceAtLeast(1).toFloat()

    companion object {
        val ALL_PRESETS = listOf(
            AspectPreset("16:9", "16:9", 1920, 1080),
            AspectPreset("9:16", "9:16", 1080, 1920),
            AspectPreset("4:5", "4:5", 1080, 1350),
            AspectPreset("1:1", "1:1", 1080, 1080),
            AspectPreset("3:4", "3:4", 1080, 1440),
            AspectPreset("4:3", "4:3", 1440, 1080),
            AspectPreset("21:9", "21:9", 1920, 822)
        )

        fun custom(w: Int, h: Int): AspectPreset {
            val safeW = ((w.coerceIn(320, 2160) + 1) / 2) * 2
            val safeH = ((h.coerceIn(320, 2160) + 1) / 2) * 2
            return AspectPreset("custom", "${safeW}x${safeH}", safeW, safeH)
        }
    }
}

object AspectPresetStore {
    private fun file(cacheManager: CacheManager) = File(cacheManager.sharedPacksDir, "project_aspect_ratios.json")

    fun get(cacheManager: CacheManager, projectId: Long): AspectPreset {
        val f = file(cacheManager)
        if (!f.exists()) return AspectPreset.ALL_PRESETS.first()
        return runCatching {
            val root = JSONObject(f.readText(Charsets.UTF_8))
            val o = root.optJSONObject(projectId.toString()) ?: return AspectPreset.ALL_PRESETS.first()
            AspectPreset(
                id = o.optString("id", "16:9"),
                label = o.optString("label", "16:9"),
                width = o.optInt("width", 1920).coerceIn(320, 2160),
                height = o.optInt("height", 1080).coerceIn(320, 2160)
            )
        }.getOrDefault(AspectPreset.ALL_PRESETS.first())
    }

    fun set(cacheManager: CacheManager, projectId: Long, preset: AspectPreset) {
        runCatching {
            val f = file(cacheManager)
            f.parentFile?.mkdirs()
            val root = if (f.exists()) runCatching { JSONObject(f.readText(Charsets.UTF_8)) }.getOrDefault(JSONObject()) else JSONObject()
            root.put(projectId.toString(), JSONObject().apply {
                put("id", preset.id)
                put("label", preset.label)
                put("width", preset.width)
                put("height", preset.height)
            })
            f.writeText(root.toString(2), Charsets.UTF_8)
        }
    }
}

data class StylePreset(
    val id: String = "preset_${System.currentTimeMillis()}",
    val name: String,
    val aspectId: String,
    val stageWidth: Int,
    val stageHeight: Int,
    val paletteJson: String,
    val customPromptNotes: String,
    val createdAtMs: Long = System.currentTimeMillis()
)

object StylePresetStore {
    private fun file(cacheManager: CacheManager) = File(cacheManager.sharedPacksDir, "style_presets.json")

    fun list(cacheManager: CacheManager): List<StylePreset> {
        val f = file(cacheManager)
        if (!f.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(f.readText(Charsets.UTF_8))
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                StylePreset(
                    id = o.optString("id", "preset_$i"),
                    name = o.optString("name", "Preset ${i + 1}"),
                    aspectId = o.optString("aspectId", "16:9"),
                    stageWidth = o.optInt("stageWidth", 1920),
                    stageHeight = o.optInt("stageHeight", 1080),
                    paletteJson = o.optString("paletteJson", ""),
                    customPromptNotes = o.optString("customPromptNotes", ""),
                    createdAtMs = o.optLong("createdAtMs", 0L)
                )
            }.sortedByDescending { it.createdAtMs }
        }.getOrDefault(emptyList())
    }

    fun save(cacheManager: CacheManager, preset: StylePreset) {
        runCatching {
            val f = file(cacheManager)
            f.parentFile?.mkdirs()
            val current = list(cacheManager).filterNot { it.id == preset.id }.toMutableList()
            current.add(0, preset)
            val arr = JSONArray()
            current.take(25).forEach { p ->
                arr.put(JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("aspectId", p.aspectId)
                    put("stageWidth", p.stageWidth)
                    put("stageHeight", p.stageHeight)
                    put("paletteJson", p.paletteJson)
                    put("customPromptNotes", p.customPromptNotes)
                    put("createdAtMs", p.createdAtMs)
                })
            }
            f.writeText(arr.toString(2), Charsets.UTF_8)
        }
    }

    fun delete(cacheManager: CacheManager, id: String) {
        runCatching {
            val f = file(cacheManager)
            val remaining = list(cacheManager).filterNot { it.id == id }
            val arr = JSONArray()
            remaining.forEach { p ->
                arr.put(JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("aspectId", p.aspectId)
                    put("stageWidth", p.stageWidth)
                    put("stageHeight", p.stageHeight)
                    put("paletteJson", p.paletteJson)
                    put("customPromptNotes", p.customPromptNotes)
                    put("createdAtMs", p.createdAtMs)
                })
            }
            f.writeText(arr.toString(2), Charsets.UTF_8)
        }
    }
}

data class CaptionPackResult(
    val captions: List<String>,
    val pinnedComments: List<String>
)

object CaptionGenerator {
    suspend fun generateFromSrt(
        geminiRouter: GeminiWaterfallRouter,
        apiKey: String,
        title: String,
        topic: String,
        srtText: String
    ): Result<CaptionPackResult> {
        if (apiKey.isBlank()) {
            return Result.failure(IllegalStateException("Add your Gemini API key in Profile -> AI & Quota first."))
        }
        val sourceText = buildString {
            if (title.isNotBlank()) appendLine("TITLE: $title")
            if (topic.isNotBlank()) appendLine("SCRIPT/TOPIC:\n${topic.take(3000)}")
            if (srtText.isNotBlank()) appendLine("SRT:\n${srtText.take(5000)}")
        }.trim()
        if (sourceText.isBlank()) {
            return Result.failure(IllegalStateException("Add audio/SRT or script first."))
        }

        val prompt = """
            Based on this reel's SRT & script, generate:
            1) 3 high-retention Instagram/Shorts captions (punchy Hinglish/English hook + short value + 5 relevant hashtags).
            2) 2 pinned comment CTAs (e.g. Comment "LINK" / "PROMPT" auto-DM style or a curiosity question to boost comments).

            Return STRICT JSON only:
            {
              "captions": ["caption 1", "caption 2", "caption 3"],
              "pinnedComments": ["pinned 1", "pinned 2"]
            }

            $sourceText
        """.trimIndent()

        return geminiRouter.generate(
            apiKey = apiKey,
            prompt = prompt,
            systemInstruction = "You are an Indian creator growth strategist (RAWDEV style). Return strict valid JSON only.",
            routeMode = GeminiRouteMode.FAST_CHAT,
            expectJson = true,
            maxOutputTokens = 1536
        ).mapCatching { res ->
            val raw = res.text.trim()
                .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val obj = JSONObject(raw)
            val capsArr = obj.optJSONArray("captions") ?: JSONArray()
            val pinArr = obj.optJSONArray("pinnedComments") ?: JSONArray()
            val caps = (0 until capsArr.length()).mapNotNull { capsArr.optString(it).takeIf { s -> s.isNotBlank() } }
            val pins = (0 until pinArr.length()).mapNotNull { pinArr.optString(it).takeIf { s -> s.isNotBlank() } }
            if (caps.isEmpty() && pins.isEmpty()) error("Empty caption response")
            CaptionPackResult(captions = caps, pinnedComments = pins)
        }
    }
}
