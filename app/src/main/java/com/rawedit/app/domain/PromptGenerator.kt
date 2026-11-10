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
            "1. Do you have the SRT subtitle file for this voiceover? [SRT text is already attached below, so skip asking this question.]"
        } else {
            "1. Do you have the SRT subtitle file for this voiceover? If yes, paste its text. An SRT gives the best timing and word accuracy."
        }

        val srtBlock = if (hasSrt) {
            "\nAttached SRT subtitles:\n$srtText\n"
        } else {
            ""
        }

        return """
You are an expert typography motion designer and creative coder. You will make one
self-contained HTML file that is a typography reel for a voiceover.

STEP 1 - ASK ME QUESTIONS FIRST (do not write code yet).
Ask me these in one short message and wait for my answers:
$q1Line
2. Which colour palette should I use? Give hex codes (background, accent, text), or say
   "default" to use the Red and Cream palette described below.
3. Which fonts do you want? Choose from the available list below, or say "default".
4. Anything else (tone, hook idea, words to emphasise)? (optional)
If I say "you decide", use the defaults.

STEP 2 - AFTER MY ANSWERS, OUTPUT ONLY THE HTML CODE (no explanation, no markdown fences).

Voiceover file: $cleanFile
Voiceover length: $durationFormatted seconds. Your timeline must last exactly this long.
About the video (optional): $cleanTopic$srtBlock

AVAILABLE FONTS (installed locally, use only these names in font-family):
Montserrat (700, 800, 900), Open Sans (400, 600), Poppins (600, 800), Inter (500, 800),
Bebas Neue, Anton, Oswald (600), Playfair Display (700, 900), Space Grotesk (700).
Default: Montserrat ExtraBold 800 for headings, Open Sans 600 for small text.

EDIT STYLE TO FOLLOW (typography explainer reel):
- Canvas 1920x1080. Two background moods that alternate scene to scene:
  * Crimson scenes: deep red (#8F0010 with a darker #6E000B vignette) with a GIANT ghost
    word (about 600px tall, the current key word, 10% lighter than the background)
    sitting behind the content and slowly drifting. Main text cream (#F1E6C8).
  * Cream scenes: warm paper (#EBDDB5 with soft vignette and very light grain/speckle)
    with maroon (#8F0010) and near-black (#2A0A0A) text.
  (If I gave a custom palette, map these roles to it: dark scene, light scene, accent.)
- Type: huge, heavy, tight-tracked words. Two-tone stacks (one accent word big, one dark
  word big), small tracked-out uppercase lead-in lines above, and underline strokes that
  draw in. Words land on the beat of the speech.
- Small pill tags (dark maroon pill, cream text) such as STEP 01, FEATURE 01, a keyword,
  or a short comment, popping in beside the main text.
- Rounded white/cream cards with a soft shadow holding a simple icon you draw yourself in
  SVG (hook, question mark, light bulb, stopwatch, phone, link, lock, mic, check). Four
  small corner brackets around the card, snapping in. NEVER use emoji; build icons from
  SVG shapes.
- Mini flow chips (PROBLEM -> TOPIC, PROBLEM -> FIX with a check) and pill clusters for
  lists of keywords.
- A thin three-part progress bar at the bottom of every scene, with three short labels
  that fit the video (for example INTRO / STEPS / CTA), filling as the video progresses.
- Pacing: fast. A new visual beat roughly every 1 to 2 seconds, something moving on every
  frame (slow ghost-word drift, tiny float on cards, underline draws, word-by-word
  reveals). Strong hook in the first 3 seconds. Clear CTA at the end.
- Animation variety: never use the same entrance twice in a row. Mix mask-reveal lines,
  slam-in with overshoot, slide-from-edge with settle, scale from a point, letter
  scramble, typewriter, rotate-in, swap/replace one word for another, and hard cuts.
  Between scenes use your own drawn transitions (full-screen colour wipe, ghost word
  wipe, cream-to-red block slide). Do not just fade.
- On-screen text is English or Roman Hinglish only. Never Devanagari. Keep text short:
  one idea per scene. Sync words to the speech using the SRT times if given; otherwise
  spread the script evenly across $durationFormatted seconds.

TECHNICAL CONTRACT (follow exactly):
1. One HTML file, under 150 KB, no external URLs, no remote fonts/images/scripts, no
   emoji. Inline CSS, JS, SVG, canvas only.
2. <div id="stage"> exactly 1920x1080, position relative, overflow hidden, body margin 0.
3. Define window.RAW = { duration, width:1920, height:1080, fps:30, layers, anim, seek }.
4. Every visible element is an entry in RAW.layers:
   { id, name, type ("text"|"shape"|"image"), x, y, w, h, opacity, z, start, end, text, src }
   (pixels on the 1920x1080 stage, seconds for start/end, src null unless image).
5. RAW.anim maps a layer id to (t) => ({dx, dy, scale, rotate, opacity}) where t is the
   time in seconds since that layer's start. Missing key = no animation.
6. RAW.seek(t) draws the exact frame for time t. It must be pure and deterministic:
   no Date, no performance.now, no unseeded Math.random, no requestAnimationFrame, no CSS
   animations/transitions, no timers. It reads RAW.layers and RAW.anim on every call and
   updates the DOM/canvas.
7. Complex per-scene effects (scramble, typewriter, drawn underline) are computed inside
   seek(t) from t only.
""".trimIndent()
    }
}
