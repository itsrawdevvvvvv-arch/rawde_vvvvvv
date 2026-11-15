package com.rawedit.app.domain

data class SrtCue(
    val index: Int,
    val startSec: Double,
    val endSec: Double,
    val text: String
)

data class GroqWordTimestamp(
    val word: String,
    val startSec: Double,
    val endSec: Double
)

data class SrtParseResult(
    val valid: Boolean,
    val cueCount: Int,
    val cleanedSrt: String,
    val promptSnippet: String,
    val error: String? = null,
    val cues: List<SrtCue> = emptyList(),
    val coveredDurationSec: Double = 0.0
)

object SrtParser {

    private const val MAX_INPUT_BYTES = 8 * 1024 * 1024
    private const val MAX_PROMPT_BYTES = 4 * 1024 * 1024
    private const val MAX_CUES = 30_000

    // Accepts both HH:MM:SS,mmm and MM:SS,mmm, with 1 to 3 millisecond digits and flexible arrow syntax
    private val TIME_LINE_REGEX = Regex(
        """(?:(\d{1,2}):)?(\d{1,2}):(\d{2})(?:[,.](\d{1,3}))?\s*(?:-->|->|—>|–>)\s*(?:(\d{1,2}):)?(\d{1,2}):(\d{2})(?:[,.](\d{1,3}))?"""
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
            .replace("```srt", "")
            .replace("```", "")
            .trim()

        val allLines = normalized.lines().map { it.trimEnd() }
        val cues = mutableListOf<SrtCue>()

        // Line-by-line scan so even SRTs without blank lines between cues are parsed 100% completely
        var i = 0
        while (i < allLines.size && cues.size < MAX_CUES) {
            val line = allLines[i].trim()
            val match = TIME_LINE_REGEX.find(line)
            if (match != null) {
                val startSec = toSecondsFlexible(
                    match.groupValues[1],
                    match.groupValues[2],
                    match.groupValues[3],
                    match.groupValues[4]
                )
                val endSecRaw = toSecondsFlexible(
                    match.groupValues[5],
                    match.groupValues[6],
                    match.groupValues[7],
                    match.groupValues[8]
                )
                val endSec = if (endSecRaw >= startSec) endSecRaw else (startSec + 0.35)

                // Collect subtitle text lines until next blank line, next numeric index before a timeline, or next timeline
                val textLines = mutableListOf<String>()
                var j = i + 1
                while (j < allLines.size) {
                    val nextLine = allLines[j].trim()
                    if (nextLine.isEmpty()) {
                        j++
                        break
                    }
                    if (TIME_LINE_REGEX.containsMatchIn(nextLine)) {
                        break
                    }
                    if (nextLine.toIntOrNull() != null && j + 1 < allLines.size && TIME_LINE_REGEX.containsMatchIn(allLines[j + 1])) {
                        j++
                        break
                    }
                    textLines.add(nextLine)
                    j++
                }
                val cueText = textLines.joinToString("\n").trim()
                if (cueText.isNotEmpty()) {
                    cues.add(
                        SrtCue(
                            index = cues.size + 1,
                            startSec = startSec,
                            endSec = endSec,
                            text = cueText
                        )
                    )
                }
                i = j
            } else {
                i++
            }
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

        return buildResultFromCues(cues)
    }

    fun buildResultFromCues(rawCues: List<SrtCue>): SrtParseResult {
        if (rawCues.isEmpty()) {
            return SrtParseResult(
                valid = false,
                cueCount = 0,
                cleanedSrt = "",
                promptSnippet = "",
                error = "No cues available."
            )
        }
        val sorted = rawCues
            .filter { it.text.isNotBlank() }
            .sortedBy { it.startSec }
            .mapIndexed { idx, c ->
                val safeStart = c.startSec.coerceAtLeast(0.0)
                val safeEnd = if (c.endSec > safeStart) c.endSec else (safeStart + 0.30)
                SrtCue(index = idx + 1, startSec = safeStart, endSec = safeEnd, text = c.text.trim())
            }

        val rebuilt = buildString {
            sorted.forEachIndexed { idx, cue ->
                append(idx + 1).append('\n')
                append(formatTimestamp(cue.startSec)).append(" --> ").append(formatTimestamp(cue.endSec)).append('\n')
                append(cue.text).append("\n\n")
            }
        }.trim()

        val maxEnd = sorted.maxOfOrNull { it.endSec } ?: 0.0
        return SrtParseResult(
            valid = true,
            cueCount = sorted.size,
            cleanedSrt = rebuilt,
            promptSnippet = truncateUtf8(rebuilt, MAX_PROMPT_BYTES),
            error = null,
            cues = sorted,
            coveredDurationSec = maxEnd
        )
    }

    /**
     * Generates a complete 100% script-covering SRT across [startOffsetSec..totalDurationSec],
     * grouping words into natural 2-4 word kinetic beats proportional to character count.
     */
    fun generateFullScriptSrt(
        scriptText: String,
        totalDurationSec: Double,
        startOffsetSec: Double = 0.0,
        startingCueIndex: Int = 1
    ): SrtParseResult {
        val cleanWords = scriptText
            .replace(Regex("[\\r\\n]+"), " ")
            .split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (cleanWords.isEmpty()) {
            return SrtParseResult(
                valid = false,
                cueCount = 0,
                cleanedSrt = "",
                promptSnippet = "",
                error = "Script is empty."
            )
        }

        val effectiveStart = startOffsetSec.coerceAtLeast(0.0)
        val effectiveEnd = maxOf(effectiveStart + 1.0, totalDurationSec)
        val spanSec = effectiveEnd - effectiveStart

        // Group into 2-4 word phrases (or break on punctuation)
        val chunks = mutableListOf<String>()
        val current = mutableListOf<String>()
        for (w in cleanWords) {
            current.add(w)
            val endsPunct = w.endsWith(".") || w.endsWith("!") || w.endsWith("?") || w.endsWith(",") || w.endsWith(";") || w.endsWith(":")
            if (current.size >= 3 || (current.size >= 2 && endsPunct)) {
                chunks.add(current.joinToString(" "))
                current.clear()
            }
        }
        if (current.isNotEmpty()) {
            chunks.add(current.joinToString(" "))
        }

        val weights = chunks.map { ch ->
            val pauseBonus = if (ch.endsWith(".") || ch.endsWith("!") || ch.endsWith("?")) 3 else 0
            (ch.length + pauseBonus).coerceAtLeast(2).toDouble()
        }
        val totalWeight = weights.sum().coerceAtLeast(1.0)

        var cursor = effectiveStart
        val cues = chunks.mapIndexed { idx, phrase ->
            val share = (weights[idx] / totalWeight) * spanSec
            val start = cursor
            val end = if (idx == chunks.lastIndex) effectiveEnd else (cursor + share)
            cursor = end
            SrtCue(
                index = startingCueIndex + idx,
                startSec = start,
                endSec = maxOf(start + 0.12, end),
                text = phrase
            )
        }

        return buildResultFromCues(cues)
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

    private fun toSecondsFlexible(hOrEmpty: String, m: String, s: String, msOrEmpty: String): Double {
        val hours = hOrEmpty.toIntOrNull() ?: 0
        val mins = m.toIntOrNull() ?: 0
        val secs = s.toIntOrNull() ?: 0
        val msPadded = when (msOrEmpty.length) {
            0 -> 0
            1 -> (msOrEmpty.toIntOrNull() ?: 0) * 100
            2 -> (msOrEmpty.toIntOrNull() ?: 0) * 10
            else -> msOrEmpty.take(3).toIntOrNull() ?: 0
        }
        return hours * 3600.0 + mins * 60.0 + secs + msPadded / 1000.0
    }

    fun formatTimestamp(seconds: Double): String {
        val totalMs = (seconds * 1000.0).toLong().coerceAtLeast(0L)
        val h = totalMs / 3_600_000L
        val m = (totalMs % 3_600_000L) / 60_000L
        val s = (totalMs % 60_000L) / 1_000L
        val ms = totalMs % 1_000L
        return "%02d:%02d:%02d,%03d".format(h, m, s, ms)
    }

    fun buildSrtFromWords(words: List<GroqWordTimestamp>): SrtParseResult {
        if (words.isEmpty()) {
            return SrtParseResult(
                valid = false,
                cueCount = 0,
                cleanedSrt = "",
                promptSnippet = "",
                error = "No words were transcribed from audio."
            )
        }
        val cues = words.mapIndexed { idx, w ->
            val start = w.startSec.coerceAtLeast(0.0)
            val end = if (w.endSec > start) w.endSec else (start + 0.25)
            SrtCue(index = idx + 1, startSec = start, endSec = end, text = w.word.trim())
        }
        return buildResultFromCues(cues)
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
