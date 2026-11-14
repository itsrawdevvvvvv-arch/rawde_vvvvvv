package com.rawedit.app.domain

import android.content.Context
import android.util.Base64
import com.rawedit.app.data.local.UserProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

data class UnifiedAiResult(
    val text: String,
    val providerUsed: String,
    val modelUsed: String,
    val fallbackTriggered: Boolean = false,
    val toastNotice: String? = null
)

object UnifiedAiRouter {

    suspend fun executeTextOrJson(
        context: Context,
        geminiRouter: GeminiWaterfallRouter,
        userProfile: UserProfile,
        prompt: String,
        systemInstruction: String? = null,
        expectJson: Boolean = true,
        images: List<GeminiImageInput> = emptyList(),
        preferCreative: Boolean = false
    ): Result<UnifiedAiResult> = withContext(Dispatchers.IO) {
        val provider = userProfile.resolvedAiProvider()
        val hasGemini = userProfile.hasGeminiKey
        val hasGroq = userProfile.hasGroqKey

        val routeMode = when {
            userProfile.geminiTaskRoute == GeminiTaskRoute.MAX_QUOTA_SAVER.id -> GeminiRouteMode.JSON_WORKHORSE
            userProfile.geminiTaskRoute == GeminiTaskRoute.MAX_QUALITY.id -> GeminiRouteMode.CREATIVE_FLASH
            preferCreative -> GeminiRouteMode.CREATIVE_FLASH
            else -> GeminiRouteMode.JSON_WORKHORSE
        }

        when (provider) {
            AiProvider.GEMINI_FREE -> {
                if (!hasGemini) {
                    return@withContext Result.failure(
                        IllegalStateException("Please add your Google AI Studio Gemini API key in Profile -> AI & Quota.")
                    )
                }
                val res = geminiRouter.generate(
                    apiKey = userProfile.geminiApiKey,
                    prompt = prompt,
                    systemInstruction = systemInstruction,
                    routeMode = routeMode,
                    expectJson = expectJson,
                    images = images
                )
                res.map { g ->
                    UnifiedAiResult(
                        text = g.text,
                        providerUsed = "Gemini",
                        modelUsed = g.modelUsed,
                        fallbackTriggered = g.fallbackTriggered,
                        toastNotice = if (g.fallbackTriggered) "Served via ${g.modelUsed} fallback" else null
                    )
                }
            }

            AiProvider.GROQ_CLOUD -> {
                if (!hasGroq) {
                    return@withContext Result.failure(
                        IllegalStateException("Please add your Groq API key in Profile -> AI & Quota.")
                    )
                }
                executeGroqFallback(
                    userProfile = userProfile,
                    prompt = prompt,
                    systemInstruction = systemInstruction,
                    expectJson = expectJson,
                    images = images
                )
            }

            AiProvider.AUTO_HYBRID -> {
                // Try Gemini waterfall first if key is present
                if (hasGemini) {
                    val geminiRes = geminiRouter.generate(
                        apiKey = userProfile.geminiApiKey,
                        prompt = prompt,
                        systemInstruction = systemInstruction,
                        routeMode = routeMode,
                        expectJson = expectJson,
                        images = images
                    )
                    if (geminiRes.isSuccess) {
                        val g = geminiRes.getOrThrow()
                        return@withContext Result.success(
                            UnifiedAiResult(
                                text = g.text,
                                providerUsed = "Gemini",
                                modelUsed = g.modelUsed,
                                fallbackTriggered = g.fallbackTriggered,
                                toastNotice = if (g.fallbackTriggered) "Served via ${g.modelUsed} fallback" else null
                            )
                        )
                    }
                }

                // If Gemini failed or no Gemini key, cascade to Groq if available
                if (hasGroq) {
                    val groqRes = executeGroqFallback(
                        userProfile = userProfile,
                        prompt = prompt,
                        systemInstruction = systemInstruction,
                        expectJson = expectJson,
                        images = images
                    )
                    return@withContext groqRes.map { gr ->
                        gr.copy(
                            fallbackTriggered = true,
                            toastNotice = "Served via Groq fallback (${gr.modelUsed})"
                        )
                    }
                }

                Result.failure(
                    IllegalStateException("All AI providers unavailable. Add a free Gemini or Groq API key in Profile -> AI & Quota.")
                )
            }
        }
    }

