package com.rawedit.app

import com.rawedit.app.domain.CodeValidator
import com.rawedit.app.domain.GeminiImageInput
import com.rawedit.app.domain.GeminiWaterfallRouter
import com.rawedit.app.domain.GitHubToolFetcher
import com.rawedit.app.domain.IssueCircle
import com.rawedit.app.domain.KeyframeSample
import com.rawedit.app.domain.KeyframeSheetGenerator
import com.rawedit.app.domain.PaletteColorMath
import com.rawedit.app.domain.PaletteState
import com.rawedit.app.domain.PromptGenerator
import com.rawedit.app.domain.SceneSpan
import com.rawedit.app.domain.SheetSamplingMode
import com.rawedit.app.domain.SrtParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainTests {

    @Test
    fun srtParserParsesValidStripsControlCharsFlexibleTimestampsAndGenerates100PercentScriptSrt() {
        val input = "1\n00:00:00,500 --> 00:00:02,250\nStop scrolling\u0007 now!\n\n2\n00:02.40 --> 00:04.0\nBuild offline reels."
        val res = SrtParser.parse(input)
        assertTrue(res.valid)
        assertEquals(2, res.cueCount)
        assertTrue(res.cleanedSrt.contains("Stop scrolling now!"))
        assertTrue(res.cleanedSrt.contains("Build offline reels."))

        val fullScript = "Most creators fail because they wait for perfection. Start today, ship fast, iterate every single day, and watch your editing skills compound."
        val fullGenerated = SrtParser.generateFullScriptSrt(fullScript, 16.0)
        assertTrue(fullGenerated.valid)
        assertTrue(fullGenerated.cueCount >= 5)
        assertEquals(16.0, fullGenerated.coveredDurationSec, 0.05)
        assertTrue(fullGenerated.cleanedSrt.contains("compound."))

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
    fun promptGeneratorHandlesWithAndWithoutSrtCustomPromptNotesAndGithubSkills() {
        val withoutSrt = PromptGenerator.buildPrompt("voice.mp3", 32.5, "Product launch", "")
        assertTrue(withoutSrt.contains("DURATION: 32.50 seconds"))
        assertTrue(withoutSrt.contains("kinetic-explainer-style.zip"))
        assertTrue(withoutSrt.contains("window.RAW"))
        assertTrue(withoutSrt.contains("max-width"))
        assertTrue(withoutSrt.contains("RAW.syncVideo"))
        assertTrue(withoutSrt.contains("build[o.t] || build.fallback"))
        assertTrue(withoutSrt.contains("TOPIC / SCRIPT: Product launch"))

        val withSrt = PromptGenerator.buildPrompt(
            fileName = "voice.mp3",
            durationSeconds = 32.5,
            topic = "Product launch",
            srtText = "1\n00:00:00,000 --> 00:00:02,000\nHello",
            customFonts = listOf("MyCustomFont"),
            customPromptNotes = "Always use warm mocha backgrounds.",
            githubSkills = listOf("kinetic_spring_skill")
        )
        assertTrue(withSrt.contains("00:00:00,000 --> 00:00:02,000"))
        assertTrue(withSrt.contains("Hello"))
        assertTrue(withSrt.contains("MyCustomFont"))
        assertTrue(withSrt.contains("Always use warm mocha backgrounds."))
        assertTrue(withSrt.contains("kinetic_spring_skill"))
        assertFalse(withSrt.contains("Swiss Grid Motion"))
    }

    @Test
    fun githubToolFetcherParsesOwnerRepoAndUrlCleanly() {
        val p1 = GitHubToolFetcher.parseOwnerRepoAndPath("https://github.com/SVG-Edit/svgedit")
        assertNotNull(p1)
        assertEquals("SVG-Edit", p1!!.first)
        assertEquals("svgedit", p1.second)

        val p2 = GitHubToolFetcher.parseOwnerRepoAndPath("xsalazar/emoji-kitchen/SKILL.md")
        assertNotNull(p2)
        assertEquals("xsalazar", p2!!.first)
        assertEquals("emoji-kitchen", p2.second)
        assertEquals("SKILL.md", p2.third)
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
    }

    @Test
    fun keyframeSheetGeneratorBuildsTimestampsSceneLabelsAndFixPromptWithCircledIssues() {
        val scenes = listOf(
            SceneSpan(index = 1, start = 0.0, end = 2.5, bg = "dark"),
            SceneSpan(index = 2, start = 2.5, end = 6.0, bg = "light")
        )

        val stdTimes = KeyframeSheetGenerator.buildSampleTimestamps(6.0, scenes, SheetSamplingMode.STANDARD_05S)
        assertTrue(stdTimes.first() == 0.0)
        assertTrue(stdTimes.last() >= 5.9)
        assertTrue(stdTimes.zipWithNext().all { (a, b) -> b > a })

        assertEquals("S1 DARK", KeyframeSheetGenerator.resolveSceneLabel(1.2, scenes))
        assertEquals("S2 LIGHT", KeyframeSheetGenerator.resolveSceneLabel(6.0, scenes))

        val samples = listOf(
            KeyframeSample(0, 0.0, "S1 DARK", "/tmp/a.jpg", 0, emptyList(), emptyList(), isFlagged = false),
            KeyframeSample(
                index = 4,
                timeSec = 2.5,
                sceneLabel = "S2 LIGHT",
                thumbPath = "/tmp/b.jpg",
                autoFitCount = 1,
                autoFitNotes = listOf("Resized \"ANDROID\" 200->128px"),
                warnings = emptyList(),
                isFlagged = true,
                circles = listOf(IssueCircle(centerXNorm = 0.5f, centerYNorm = 0.5f, radiusNorm = 0.15f, note = "Overlap"))
            ),
            KeyframeSample(7, 4.25, "S2 LIGHT", "/tmp/c.jpg", 1, emptyList(), listOf("Edge clip on \"VERYLONGTEXT\""), isFlagged = true)
        )
        val prompt = KeyframeSheetGenerator.buildFixPrompt(
            samples = samples,
            title = "My Test Reel",
            durationSec = 6.0,
            htmlCode = "<!DOCTYPE html><html><div id=\"stage\"></div></html>"
        )
        assertTrue(prompt.contains("t=4.25s"))
        assertTrue(prompt.contains("t=2.50s"))
        assertTrue(prompt.contains("USER CIRCLED ISSUE"))
        assertTrue(prompt.contains("x=960, y=540"))
        assertFalse(prompt.contains("t=0.00s"))
        assertTrue(prompt.contains("<<<SEARCH"))
        assertTrue(prompt.contains("===REPLACE"))
        assertTrue(prompt.contains("CURRENT HTML"))
    }

    @Test
    fun paletteMathComputesContrastInkAndHexNormalization() {
        assertEquals("#F7ECE6", PaletteColorMath.normalizeHex("f7ece6"))
        assertEquals("#DCC9A9", PaletteColorMath.normalizeHex("not-a-hex"))
        assertTrue(PaletteColorMath.luminance("#F7ECE6") > PaletteColorMath.luminance("#111112"))
        assertEquals(0xFF111112L, PaletteColorMath.contrastInkArgb("#DCC9A9"))
        assertEquals(0xFFF7ECE6L, PaletteColorMath.contrastInkArgb("#0D0D0D"))
        assertEquals("01", PaletteState.DEFAULT_01.numFormatted)
    }

    @Test
    fun geminiWaterfallRouterVerification() {
        assertEquals("gemini-3.5-flash-lite", GeminiWaterfallRouter.WORKHORSE_CHAIN.first())
        assertEquals("gemini-3.8-flash", GeminiWaterfallRouter.CREATIVE_CHAIN.first())
        assertEquals("gemini-2.5-flash", GeminiWaterfallRouter.FAST_CHAIN.first())
        assertEquals(4, GeminiWaterfallRouter.WORKHORSE_CHAIN.size)
        assertEquals(4, GeminiWaterfallRouter.CREATIVE_CHAIN.size)

        val payload = GeminiWaterfallRouter.buildGenerateContentPayload(
            prompt = "Test prompt",
            systemInstruction = "You are a helper",
            expectJson = true,
            images = emptyList<GeminiImageInput>()
        )
        assertFalse(payload.contains("thinkingConfig"))
        assertTrue(payload.contains("\"maxOutputTokens\":8192") || payload.contains("\"maxOutputTokens\": 8192"))
        assertTrue(payload.contains("application/json"))
        assertTrue(payload.contains("Test prompt"))
    }
}
