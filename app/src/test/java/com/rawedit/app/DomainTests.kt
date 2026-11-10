package com.rawedit.app

import com.rawedit.app.domain.CodeValidator
import com.rawedit.app.domain.PromptGenerator
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
}