    suspend fun reviewContactSheet(
        geminiRouter: GeminiWaterfallRouter,
        userProfile: UserProfile,
        samples: List<KeyframeSample>,
        durationSec: Double,
        htmlHash: Int = 0
    ): Result<SheetReviewResult> = withContext(Dispatchers.IO) {
        val provider = userProfile.resolvedAiProvider()
        val hasGemini = userProfile.hasGeminiKey
        val hasGroq = userProfile.hasGroqKey

        if (provider == AiProvider.GROQ_CLOUD || (!hasGemini && hasGroq)) {
            return@withContext GroqClient.reviewContactSheet(
                apiKey = userProfile.groqApiKey,
                samples = samples,
                durationSec = durationSec,
                htmlHash = htmlHash,
                modelPref = userProfile.groqModelPref
            )
        }

        if (hasGemini) {
            val geminiResult = runCatching {
                val bundle = KeyframeSheetGenerator.buildReviewCollageBundle(samples, durationSec)
                if (bundle.jpegBytes.isEmpty() || bundle.pickedSamples.isEmpty()) {
                    throw IllegalStateException("No keyframe thumbnails available to inspect.")
                }
                val flagsText = if (bundle.flagLines.isEmpty()) "None" else bundle.flagLines.joinToString("\n")
                val userPrompt = (
                    "DURATION: ${String.format(Locale.US, "%.1f", durationSec)}s\n" +
                        "FLAGS (f|t|selector|x,y,wxh|issue):\n$flagsText"
                    ).take(1500)
                val b64 = Base64.encodeToString(bundle.jpegBytes, Base64.NO_WRAP)

                val genRes = geminiRouter.generate(
                    apiKey = userProfile.geminiApiKey,
                    prompt = userPrompt,
                    systemInstruction = GroqClient.REVIEW_SYSTEM,
                    routeMode = GeminiRouteMode.JSON_WORKHORSE,
                    expectJson = true,
                    images = listOf(GeminiImageInput(base64Data = b64, mimeType = "image/jpeg"))
                ).getOrThrow()

                val parsed = GroqCodec.parseReviewJson(genRes.text, htmlHash)
                if (parsed.parseFailed) {
                    SheetReviewResult(
                        summary = "Unreadable review response from Gemini — retry",
                        issues = emptyList(),
                        cachedReview = null
                    )
                } else {
                    val mappedIssues = parsed.issues.map { issue ->
                        val picked = bundle.pickedSamples.getOrNull(
                            (issue.f - 1).coerceIn(0, (bundle.pickedSamples.size - 1).coerceAtLeast(0))
                        )
                        SheetIssue(
                            frame = picked?.let { "#${it.index + 1}" } ?: "F${issue.f}",
                            time = picked?.let { String.format(Locale.US, "t=%.2fs", it.timeSec) }.orEmpty(),
                            problem = "[${issue.type}] ${issue.where}".trim(),
                            fix = issue.fix,
                            sampleIndex = picked?.index ?: -1
                        )
                    }
                    val summary = if (mappedIssues.isEmpty()) {
                        "Gemini Vision checked ${bundle.pickedSamples.size}-frame collage: layout and contrast look clean."
                    } else {
                        "Found ${mappedIssues.size} visual issue(s) across ${bundle.pickedSamples.size}-frame collage."
                    }
                    SheetReviewResult(
                        summary = summary,
                        issues = mappedIssues,
                        cachedReview = parsed
                    )
                }
            }

            if (geminiResult.isSuccess) {
                return@withContext geminiResult
            }

            // Fallback to Groq if in AUTO_HYBRID mode
            if (provider == AiProvider.AUTO_HYBRID && hasGroq) {
                return@withContext GroqClient.reviewContactSheet(
                    apiKey = userProfile.groqApiKey,
                    samples = samples,
                    durationSec = durationSec,
                    htmlHash = htmlHash,
                    modelPref = userProfile.groqModelPref
                )
            }

            return@withContext Result.failure(geminiResult.exceptionOrNull() ?: IllegalStateException("Review failed"))
        }

        Result.failure(IllegalStateException("No AI provider configured. Add Gemini or Groq key."))
    }

