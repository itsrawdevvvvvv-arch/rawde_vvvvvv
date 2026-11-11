package com.rawedit.app

import com.rawedit.app.data.local.LocalAssetEntity
import com.rawedit.app.domain.BackgroundKeyMath
import com.rawedit.app.domain.CodeValidator
import com.rawedit.app.domain.CutoutAuditMath
import com.rawedit.app.domain.CutoutAuditResult
import com.rawedit.app.domain.CutoutPadRatios
import com.rawedit.app.domain.KeyframeSample
import com.rawedit.app.domain.KeyframeSheetGenerator
import com.rawedit.app.domain.PaletteColorMath
import com.rawedit.app.domain.PaletteState
import com.rawedit.app.domain.PaletteSwatch
import com.rawedit.app.domain.PromptGenerator
import com.rawedit.app.domain.PuterApiException
import com.rawedit.app.domain.PuterCodec
import com.rawedit.app.domain.PuterFailureCategory
import com.rawedit.app.domain.PuterImageInput
import com.rawedit.app.domain.SceneSpan
import com.rawedit.app.domain.SheetSamplingMode
import com.rawedit.app.domain.SrtParser
import com.rawedit.app.domain.SvgSanitizerAndPadding
import com.rawedit.app.domain.WebCaptureHelper
import com.rawedit.app.domain.WebUiElementBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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
    fun puterCodecBuildsDriversCallPayloadsVisionAndExtractsContent() {
        assertEquals("tok_abc123", PuterCodec.normalizeAuthToken("  \"Bearer tok_abc\n123\" "))

        val authUrl = PuterCodec.buildAuthMeUrl("http://127.0.0.1:45678/callback")
        assertTrue(authUrl.startsWith("https://puter.com/action/authme?redirectURL="))
        assertTrue(authUrl.contains("127.0.0.1"))

        assertEquals(
            "ptk_xyz_123",
            PuterCodec.extractTokenFromCallbackUri("http://127.0.0.1:45678/callback?token=ptk_xyz_123")
        )
        assertEquals(
            "ptk_deep_999",
            PuterCodec.extractTokenFromCallbackUri("rawedit://puter-auth?token=ptk_deep_999")
        )

        val payload = PuterCodec.buildDriversCallJson(
            authToken = "ptk_1",
            prompt = "Inspect image",
            images = listOf(PuterImageInput(base64Data = "AQID", mimeType = "image/jpeg")),
            systemInstruction = "Return valid JSON",
            model = PuterCodec.MODEL_GPT_4O_MINI
        )
        assertTrue(payload.contains("\"interface\":\"puter-chat-completion\""))
        assertTrue(payload.contains("\"driver\":\"ai-chat\""))
        assertTrue(payload.contains("\"method\":\"complete\""))
        assertTrue(payload.contains("\"auth_token\":\"ptk_1\""))
        assertTrue(payload.contains("\"model\":\"gpt-4o-mini\""))
        assertTrue(payload.contains("\"vision\":true"))
        assertTrue(payload.contains("\"url\":\"data:image/jpeg;base64,AQID\""))

        val strResponse = """{"success":true,"result":{"message":{"role":"assistant","content":"READY"}}}"""
        assertEquals("READY", PuterCodec.extractResponseText(strResponse))

        val arrayResponse = """
            {
              "success": true,
              "result": {
                "message": {
                  "role": "assistant",
                  "content": [
                    {"type": "text", "text": "{\"ok\":"},
                    {"type": "output_text", "text": "true}"}
                  ]
                }
              }
            }
        """.trimIndent()
        assertEquals("{\"ok\":true}", PuterCodec.extractResponseText(arrayResponse))

        val fundingError = """{"success":false,"error":{"code":"insufficient_funds","message":"Not enough funding","metadata":{"usage_limited":true}}}"""
        try {
            PuterCodec.extractResponseText(fundingError)
            fail("Expected PuterApiException")
        } catch (ex: PuterApiException) {
            assertEquals("insufficient_funds", ex.errorCode)
            assertTrue(ex.usageLimited)
        }
    }

    @Test
    fun puterCodecParsesUsageCreditsRoutesModelsAndClassifiesExhaustion() {
        assertEquals("alice", PuterCodec.parseWhoamiUsername("""{"username":"alice","uuid":"u123456"}"""))

        val usdUsageJson = """
            {
              "usage": {"total": 325000000, "allowanceUsed": 325000000},
              "allowanceInfo": {"monthUsageAllowance": 500000000, "remaining": 175000000}
            }
        """.trimIndent()
        val usdReport = PuterCodec.parseUsageReport(usdUsageJson)
        assertFalse(usdReport.isCreditsUnit)
        assertFalse(usdReport.isExhausted)
        assertEquals(35, usdReport.remainingPercent)
        assertEquals("$1.75", usdReport.formattedRemaining())
        assertEquals(
            "$1.75 left",
            PuterCodec.formatUsageBannerLabel(
                remainingRaw = usdReport.totalRemainingRaw,
                allowanceRaw = usdReport.monthlyAllowanceRaw,
                isCreditsUnit = usdReport.isCreditsUnit,
                exhausted = usdReport.isExhausted
            )
        )

        val creditsExhaustedJson = """
            {
              "usage": {"total": 1000, "allowanceUsed": 1000},
              "allowanceInfo": {"monthUsageAllowance": 1000, "remaining": 0, "unit": "credits"}
            }
        """.trimIndent()
        val crReport = PuterCodec.parseUsageReport(creditsExhaustedJson)
        assertTrue(crReport.isCreditsUnit)
        assertTrue(crReport.isExhausted)
        assertEquals(0, crReport.remainingPercent)
        assertEquals("0 cr", crReport.formattedRemaining())
        assertEquals("0 cr left", PuterCodec.formatUsageBannerLabel(0.0, 1000.0, isCreditsUnit = true, exhausted = true))

        val lightAuto = PuterCodec.buildCandidateModels(modelPref = PuterCodec.PREF_AUTO, heavyTask = false)
        assertEquals("gpt-4o-mini", lightAuto.first())
        assertTrue(lightAuto.none { it.contains("gemini", ignoreCase = true) })

        val heavyAuto = PuterCodec.buildCandidateModels(modelPref = PuterCodec.PREF_AUTO, heavyTask = true)
        assertEquals("claude-sonnet-4-6", heavyAuto.first())

        val cooledAuto = PuterCodec.buildCandidateModels(
            modelPref = PuterCodec.PREF_AUTO,
            heavyTask = false,
            cooledDownModels = setOf("gpt-4o-mini")
        )
        assertEquals("gpt-5-nano", cooledAuto.first())
        assertEquals("gpt-4o-mini", cooledAuto.last())

        val pinned = PuterCodec.buildCandidateModels(
            modelPref = PuterCodec.MODEL_CLAUDE_SONNET,
            heavyTask = false,
            cooledDownModels = setOf(PuterCodec.MODEL_CLAUDE_SONNET, PuterCodec.MODEL_GPT_5_MINI)
        )
        assertEquals("claude-sonnet-4-6", pinned.first())

        assertEquals(
            PuterFailureCategory.INSUFFICIENT_CREDITS,
            PuterCodec.classifyFailure(402, "Error", errorCode = "insufficient_funds", usageLimited = true)
        )
        assertEquals(
            PuterFailureCategory.FATAL_AUTH,
            PuterCodec.classifyFailure(401, "Unauthorized")
        )
        assertEquals(
            PuterFailureCategory.RATE_OR_OVERLOAD,
            PuterCodec.classifyFailure(429, "Rate limit exceeded")
        )
        assertEquals(
            PuterFailureCategory.NETWORK_TIMEOUT,
            PuterCodec.classifyFailure(null, "Read timed out", isTimeout = true)
        )

        val outMsg = PuterCodec.buildFriendlyErrorMessage(
            lastCategory = PuterFailureCategory.INSUFFICIENT_CREDITS,
            lastRawMessage = "insufficient_funds",
            attemptedModels = listOf("gpt-4o-mini"),
            sawAnyHttpResponse = true
        )
        assertTrue(outMsg.contains("Switch to another Puter or Google account"))
    }
}
