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
        palette: PaletteState? = null,
        customFonts: List<String> = emptyList()
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

        val activePalette = palette?.takeIf { it.colors.size >= 3 }
        val paletteOptionA = if (activePalette != null) {
            "A) Use Studio Palette #${activePalette.numFormatted} (${activePalette.mood}) listed below"
        } else {
            "A) Use Kinetic Cream & Crimson from ZIP / attached Palette PNG"
        }

        val fontList = if (customFonts.isNotEmpty()) {
            "Poppins, Montserrat, Open Sans, Inter, Bebas Neue, Anton, Oswald, Playfair Display, Space Grotesk, JetBrains Mono, plus user-imported fonts: ${customFonts.joinToString(", ")}"
        } else {
            "Poppins, Montserrat, Open Sans, Inter, Bebas Neue, Anton, Oswald, Playfair Display, Space Grotesk, JetBrains Mono"
        }

        return """
You are a senior motion-graphics engineer. Build ONE self-contained HTML reel for the raw_edit offline renderer.

## 0. STEP 1 — ASK 3 QUICK QUESTIONS FIRST, THEN BUILD (credit discipline)
- Do NOT write code or draft yet. Start immediately by asking ONLY these 3 short multiple-choice questions (and if both TOPIC/SCRIPT and SRT below are empty, ask for the topic too) so I can pick cleanly in one quick reply (like "1A 2B 3B" or "defaults"):
  1. **Colour Palette:**
     $paletteOptionA
     B) Classic Dark Obsidian + Cream & Warm Tan editorial look
     C) Dark/Light mood tailored to the script (I will confirm hex codes cleanly)
  2. **Fonts** (bundled offline fonts only):
     A) ZIP Editorial (Bebas Neue / Anton + Inter / Poppins)
     B) Apple Clean Modern (Space Grotesk / Montserrat + Inter)
     C) Editorial Serif Mix (Playfair Display + Poppins + JetBrains Mono)
  3. **Text Animation Style** (everything silky smooth at 30 fps):
     A) Simple ZIP Style — clean editorial mask reveals, smooth slides & crisp kinetic beats
     B) Apple-Style Bouncy — smooth deterministic spring / soft-overshoot text animations with gentle settle
- Stop after those 3 short questions and wait for my reply.
- Once I answer, spend every token on building: output ONE complete `<!DOCTYPE html>` code block immediately — zero greeting, no plan recap, no progress updates, no wasted prose (at most ONE line after the code block only if an asset could not be used).

## 1. UNDERSTAND FIRST, THEN SHOW
Silently read the entire script/SRT and work out the topic, audience, core idea, and logical steps. Show it visual-first as if explaining to a curious 12-year-old:
- Every narrated beat gets something clear to SEE: diagrams, flows, arrows, step-by-step builds, comparisons, before/after, counters, charts, labeled analogies, inline SVG icons.
- Use many visuals: at least one illustrative graphic per scene and a new visual element or motion beat roughly every 1.5 to 2 s.
- Text is minimal: punchy headlines (6 words max) and short labels. Never put full sentences on screen.
- A visual appears within 0.2 s of the word it illustrates.
- No emoji glyphs (they differ by device). Draw icons as inline SVG.

## 2. CONTINUOUS SMOOTH MOTION & ANIMATION FEEL (no static frames)
- **Zero static frames:** every single frame from `t = 0` to `t = duration` MUST have visible continuous movement. Never let any scene freeze flat while holding for narration.
- Keep smooth continuous life across the whole stage: gentle ambient background parallax/gradient drift, subtle slow float/scale breathing on cards or illustrations, animated progress strokes/connectors, and overlapping smooth entrances/exits between scenes so motion flows unbroken from start to finish.
- **Everything smooth:** derive all motion from clamped `u` in `0..1` using silky ease curves (`easeOutCubic`, `easeInOutQuart`, smooth sine drift) — never harsh linear cuts or frame-to-frame jitter.
- **Text animation implementation:**
  - If **Option A (Simple ZIP Style)** is chosen: clean overflow mask wipes, smooth vertical/horizontal glides, and crisp opacity/tracking settles matching `kinetic-explainer-style.zip`.
  - If **Option B (Apple-Style Bouncy)** is chosen: deterministic damped-spring overshoot computed purely from `t` (e.g., smooth spring curve blended cleanly to settle at exact `1.0` with zero overshoot wobble on exit) driving scale, translateY, and opacity per word/line so typography pops with glossy Apple-keynote smoothness.

## 3. INPUTS
DURATION: $durationFormatted seconds (30 fps, 1920x1080)
FILE NAME: $cleanFile
PALETTE: $paletteBlock
TOPIC / SCRIPT: $topicBlock
SRT (scene timing source; scene starts = cue starts): $srtBlock
LOCAL ASSETS (use only these, by exact relative path): $assetsBlock

STYLE KIT: if `kinetic-explainer-style.zip` is attached, read `SKILL.md` and `docs/STYLE_BIBLE.md` first and reuse its tokens and components (`engine/raw-helpers.js` is bundled in the ZIP). Its `engine.js` only implements these element types: `ghost`, `kicker`, `sub`, `pill`, `headline`, `chips`, `card`, `row`, `phone`, `buttons`, `comment`, `round`. For ANY other type (`image`, `video`, `ui`, `diagram`, etc.), extend `build` yourself or write your own renderer. Always call `(build[o.t] || build.fallback)(o, s)`, never a bare `build[o.t]`. Do NOT copy `@font-face` blocks from `tokens.css`. If no zip is attached, write a minimal engine inline.

## 4. RENDERER RULES (violations break the render)
Frames are captured one at a time, out of order, so the page must be a pure function of `t`.
- Define `<div id="stage">` (1920x1080, `position:relative`, `overflow:hidden`) and `window.RAW = { duration: $durationFormatted, width: 1920, height: 1080, fps: 30, seek(t) {...} }`.
- Build all DOM ONCE at load. `seek(t)` may only set `style`, `transform`, `opacity`, attributes or `textContent` computed from `t`. Never append, remove, or set `innerHTML` inside `seek`. Never keep counters or state between calls. Calling `seek(t)` twice must leave `stage.innerHTML` identical.
- Randomness: use a seeded hash of an index. Never `Math.random`.
- FORBIDDEN: CSS `@keyframes`, `transition`, `animation`; `requestAnimationFrame`; `setTimeout`; `setInterval`; `Date.now`; `performance.now`; `video.play()`; `<script src>`; `<link>`; `@import`; `fetch`; `XMLHttpRequest`; `WebSocket`; `import()`; `window.open()`; any `http://` or `https://` URL (the only exception is the SVG namespace `http://www.w3.org/2000/svg`).
- Fonts: use only these names, with no `@font-face`: $fontList. Always add a `sans-serif` fallback.
- JS strings: escape quotes and backslashes, no raw newlines inside quotes, never write the closing `</script>` tag inside a JS string.
- Time: `seek` clamps `t` to `[0, duration]`. Scenes are contiguous from `0` to `duration`. Use `t >= start && t < end` for every scene except the last, which uses `t <= end`, so `t === duration` is never blank. No scene is empty for more than `0.3 s`. Eases must clamp progress to `0..1`.

## 5. LAYOUT MATH (no overflow, no collisions)
- Safe area: `x 120..1800`, `y 70..930`. The progress bar occupies `y 972..1080`, so nothing may extend below `y 930`.
- Columns: left `x 140..1040` (`W 900`), right `x 1160..1760` (`W 600`), full width `x 140..1780` (`W 1640`).
- If an element has brackets, outlines or shadows extending `N px` outside its box, shrink its usable box by `N` per side. Keep at least `40 px` between neighbouring boxes after expansion. Example: a `34 px` bracket on a card at `x 1190..1750` really occupies `1156..1784`.
- Every text element needs an explicit `width` or `max-width`, and center-aligned text needs one too. Never rely on `white-space:nowrap` for long strings.
- Font size: `fs = floor(min(maxFs, W / (chars * k)))`, where `k` is `0.45` for Bebas Neue, Anton and Oswald; `0.62` for Poppins, Montserrat, Inter and Space Grotesk (use `0.66` at weight 800 or 900); `0.58` for Playfair Display; `0.60` for JetBrains Mono. If `fs` would drop below `56`, wrap to lines: `lines = ceil(chars * fs * k / W)`, `height = lines * fs * 1.1`. Check that `height` fits the space above `y 930`.
- Contrast: text against its background is at least 4.5:1. Never use light text or icons on cream or light surfaces. Set SVG `stroke` and `fill` explicitly, never inherit.

## 6. ASSETS
- Images and cutouts: `<img src="assets/...">` built once, with explicit `width` and `height`. Use `object-fit:contain` for cutouts. Respect each asset's `Safe text zone` note.
- Video: `<video muted playsinline preload="auto" src="assets/...">` built once. Use `RAW.syncVideo(videoEl, t, start, end, speed)` from `engine/raw-helpers.js` if present. Otherwise write a helper that clamps global `t` to `[start, end]`, sets `localT = max(0, (clampedGlobal - start) * speed)` capped at `duration - 0.033`, and only updates `currentTime` when it differs by more than `0.015 s`. Hide the video outside its scene. Never call `play()`.
- Website UI screenshots: coordinates `[x, y, w x h]` are in source-image pixels. With display scale `s = displayW / naturalW`, the screen position is `imgLeft + x * s`, `imgTop + y * s`. Build the cursor as an SVG that moves between waypoints with eased interpolation. A click ripple is scale/opacity as a function of `t`. A highlight box is the element rect padded `8 px` with a `6 px` stroke. A zoom callout scales the image around the element centre, up to `2.2x`, and stays inside the stage.
- Never reference an asset that is not listed. If one is missing, draw the visual with SVG instead.

## 7. SILENT PRE-FLIGHT (check before emitting, do not print)
Column and size math done for every text element; nothing below `y 930`; continuous smooth motion across every frame (no static frames); selected palette, font pairing & text animation style applied cleanly; the last scene reaches `duration`; no forbidden API or URL anywhere; `seek` is idempotent; every scene type exists in the renderer; contrast passes; JS strings valid; only bundled fonts; every narrated beat has a visual.

OUTPUT: after my 3-choice answer, emit ONLY the single complete `<!DOCTYPE html>` document, nothing truncated.
        """.trimIndent()
    }
}
