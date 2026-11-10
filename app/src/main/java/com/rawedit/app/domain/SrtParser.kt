package com.rawedit.app.domain

data class SrtCue(
    val index: Int,
    val startSec: Double,
    val endSec: Double,
    val text: String
)

data class SrtParseResult(
    val valid: Boolean,
    val cueCount: Int,
    val cleanedSrt: String,
    val promptSnippet: String,
    val error: String? = null
)

object SrtParser {

    private const val MAX_INPUT_BYTES = 4 * 1024 * 1024
    private const val MAX_PROMPT_BYTES = 2 * 1024 * 1024
    private const val MAX_CUES = 20_000

    private val TIME_LINE_REGEX = Regex(
        """(\d{1,2}):(\d{2}):(\d{2})[,.](\d{3})\s*-->\s*(\d{1,2}):(\d{2}):(\d{2})[,.](\d{3})"""
    )

    fun parse(rawInput: String): SrtParseResult {
        if (rawInput.isBlank()) {
            return SrtParseResult(
                valid = false,
                cueCount = 0,
                cleanedSrt = "",
                promptSnippet = "",
                error = "The SRT file is empty."
            )
        }

        val capped = if (rawInput.toByteArray(Charsets.UTF_8).size > MAX_INPUT_BYTES) {
            truncateUtf8(rawInput, MAX_INPUT_BYTES)
        } else {
            rawInput
        }

        val normalized = stripControlChars(capped)
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .trim()

        val blocks = normalized.split(Regex("\n\\s*\n"))
        val cues = mutableListOf<SrtCue>()

        for (block in blocks) {
            if (cues.size >= MAX_CUES) break
            val lines = block.lines().map { it.trimEnd() }.filter { it.isNotEmpty() }
            if (lines.isEmpty()) continue

            var timeIdx = -1
            var match: MatchResult? = null
            for (i in lines.indices) {
                val m = TIME_LINE_REGEX.find(lines[i])
                if (m != null) {
                    timeIdx = i
                    match = m
                    break
                }
            }
            if (match == null) continue

            val startSec = toSeconds(match.groupValues[1], match.groupValues[2], match.groupValues[3], match.groupValues[4])
            val endSec = toSeconds(match.groupValues[5], match.groupValues[6], match.groupValues[7], match.groupValues[8])
            if (endSec < startSec) continue

            val textLines = lines.drop(timeIdx + 1)
            val cueText = textLines.joinToString("\n").trim()
            if (cueText.isEmpty()) continue

            cues.add(
                SrtCue(
                    index = cues.size + 1,
                    startSec = startSec,
                    endSec = endSec,
                    text = cueText
                )
            )
        }

        if (cues.isEmpty()) {
            return SrtParseResult(
                valid = false,
                cueCount = 0,
                cleanedSrt = "",
                promptSnippet = "",
                error = "No valid SRT subtitle timings were found in this file."
            )
        }

        val rebuilt = buildString {
            cues.forEachIndexed { idx, cue ->
                append(idx + 1).append('\n')
                append(formatTimestamp(cue.startSec)).append(" --> ").append(formatTimestamp(cue.endSec)).append('\n')
                append(cue.text).append("\n\n")
            }
        }.trim()

        val promptSnippet = truncateUtf8(rebuilt, MAX_PROMPT_BYTES)

        return SrtParseResult(
            valid = true,
            cueCount = cues.size,
            cleanedSrt = rebuilt,
            promptSnippet = promptSnippet,
            error = null
        )
    }

    private fun stripControlChars(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            if (ch == '\n' || ch == '\r' || ch == '\t' || ch.code >= 0x20) {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun toSeconds(h: String, m: String, s: String, ms: String): Double {
        return h.toInt() * 3600.0 + m.toInt() * 60.0 + s.toInt() + ms.toInt() / 1000.0
    }

    private fun formatTimestamp(seconds: Double): String {
        val totalMs = (seconds * 1000.0).toLong().coerceAtLeast(0L)
        val h = totalMs / 3_600_000L
        val m = (totalMs % 3_600_000L) / 60_000L
        val s = (totalMs % 60_000L) / 1_000L
        val ms = totalMs % 1_000L
        return "%02d:%02d:%02d,%03d".format(h, m, s, ms)
    }

    private fun truncateUtf8(str: String, maxBytes: Int): String {
        val bytes = str.toByteArray(Charsets.UTF_8)
        if (bytes.size <= maxBytes) return str
        var end = maxBytes
        while (end > 0 && (bytes[end].toInt() and 0xC0) == 0x80) {
            end--
        }
        return String(bytes, 0, end, Charsets.UTF_8)
    }
}
