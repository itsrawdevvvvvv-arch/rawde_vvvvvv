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
        val hasLongScript = cleanTopic.length > 160

        val q1Line = when {
            hasSrt -> "1. Script / SRT timing: [SRT subtitles are already attached below — DO NOT ask for SRT or script again.]"
            hasLongScript -> "1. SRT subtitles: Do you have an `.srt` file with exact word timestamps? (Voiceover script text is attached below — reply \"no SRT\" to distribute scenes evenly across $durationFormatted s, or paste `.srt`.)"
            else -> "1. Script & timing: Paste your voiceover script or `.srt` subtitle file so every scene matches exact word timing."
        }

        val srtBlock = if (hasSrt) {
            "\nATTACHED SRT SUBTITLES:\n$srtText\n"
        } else {
            ""
        }

        return """
You are an expert motion designer and creative coder. Using the attached `kinetic-explainer-style.zip` skill pack (which I uploaded with this prompt), build ONE complete, self-contained HTML file rendering a 1920x1080 (16:9, 30 fps) kinetic-typography explainer synced to my voiceover.

FIRST READ THE ATTACHED ZIP (`kinetic-explainer-style.zip`):
- Read `kinetic-explainer-style/SKILL.md`, `docs/STYLE_BIBLE.md`, `docs/BEAT_SHEETS.md`.
- Inspect `style/tokens.css`, `style/components.css`, `engine/engine.js`, and `template/scenes.js`.
- If you can view images, look at `reference/*.jpg` and `reference/reference_fullframe_step01.png` to match the exact visual proportions, margins, cards, viewfinder brackets, and polish.

================================================================================
STEP 1 - ASK BRIEF QUESTIONS FIRST (DO NOT WRITE HTML CODE YET)
================================================================================
Send ONE short message asking only what is missing, then WAIT for my reply:
$q1Line
2. Palette & fonts: Reply "default" to use the signature Kinetic Cream & Crimson palette + Poppins font from the ZIP, OR give custom dark/light/accent hex codes and font names from the bundled font list below.
3. CTA & language: Confirm language style (English / Hinglish / etc.) and any social handle, keyword, or hook idea (optional — or say "default").

If I reply "default", "you decide", or answer these questions, immediately proceed to STEP 2 without running Python/Playwright tools (`tools/render.py` is NOT needed because the `raw_edit` Android app renders your HTML on-device).

================================================================================
STEP 2 - COMPACT SINGLE-FILE HTML OUTPUT (ZERO ERRORS, ZERO TOKEN TRUNCATION)
================================================================================
Do NOT split output into separate `.css`/`.js` files. Combine CSS, declarative `window.PROJECT` scene data, and the scene engine into ONE self-contained HTML file:
- Start directly with `<!DOCTYPE html>` and end cleanly with `</html>`.
- Output ONLY valid raw HTML (no prose before or after, no unclosed fences).
- **KEEP IT COMPACT BY USING THE DATA-DRIVEN ENGINE**:
  Inline the compact `tokens.css` variables + `components.css` styles inside `<style>`, inline the compact deterministic `engine.js` runtime inside `<script>`, and author the entire video declaratively inside `window.PROJECT = { duration: $durationFormatted, progress: [...], scenes: [...] }`.
  Using compact `PROJECT.scenes` data + the reusable engine guarantees a rich 15-25 scene reel fits comfortably inside a single response without ever hitting output word/token limits!

PROJECT INPUTS:
- Voiceover file: $cleanFile
- Voiceover length: $durationFormatted seconds (`window.PROJECT.duration` and `window.RAW.duration` MUST equal $durationFormatted).
- Script / topic notes: $cleanTopic$srtBlock

BUNDLED OFL FONTS ALREADY LOADED LOCALLY BY THE APP:
- `Poppins` (weights: 400, 600, 700, 800, 900)  [ZIP default]
- `Montserrat` (weights: 600, 700, 800, 900)
- `Open Sans` (weights: 400, 600)
- `Inter` (weights: 400, 500, 600, 700, 800, 900)
- `Bebas Neue` (400), `Anton` (400), `Oswald` (600), `Playfair Display` (700, 900), `Space Grotesk` (700), `JetBrains Mono` (400, 500, 700)

================================================================================
CRITICAL FIXES TO PREVENT VALIDATION & CLIPPING ERRORS ON MOBILE WEBVIEW
================================================================================
When combining `tokens.css`, `components.css`, and `engine.js` from the ZIP into ONE HTML file, apply ALL of these mandatory fixes:

1. **FIX OFF-SCREEN / OVERFLOW SIZING BUGS (VERY IMPORTANT)**:
   In the ZIP's `components.css`, `.el { white-space: nowrap; }` with `--fs-xl: 230px` causes longer words to clip off the edges or collide with the right-hand card (`x: 1190`). Fix this cleanly:
   - Never let text exceed its column width or screen margins (`x >= 130`, `x + w <= 1790`, `y >= 80`, `y + h <= 940`, clear of bottom progress bar at `y ~ 1015`).
   - Left column (`x = 140..150`) beside a right object (`x = 1190..1750`): constrain left-column elements to `max-width: 940px; white-space: normal; overflow-wrap: break-word;`. Leave >= 80px gap between left text and right card/brackets!
   - Centered scenes (`anchor: 'c'`, `x: 960`): allow `max-width: 1620px; width: max-content; white-space: normal; text-align: center;`.
   - Choose headline `size` strictly by word length (and override `font-size` downward if needed using `fontPx <= Math.floor(colWidth / (longestWordChars * 0.65))`):
     * `size: 'xl'` (`190px-220px`) ONLY when every word has `<= 6` chars (`SCRIPT`, `HOOK`, `FREE`, `BAS!`, `CTA`).
     * `size: 'l'` (`120px-145px`) for `7-11` char words (`PROBLEM`, `FORMULA`, `ANDROID`).
     * `size: 'm'` (`82px-105px`) for `12+` char words or multi-word headlines so NO word ever clips or wraps awkwardly!
   - Keep `phone`, `card`, `row`, `comment`, and `buttons` strictly within `x: 120..1800`, `y: 90..930` (note brackets extend `34px` outside `card`, so keep bracketed cards at `x >= 160`, `x + w <= 1756`, `y >= 110`, `y + h <= 910`).

2. **FIX ZERO EXTERNAL REQUESTS & BROKEN FONT URLS**:
   - Do NOT include `<link rel="stylesheet">`, `<script src="...">`, `@import`, `fetch()`, `XMLHttpRequest`, `WebSocket`, `import()`, or `localStorage`.
   - Do NOT copy `@font-face { src: url("../fonts/...") }` from `tokens.css` — fonts above are ALREADY injected locally at `100%` reliability.
   - NEVER use external image URLs in `{ t: 'phone', img: 'https://...' }`. For `phone` elements, render crisp inline HTML/SVG mockups inside `.phone .screen` (zero `http://` or `https://` URLs anywhere except SVG namespace `http://www.w3.org/2000/svg`).

3. **FIX END-OF-VIDEO BLANK FRAME & JS ERRORS**:
   - In `engine.js`, `t >= s.start && t < s.end` goes blank at `t === duration`. Update the last scene condition to:
     `const isLast = (idx === P.scenes.length - 1); const on = isLast ? (t >= s.start && t <= s.end) : (t >= s.start && t < s.end);`
   - Ensure `scenes[0].start === 0` and `scenes[scenes.length - 1].end === $durationFormatted` with NO gaps or overlapping timestamps between consecutive scenes.
   - Check every single string literal in `window.PROJECT` so quotes/apostrophes are properly escaped (`\'` or double quotes), ZERO raw newlines inside single-line strings, and ZERO emojis anywhere in HTML/CSS/JS.
   - Ensure every card icon / button icon / round icon uses high-contrast SVG colors (`fill="currentColor"`, `#9A0B12` crimson or `#2A0A0A` ink on cream cards, `#FFFFFF` or `#F2E5B6` cream on red cards — NEVER light icon on light card).

4. **MANDATORY DETERMINISTIC JS CONTRACT**:
   - Stage container: `<div id="stage"></div>` styled `position:relative; width:1920px; height:1080px; overflow:hidden;`.
   - NO `Date.now()`, NO `performance.now()`, NO `Math.random()`, NO `requestAnimationFrame()`, NO `setTimeout`/`setInterval`, NO CSS `@keyframes` or CSS `transition`.
   - Expose BOTH `window.seek(t)` AND:
     ```js
     window.RAW = {
       duration: $durationFormatted,
       width: 1920,
       height: 1080,
       fps: 30,
       seek: function(t) { window.seek(t); }
     };
     window.RAW.seek(0);
     ```
   - Guard all DOM queries and math (`clamp`, null checks) so calling `RAW.seek(0)`, `RAW.seek(duration * 0.25)`, `RAW.seek(duration / 2)`, and `RAW.seek(duration)` twice identically produces zero JS errors and identical DOM output.
""".trimIndent()
    }
}
