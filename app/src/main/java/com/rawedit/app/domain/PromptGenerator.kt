package com.rawedit.app.domain

import com.rawedit.app.data.local.LocalAssetEntity
import java.util.Locale

object PromptGenerator {

    fun buildPrompt(
        fileName: String,
        durationSeconds: Double,
        topic: String,
        srtText: String,
        assets: List<LocalAssetEntity> = emptyList(),
        palette: PaletteState? = null
    ): String {
        val cleanFile = fileName.trim().ifEmpty { "voiceover.mp3" }
        val durationFormatted = String.format(Locale.US, "%.2f", durationSeconds)
        val cleanTopic = topic.trim()
        val hasSrt = srtText.isNotBlank()

        val topicBlock = if (cleanTopic.isNotEmpty()) {
            cleanTopic
        } else {
            "Not specified"
        }

        val srtBlock = if (hasSrt) {
            "\n$srtText"
        } else {
            "None supplied (distribute scenes evenly across $durationFormatted s matching the script beats)"
        }

        val paletteBlock = if (palette != null && palette.colors.size >= 3) {
            val hexList = palette.colors.joinToString(" / ") { "${it.hex} (${it.name})" }
            "Studio Palette (#${palette.numFormatted}, ${palette.mood}): #0D0D0D dark + $hexList — ${palette.pairing}"
        } else {
            "Kinetic Cream & Crimson from ZIP (or attached Palette PNG)"
        }

        val assetsBlock = if (assets.isNotEmpty()) {
            buildString {
                assets.forEach { a ->
                    val kindTag = when (a.kind) {
                        LocalAssetEntity.KIND_CLIP -> "VIDEO CLIP (${String.format(Locale.US, "%.1f", a.durationSec)}s)"
                        LocalAssetEntity.KIND_CUTOUT -> "TRANSPARENT PNG CUTOUT"
                        else -> "IMAGE"
                    }
                    val desc = a.description.ifBlank { "Local user media" }
                    val safe = a.safeZone.ifBlank { "Keep overlay typography away from central subject." }
                    appendLine()
                    appendLine("  - `${a.relativePath}` (${a.width}x${a.height}, $kindTag)")
                    appendLine("    Visual: $desc")
                    append("    Safe text zone: $safe")
                }
            }
        } else {
            "None attached (pure kinetic typography and inline SVG graphics)."
        }

        return """
You are a senior motion-graphics engineer. Build ONE self-contained HTML reel for the raw_edit offline renderer. Output the finished file, nothing else.

## 0. CREDIT DISCIPLINE (mandatory)
- Spend every token on building. No greeting, no plan, no progress updates, no recap, no explanation after the file.
- Run all checklists silently. Never print them.
- If a script, SRT or topic is supplied below, ask NOTHING and build immediately. Only if all three are empty, ask ONE short question, then build.
- Get it right the first time. Do not draft twice. Keep code compact: a data-driven scenes array plus a small renderer, no dead code, no long comments.
- Reply format: one code block with the full HTML. After it, at most ONE line, only if an asset could not be used.

## 1. UNDERSTAND FIRST, THEN SHOW
Before designing, silently read the entire script/SRT and work out: the topic, the audience, the single core idea, and the logical steps. Then explain it in the simplest possible way, as if to a curious 12-year-old, visual-first:
- Every narrated beat gets something to SEE that illustrates it: diagrams, flows, arrows, step-by-step builds, comparisons, before/after, counters, charts, labeled analogies, inline SVG icons.
- Use many visuals: at least one illustrative graphic per scene and a new visual element or motion roughly every 1.5 to 2 s.
- Text is minimal: keyword headlines (6 words max) and short labels. Never put full sentences on screen.
- A visual appears within 0.2 s of the word it illustrates.
- No emoji glyphs (they differ by device). Draw icons as inline SVG.

## 2. INPUTS
DURATION: $durationFormatted seconds (30 fps, 1920x1080)
FILE NAME: $cleanFile
PALETTE: $paletteBlock
TOPIC / SCRIPT: $topicBlock
SRT (scene timing source; scene starts = cue starts): $srtBlock
LOCAL ASSETS (use only these, by exact relative path): $assetsBlock

STYLE KIT: if `kinetic-explainer-style.zip` is attached, read `SKILL.md` and `docs/STYLE_BIBLE.md` first and reuse its tokens and components (`engine/raw-helpers.js` is bundled in the ZIP). Its `engine.js` only implements these element types: `ghost`, `kicker`, `sub`, `pill`, `headline`, `chips`, `card`, `row`, `phone`, `buttons`, `comment`, `round`. For ANY other type (`image`, `video`, `ui`, `diagram`, etc.), extend `build` yourself or write your own renderer. Always call `(build[o.t] || build.fallback)(o, s)`, never a bare `build[o.t]`. Do NOT copy `@font-face` blocks from `tokens.css`. If no zip is attached, write a minimal engine inline.

## 3. RENDERER RULES (violations break the render)
Frames are captured one at a time, out of order, so the page must be a pure function of `t`.
- Define `<div id="stage">` (1920x1080, `position:relative`, `overflow:hidden`) and `window.RAW = { duration: $durationFormatted, width: 1920, height: 1080, fps: 30, seek(t) {...} }`.
- Build all DOM ONCE at load. `seek(t)` may only set `style`, `transform`, `opacity`, attributes or `textContent` computed from `t`. Never append, remove, or set `innerHTML` inside `seek`. Never keep counters or state between calls. Calling `seek(t)` twice must leave `stage.innerHTML` identical.
- Randomness: use a seeded hash of an index. Never `Math.random`.
- FORBIDDEN: CSS `@keyframes`, `transition`, `animation`; `requestAnimationFrame`; `setTimeout`; `setInterval`; `Date.now`; `performance.now`; `video.play()`; `<script src>`; `<link>`; `@import`; `fetch`; `XMLHttpRequest`; `WebSocket`; `import()`; `window.open()`; any `http://` or `https://` URL (the only exception is the SVG namespace `http://www.w3.org/2000/svg`).
- Fonts: use only these bundled names, with no `@font-face`: Poppins, Montserrat, Open Sans, Inter, Bebas Neue, Anton, Oswald, Playfair Display, Space Grotesk, JetBrains Mono. Always add a `sans-serif` fallback.
- JS strings: escape quotes and backslashes, no raw newlines inside quotes, never write the closing `</script>` tag inside a JS string.
- Time: `seek` clamps `t` to `[0, duration]`. Scenes are contiguous from `0` to `duration`. Use `t >= start && t < end` for every scene except the last, which uses `t <= end`, so `t === duration` is never blank. No scene is empty for more than `0.3 s`. Eases must clamp progress to `0..1`.

## 4. LAYOUT MATH (no overflow, no collisions)
- Safe area: `x 120..1800`, `y 70..930`. The progress bar occupies `y 972..1080`, so nothing may extend below `y 930`.
- Columns: left `x 140..1040` (`W 900`), right `x 1160..1760` (`W 600`), full width `x 140..1780` (`W 1640`).
- If an element has brackets, outlines or shadows extending `N px` outside its box, shrink its usable box by `N` per side. Keep at least `40 px` between neighbouring boxes after expansion. Example: a `34 px` bracket on a card at `x 1190..1750` really occupies `1156..1784`.
- Every text element needs an explicit `width` or `max-width`, and center-aligned text needs one too. Never rely on `white-space:nowrap` for long strings.
- Font size: `fs = floor(min(maxFs, W / (chars * k)))`, where `k` is `0.45` for Bebas Neue, Anton and Oswald; `0.62` for Poppins, Montserrat, Inter and Space Grotesk (use `0.66` at weight 800 or 900); `0.58` for Playfair Display; `0.60` for JetBrains Mono. If `fs` would drop below `56`, wrap to lines: `lines = ceil(chars * fs * k / W)`, `height = lines * fs * 1.1`. Check that `height` fits the space above `y 930`.
- Contrast: text against its background is at least 4.5:1. Never use light text or icons on cream or light surfaces. Set SVG `stroke` and `fill` explicitly, never inherit.

## 5. ASSETS
- Images and cutouts: `<img src="assets/...">` built once, with explicit `width` and `height`. Use `object-fit:contain` for cutouts. Respect each asset's `Safe text zone` note.
- Video: `<video muted playsinline preload="auto" src="assets/...">` built once. Use `RAW.syncVideo(videoEl, t, start, end, speed)` from `engine/raw-helpers.js` if present. Otherwise write a helper that clamps global `t` to `[start, end]`, sets `localT = max(0, (clampedGlobal - start) * speed)` capped at `duration - 0.033`, and only updates `currentTime` when it differs by more than `0.015 s`. Hide the video outside its scene. Never call `play()`.
- Website UI screenshots: coordinates `[x, y, w x h]` are in source-image pixels. With display scale `s = displayW / naturalW`, the screen position is `imgLeft + x * s`, `imgTop + y * s`. Build the cursor as an SVG that moves between waypoints with eased interpolation. A click ripple is scale/opacity as a function of `t`. A highlight box is the element rect padded `8 px` with a `6 px` stroke. A zoom callout scales the image around the element centre, up to `2.2x`, and stays inside the stage.
- Never reference an asset that is not listed. If one is missing, draw the visual with SVG instead.

## 6. SILENT PRE-FLIGHT (check before emitting, do not print)
Column and size math done for every text element; nothing below `y 930`; the last scene reaches `duration`; no forbidden API or URL anywhere; `seek` is idempotent; every scene type exists in the renderer; contrast passes; JS strings valid; only bundled fonts; every narrated beat has a visual.

OUTPUT: a single `<!DOCTYPE html>` document, complete, nothing truncated.
        """.trimIndent()
    }
}
