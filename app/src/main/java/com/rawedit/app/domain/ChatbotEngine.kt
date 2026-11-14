package com.rawedit.app.domain

import android.content.Context
import com.rawedit.app.data.CacheManager
import com.rawedit.app.data.ChatUploadedFile
import com.rawedit.app.data.local.LocalAssetEntity
import com.rawedit.app.data.local.ProjectEntity
import com.rawedit.app.data.local.UserPreferences
import com.rawedit.app.data.local.UserProfile
import com.rawedit.app.data.repository.RawEditRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

data class ChatMessageItem(
    val id: String = "msg_${System.currentTimeMillis()}_${(100..999).random()}",
    val role: String, // "user" or "assistant"
    val text: String,
    val attachmentName: String = "",
    val attachmentPath: String = "",
    val attachmentSize: Long = 0L,
    val appliedActions: List<String> = emptyList(),
    val timestampMs: Long = System.currentTimeMillis()
)

data class ChatbotTurnOutcome(
    val assistantMessage: ChatMessageItem,
    val updatedProject: ProjectEntity? = null,
    val updatedPromptNotes: String? = null
)

object ChatbotEngine {

    private val PROMPT_UPDATE_REGEX = Regex("""<<<PROMPT_UPDATE\s*\n([\s\S]*?)>>>""", RegexOption.IGNORE_CASE)
    private val STYLE_ZIP_REGEX = Regex("""<<<STYLE_ZIP_FILE\s+path="([^"]+)"\s*>>>\s*\n([\s\S]*?)<<<END_STYLE_ZIP_FILE>>>""", RegexOption.IGNORE_CASE)
    private val GITHUB_FETCH_REGEX = Regex("""<<<GITHUB_FETCH\s+query="([^"]+)"\s*>>>""", RegexOption.IGNORE_CASE)

