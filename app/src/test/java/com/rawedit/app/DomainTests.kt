package com.rawedit.app

import com.rawedit.app.data.model.LayerJson
import com.rawedit.app.data.model.RawLayer
import com.rawedit.app.domain.CodeValidator
import com.rawedit.app.domain.PromptGenerator
import com.rawedit.app.domain.SrtParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainTests {

    @Test
    fun srtParserParsesValidAndStripsControlChars() {
        val input = "1\n00:00:00,500 --> 00:00:02,250\nStop scrolling\u0007 now!\n\n2\n00:00:02,400 --> 00:00:04,000\nBuild offline reels."
        val res = SrtParser.parse(input)
        assertTrue(res.valid)
        assertEquals(2, res.cueCount)
        assertTrue(res.cleanedSrt.contains("Stop scrolling now!"))
    }

    @Test
    fun srtParserRejectsBrokenText() {
        val res = SrtParser.parse("not an srt file at all")
        assertFalse(res.valid)
        assertEquals(0, res.cueCount)
    }

    @Test
    fun promptGeneratorHandlesWithAndWithoutSrt() {
        val withoutSrt = PromptGenerator.buildPrompt("voice.mp3", 32.5, "Product launch", "")
        assertTrue(withoutSrt.contains("Voiceover length: 32.50 seconds"))
        assertTrue(withoutSrt.contains("If yes, paste its text"))

        val withSrt = PromptGenerator.buildPrompt("voice.mp3", 32.5, "Product launch", "1\n00:00:00,000 --> 00:00:02,000\nHello")
        assertTrue(withSrt.contains("skip asking this question"))
        assertTrue(withSrt.contains("Attached SRT subtitles:"))
    }

    @Test
    fun codeValidatorBlocksExternalUrlsAndNetworkApis() {
        val badUrl = "<html><div id='stage'></div><script>window.RAW={duration:5,seek:()=>{}};fetch('https://evil.com')</script></html>"
        assertFalse(CodeValidator.staticValidate(badUrl).first)

        val badScriptSrc = "<html><script src='https://cdn.example.com/a.js'></script><script>window.RAW={duration:5,seek:()=>{}}</script></html>"
        assertFalse(CodeValidator.staticValidate(badScriptSrc).first)

        val valid = "<html><div id='stage'></div><script>window.RAW={duration:5,layers:[{id:'a',src:'https://assets.local/AbCd1234.png'}],seek:function(t){}}</script></html>"
        assertTrue(CodeValidator.staticValidate(valid).first)
    }

    @Test
    fun layerJsonRoundTripAndHiddenExport() {
        val layers = listOf(
            RawLayer(id = "t1", name = "Title", type = "text", opacity = 0.9f, hidden = true)
        )
        val decoded = LayerJson.decode(LayerJson.encode(layers))
        assertEquals(1, decoded.size)
        assertTrue(decoded.first().hidden)

        val stageJson = LayerJson.encodeForStage(decoded)
        assertTrue(stageJson.contains("\"opacity\":0.0"))
    }
}
