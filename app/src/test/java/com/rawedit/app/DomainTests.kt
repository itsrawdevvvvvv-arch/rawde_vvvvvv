package com.rawedit.app

import com.rawedit.app.data.local.LocalAssetEntity
import com.rawedit.app.domain.BackgroundKeyMath
import com.rawedit.app.domain.CodeValidator
import com.rawedit.app.domain.CutoutAuditMath
import com.rawedit.app.domain.CutoutAuditResult
import com.rawedit.app.domain.CutoutPadRatios
import com.rawedit.app.domain.GeminiFailureCategory
import com.rawedit.app.domain.GeminiInteractionsCodec
import com.rawedit.app.domain.InteractionImageInput
import com.rawedit.app.domain.KeyframeSample
import com.rawedit.app.domain.KeyframeSheetGenerator
import com.rawedit.app.domain.PaletteColorMath
import com.rawedit.app.domain.PaletteState
import com.rawedit.app.domain.PaletteSwatch
import com.rawedit.app.domain.PromptGenerator
import com.rawedit.app.domain.SceneSpan
import com.rawedit.app.domain.SheetSamplingMode
import com.rawedit.app.domain.SrtParser
import com.rawedit.app.domain.SvgSanitizerAndPadding
import com.rawedit.app.domain.WebCaptureHelper
import com.rawedit.app.domain.WebUiElementBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainTests {

    @Test
    fun srtParserParsesValidStripsControlCharsAndHandlesLongInputs() {
        val input = "1\n00:00:00,500 --> 00:00:02,250\nStop scrolling\u0007 now!\n\n2\n00:00:02,400 --> 00:00:04,000\nBuild offline reels."
        val res = SrtParser.parse(input)
        assertTrue(res.valid)
        assertEquals(2, res.cueCount)
        assertTrue(res.cleanedSrt.contains("Stop scrolling now!"))

        val manyCues = buildString {
            for (i in 1..2500) {
                append("$i\n00:00:01,000 --> 00:00:02,000\nLine $i\n\n")
            }
        }
        val bigRes = SrtParser.parse(manyCues)
        assertTrue(bigRes.valid)
        assertEquals(2500, bigRes.cueCount)
        assertTrue(bigRes.promptSnippet.contains("Line 2500"))
    }

    @Test
    fun srtParserRejectsBrokenText() {
        val res = SrtParser.parse("not an srt file at all")
        assertFalse(res.valid)
        assertEquals(0, res.cueCount)
    }

    @Test
    fun promptGeneratorHandlesWithAndWithoutSrtAndStylePack() {
        val withoutSrt = PromptGenerator.buildPrompt("voice.mp3", 32.5, "Product launch", "")
        assertTrue(withoutSrt.contains("Voiceover length: 32.50 seconds"))
        assertTrue(withoutSrt.contains("kinetic-explainer-style.zip"))
        assertTrue(withoutSrt.contains("window.PROJECT"))
        assertTrue(withoutSrt.contains("window.RAW"))
        assertTrue(withoutSrt.contains("max-width"))
        assertTrue(withoutSrt.contains("Paste your voiceover script"))

        val withSrt = PromptGenerator.buildPrompt("voice.mp3", 32.5, "Product launch", "1\n00:00:00,000 --> 00:00:02,000\nHello")
        assertTrue(withSrt.contains("DO NOT ask for SRT"))
        assertTrue(withSrt.contains("ATTACHED SRT SUBTITLES:"))
    }

    @Test
    fun codeValidatorHandlesOfflineRulesStylePackEngineAndEdgeCases() {
        val badUrl = "<html><div id='stage'></div><script>window.RAW={duration:5,seek:()=>{}};fetch('https://evil.com')</script></html>"
        assertFalse(CodeValidator.staticValidate(badUrl).first)

        val badScriptSrc = "<html><script src='https://cdn.example.com/a.js'></script><script>window.RAW={duration:5,seek:()=>{}}</script></html>"
        assertFalse(CodeValidator.staticValidate(badScriptSrc).first)

        val padding = "/* " + "x".repeat(80_000) + " */"
        val validLarge = "<html><!-- ignore https://example.com comment --><div id='stage'></div><svg xmlns='https://www.w3.org/2000/svg'></svg><script>$padding window.RAW={duration:5,seek:function(t){}}</script></html>"
        assertTrue(CodeValidator.staticValidate(validLarge).first)

        val stylePackHtml = "<!DOCTYPE html><html><body><div id='stage'></div><script>window.PROJECT={duration:10,scenes:[]};window.seek=function(t){};</script></body></html>"
        assertTrue(CodeValidator.staticValidate(stylePackHtml).first)

        val multiFenceResponse = """
Here are quick commands:
```bash
echo hi
```
And here is your reel HTML:
```html
<!DOCTYPE html><html><body><div id='stage'></div><script>window.RAW={duration:5,seek:function(t){}}</script></body></html>
```
Enjoy!
        """.trimIndent()
        val unwrapped = CodeValidator.unwrapMarkdownFences(multiFenceResponse)
        assertTrue(unwrapped.startsWith("<!DOCTYPE html>"))
        assertTrue(unwrapped.endsWith("</html>"))
        assertTrue(CodeValidator.staticValidate(unwrapped).first)

        val unclosedFence = "```html\n<!DOCTYPE html><html><body><div id='stage'></div><script>window.RAW={duration:5,seek:function(t){}}</script></body></html>"
        assertTrue(CodeValidator.unwrapMarkdownFences(unclosedFence).startsWith("<!DOCTYPE html>"))
        assertTrue(CodeValidator.staticValidate(unclosedFence).first)
    }

    @Test
    fun keyframeSheetGeneratorBuildsTimestampsSceneLabelsAndFixPrompt() {
        val scenes = listOf(
            SceneSpan(index = 1, start = 0.0, end = 2.5, bg = "dark"),
            SceneSpan(index = 2, start = 2.5, end = 6.0, bg = "light")
        )

        val stdTimes = KeyframeSheetGenerator.buildSampleTimestamps(6.0, scenes, SheetSamplingMode.STANDARD_05S)
        assertTrue(stdTimes.first() == 0.0)
        assertTrue(stdTimes.last() >= 5.9)
        assertTrue(stdTimes.zipWithNext().all { (a, b) -> b > a })

        val beatTimes = KeyframeSheetGenerator.buildSampleTimestamps(6.0, scenes, SheetSamplingMode.SCENE_BEATS)
        assertTrue(beatTimes.size in 4..10)

        assertEquals("S1 DARK", KeyframeSheetGenerator.resolveSceneLabel(1.2, scenes))
        assertEquals("S2 LIGHT", KeyframeSheetGenerator.resolveSceneLabel(6.0, scenes))

        val samples = listOf(
            KeyframeSample(0, 0.0, "S1 DARK", "/tmp/a.jpg", 0, emptyList(), emptyList()),
            KeyframeSample(4, 2.5, "S2 LIGHT", "/tmp/b.jpg", 1, listOf("Resized \"ANDROID\" 200->128px"), emptyList()),
            KeyframeSample(7, 4.25, "S2 LIGHT", "/tmp/c.jpg", 1, emptyList(), listOf("Edge clip on \"VERYLONGTEXT\""))
        )
        val prompt = KeyframeSheetGenerator.buildFixPrompt(samples, "My Test Reel", 6.0)
        assertTrue(prompt.contains("t=4.25s"))
        assertTrue(prompt.contains("t=2.50s"))
        assertTrue(prompt.contains("Edge clip"))
        assertTrue(prompt.contains("Resized"))
        assertFalse(prompt.contains("t=0.00s"))
    }

    @Test
    fun promptGeneratorInjectsAttachedAssetsVideoSyncAndStudioPalette() {
        val assets = listOf(
            LocalAssetEntity(
                id = 1L,
                fileName = "broll_city.mp4",
                relativePath = "assets/broll_city.mp4",
                kind = LocalAssetEntity.KIND_CLIP,
                mimeType = "video/mp4",
                width = 1920,
                height = 1080,
                durationSec = 4.2,
                description = "Aerial night skyline pan",
                safeZone = "Top-left corner has clean dark sky for headline"
            ),
            LocalAssetEntity(
                id = 2L,
                fileName = "hero_cutout.png",
                relativePath = "assets/hero_cutout.png",
                kind = LocalAssetEntity.KIND_CUTOUT,
                mimeType = "image/png",
                width = 900,
                height = 1200,
                description = "Transparent cutout of smartphone",
                safeZone = "Place copy on left 55%"
            )
        )
        val palette = PaletteState(
            number = 4,
            mood = "Noir Gold",
            pairing = "Use 01 warm cream headlines over 03 deep slate cards.",
            colors = listOf(
                PaletteSwatch("#F7ECE6", "warm cream"),
                PaletteSwatch("#CAA07D", "roasted tan"),
                PaletteSwatch("#1F2428", "deep slate")
            )
        )

        val prompt = PromptGenerator.buildPrompt(
            fileName = "launch.mp3",
            durationSeconds = 18.0,
            topic = "Offline video studio walkthrough",
            srtText = "",
            assets = assets,
            palette = palette
        )

        assertTrue(prompt.contains("assets/broll_city.mp4"))
        assertTrue(prompt.contains("assets/hero_cutout.png"))
        assertTrue(prompt.contains("Aerial night skyline pan"))
        assertTrue(prompt.contains("Top-left corner has clean dark sky"))
        assertTrue(prompt.contains("RAW.syncVideo"))
        assertTrue(prompt.contains("engine/raw-helpers.js"))
        assertTrue(prompt.contains("#04, Noir Gold"))
        assertTrue(prompt.contains("#0D0D0D dark"))
        assertTrue(prompt.contains("#F7ECE6 (warm cream)"))
        assertTrue(prompt.contains("#CAA07D (roasted tan)"))
    }

    @Test
    fun codeValidatorAllowsLocalAssetsWhileBlockingNetworkUrls() {
        val offlineWithAssets = """
            <!DOCTYPE html><html><body>
            <div id="stage">
              <img src="assets/hero_cutout.png" alt="" />
              <video id="clip" src="assets/broll_city.mp4" muted playsinline></video>
            </div>
            <script>
              window.RAW = {
                duration: 5,
                seek: function(t) {
                  RAW.syncVideo(document.getElementById('clip'), t, 0, 5, 1);
                }
              };
            </script>
            </body></html>
        """.trimIndent()
        assertTrue(CodeValidator.staticValidate(offlineWithAssets).first)

        val leakImg = """
            <!DOCTYPE html><html><body>
            <div id="stage"><img src="https://cdn.example.com/pic.png" /></div>
            <script>window.RAW = { duration: 5, seek: function(t) {} };</script>
            </body></html>
        """.trimIndent()
        assertFalse(CodeValidator.staticValidate(leakImg).first)
    }

    @Test
    fun paletteMathComputesContrastInkAndHexNormalization() {
        assertEquals("#F7ECE6", PaletteColorMath.normalizeHex("f7ece6"))
        assertEquals("#DCC9A9", PaletteColorMath.normalizeHex("not-a-hex"))
        assertTrue(PaletteColorMath.luminance("#F7ECE6") > PaletteColorMath.luminance("#111112"))
        assertEquals(0xFF111112L, PaletteColorMath.contrastInkArgb("#DCC9A9"))
        assertEquals(0xFF111112L, PaletteColorMath.contrastInkArgb("#4E6851"))
        assertEquals(0xFFF7ECE6L, PaletteColorMath.contrastInkArgb("#0D0D0D"))
        assertEquals(0xFFF7ECE6L, PaletteColorMath.contrastInkArgb("#1A1024"))
        assertEquals("01", PaletteState.DEFAULT_01.numFormatted)
    }

    @Test
    fun svgSanitizerAndPaddingStripsScriptsAndExpandsViewBox() {
        val raw = """
            <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" onclick="evil()">
              <script>alert(1)</script>
              <foreignObject><div>bad</div></foreignObject>
              <path fill="currentColor" d="M2 2h20v20H2z" />
              <image href="https://evil.example.com/x.png" />
            </svg>
        """.trimIndent()
        val sanitized = SvgSanitizerAndPadding.sanitizeSvg(raw, tintHex = "#F7ECE6")
        assertFalse(sanitized.contains("<script", ignoreCase = true))
        assertFalse(sanitized.contains("onclick=", ignoreCase = true))
        assertFalse(sanitized.contains("foreignObject", ignoreCase = true))
        assertFalse(sanitized.contains("evil.example.com"))
        assertTrue(sanitized.contains("http://www.w3.org/2000/svg"))
        assertTrue(sanitized.contains("#F7ECE6"))

        val expanded = SvgSanitizerAndPadding.expandSvgViewBox(
            sanitized,
            CutoutPadRatios(0.10f, 0.10f, 0.10f, 0.10f)
        )
        assertTrue(expanded.contains("-2.400 -2.400 28.800 28.800"))
        assertTrue(expanded.contains("width=\"100%\""))
        assertTrue(expanded.contains("preserveAspectRatio=\"xMidYMid meet\""))
    }

    @Test
    fun backgroundKeyMathAndAuditAdjustmentComputeSafeParams() {
        val w = 8
        val h = 8
        val whitePixels = IntArray(w * h) { 0xFFFFFFFF.toInt() }
        // Solid red icon inside center 4x4
        for (y in 2..5) {
            for (x in 2..5) {
                whitePixels[y * w + x] = 0xFFFF0000.toInt()
            }
        }
        assertFalse(BackgroundKeyMath.hasMeaningfulTransparency(whitePixels))
        val detectedBg = BackgroundKeyMath.detectUniformCornerBackground(whitePixels, w, h)
        assertEquals(0xFFFFFFFF.toInt(), detectedBg)

        whitePixels[0] = 0x00000000
        whitePixels[1] = 0x00000000
        whitePixels[2] = 0x00000000
        whitePixels[3] = 0x00000000
        assertTrue(BackgroundKeyMath.hasMeaningfulTransparency(whitePixels))

        val (innerSq, outerSq) = BackgroundKeyMath.toleranceToDistSq(20)
        assertEquals(0, BackgroundKeyMath.smoothAlphaFromDistSq(innerSq - 10, innerSq, outerSq))
        assertEquals(255, BackgroundKeyMath.smoothAlphaFromDistSq(outerSq + 10, innerSq, outerSq))

        val audit = CutoutAuditResult(
            status = "ADJUST",
            diagnosis = "Tight edge & washed white center",
            keepOriginal = false,
            recommendedTolerance = 14,
            extraPadTop = 0.08f,
            extraPadRight = 0.04f,
            extraPadBottom = 0.0f,
            extraPadLeft = 0.04f,
            needsReprocess = true
        )
        val adjustedRaster = CutoutAuditMath.applyAdjustment(
            currentPad = CutoutPadRatios(0.04f, 0.04f, 0.04f, 0.04f),
            currentTolerance = 32,
            isSvgSource = false,
            audit = audit
        )
        assertEquals(14, adjustedRaster.tolerance)
        assertTrue(adjustedRaster.pad.top > 0.11f)

        val adjustedSvg = CutoutAuditMath.applyAdjustment(
            currentPad = CutoutPadRatios(0.04f, 0.04f, 0.04f, 0.04f),
            currentTolerance = 26,
            isSvgSource = true,
            audit = audit
        )
        assertEquals(0, adjustedSvg.tolerance)
    }

    @Test
    fun webCaptureHelperNormalizesUrlsAndFormatsUiCoordinates() {
        assertEquals("https://github.com/login", WebCaptureHelper.normalizeUrlInput("github.com/login"))
        assertTrue(WebCaptureHelper.normalizeUrlInput("login button icons").contains("duckduckgo.com/?q="))
        assertEquals("github_com", WebCaptureHelper.extractHostSlug("https://www.github.com/login"))

        val (desc, safe) = WebCaptureHelper.formatScreenshotMetadata(
            url = "https://github.com/login",
            pageTitle = "Sign in to GitHub",
            imgW = 1080,
            imgH = 1920,
            elements = listOf(
                WebUiElementBox("Sign in", "button", 120, 820, 840, 92)
            )
        )
        assertTrue(desc.contains("Interactive UI targets (px):"))
        assertTrue(desc.contains("Sign in"))
        assertTrue(desc.contains("x=120,y=820,840x92"))
        assertTrue(safe.contains("Top band"))

        val prompt = PromptGenerator.buildPrompt(
            fileName = "tutorial.mp3",
            durationSeconds = 12.0,
            topic = "How to sign in",
            srtText = "",
            assets = listOf(
                LocalAssetEntity(
                    id = 5L,
                    fileName = "github_com_shot.png",
                    relativePath = "assets/github_com_shot.png",
                    kind = LocalAssetEntity.KIND_IMAGE,
                    mimeType = "image/png",
                    width = 1080,
                    height = 1920,
                    description = desc,
                    safeZone = safe
                )
            )
        )
        assertTrue(prompt.contains("Interactive UI targets (px)"))
        assertTrue(prompt.contains("cursor clicks"))
    }

    @Test
    fun geminiInteractionsCodecBuildsCompliant38FlashPayloadsAndParsesSteps() {
        assertEquals("gemini-3.8-flash", GeminiInteractionsCodec.MODEL_ID)
        assertEquals("low", GeminiInteractionsCodec.normalizeThinkingLevel("minimal"))
        assertEquals("minimal", GeminiInteractionsCodec.normalizeThinkingLevelForModel("minimal", "gemini-3.6-flash"))
        assertEquals("minimal", GeminiInteractionsCodec.normalizeThinkingLevelForModel("minimal", "gemini-3.5-flash-lite"))
        assertEquals("low", GeminiInteractionsCodec.normalizeThinkingLevel("LOW"))
        assertEquals("AIzaTestKey123", GeminiInteractionsCodec.normalizeApiKey("  \"AIzaTest\nKey123\" "))

        val payload = GeminiInteractionsCodec.buildRequestJson(
            prompt = "Inspect image",
            images = listOf(InteractionImageInput(base64Data = "AQID", mimeType = "image/jpeg")),
            thinkingLevel = "minimal",
            systemInstruction = "Return valid JSON",
            jsonSchema = GeminiInteractionsCodec.PALETTE_SCHEMA
        )
        assertTrue(payload.contains("\"model\":\"gemini-3.8-flash\""))
        assertTrue(payload.contains("\"thinking_level\":\"low\""))
        assertTrue(payload.contains("\"type\":\"image\""))
        assertTrue(payload.contains("\"mime_type\":\"image/jpeg\""))
        assertTrue(payload.contains("\"response_format\":{"))
        assertTrue(payload.contains("\"mime_type\":\"application/json\""))
        assertFalse(payload.contains("temperature"))
        assertFalse(payload.contains("response_mime_type"))
        assertFalse(payload.contains("inlineData"))

        val stepsResponse = """
            {
              "id": "int_abc",
              "model": "gemini-3.8-flash",
              "status": "completed",
              "steps": [
                {
                  "type": "thought",
                  "summary": [{"type": "text", "text": "internal thought"}]
                },
                {
                  "type": "model_output",
                  "content": [
                    {"type": "text", "text": "READY"}
                  ]
                }
              ]
            }
        """.trimIndent()
        assertEquals("READY", GeminiInteractionsCodec.extractResponseText(stepsResponse))

        val errorBody = """{"error":{"code":400,"message":"API key not valid."}}"""
        assertEquals("API key not valid.", GeminiInteractionsCodec.extractErrorMessage(errorBody, 400))
    }

    @Test
    fun geminiCodecRoutesFallbackChainsAndClassifiesTrafficTimeouts() {
        assertEquals("auto", GeminiInteractionsCodec.normalizeModelPref(""))
        assertEquals("auto", GeminiInteractionsCodec.normalizeModelPref("unknown-model"))
        assertEquals("gemini-3.6-flash", GeminiInteractionsCodec.normalizeModelPref("gemini-3.6-flash"))

        val lightAuto = GeminiInteractionsCodec.buildCandidateModels(
            modelPref = GeminiInteractionsCodec.PREF_AUTO,
            heavyTask = false
        )
        assertEquals(
            listOf("gemini-3.6-flash", "gemini-3.5-flash-lite", "gemini-3.8-flash", "gemini-3.1-flash-lite"),
            lightAuto
        )

        val heavyAuto = GeminiInteractionsCodec.buildCandidateModels(
            modelPref = GeminiInteractionsCodec.PREF_AUTO,
            heavyTask = true
        )
        assertEquals("gemini-3.8-flash", heavyAuto.first())
        assertTrue(heavyAuto.contains("gemini-3.6-flash"))

        // Auto mode shifts cooled-down congested models to the tail
        val cooledAuto = GeminiInteractionsCodec.buildCandidateModels(
            modelPref = GeminiInteractionsCodec.PREF_AUTO,
            heavyTask = false,
            cooledDownModels = setOf("gemini-3.6-flash")
        )
        assertEquals("gemini-3.5-flash-lite", cooledAuto.first())
        assertEquals("gemini-3.6-flash", cooledAuto.last())

        // Explicitly pinned preference stays first even if previously cooled down
        val pinned = GeminiInteractionsCodec.buildCandidateModels(
            modelPref = "gemini-3.8-flash",
            heavyTask = false,
            cooledDownModels = setOf("gemini-3.8-flash", "gemini-3.6-flash")
        )
        assertEquals("gemini-3.8-flash", pinned.first())
        assertEquals("gemini-3.5-flash-lite", pinned[1])

        assertEquals(
            GeminiFailureCategory.RATE_OR_OVERLOAD,
            GeminiInteractionsCodec.classifyFailure(503, "Model is experiencing high traffic right now")
        )
        assertEquals(
            GeminiFailureCategory.RATE_OR_OVERLOAD,
            GeminiInteractionsCodec.classifyFailure(429, "Quota exceeded")
        )
        assertEquals(
            GeminiFailureCategory.NETWORK_TIMEOUT,
            GeminiInteractionsCodec.classifyFailure(null, "Read timed out", isTimeout = true)
        )
        assertEquals(
            GeminiFailureCategory.FATAL_CLIENT,
            GeminiInteractionsCodec.classifyFailure(400, "API key not valid")
        )

        val offlineMsg = GeminiInteractionsCodec.buildFriendlyErrorMessage(
            lastCategory = GeminiFailureCategory.NETWORK_TIMEOUT,
            lastRawMessage = "Read timed out",
            attemptedModels = listOf("gemini-3.6-flash", "gemini-3.5-flash-lite"),
            sawAnyHttpResponse = false
        )
        assertTrue(offlineMsg.contains("Wi-Fi"))

        val highTrafficMsg = GeminiInteractionsCodec.buildFriendlyErrorMessage(
            lastCategory = GeminiFailureCategory.RATE_OR_OVERLOAD,
            lastRawMessage = "high traffic",
            attemptedModels = listOf("gemini-3.6-flash", "gemini-3.5-flash-lite"),
            sawAnyHttpResponse = true
        )
        assertTrue(highTrafficMsg.contains("High traffic"))
        assertTrue(highTrafficMsg.contains("3.5 Lite"))
    }
}