    suspend fun autoFixLoop(
        geminiRouter: GeminiWaterfallRouter,
        userProfile: UserProfile,
        samples: List<KeyframeSample>,
        htmlCode: String,
        durationSec: Double,
        lastReview: GroqReviewResult? = null,
        maxRounds: Int = 3,
        validator: suspend (String) -> CodeValidationResult,
        recapture: suspend (String) -> List<KeyframeSample>,
        onRound: (round: Int, issuesNow: Int) -> Unit = { _, _ -> }
    ): Result<GroqAutoFixOutcome> = withContext(Dispatchers.IO) {
        val provider = userProfile.resolvedAiProvider()
        val hasGemini = userProfile.hasGeminiKey
        val hasGroq = userProfile.hasGroqKey

        if (provider == AiProvider.GROQ_CLOUD || (!hasGemini && hasGroq)) {
            return@withContext GroqClient.autoFixLoop(
                apiKey = userProfile.groqApiKey,
                samples = samples,
                htmlCode = htmlCode,
                durationSec = durationSec,
                modelPref = userProfile.groqModelPref,
                lastReview = lastReview,
                maxRounds = maxRounds,
                validator = validator,
                recapture = recapture,
                onRound = onRound
            )
        }

        // Try Gemini auto fix if Gemini is active
        if (hasGemini) {
            val geminiFix = runCatching {
                autoFixWithGemini(
                    geminiRouter = geminiRouter,
                    apiKey = userProfile.geminiApiKey,
                    samples = samples,
                    htmlCode = htmlCode,
                    durationSec = durationSec,
                    lastReview = lastReview,
                    maxRounds = maxRounds,
                    validator = validator,
                    recapture = recapture,
                    onRound = onRound
                )
            }
            if (geminiFix.isSuccess) {
                return@withContext geminiFix
            }

            if (provider == AiProvider.AUTO_HYBRID && hasGroq) {
                return@withContext GroqClient.autoFixLoop(
                    apiKey = userProfile.groqApiKey,
                    samples = samples,
                    htmlCode = htmlCode,
                    durationSec = durationSec,
                    modelPref = userProfile.groqModelPref,
                    lastReview = lastReview,
                    maxRounds = maxRounds,
                    validator = validator,
                    recapture = recapture,
                    onRound = onRound
                )
            }

            return@withContext Result.failure(geminiFix.exceptionOrNull() ?: IllegalStateException("Auto-fix failed"))
        }

        Result.failure(IllegalStateException("No AI provider configured. Add Gemini or Groq key."))
    }

