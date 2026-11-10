package com.rawedit.app.domain

import java.util.Locale

object PromptGenerator {

    fun buildPrompt(
        fileName: String,
        durationSeconds: Double,
        topic: String,
        srtText: String
    ): String {
        val cleanFile = fileName.trim().ifEmpty { "voiceover.mp3" }
        val durationFormatted = String.format(Locale.US, "%.2f", durationSeconds)
        val cleanTopic = topic.trim().ifEmpty { "Not specified" }
        val hasSrt = srtText.isNotBlank()

        val q1Line = if (hasSrt) {
            "1. Do you have the SRT subtitle file for this voiceover? [SRT text is already attached below, so DO NOT ask question 1 again.]"
        } else {
            "1. Do you have the SRT subtitle file for this voiceover? If yes, paste its text. An SRT gives exact word timing."
        }

        val srtBlock = if (hasSrt) {
            "\nATTACHED SRT SUBTITLES:\n$srtText\n"
        } else {
            ""
        }

        return """
You are an expert typography motion designer and creative coder. You will build ONE
self-contained HTML file that renders a 1920x1080 kinetic typography reel synced to a voiceover.

================================================================================
STEP 1 - ASK QUESTIONS FIRST (do not write HTML code yet)
================================================================================
Ask me these in one short message and wait for my reply:
$q1Line
2. Which colour palette should I use? Give hex codes (dark background, light background, accent), or say "default" for the Crimson & Warm Cream palette below.
3. Which fonts do you want? Pick from the bundled font list below, or say "default".
4. Any hook idea, tone, or specific words to emphasise? (optional)
If I say "you decide" or "default", use the defaults immediately.

================================================================================
STEP 2 - AFTER MY ANSWERS, OUTPUT ONLY VALID RAW HTML (ZERO EXPLANATION, ZERO FENCES)
================================================================================
- Start directly with `<!DOCTYPE html>` and end with `</html>`.
- Do NOT wrap output in ```html or ``` markdown fences.
- Do NOT add any commentary before or after the HTML.

PROJECT INPUTS:
- Voiceover file: $cleanFile
- Exact duration: $durationFormatted seconds (`window.RAW.duration` MUST equal $durationFormatted).
- Topic / notes: $cleanTopic$srtBlock

BUNDLED OFL FONTS (already loaded locally; use ONLY these exact family names in CSS):
- `Montserrat` (weights: 700, 800, 900)
- `Open Sans` (weights: 400, 600)
- `Poppins` (weights: 600, 800)
- `Inter` (weights: 400, 500, 600, 700, 800)
- `Bebas Neue` (weight: 400)
- `Anton` (weight: 400)
- `Oswald` (weight: 600)
- `Playfair Display` (weights: 700, 900)
- `Space Grotesk` (weight: 700)
Default pairing: `Montserrat` (800/900) for hero headlines + `Open Sans` (600) for tags/subtitles.

================================================================================
STRICT LAYOUT, SIZING & CONTRAST RULES (CRITICAL - PREVENT CLIPPING & BLANK BOXES)
================================================================================
1. **Safe Margins (NEVER clip text off screen):**
   - Stage is 1920x1080 px (`#stage { position:relative; width:1920px; height:1080px; overflow:hidden; }`).
   - Keep ALL foreground text, cards, and badges inside the safe zone:
     `x >= 140`, `x + w <= 1780` (max content width `1640px`), `y >= 90`, `y + h <= 940` (reserve bottom `y = 960..1040` for the 3-part progress bar).
   - Set `box-sizing: border-box; max-width: 1640px; padding: 0 24px; white-space: nowrap;` ONLY for single short words, OR allow clean wrapping with `line-height: 1.05` so NO letter is ever cut off on the left or right edge!
2. **Automatic Font Size Cap Formula (MANDATORY):**
   - Before setting `font-size` on any hero headline, compute:
     `maxFontPx = Math.min(165, Math.floor(1560 / Math.max(charCount * 0.64, 1)))`
   - Examples:
     * 4-6 chars (e.g. "OPEN", "FREE") -> max `165px`
     * 10 chars (e.g. "CONNECTORS") -> max `150px`
     * 14 chars (e.g. "MAKE YOUR OWN") -> max `118px`
     * 18+ chars (e.g. "ANDROID APP USING") -> max `92px`
   - NEVER use `200px+` on words longer than 6 characters! Never let two elements overlap unintentionally.
3. **High Contrast (NEVER invisible text or blank white cards):**
   - **Dark / Crimson scenes** (`#8F0010` with radial vignette `#5C000A`):
     * Giant background ghost word: `420px` tall, `opacity: 0.10`, color `#FFFFFF`, centered (`x: 60, w: 1800`).
     * Foreground headlines: `#F1E6C8` or `#FFFFFF`. Accent highlight word: `#FFD27D`.
     * Pill tags: background `#F1E6C8` with dark `#5C000A` bold text (or `#2A0A0A` pill with `#F1E6C8` text).
   - **Light / Cream scenes** (`#EBDDB5` with subtle radial vignette `#DFCFA0`):
     * Foreground headlines: `#2A0A0A` (near-black) and `#8F0010` (deep crimson). NEVER use white or light cream text on a cream background!
     * Pill tags: background `#8F0010` or `#2A0A0A` with `#F1E6C8` cream text.
4. **Icon Cards (NEVER leave an empty white box!):**
   - Whenever you create a rounded icon card (`width: 180px; height: 180px; border-radius: 32px`), you MUST put a bold, visible inline `<svg viewBox="0 0 64 64" width="88" height="88">` inside it!
   - On a `#FFFFFF` or `#F1E6C8` light card, the SVG strokes MUST be dark (`stroke="#8F0010"` or `stroke="#2A0A0A"`, `stroke-width="5"`, `fill="none"`, `stroke-linecap="round"`). NEVER draw white/cream strokes on a white/cream card!
   - Draw clean geometric icons (phone, link chain, chat bubble, document, play triangle, check mark, bolt, clock, mic) plus 4 crisp corner brackets around the card.
   - ZERO emoji characters anywhere in HTML, CSS, JS, or text!

================================================================================
VISUAL PACING & MOTION STYLE
================================================================================
- Alternate between Dark Crimson and Warm Cream scenes every 2 to 4 seconds.
- Sync every scene and word reveal directly to the SRT timestamps (or evenly across $durationFormatted s if no SRT).
- Include the thin 3-part bottom progress bar (`y: 1015`, `x: 160`, `w: 1600`) with 3 short section labels (e.g. `INTRO`, `STEPS`, `CTA`) filling smoothly from `0` to `$durationFormatted` seconds.
- Vary entrances across scenes: mask clip-reveal, slam-in with overshoot settle, horizontal slide from edge, scale-up from center, typewriter cursor, and full-screen wipe transitions between scenes.
- Every frame must have subtle life (slow ghost-word horizontal drift, gentle card float, progress bar fill).

================================================================================
MANDATORY JAVASCRIPT & DOM CONTRACT (MUST PASS VALIDATION ON FIRST TRY)
================================================================================
1. Single HTML file under 150 KB. ZERO external URLs (`http://` or `https://` except `http://www.w3.org/2000/svg` namespace). No `<script src>`, `<link>`, `@import`, `fetch`, `XMLHttpRequest`, or `localStorage`.
2. `<div id="stage"></div>` styled `position:relative; width:1920px; height:1080px; overflow:hidden;`.
3. Define `window.RAW = { duration: $durationFormatted, width: 1920, height: 1080, fps: 30, layers: [...], anim: {...}, seek: function(t) { ... } }`.
4. **`RAW.layers` MUST be a non-empty Array** of EVERY visible text, shape, card, and image element so the user can edit them in the app's timeline editor:
   `{ id: "scene1_title", name: "Hook Title", type: "text", x: 160, y: 380, w: 1600, h: 180, opacity: 1, z: 10, start: 0.0, end: 3.2, text: "YOUR HOOK", src: null }`
   - Every visible text block MUST be its own object in `RAW.layers` with accurate `x, y, w, h, start, end, text`!
   - `seek(t)` MUST read `l.x`, `l.y`, `l.w`, `l.h`, `l.opacity`, `l.z`, `l.start`, `l.end`, and `l.text` directly from `this.layers` on EVERY call so user edits (position, scale, opacity, text, timing, z-order) update immediately!
   - When `l.type === "image"` and `l.src` is set (from user-added images in the editor), `seek(t)` MUST render an `<img>` element inside `#L_<id>` with `width:100%;height:100%;object-fit:contain`.
5. **`RAW.seek(t)` MUST be 100% pure and deterministic:**
   - NO `Date.now()`, NO `performance.now()`, NO `Math.random()`, NO `requestAnimationFrame()`, NO `setTimeout`/`setInterval`, NO CSS `@keyframes` or CSS `transition`.
   - Compute all animations purely from `t` (clamped to `[0, duration]`).
   - Guard all DOM lookups (`if (!el) return;`) so `seek(0)`, `seek(duration / 2)`, and `seek(duration)` NEVER throw an exception.
6. Call `window.RAW.seek(0);` at the end of `<script>`.
""".trimIndent()
    }
}
