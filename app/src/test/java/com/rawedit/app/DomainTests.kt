package com.rawedit.app

import com.rawedit.app.data.local.LocalAssetEntity
import com.rawedit.app.domain.CodeValidator
import com.rawedit.app.domain.KeyframeSample
import com.rawedit.app.domain.KeyframeSheetGenerator
import com.rawedit.app.domain.PaletteColorMath
import com.rawedit.app.domain.PaletteState
import com.rawedit.app.domain.PaletteSwatch
import com.rawedit.app.domain.PromptGenerator
import com.rawedit.app.domain.SceneSpan
import com.rawedit.app.domain.SheetSamplingMode
import com.rawedit.app.domain.SrtParser
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
}