    private suspend fun autoFixWithGemini(
        geminiRouter: GeminiWaterfallRouter,
        apiKey: String,
        samples: List<KeyframeSample>,
        htmlCode: String,
        durationSec: Double,
        lastReview: GroqReviewResult?,
        maxRounds: Int,
        validator: suspend (String) -> CodeValidationResult,
        recapture: suspend (String) -> List<KeyframeSample>,
        onRound: (round: Int, issuesNow: Int) -> Unit
    ): GroqAutoFixOutcome {
        val startIssues = KeyframeSheetGenerator.countIssues(samples)
        var curHtml = htmlCode
        var curSamples = samples
        var curIssues = startIssues
        var bestOutcome: GroqAutoFixOutcome? = null
        var editsTotal = 0
        var rounds = 0
        var lastStatus = GroqAutoFixStatus.NOTHING_MATCHED
        var review = lastReview

        while (rounds < maxRounds) {
            val domBundle = KeyframeSheetGenerator.extractDomFlagBundle(curSamples)
            val findings = buildString {
                review?.issues?.forEach { issue ->
                    append("R|f").append(issue.f)
                        .append('|').append(issue.type)
                        .append('|').append(issue.where)
                        .append('|').append(issue.sev)
                        .append('|').append(issue.fix)
                        .append('\n')
                }
                if (domBundle.flagLines.isNotEmpty()) {
                    append("DOM (selector|x,y,wxh|issue|times):\n")
                    append(KeyframeSheetGenerator.compressFlagLines(domBundle.flagLines))
                }
            }.trim().take(2600)

            val issueTokens = review?.issues?.flatMap { issue ->
                Regex("[A-Za-z0-9_#.-]{3,}").findAll("${issue.where} ${issue.fix}").map { it.value }.toList()
            }.orEmpty()
            val excerptTokens = (domBundle.flagTokens + issueTokens).distinct()
            val excerpt = GroqCodec.buildCleanExcerpt(html = curHtml, tokens = excerptTokens, maxChars = 6000, radius = 10)
            val durStr = String.format(Locale.US, "%.1f", durationSec)
            val userPrompt = "DURATION: $durStr\nFINDINGS:\n$findings\nEXCERPT:\n$excerpt"

            val genRes = geminiRouter.generate(
                apiKey = apiKey,
                prompt = userPrompt,
                systemInstruction = GroqClient.PROMPT_C1_SYSTEM,
                routeMode = GeminiRouteMode.JSON_WORKHORSE,
                expectJson = true
            ).getOrThrow()

            val plan = GroqCodec.parseLeanPlanJson(genRes.text)
            val applyOutcome = GroqCodec.applyEditsValidated(curHtml, plan.edits) { candidate ->
                validator(candidate).isValid
            }

            if (applyOutcome.appliedCount == 0) {
                lastStatus = GroqAutoFixStatus.NOTHING_MATCHED
                break
            }

            rounds++
            review = null
            val candidateHtml = applyOutcome.updatedHtml
            val validated = validator(candidateHtml)
            val newSamples = recapture(candidateHtml)
            val after = KeyframeSheetGenerator.countIssues(newSamples)
            onRound(rounds, after)

            if (after < curIssues) {
                curHtml = candidateHtml
                curSamples = newSamples
                curIssues = after
                editsTotal += applyOutcome.appliedCount
                bestOutcome = GroqAutoFixOutcome(
                    status = GroqAutoFixStatus.APPLIED,
                    fixedHtml = curHtml,
                    validation = validated,
                    editsApplied = editsTotal,
                    modelUsed = genRes.modelUsed,
                    issuesBefore = startIssues,
                    issuesAfter = curIssues,
                    roundsRun = rounds
                )
                if (after == 0) break
            } else {
                break
            }
        }

        return bestOutcome ?: GroqAutoFixOutcome(
            status = lastStatus,
            fixedHtml = htmlCode,
            validation = null,
            failureSummary = "No patches could improve the layout.",
            issuesBefore = startIssues,
            issuesAfter = startIssues,
            roundsRun = rounds
        )
    }

    private suspend fun executeGroqFallback(
        userProfile: UserProfile,
        prompt: String,
        systemInstruction: String?,
        expectJson: Boolean,
        images: List<GeminiImageInput>
    ): Result<UnifiedAiResult> = withContext(Dispatchers.IO) {
        try {
            val groqImages = images.map { img ->
                GroqImageInput(
                    base64Data = img.base64Data,
                    mimeType = img.mimeType
                )
            }

            val res = GroqClient.callRawChatCompletion(
                apiKey = userProfile.groqApiKey,
                modelPref = userProfile.groqModelPref,
                prompt = prompt,
                systemInstruction = systemInstruction,
                jsonObjectMode = expectJson,
                images = groqImages
            )
            Result.success(
                UnifiedAiResult(
                    text = res.text,
                    providerUsed = "Groq",
                    modelUsed = res.modelUsed,
                    fallbackTriggered = false
                )
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }
}
