package com.rawedit.app

import com.rawedit.app.data.local.LocalAssetEntity
import com.rawedit.app.domain.BackgroundKeyMath
import com.rawedit.app.domain.CodeValidator
import com.rawedit.app.domain.CutoutAuditMath
import com.rawedit.app.domain.CutoutAuditResult
import com.rawedit.app.domain.CutoutPadRatios
import com.rawedit.app.domain.GroqApiException
import com.rawedit.app.domain.GroqCodec
import com.rawedit.app.domain.GroqFailureCategory
import com.rawedit.app.domain.GroqImageInput
import com.rawedit.app.domain.GroqLimitKind
import com.rawedit.app.domain.GroqPatchEdit
import com.rawedit.app.domain.GroqPromptTooLargeException
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
        assertTrue(withoutSrt.contains("DURATION: 32.50 seconds"))
        assertTrue(withoutSrt.contains("kinetic-explainer-style.zip"))
        assertTrue(withoutSrt.contains("window.RAW"))
        assertTrue(withoutSrt.contains("max-width"))
        assertTrue(withoutSrt.contains("RAW.syncVideo"))
        assertTrue(withoutSrt.contains("build[o.t] || build.fallback"))
        assertTrue(withoutSrt.contains("TOPIC / SCRIPT: Product launch"))

        val withSrt = PromptGenerator.buildPrompt("voice.mp3", 32.5, "Product launch", "1\n00:00:00,000 --> 00:00:02,000\nHello")
        assertTrue(withSrt.contains("00:00:00,000 --> 00:00:02,000"))
        assertTrue(withSrt.contains("Hello"))
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
        val prompt = KeyframeSheetGenerator.buildFixPrompt(
            samples = samples,
            title = "My Test Reel",
            durationSec = 6.0,
            htmlCode = "<!DOCTYPE html><html><div id=\"stage\"></div></html>"
        )
        assertTrue(prompt.contains("t=4.25s"))
        assertTrue(prompt.contains("t=2.50s"))
        assertTrue(prompt.contains("Edge clip"))
        assertTrue(prompt.contains("Resized"))
        assertFalse(prompt.contains("t=0.00s"))
        assertTrue(prompt.contains("STEP 1: SILENT AUDIT"))
        assertTrue(prompt.contains("<<<SEARCH"))
        assertTrue(prompt.contains("===REPLACE"))
        assertTrue(prompt.contains("CURRENT HTML"))
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
        assertTrue(prompt.contains("Colour Palette"))
        assertTrue(prompt.contains("Fonts"))
        assertTrue(prompt.contains("Text Animation Style"))
        assertTrue(prompt.contains("Apple-Style Bouncy"))
        assertTrue(prompt.contains("Zero static frames"))
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
        assertTrue(prompt.contains("click ripple"))
    }

    @Test
    fun groqCodecBuildsVisionAndJsonPayloadsParsesQuotaHeadersAndClassifiesErrors() {
        assertEquals("gsk_abc123", GroqCodec.normalizeApiKey("  \"Bearer gsk_abc\n123\" "))
        assertEquals("gsk_...c123", GroqCodec.maskApiKey("gsk_abcc123"))

        val sevenImages = (1..7).map { GroqImageInput(base64Data = "AQID$it", mimeType = "image/jpeg") }
        val qwenVisionPayload = GroqCodec.buildChatCompletionJson(
            prompt = "Inspect frames",
            images = sevenImages,
            systemInstruction = "Return valid JSON",
            model = GroqCodec.MODEL_QWEN38_27B,
            jsonObjectMode = true,
            maxCompletionTokens = 1024,
            temperature = 0.15
        )
        assertTrue(qwenVisionPayload.contains("\"model\":\"qwen/qwen3.8-27b\""))
        assertTrue(qwenVisionPayload.contains("\"type\":\"json_object\""))
        assertTrue(qwenVisionPayload.contains("\"reasoning_effort\":\"none\""))
        assertTrue(qwenVisionPayload.contains("data:image/jpeg;base64,AQID3"))
        assertFalse(qwenVisionPayload.contains("AQID4"))

        val scoutPayload = GroqCodec.buildChatCompletionJson(
            prompt = "Inspect frames",
            images = sevenImages,
            model = GroqCodec.MODEL_LLAMA4_SCOUT
        )
        assertTrue(scoutPayload.contains("data:image/jpeg;base64,AQID5"))
        assertFalse(scoutPayload.contains("AQID6"))

        val ossProbePayload = GroqCodec.buildChatCompletionJson(
            prompt = "Reply READY",
            model = GroqCodec.MODEL_GPT_OSS_120B,
            maxCompletionTokens = 4,
            heavyTask = false
        )
        assertTrue(ossProbePayload.contains("\"max_completion_tokens\":256"))
        assertTrue(ossProbePayload.contains("\"reasoning_effort\":\"low\""))

        val qwenLegacyPayload = GroqCodec.buildChatCompletionJson(
            prompt = "Patch HTML",
            model = GroqCodec.MODEL_QWEN3_32B
        )
        assertTrue(qwenLegacyPayload.contains("\"reasoning_format\":\"hidden\""))

        val thinkingResp = """{"choices":[{"message":{"role":"assistant","content":"<think>check margin</think>{\"status\":\"OK\"}"},"finish_reason":"stop"}],"error":null}"""
        assertEquals("{\"status\":\"OK\"}", GroqCodec.extractResponseText(thinkingResp, modelId = GroqCodec.MODEL_QWEN38_27B))

        val lengthTruncatedResp = """{"choices":[{"message":{"role":"assistant","content":""},"finish_reason":"length"}]}"""
        try {
            GroqCodec.extractResponseText(lengthTruncatedResp, modelId = GroqCodec.MODEL_GPT_OSS_120B)
            fail("Expected GroqApiException on finish_reason=length")
        } catch (ex: GroqApiException) {
            assertEquals("length_truncated", ex.errorCode)
            assertTrue(ex.message.contains("finish_reason=length"))
        }

        val authErrJson = """{"error":{"message":"Invalid API Key","type":"invalid_request_error","code":"invalid_api_key"}}"""
        try {
            GroqCodec.extractResponseText(authErrJson)
            fail("Expected GroqApiException")
        } catch (ex: GroqApiException) {
            assertEquals("invalid_api_key", ex.errorCode)
        }

        val modelsJson = """{"data":[{"id":"whisper-large-v3"},{"id":"canopylabs/orpheus-v1-english"},{"id":"openai/gpt-oss-120b"},{"id":"openai/gpt-oss-20b"},{"id":"qwen/qwen3.8-27b"}]}"""
        val parsedIds = GroqCodec.parseAvailableChatModelIds(modelsJson)
        assertEquals(
            setOf(GroqCodec.MODEL_GPT_OSS_120B, GroqCodec.MODEL_GPT_OSS_20B, GroqCodec.MODEL_QWEN38_27B),
            parsedIds
        )

        val filteredHeavy = GroqCodec.buildCandidateModels(
            GroqCodec.PREF_AUTO,
            requiresVision = false,
            heavyTask = true,
            availableModels = parsedIds
        )
        assertEquals(listOf(GroqCodec.MODEL_GPT_OSS_120B, GroqCodec.MODEL_QWEN38_27B, GroqCodec.MODEL_GPT_OSS_20B), filteredHeavy)

        val headers = mapOf<String?, List<String>>(
            "x-ratelimit-remaining-requests" to listOf("0"),
            "x-ratelimit-limit-requests" to listOf("1000"),
            "x-ratelimit-reset-requests" to listOf("4m12s"),
            "x-ratelimit-remaining-tokens" to listOf("12000"),
            "x-ratelimit-limit-tokens" to listOf("300000"),
            "x-ratelimit-reset-tokens" to listOf("5s"),
            "retry-after" to listOf("15")
        )
        val report = GroqCodec.parseQuotaHeaders(headers, httpCode = 429)!!
        assertTrue(report.isExhausted)
        assertEquals("Quota 0 left", report.bannerLabel())
        assertEquals(0, report.remainingPercent)

        val visionModels = GroqCodec.buildCandidateModels(GroqCodec.PREF_AUTO, requiresVision = true, heavyTask = false)
        assertEquals(
            listOf(GroqCodec.MODEL_QWEN38_27B, GroqCodec.MODEL_MUSE_SPARK, GroqCodec.MODEL_LLAMA4_SCOUT, GroqCodec.MODEL_LLAMA4_MAVERICK),
            visionModels
        )

        val heavyTextModels = GroqCodec.buildCandidateModels(GroqCodec.PREF_AUTO, requiresVision = false, heavyTask = true)
        assertEquals(GroqCodec.MODEL_GPT_OSS_120B, heavyTextModels.first())

        assertEquals(
            GroqFailureCategory.FATAL_AUTH,
            GroqCodec.classifyFailure(401, "Invalid API key", errorCode = "invalid_api_key")
        )
        assertEquals(
            GroqFailureCategory.MODEL_UNAVAILABLE,
            GroqCodec.classifyFailure(400, "model decommissioned", errorCode = "model_decommissioned")
        )
        assertEquals(
            GroqFailureCategory.MODEL_UNAVAILABLE,
            GroqCodec.classifyFailure(403, "blocked at the organization level", errorCode = "model_permission_blocked_org")
        )
        assertEquals(
            GroqFailureCategory.QUOTA_OR_CREDITS_EXHAUSTED,
            GroqCodec.classifyFailure(429, "Rate limit reached", quota = report)
        )
        assertEquals(
            GroqFailureCategory.RATE_OR_OVERLOAD,
            GroqCodec.classifyFailure(429, "Too many requests per minute")
        )
        assertEquals(
            GroqFailureCategory.PAYLOAD_TOO_LARGE,
            GroqCodec.classifyFailure(413, "Request entity too large")
        )
        assertEquals(
            GroqFailureCategory.NETWORK_TIMEOUT,
            GroqCodec.classifyFailure(null, "Read timed out", isTimeout = true)
        )

        val quotaMsg = GroqCodec.buildFriendlyErrorMessage(
            lastCategory = GroqFailureCategory.QUOTA_OR_CREDITS_EXHAUSTED,
            lastRawMessage = "limit reached",
            attemptedModels = listOf(GroqCodec.MODEL_GPT_OSS_120B),
            sawAnyHttpResponse = true,
            quota = report
        )
        assertTrue(quotaMsg.contains("Switch to another Groq API key"))
    }

    @Test
    fun groqPatchEngineAppliesExactOnceEditsExtractsExcerptsAndParsesStages() {
        val stage1Json = """
            ```json
            {
              "rootCauses": [
                {
                  "id": "R1",
                  "type": "overflow",
                  "t": [2.5, 3.0],
                  "el": "#hero-title",
                  "evidence": "Edge clip right=1860",
                  "fix": "Set max-width: 820px and reduce font-size"
                }
              ],
              "edits": [
                {
                  "rc": "R1",
                  "find": ".hero-title { font-size: 96px; }",
                  "replace": ".hero-title { font-size: 72px; max-width: 820px; }"
                }
              ],
              "needsFull": false,
              "reason": ""
            }
            ```
        """.trimIndent()
        val plan = GroqCodec.parseStage1Plan(stage1Json)
        assertEquals(1, plan.rootCauses.size)
        assertEquals("R1", plan.rootCauses.first().id)
        assertEquals(2.5, plan.rootCauses.first().timesSec.first(), 0.001)
        assertEquals(1, plan.edits.size)
        assertFalse(plan.needsFull)

        val stage2Json = """{"edits":[{"rc":"R1","find":"old","replace":"new"}]}"""
        val (s2Edits, s2Html) = GroqCodec.parseStage2Repair(stage2Json)
        assertEquals(1, s2Edits.size)
        assertTrue(s2Html == null)

        val claudePatch = """
            Root cause: title overflows right column.
            <<<SEARCH
            .hero-title { font-size: 96px; }
            ===REPLACE
            .hero-title { font-size: 72px; max-width: 820px; }
            >>>
        """.trimIndent()
        val parsedBlocks = GroqCodec.parseClaudeSearchReplacePatches(claudePatch)
        assertEquals(1, parsedBlocks.size)

        val sampleHtml = """
            <!DOCTYPE html><html><head><style>
            .hero-title { font-size: 96px; }
            .dup { color: red; }
            .dup { color: red; }
            </style></head><body><div id="stage"></div>
            <script>
            const scenes = [
              { start: 0.0, end: 2.0, title: "Intro" },
              { start: 2.0, end: 5.0, title: "Main Hero" }
            ];
            window.RAW = { duration: 5.0, seek(t) {} };
            </script></body></html>
        """.trimIndent()

        val applied = GroqCodec.applyExactOnceEdits(sampleHtml, parsedBlocks)
        assertEquals(1, applied.appliedCount)
        assertTrue(applied.failedEdits.isEmpty())
        assertTrue(applied.updatedHtml.contains("max-width: 820px"))

        val badAmbiguous = GroqCodec.applyExactOnceEdits(
            sampleHtml,
            listOf(
                GroqPatchEdit("R2", ".dup { color: red; }", ".dup { color: blue; }"),
                GroqPatchEdit("R3", "NOT_IN_FILE", "X")
            )
        )
        assertEquals(0, badAmbiguous.appliedCount)
        assertEquals(2, badAmbiguous.failedEdits.size)

        val excerpt = GroqCodec.buildHtmlExcerpt(sampleHtml, listOf(2.5), listOf("hero-title"))
        assertTrue(excerpt.contains("hero-title"))
        assertTrue(excerpt.contains("Main Hero"))

        val windows = GroqCodec.buildRepairWindows(sampleHtml, badAmbiguous.failedEdits, "JS error at line 10")
        assertTrue(windows.contains("window.RAW"))
    }

    @Test
    fun groqLimiterClassifiesTpmCooldownDailyAndCreditsAndAppliesReliableEdits() {
        assertEquals(90.0, GroqCodec.parseDurationSec("1m30s")!!, 0.01)
        assertEquals(42.0, GroqCodec.parseDurationSec("42s")!!, 0.01)
        assertTrue(GroqCodec.estTokens("abcde") > 0)

        try {
            GroqCodec.guardTokens(images = 3, "a".repeat(2000), maxCompletion = 700, cap = 6500)
            fail("Expected GroqPromptTooLargeException")
        } catch (ex: GroqPromptTooLargeException) {
            assertTrue(ex.estimatedTokens > 6500)
        }

        val coolingHeaders = mapOf<String?, List<String>>(
            "x-ratelimit-remaining-requests" to listOf("995"),
            "x-ratelimit-limit-requests" to listOf("1000"),
            "x-ratelimit-reset-requests" to listOf("12m"),
            "x-ratelimit-remaining-tokens" to listOf("0"),
            "x-ratelimit-limit-tokens" to listOf("8000"),
            "x-ratelimit-reset-tokens" to listOf("42s")
        )
        val coolingQuota = GroqCodec.parseQuotaHeaders(coolingHeaders, httpCode = 200)!!
        assertFalse(coolingQuota.isExhausted)
        assertTrue(coolingQuota.isCooling)
        assertEquals("Cooling 42s", coolingQuota.bannerLabel())

        val lowerHdr = GroqCodec.headerFieldsToLowerMap(coolingHeaders)
        val tpmLimit = GroqCodec.classifyLimit(
            429,
            "Rate limit reached on tokens per minute (TPM): Limit 8000. Please try again in 42s",
            lowerHdr
        )
        assertEquals(GroqLimitKind.COOLDOWN, tpmLimit.kind)

        val dailyLimit = GroqCodec.classifyLimit(
            429,
            "Limit reached on requests per day (RPD) and tokens per day (TPD).",
            emptyMap()
        )
        assertEquals(GroqLimitKind.DAILY, dailyLimit.kind)

        val creditLimit = GroqCodec.classifyLimit(402, "insufficient_quota billing limit", emptyMap())
        assertEquals(GroqLimitKind.CREDITS, creditLimit.kind)

        val html = """
            <!DOCTYPE html><html><head><style>
            .card {
            	width: 1400px;
            }
            .dup { color: red; }
            .dup { color: red; }
            </style></head><body><div id="stage"></div>
            <script>
            const s1 = { start: 1, end: 5 };
            window.RAW = { duration: 5.0, seek(t) {} };
            </script></body></html>
        """.trimIndent()

        // Tier-2 tab-vs-space normalization + line-number stripping + forbidden + ambiguous
        val mixedEdits = listOf(
            GroqPatchEdit("1", "  42: const s1 = { start: 1, end: 5 };", "const s1 = { start: 1, end: 5.0 };"),
            GroqPatchEdit("2", ".card { width: 1400px; }", ".card { width: 900px; }"),
            GroqPatchEdit("3", ".dup { color: red; }", ".dup { color: blue; }"),
            GroqPatchEdit("4", "window.RAW = { duration: 5.0, seek(t) {} };", "window.RAW = { transition: all 0.2s; };")
        )
        val applyOut = GroqCodec.applyExactOnceEdits(html, mixedEdits)
        assertEquals(2, applyOut.appliedCount)
        assertTrue(applyOut.updatedHtml.contains("width: 900px;"))
        assertTrue(applyOut.failedEdits.any { it.edit.rc == "3" && it.reason.startsWith("ambiguous") })
        assertTrue(applyOut.failedEdits.any { it.edit.rc == "4" && it.reason == "forbidden" })

        val review = GroqCodec.parseReviewJson(
            """{"ok":false,"issues":[{"f":1,"type":"clip","where":".headline","sev":"high","fix":"Reduce font size"}]}""",
            htmlHash = html.hashCode()
        )
        assertFalse(review.ok)
        assertEquals(1, review.issues.size)

        val leanPlan = GroqCodec.parseLeanPlanJson(
            """{"edits":[{"i":1,"find":".card { width: 1400px; }","replace":".card { width: 900px; }"}],"skipped":[{"t":2.0,"why":"minor"}],"structural":false}"""
        )
        assertEquals(1, leanPlan.edits.size)
        assertEquals(1, leanPlan.skippedCount)
        assertFalse(leanPlan.structural)

        val samples = listOf(
            KeyframeSample(0, 0.0, "S1", "", 0, emptyList(), emptyList()),
            KeyframeSample(1, 2.0, "S2", "", 1, listOf("Auto-resized div.headline@[140,300,1900x220] \"Hello World\""), emptyList()),
            KeyframeSample(2, 2.5, "S2", "", 0, emptyList(), listOf("Edge clip span.sub@[10,960,1880x100]")),
            KeyframeSample(3, 4.9, "S3", "", 0, emptyList(), emptyList())
        )
        val picked = KeyframeSheetGenerator.pickCollageFrames(samples, 5.0)
        assertTrue(picked.size <= 4)
        assertTrue(picked.any { it.index == 2 })
        val domBundle = KeyframeSheetGenerator.extractDomFlagBundle(samples, picked)
        assertTrue(domBundle.flagLines.any { it.contains("span.sub") && it.contains("edge-clip") })
        assertTrue(domBundle.flagTokens.contains("sub"))
    }
}