    fun loadChatHistory(cacheManager: CacheManager, sessionKey: String = "global"): List<ChatMessageItem> {
        val file = File(cacheManager.chatHistoryDir, "chat_${sessionKey}.json")
        if (!file.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(file.readText(Charsets.UTF_8))
            val out = mutableListOf<ChatMessageItem>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val actionsArr = o.optJSONArray("appliedActions") ?: JSONArray()
                val actions = (0 until actionsArr.length()).mapNotNull { idx ->
                    actionsArr.optString(idx).takeIf { it.isNotBlank() }
                }
                out.add(
                    ChatMessageItem(
                        id = o.optString("id", "msg_$i"),
                        role = o.optString("role", "assistant"),
                        text = o.optString("text", ""),
                        attachmentName = o.optString("attachmentName", ""),
                        attachmentPath = o.optString("attachmentPath", ""),
                        attachmentSize = o.optLong("attachmentSize", 0L),
                        appliedActions = actions,
                        timestampMs = o.optLong("timestampMs", 0L)
                    )
                )
            }
            out
        }.getOrDefault(emptyList())
    }

    fun saveChatHistory(cacheManager: CacheManager, messages: List<ChatMessageItem>, sessionKey: String = "global") {
        runCatching {
            val file = File(cacheManager.chatHistoryDir, "chat_${sessionKey}.json")
            val arr = JSONArray()
            messages.takeLast(80).forEach { m ->
                arr.put(
                    JSONObject().apply {
                        put("id", m.id)
                        put("role", m.role)
                        put("text", m.text)
                        put("attachmentName", m.attachmentName)
                        put("attachmentPath", m.attachmentPath)
                        put("attachmentSize", m.attachmentSize)
                        put("appliedActions", JSONArray().apply { m.appliedActions.forEach { put(it) } })
                        put("timestampMs", m.timestampMs)
                    }
                )
            }
            file.writeText(arr.toString(2), Charsets.UTF_8)
        }
    }

    fun clearChatHistory(cacheManager: CacheManager, sessionKey: String = "global") {
        runCatching {
            File(cacheManager.chatHistoryDir, "chat_${sessionKey}.json").delete()
        }
    }

    suspend fun sendAppChatMessage(
        context: Context,
        geminiRouter: GeminiWaterfallRouter,
        cacheManager: CacheManager,
        userPreferences: UserPreferences,
        repository: RawEditRepository,
        userProfile: UserProfile,
        allProjects: List<ProjectEntity>,
        activeProject: ProjectEntity?,
        allAssets: List<LocalAssetEntity>,
        activePalette: PaletteState,
        history: List<ChatMessageItem>,
        userText: String,
        uploadedFile: ChatUploadedFile? = null
    ): Result<ChatbotTurnOutcome> = withContext(Dispatchers.IO) {
        runCatching {
            if (!userProfile.hasGeminiKey) {
                throw IllegalStateException("Please add your Gemini API key in Profile -> AI & Quota first.")
            }

            val appliedBadges = mutableListOf<String>()

            // If user explicitly asked to fetch from a GitHub URL or repo in their message, pre-fetch it!
            val directGhMatch = Regex("""(?:github\.com/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+|fetch\s+from\s+github\s+([A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+))""", RegexOption.IGNORE_CASE)
                .find(userText)
            var preFetchedGithubNote = ""
            if (directGhMatch != null) {
                val target = directGhMatch.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() } ?: directGhMatch.value
                GitHubToolFetcher.fetchAndSaveFromGithub(target, userProfile.githubAccessToken, cacheManager)
                    .onSuccess { items ->
                        items.forEach { item ->
                            appliedBadges.add("Fetched GitHub skill: ${item.name}")
                            preFetchedGithubNote += "\nFetched from GitHub (${item.name}):\n${item.contentPreview}\n"
                        }
                    }
            }

            // Resolve active project's full SRT, Script, and HTML code
            val activeSrt = activeProject?.let { cacheManager.resolveSrtText(it.srtText) }.orEmpty()
            val activeTopic = activeProject?.let { cacheManager.resolveTopicText(it.topic) }.orEmpty()
            val activeHtml = activeProject?.let { cacheManager.resolveHtmlCode(it.code) }.orEmpty()

            // Summarize all projects' scripts & SRTs so the Chatbot can suggest next reel ideas
            val projectsSummary = buildString {
                if (allProjects.isEmpty()) {
                    appendLine("No projects created yet.")
                } else {
                    allProjects.take(8).forEachIndexed { idx, p ->
                        val t = cacheManager.resolveTopicText(p.topic).take(500)
                        val s = cacheManager.resolveSrtText(p.srtText).take(600)
                        appendLine("Project #${idx + 1}: \"${p.title}\" (${String.format(Locale.US, "%.1f", p.audioDurationSec)}s, ${p.srtCueCount} SRT cues)")
                        if (t.isNotBlank()) appendLine("  Script/Topic: $t")
                        if (s.isNotBlank()) appendLine("  SRT Excerpt: ${s.replace("\n", " | ").take(350)}")
                    }
                }
            }

            val customFonts = cacheManager.listCustomFonts().map { it.name }
            val githubSkills = cacheManager.listGithubSkillFiles().map { "${it.name}: ${it.readText(Charsets.UTF_8).take(220).replace("\n", " ")}" }
            val styleSkillMd = cacheManager.readStylePackEntryText("kinetic-explainer-style/SKILL.md").take(1800)
            val styleTokensCss = cacheManager.readStylePackEntryText("kinetic-explainer-style/style/tokens.css").take(1000)

            val attachmentContext = if (uploadedFile != null) {
                buildString {
                    appendLine("USER UPLOADED FILE IN CHAT: ${uploadedFile.fileName} (${uploadedFile.sizeBytes} bytes, MIME: ${uploadedFile.mimeType}, stored locally at ${uploadedFile.localPath})")
                    if (uploadedFile.textPreview.isNotBlank()) {
                        appendLine("FILE CONTENT / PREVIEW:")
                        appendLine(uploadedFile.textPreview.take(12_000))
                    }
                }
            } else ""

            val recentConvo = history.takeLast(8).joinToString("\n") { m ->
                "${m.role.uppercase(Locale.US)}: ${m.text.take(900)}"
            }

            val visionInputs = mutableListOf<GeminiImageInput>()
            if (uploadedFile != null) {
                val f = File(uploadedFile.localPath)
                val ext = f.extension.lowercase(Locale.US)
                if (f.exists() && ext in setOf("png", "jpg", "jpeg", "webp") && f.length() <= 25L * 1024L * 1024L) {
                    runCatching {
                        val (b64, mime) = geminiRouter.encodeFileForVision(f, 1024)
                        visionInputs.add(GeminiImageInput(base64Data = b64, mimeType = mime))
                    }
                }
            }

            val systemInstruction = """
                You are the built-in RAW EDIT Studio AI Chatbot & Creative Director.
                You have full access to the entire RawEdit app on the user's phone:
                - User's pasted SRT subtitles and scripts across all projects (use these to suggest high-retention ideas, hooks, and scripts for their next reel!).
                - The active Studio Color Palette (#${activePalette.numFormatted} ${activePalette.mood}).
                - Installed custom fonts (${if (customFonts.isEmpty()) "none yet" else customFonts.joinToString(", ")}).
                - The Style Pack ZIP (`kinetic-explainer-style.zip`, containing `SKILL.md`, `docs/STYLE_BIBLE.md`, `style/tokens.css`, `style/components.css`, `engine/engine.js`, `engine/raw-helpers.js`).
                - The Cloud AI Prompt generator & Custom Prompt Directives.
                - GitHub integration (${if (userProfile.hasGithubToken) "GitHub token active" else "Public GitHub access active — user can also paste a token in Settings"}).

                HOW TO APPLY LIVE CHANGES WHEN THE USER ASKS TO TWEAK THE PROMPT, STYLE ZIP, GITHUB SKILLS, OR HTML CODE:
                1. To tweak/customize the Prompt that gets copied for AI tools, include a block in your reply:
                <<<PROMPT_UPDATE
                [Write the exact custom rules/tweaks to inject into the prompt here]
                >>>
                2. To customize or add a file inside `kinetic-explainer-style.zip` (such as `SKILL.md`, `docs/STYLE_BIBLE.md`, `style/tokens.css`, `engine/raw-helpers.js`, or a custom skill), include:
                <<<STYLE_ZIP_FILE path="kinetic-explainer-style/SKILL.md">>>
                [Full updated file content]
                <<<END_STYLE_ZIP_FILE>>>
                3. To fetch a tool/skill/repo from GitHub, include:
                <<<GITHUB_FETCH query="owner/repo or search term">>>
                4. To patch the active project's HTML reel code, include standard search/replace blocks:
                <<<SEARCH
                exact lines in current HTML
                ===REPLACE
                updated lines
                >>>
                Always explain clearly and warmly what you did or suggest punchy, actionable reel ideas tailored to the user's style!
            """.trimIndent()

            val lowerMsg = userText.lowercase(Locale.US)
            val isHeavyTask = uploadedFile != null ||
                userText.length > 140 ||
                listOf("srt", "script", "idea", "reel", "prompt", "zip", "style", "skill", "html", "code", "fix", "patch", "github", "font").any { lowerMsg.contains(it) }

            val fullPrompt = if (!isHeavyTask) {
                buildString {
                    if (recentConvo.isNotBlank()) {
                        appendLine("RECENT CHAT:")
                        appendLine(recentConvo)
                    }
                    appendLine("USER: $userText")
                }
            } else {
                buildString {
                    appendLine("=== APP STATE & PROJECTS SUMMARY ===")
                    appendLine(projectsSummary)
                    if (activeProject != null) {
                        appendLine("=== ACTIVE PROJECT (${activeProject.title}) ===")
                        appendLine("Audio: ${activeProject.audioName} (${String.format(Locale.US, "%.2f", activeProject.audioDurationSec)}s)")
                        if (activeTopic.isNotBlank()) appendLine("Full Active Script/Topic:\n${activeTopic.take(4000)}")
                        if (activeSrt.isNotBlank()) appendLine("Full Active SRT (${activeProject.srtCueCount} cues):\n${activeSrt.take(5000)}")
                        if (activeHtml.isNotBlank()) appendLine("Active HTML Code (${activeHtml.length} chars):\n${activeHtml.take(8000)}")
                    }
                    appendLine("=== CURRENT CUSTOM PROMPT DIRECTIVES ===")
                    appendLine(userProfile.customPromptNotes.ifBlank { "(Default Kinetic Explainer prompt)" })
                    appendLine("=== STYLE PACK ZIP (SKILL.md & tokens.css preview) ===")
                    appendLine(styleSkillMd)
                    appendLine(styleTokensCss)
                    if (githubSkills.isNotEmpty()) {
                        appendLine("=== DOWNLOADED GITHUB SKILLS ===")
                        githubSkills.forEach { appendLine(it) }
                    }
                    if (preFetchedGithubNote.isNotBlank()) {
                        appendLine(preFetchedGithubNote)
                    }
                    if (attachmentContext.isNotBlank()) {
                        appendLine(attachmentContext)
                    }
                    if (recentConvo.isNotBlank()) {
                        appendLine("=== RECENT CONVERSATION ===")
                        appendLine(recentConvo)
                    }
                    appendLine("=== USER MESSAGE ===")
                    appendLine(userText)
                }
            }

            val genRes = geminiRouter.generate(
                apiKey = userProfile.geminiApiKey,
                prompt = fullPrompt,
                systemInstruction = if (isHeavyTask) systemInstruction else "You are the friendly, fast RAW EDIT Studio AI Assistant. Reply concisely and helpfully.",
                routeMode = GeminiRouteMode.FAST_CHAT,
                expectJson = false,
                images = visionInputs,
                maxOutputTokens = if (isHeavyTask) 4096 else 1024
            ).getOrThrow()

            var replyText = genRes.text
            var updatedPromptNotes: String? = null
            var updatedProject: ProjectEntity? = null

            // 1. Check for <<<PROMPT_UPDATE ... >>>
            PROMPT_UPDATE_REGEX.find(replyText)?.let { m ->
                val newNotes = m.groupValues[1].trim()
                if (newNotes.isNotBlank()) {
                    userPreferences.saveCustomPromptNotes(newNotes)
                    updatedPromptNotes = newNotes
                    appliedBadges.add("Updated Cloud AI Prompt Directives")
                }
                replyText = replyText.replace(m.value, "").trim()
            }

            // 2. Check for <<<STYLE_ZIP_FILE path="...">>> ... <<<END_STYLE_ZIP_FILE>>>
            STYLE_ZIP_REGEX.findAll(replyText).toList().forEach { m ->
                val path = m.groupValues[1].trim()
                val content = m.groupValues[2].trim()
                if (path.isNotBlank() && content.isNotBlank()) {
                    cacheManager.saveStylePackOverride(path, content)
                    appliedBadges.add("Customized Style ZIP: $path")
                }
                replyText = replyText.replace(m.value, "").trim()
            }

            // 3. Check for <<<GITHUB_FETCH query="...">>>
            GITHUB_FETCH_REGEX.findAll(replyText).toList().forEach { m ->
                val q = m.groupValues[1].trim()
                if (q.isNotBlank()) {
                    GitHubToolFetcher.fetchAndSaveFromGithub(q, userProfile.githubAccessToken, cacheManager)
                        .onSuccess { items ->
                            items.forEach { item ->
                                appliedBadges.add("Downloaded GitHub tool: ${item.name}")
                                replyText += "\n\nDownloaded from GitHub: **${item.name}** (`${item.localFileName}`)"
                            }
                        }.onFailure { err ->
                            replyText += "\n\n*(GitHub fetch note: ${err.message})*"
                        }
                }
                replyText = replyText.replace(m.value, "").trim()
            }

            // 4. Check for HTML patches if activeProject has HTML code
            if (activeProject != null && activeHtml.isNotBlank() && replyText.contains("<<<SEARCH")) {
                val patches = GroqCodec.parseClaudeSearchReplacePatches(replyText)
                if (patches.isNotEmpty()) {
                    val outcome = GroqCodec.applyExactOnceEdits(activeHtml, patches)
                    if (outcome.appliedCount > 0) {
                        val validated = CodeValidator.validateFull(context, cacheManager, outcome.updatedHtml)
                        if (validated.isValid) {
                            HtmlVersionStore.save(context, activeProject.id, activeHtml, "Before Chatbot edit")
                            val stored = cacheManager.persistHtmlCode(activeProject.id, validated.cleanedHtml)
                            val nextProj = activeProject.copy(
                                code = stored,
                                codeDurationSec = validated.durationSec
                            )
                            repository.saveProject(nextProj)
                            updatedProject = nextProj
                            appliedBadges.add("Applied ${outcome.appliedCount} HTML patch(es) to ${activeProject.title}")
                        }
                    }
                }
            }

            val assistantMsg = ChatMessageItem(
                role = "assistant",
                text = replyText.ifBlank { "Done! I've applied your requested updates." },
                appliedActions = appliedBadges
            )

            ChatbotTurnOutcome(
                assistantMessage = assistantMsg,
                updatedProject = updatedProject,
                updatedPromptNotes = updatedPromptNotes
            )
        }
    }

    /**
     * Dedicated Scene Fix Chatbot inside Contact Sheet -> Scenes tab.
     * Receives the selected scene's frames (including user-flagged & circled images),
     * the scene's time range, and the project HTML, and applies the requested fix directly to the HTML!
     */
    suspend fun sendSceneFixMessage(
        context: Context,
        geminiRouter: GeminiWaterfallRouter,
        cacheManager: CacheManager,
        userProfile: UserProfile,
        project: ProjectEntity,
        currentHtml: String,
        scene: SceneSpan,
        sceneSamples: List<KeyframeSample>,
        allSamples: List<KeyframeSample>,
        userInstruction: String
    ): Result<Pair<ChatMessageItem, CodeValidationResult?>> = withContext(Dispatchers.IO) {
        runCatching {
            if (!userProfile.hasGeminiKey) {
                throw IllegalStateException("Please add your Gemini API key in Profile -> AI & Quota first.")
            }

            // Attach up to 3 frame images from this scene (prioritizing FLAGGED & circled frames!)
            // using the annotated (circled) image whenever circles are present!
            val prioritizedFrames = sceneSamples.sortedWith(
                compareByDescending<KeyframeSample> { it.isFlagged }
                    .thenByDescending { it.circles.size }
                    .thenBy { it.timeSec }
            ).take(3)

            val visionImages = mutableListOf<GeminiImageInput>()
            val tempDir = cacheManager.sheetTempDir(project.id)
            for (s in prioritizedFrames) {
                val imgPath = if (s.circles.isNotEmpty()) {
                    KeyframeSheetGenerator.saveAnnotatedThumbnail(s, tempDir)
                } else {
                    s.thumbPath
                }
                val file = File(imgPath)
                if (file.exists()) {
                    runCatching {
                        val (b64, mime) = geminiRouter.encodeFileForVision(file, 960)
                        visionImages.add(GeminiImageInput(base64Data = b64, mimeType = mime))
                    }
                }
            }

            val flaggedDetails = buildString {
                val flaggedInScene = sceneSamples.filter { it.isFlagged }
                val otherFlagged = allSamples.filter { it.isFlagged && it.index !in sceneSamples.map { sc -> sc.index } }
                if (flaggedInScene.isNotEmpty()) {
                    appendLine("FLAGGED FRAMES IN SELECTED SCENE S${scene.index} (${String.format(Locale.US, "%.2fs..%.2fs", scene.start, scene.end)}):")
                    flaggedInScene.forEach { s ->
                        val circleDesc = if (s.circles.isNotEmpty()) {
                            s.circles.joinToString("; ") { c ->
                                val cx = (c.centerXNorm * 1920).toInt()
                                val cy = (c.centerYNorm * 1080).toInt()
                                val r = (c.radiusNorm * 1080).toInt()
                                "Red circled issue area at stage coords ($cx, $cy) radius ${r}px" +
                                    (if (c.note.isNotBlank()) " [${c.note}]" else "")
                            }
                        } else "Flagged by user/DOM"
                        val domW = (s.warnings + s.autoFitNotes).joinToString("; ")
                        appendLine("- Frame #${s.index + 1} (t=${String.format(Locale.US, "%.2fs", s.timeSec)}): $circleDesc ${if (domW.isNotBlank()) "| DOM: $domW" else ""} ${if (s.userNote.isNotBlank()) "| Note: ${s.userNote}" else ""}")
                    }
                }
                if (otherFlagged.isNotEmpty()) {
                    appendLine("OTHER FLAGGED FRAMES IN REEL:")
                    otherFlagged.take(8).forEach { s ->
                        appendLine("- Frame #${s.index + 1} (t=${String.format(Locale.US, "%.2fs", s.timeSec)} ${s.sceneLabel}): ${(s.warnings + s.autoFitNotes).joinToString("; ")}")
                    }
                }
            }

            val systemPrompt = """
                You are the RawEdit Scene Fix Engineer.
                The user has selected Scene S${scene.index} (${String.format(Locale.US, "%.2fs to %.2fs", scene.start, scene.end)}) in the Contact Sheet and wants you to fix or modify the HTML reel code.
                Any red circles drawn on the attached frame images highlight the exact visual defect area the user flagged.
                
                RULES:
                1. First write 1 to 3 concise sentences explaining what you fixed in Scene S${scene.index}.
                2. Then output exact search-and-replace patches using this exact format:
                <<<SEARCH
                exact lines from CURRENT HTML
                ===REPLACE
                updated lines
                >>>
                (Or if the whole HTML needs rewriting, output a single complete <!DOCTYPE html>...</html> block.)
                3. Never introduce external URLs, CSS @keyframes/transitions, requestAnimationFrame, setTimeout, or Math.random(). Keep window.RAW.seek(t) 100% deterministic.
            """.trimIndent()

            val userPrompt = """
                SELECTED SCENE: Scene S${scene.index} (${String.format(Locale.US, "%.2fs - %.2fs", scene.start, scene.end)}, bg=${scene.bg})
                $flaggedDetails
                USER FIX REQUEST:
                $userInstruction

                CURRENT HTML CODE:
                ${currentHtml.take(90_000)}
            """.trimIndent()

            val genRes = geminiRouter.generate(
                apiKey = userProfile.geminiApiKey,
                prompt = userPrompt,
                systemInstruction = systemPrompt,
                routeMode = GeminiRouteMode.CREATIVE_FLASH,
                expectJson = false,
                images = visionImages,
                maxOutputTokens = 8192
            ).getOrThrow()

            val rawReply = genRes.text
            var validatedResult: CodeValidationResult? = null
            val appliedBadges = mutableListOf<String>()

            if (rawReply.contains("<<<SEARCH")) {
                val patches = GroqCodec.parseClaudeSearchReplacePatches(rawReply)
                if (patches.isNotEmpty()) {
                    val outcome = GroqCodec.applyExactOnceEdits(currentHtml, patches)
                    if (outcome.appliedCount > 0) {
                        val valRes = CodeValidator.validateFull(context, cacheManager, outcome.updatedHtml)
                        if (valRes.isValid) {
                            validatedResult = valRes
                            appliedBadges.add("Applied ${outcome.appliedCount} patch(es) to Scene S${scene.index}")
                        }
                    }
                }
            } else if (rawReply.contains("<!DOCTYPE html", ignoreCase = true) || rawReply.contains("<html", ignoreCase = true)) {
                val unwrapped = CodeValidator.unwrapMarkdownFences(rawReply)
                val valRes = CodeValidator.validateFull(context, cacheManager, unwrapped)
                if (valRes.isValid) {
                    validatedResult = valRes
                    appliedBadges.add("Updated HTML for Scene S${scene.index}")
                }
            }

            // Strip raw code blocks from display text so chat bubble stays clean and readable
            val cleanDisplayReply = rawReply
                .replace(Regex("<<<SEARCH[\\s\\S]*?>>>"), "")
                .replace(Regex("```html[\\s\\S]*?```", RegexOption.IGNORE_CASE), "")
                .trim()
                .ifBlank {
                    if (validatedResult != null) "Fixed Scene S${scene.index} and updated the reel HTML!"
                    else "Here is the analysis for Scene S${scene.index}."
                }

            val msg = ChatMessageItem(
                role = "assistant",
                text = cleanDisplayReply,
                appliedActions = appliedBadges
            )
            msg to validatedResult
        }
    }
}
