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


data class ChatSessionMeta(
    val id: String,
    val title: String,
    val updatedAtMs: Long
)

data class ChatMessageItem(
    val id: String = "msg_${System.currentTimeMillis()}_${(100..999).random()}",
    val role: String, // "user" or "assistant"
    val text: String,
    val attachmentName: String = "",
    val attachmentPath: String = "",
    val attachmentSize: Long = 0L,
    val appliedActions: List<String> = emptyList(),
    val generatedFilePath: String = "",
    val generatedFileName: String = "",
    val websitePreviewPath: String = "",
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
    private val GITHUB_CREATE_REPO_REGEX = Regex("<<<GITHUB_CREATE_REPO\\s+([^>]+?)>>>", RegexOption.IGNORE_CASE)
    private val GITHUB_WRITE_FILE_REGEX = Regex("<<<GITHUB_WRITE_FILE\\s+([^>]+?)>>>\\s*\\n([\\s\\S]*?)<<<END_GITHUB_WRITE_FILE>>>", RegexOption.IGNORE_CASE)
    private val GITHUB_DELETE_FILE_REGEX = Regex("<<<GITHUB_DELETE_FILE\\s+([^>]+?)>>>", RegexOption.IGNORE_CASE)
    private val WEB_SEARCH_REGEX = Regex("<<<WEB_SEARCH\\s+([^>]+?)>>>", RegexOption.IGNORE_CASE)
    private val WEB_FETCH_REGEX = Regex("<<<WEB_FETCH\\s+([^>]+?)>>>", RegexOption.IGNORE_CASE)
    private val WEB_DOWNLOAD_REGEX = Regex("<<<WEB_DOWNLOAD\\s+([^>]+?)>>>", RegexOption.IGNORE_CASE)
    private val CREATE_PDF_REGEX = Regex("<<<CREATE_PDF([^>]*)>>>\\s*([\\s\\S]*?)(?:<<<END_CREATE_PDF>>>|$)", RegexOption.IGNORE_CASE)
    private val CREATE_FILE_REGEX = Regex("<<<CREATE_FILE([^>]*)>>>\\s*([\\s\\S]*?)(?:<<<END_CREATE_FILE>>>|$)", RegexOption.IGNORE_CASE)
    private val WEBSITE_PREVIEW_REGEX = Regex("<<<WEBSITE_PREVIEW([^>]*)>>>\\s*([\\s\\S]*?)(?:<<<END_WEBSITE_PREVIEW>>>|$)", RegexOption.IGNORE_CASE)
    private val HTML_FENCE_REGEX = Regex("```(?:html|htm)?\\s*\\n([\\s\\S]*?(?:<!DOCTYPE html|<html)[\\s\\S]*?</html>)\\s*```", RegexOption.IGNORE_CASE)
    private val ANY_FENCE_REGEX = Regex("```([a-zA-Z0-9_.-]*)\\s*\\n([\\s\\S]*?)```", RegexOption.IGNORE_CASE)



    private fun extractAttr(attrBlob: String, key: String): String {
        val dq = Regex("$key\\s*=\\s*\"([^\"]*)\"", RegexOption.IGNORE_CASE).find(attrBlob)
        if (dq != null) return dq.groupValues[1].trim()
        val sq = Regex("$key\\s*=\\s*'([^']*)'", RegexOption.IGNORE_CASE).find(attrBlob)
        if (sq != null) return sq.groupValues[1].trim()
        return ""
    }

    fun listChatSessions(cacheManager: CacheManager): List<ChatSessionMeta> {
        val indexFile = File(cacheManager.chatHistoryDir, "chat_sessions_index.json")
        if (!indexFile.exists()) {
            // Migrate legacy global chat if present
            val legacy = loadChatHistory(cacheManager, "global")
            return if (legacy.isNotEmpty()) {
                val title = legacy.firstOrNull { it.role == "user" }?.text?.take(36)?.ifBlank { "Studio Chat" } ?: "Studio Chat"
                listOf(ChatSessionMeta("global", title, legacy.lastOrNull()?.timestampMs ?: System.currentTimeMillis()))
            } else emptyList()
        }
        return runCatching {
            val arr = JSONArray(indexFile.readText(Charsets.UTF_8))
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                ChatSessionMeta(
                    id = o.optString("id", "global"),
                    title = o.optString("title", "New Chat"),
                    updatedAtMs = o.optLong("updatedAtMs", 0L)
                )
            }.sortedByDescending { it.updatedAtMs }
        }.getOrDefault(emptyList())
    }

    fun upsertChatSessionMeta(cacheManager: CacheManager, sessionId: String, messages: List<ChatMessageItem>) {
        runCatching {
            val currentList = listChatSessions(cacheManager)
            val prevTitle = currentList.firstOrNull { it.id == sessionId }?.title?.takeIf { it.isNotBlank() && it != "New Chat" }
            val existing = currentList.filterNot { it.id == sessionId }.toMutableList()
            val firstUser = messages.firstOrNull { it.role == "user" }?.text?.trim().orEmpty()
            val title = prevTitle ?: firstUser.take(40).ifBlank { "New Chat" }
            val updatedAt = messages.lastOrNull()?.timestampMs ?: System.currentTimeMillis()
            existing.add(0, ChatSessionMeta(sessionId, title, updatedAt))
            val arr = JSONArray()
            existing.take(30).forEach { s ->
                arr.put(JSONObject().apply {
                    put("id", s.id)
                    put("title", s.title)
                    put("updatedAtMs", s.updatedAtMs)
                })
            }
            File(cacheManager.chatHistoryDir, "chat_sessions_index.json").writeText(arr.toString(2), Charsets.UTF_8)
        }
    }


    fun renameChatSession(cacheManager: CacheManager, sessionId: String, newTitle: String) {
        val clean = newTitle.trim().take(60)
        if (clean.isEmpty()) return
        runCatching {
            val all = listChatSessions(cacheManager).toMutableList()
            val idx = all.indexOfFirst { it.id == sessionId }
            if (idx != -1) {
                all[idx] = all[idx].copy(title = clean)
            } else {
                all.add(0, ChatSessionMeta(sessionId, clean, System.currentTimeMillis()))
            }
            val arr = JSONArray()
            all.take(30).forEach { s ->
                arr.put(JSONObject().apply {
                    put("id", s.id)
                    put("title", s.title)
                    put("updatedAtMs", s.updatedAtMs)
                })
            }
            File(cacheManager.chatHistoryDir, "chat_sessions_index.json").writeText(arr.toString(2), Charsets.UTF_8)
        }
    }

    fun deleteChatSession(cacheManager: CacheManager, sessionId: String) {
        runCatching {
            File(cacheManager.chatHistoryDir, "chat_${sessionId}.json").delete()
            val remaining = listChatSessions(cacheManager).filterNot { it.id == sessionId }
            val arr = JSONArray()
            remaining.forEach { s ->
                arr.put(JSONObject().apply {
                    put("id", s.id)
                    put("title", s.title)
                    put("updatedAtMs", s.updatedAtMs)
                })
            }
            File(cacheManager.chatHistoryDir, "chat_sessions_index.json").writeText(arr.toString(2), Charsets.UTF_8)
        }
    }

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
                        generatedFilePath = o.optString("generatedFilePath", ""),
                        generatedFileName = o.optString("generatedFileName", ""),
                        websitePreviewPath = o.optString("websitePreviewPath", ""),
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
                        put("generatedFilePath", m.generatedFilePath)
                        put("generatedFileName", m.generatedFileName)
                        put("websitePreviewPath", m.websitePreviewPath)
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
        uploadedFile: ChatUploadedFile? = null,
        browserPageContext: String? = null
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
                    val inspected = ChatbotFileTools.inspectLocalFile(File(uploadedFile.localPath))
                    if (inspected.isNotBlank()) {
                        appendLine("FILE CONTENT / ARCHIVE INSPECTION:")
                        appendLine(inspected.take(12_000))
                    } else if (uploadedFile.textPreview.isNotBlank()) {
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
                - GitHub integration & Cloud APK Builder (${if (userProfile.hasGithubToken) "GitHub token active — can push ZIPs to GitHub Actions, auto-fix errors, and return compiled .apk files" else "User can paste a GitHub token in Settings to enable cloud APK builds"}).

                HOW TO APPLY LIVE CHANGES WHEN THE USER ASKS TO TWEAK THE PROMPT, STYLE ZIP, GITHUB SKILLS, OR HTML CODE:
                1. To tweak/customize the Prompt that gets copied for AI tools, include a block in your reply:
                <<<PROMPT_UPDATE
                [Write the exact custom rules/tweaks to inject into the prompt here]
                >>>
                2. To customize or add a file inside `kinetic-explainer-style.zip` (such as `SKILL.md`, `docs/STYLE_BIBLE.md`, `style/tokens.css`, `engine/raw-helpers.js`, or a custom skill), include:
                <<<STYLE_ZIP_FILE path="kinetic-explainer-style/SKILL.md">>>
                [Full updated file content]
                <<<END_STYLE_ZIP_FILE>>>
                3. GitHub tools (when token is active):
                - Fetch repo/skill: <<<GITHUB_FETCH query="owner/repo or search term">>>
                - Create repo: <<<GITHUB_CREATE_REPO name="repo-name" description="..." private="false">>>
                - Create or edit file: <<<GITHUB_WRITE_FILE repo="owner/repo" path="path/to/file" message="commit msg">>>\n[file content]\n<<<END_GITHUB_WRITE_FILE>>>
                - Delete file: <<<GITHUB_DELETE_FILE repo="owner/repo" path="path/to/file" message="commit msg">>>
                - Build Android APK from uploaded .zip: Handled automatically via GitHub Actions (`GitHubApkBuilder`). NEVER tell the user you cannot compile APKs.
                4. File, PDF & Website Creation Tools (MANDATORY WHEN USER ASKS FOR A FILE):
                Whenever the user asks to create, export, download, or give any `.html`, `.txt`, `.pdf`, `.md`, `.skill`, `.json`, `.css`, `.js`, or code/document file, you MUST emit the corresponding tool block below (do NOT just paste raw file text in chat):
                - Create PDF (`.pdf`): <<<CREATE_PDF title="Title" filename="doc.pdf">>>\n[Clean Markdown/text content]\n<<<END_CREATE_PDF>>>
                - Create any text/code/markdown/skill file (`.txt`, `.md`, `.skill`, `.html`, `.json`, `.css`, `.js`): <<<CREATE_FILE filename="output.ext">>>\n[Complete file content]\n<<<END_CREATE_FILE>>>
                - Build HTML/CSS/JS website with in-app live preview (`.html`): <<<WEBSITE_PREVIEW title="My Site">>>\n[Complete self-contained HTML with <style> and <script>]\n<<<END_WEBSITE_PREVIEW>>>
                Keep your chat message outside the tool block to a short 1-3 line summary.
                5. Live Web & Dependency Tools:
                - Search web: <<<WEB_SEARCH query="search terms">>>
                - Read webpage: <<<WEB_FETCH url="https://...">>>
                - Download JS/CSS/font/asset dependency: <<<WEB_DOWNLOAD url="https://..." filename="lib.js">>>
                6. To patch the active project's HTML reel code, include standard search/replace blocks:
                <<<SEARCH
                exact lines in current HTML
                ===REPLACE
                updated lines
                >>>
                FORMATTING & TYPOGRAPHY STYLE: Structure every reply cleanly with `## Section Headings`, `### Subheadings`, numbered steps (`1. `), crisp bullets (`- `), `==highlighted key phrases==`, `> Pro Tip` callout blocks, and `inline code` for filenames/values. Keep paragraphs short and scannable; use **bold** only for key labels.
            """.trimIndent()

            val rawdevSkillText = runCatching {
                context.assets.open("skills/rawdev-reel-scripts/SKILL.md").bufferedReader(Charsets.UTF_8).use { it.readText() }
            }.getOrDefault("")

            val lowerMsg = userText.lowercase(Locale.US)
            // Direct Cloud APK Build via GitHub Actions when user uploads a .zip and asks to compile/build APK
            val wantsApkBuild = uploadedFile != null &&
                uploadedFile.fileName.endsWith(".zip", ignoreCase = true) &&
                listOf("apk", "compile", "build").any { lowerMsg.contains(it) }
            if (wantsApkBuild && uploadedFile != null) {
                val apkOutcome = GitHubApkBuilder.buildApkFromZip(
                    zipFile = File(uploadedFile.localPath),
                    githubToken = userProfile.githubAccessToken,
                    geminiRouter = geminiRouter,
                    geminiApiKey = userProfile.geminiApiKey,
                    cacheManager = cacheManager
                ).getOrThrow()
                appliedBadges.add("Compiled APK via GitHub Actions")
                val msg = ChatMessageItem(
                    role = "assistant",
                    text = "## APK Build Complete\n\n${apkOutcome.notes}\n- **Repo:** `${apkOutcome.repoFullName}`\n- **Workflow:** ${apkOutcome.runUrl}\n\nTap **Save** below to save `${apkOutcome.apkFile.name}` to your Downloads.",
                    appliedActions = appliedBadges,
                    generatedFilePath = apkOutcome.apkFile.absolutePath,
                    generatedFileName = apkOutcome.apkFile.name
                )
                return@runCatching ChatbotTurnOutcome(assistantMessage = msg)
            }

            val isScriptRequest = listOf("script", "hook", "reel", "tts", "hinglish", "caption", "viral").any { lowerMsg.contains(it) }
            val wantsLiveWeb = listOf("latest", "current", "today", "2026", "trending", "new ", "search", "web", "fetch", "news", "best ", "write a script", "reel script").any { lowerMsg.contains(it) }

            var liveWebContext = ""
            if (wantsLiveWeb && browserPageContext.isNullOrBlank()) {
                val searchRes = WebAgentTools.searchWeb(userText.take(120), maxResults = 4).getOrNull()
                if (!searchRes.isNullOrEmpty()) {
                    appliedBadges.add("Fetched live web data")
                    liveWebContext = buildString {
                        appendLine("=== LIVE WEB SEARCH RESULTS ===")
                        searchRes.forEach { hit ->
                            appendLine("- ${hit.title} (${hit.url}): ${hit.snippet}")
                        }
                    }
                }
            }
            val requestedExt = when {
                Regex("\\.(html|htm)\\b|html file|website|landing page", RegexOption.IGNORE_CASE).containsMatchIn(lowerMsg) -> "html"
                Regex("\\.pdf\\b|as pdf|pdf file|make pdf|create pdf", RegexOption.IGNORE_CASE).containsMatchIn(lowerMsg) -> "pdf"
                Regex("\\.skill\\b|skill file", RegexOption.IGNORE_CASE).containsMatchIn(lowerMsg) -> "skill"
                Regex("\\.md\\b|markdown file", RegexOption.IGNORE_CASE).containsMatchIn(lowerMsg) -> "md"
                Regex("\\.txt\\b|text file", RegexOption.IGNORE_CASE).containsMatchIn(lowerMsg) -> "txt"
                Regex("\\.json\\b", RegexOption.IGNORE_CASE).containsMatchIn(lowerMsg) -> "json"
                Regex("\\.css\\b", RegexOption.IGNORE_CASE).containsMatchIn(lowerMsg) -> "css"
                Regex("\\.js\\b", RegexOption.IGNORE_CASE).containsMatchIn(lowerMsg) -> "js"
                else -> ""
            }
            val isHeavyTask = uploadedFile != null || !browserPageContext.isNullOrBlank() || isScriptRequest || wantsLiveWeb ||
                requestedExt.isNotEmpty() || userText.length > 140 ||
                listOf("srt", "script", "idea", "reel", "prompt", "zip", "style", "skill", "html", "code", "fix", "patch", "github", "font", "pdf", "website", "preview", "css", "js", "file", "md", "txt", "create", "make", "export", "download", "doc").any { lowerMsg.contains(it) }

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
                    if (isScriptRequest && rawdevSkillText.isNotBlank()) {
                        appendLine("=== TRAINED SCRIPT SKILL (rawdev-reel-scripts/SKILL.md — FOLLOW STRICTLY) ===")
                        appendLine(rawdevSkillText.take(4000))
                    }
                    if (!browserPageContext.isNullOrBlank()) {
                        appendLine("=== IN-APP BROWSER ACTIVE PAGE ===")
                        appendLine(browserPageContext.take(10_000))
                    }
                    if (liveWebContext.isNotBlank()) {
                        appendLine(liveWebContext)
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
                systemInstruction = if (isHeavyTask) systemInstruction else "You are the friendly, fast RAW EDIT Studio AI Assistant. Format answers cleanly with ## headings, ### subheadings, numbered lists, bullet points (- ), ==highlighted key points==, > callout tips, and `code` highlights. Do not overuse **asterisks**.",
                routeMode = GeminiRouteMode.FAST_CHAT,
                expectJson = false,
                images = visionInputs,
                maxOutputTokens = if (isHeavyTask) 16384 else 2048
            ).getOrThrow()

            var replyText = genRes.text
            var updatedPromptNotes: String? = null
            var updatedProject: ProjectEntity? = null


            var genFilePath = ""
            var genFileName = ""
            var webPreviewPath = ""

            CREATE_PDF_REGEX.find(replyText)?.let { m ->
                val attrs = m.groupValues[1]
                val pdfTitle = extractAttr(attrs, "title").ifBlank { "RAW EDIT Document" }
                val rawFn = extractAttr(attrs, "filename").ifBlank { "raw_edit_doc.pdf" }
                val safeFn = (if (rawFn.endsWith(".pdf", true)) rawFn else "$rawFn.pdf").replace(Regex("[^A-Za-z0-9._-]"), "_")
                val body = m.groupValues[2].trim()
                runCatching {
                    val out = File(cacheManager.sharedPacksDir, safeFn)
                    ChatbotFileTools.createStyledPdf(out, pdfTitle, body)
                    genFilePath = out.absolutePath
                    genFileName = out.name
                    appliedBadges.add("Created PDF: ${out.name}")
                }.onFailure { err ->
                    replyText += "\n\n*(PDF error: ${err.message})*"
                }
                replyText = replyText.replace(m.value, "").trim()
            }

            CREATE_FILE_REGEX.find(replyText)?.let { m ->
                val attrs = m.groupValues[1]
                val rawFn = extractAttr(attrs, "filename").replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "output.txt" }
                val content = m.groupValues[2]
                runCatching {
                    val out = File(cacheManager.sharedPacksDir, rawFn)
                    out.parentFile?.mkdirs()
                    out.writeText(content, Charsets.UTF_8)
                    genFilePath = out.absolutePath
                    genFileName = out.name
                    if (rawFn.endsWith(".html", true) || rawFn.endsWith(".htm", true)) {
                        webPreviewPath = out.absolutePath
                    }
                    appliedBadges.add("Created file: ${out.name}")
                }
                replyText = replyText.replace(m.value, "").trim()
            }

            WEBSITE_PREVIEW_REGEX.find(replyText)?.let { m ->
                val attrs = m.groupValues[1]
                val siteTitle = extractAttr(attrs, "title").ifBlank { "Website" }
                val html = m.groupValues[2].trim()
                runCatching {
                    val out = ChatbotFileTools.saveWebsiteHtml(cacheManager, siteTitle, html)
                    webPreviewPath = out.absolutePath
                    if (genFilePath.isBlank()) {
                        genFilePath = out.absolutePath
                        genFileName = out.name
                    }
                    appliedBadges.add("Website ready to preview: $siteTitle")
                }
                replyText = replyText.replace(m.value, "").trim()
            }

            // Fallback 1: if AI returned fenced HTML without <<<WEBSITE_PREVIEW>>>, save & enable live preview
            if (webPreviewPath.isBlank()) {
                HTML_FENCE_REGEX.find(replyText)?.let { m ->
                    val html = m.groupValues[1].trim()
                    runCatching {
                        val out = ChatbotFileTools.saveWebsiteHtml(cacheManager, "site_${System.currentTimeMillis()}", html)
                        webPreviewPath = out.absolutePath
                        if (genFilePath.isBlank()) {
                            genFilePath = out.absolutePath
                            genFileName = out.name
                        }
                        appliedBadges.add("Website ready to preview")
                    }
                }
            }

            // Fallback 2: if user explicitly requested .pdf / .html / .txt / .md / .skill / .json / .css / .js and AI did not emit a tool block
            if (genFilePath.isBlank() && requestedExt.isNotEmpty() && replyText.isNotBlank()) {
                val ts = System.currentTimeMillis()
                when (requestedExt) {
                    "pdf" -> {
                        val out = File(cacheManager.sharedPacksDir, "rawedit_${ts}.pdf")
                        runCatching {
                            ChatbotFileTools.createStyledPdf(out, "RAW EDIT Document", replyText)
                            genFilePath = out.absolutePath
                            genFileName = out.name
                            appliedBadges.add("Created PDF: ${out.name}")
                        }
                    }
                    "html" -> {
                        val fence = ANY_FENCE_REGEX.find(replyText)?.groupValues?.getOrNull(2)?.trim()
                        val htmlBody = when {
                            !fence.isNullOrBlank() && (fence.contains("<html", true) || fence.contains("<!doctype", true) || fence.contains("<body", true)) -> fence
                            replyText.contains("<!doctype", true) || replyText.contains("<html", true) -> replyText
                            else -> "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><style>body{font-family:system-ui,sans-serif;padding:24px;line-height:1.6;max-width:800px;margin:auto;background:#111;color:#f5f2eb;}</style></head><body><pre style=\"white-space:pre-wrap\">${replyText.replace("<", "&lt;").replace(">", "&gt;")}</pre></body></html>"
                        }
                        runCatching {
                            val out = ChatbotFileTools.saveWebsiteHtml(cacheManager, "rawedit_${ts}", htmlBody)
                            genFilePath = out.absolutePath
                            genFileName = out.name
                            webPreviewPath = out.absolutePath
                            appliedBadges.add("Created HTML: ${out.name}")
                        }
                    }
                    else -> {
                        val fence = ANY_FENCE_REGEX.find(replyText)?.groupValues?.getOrNull(2)?.trim()
                        val fileBody = if (!fence.isNullOrBlank() && requestedExt in setOf("json", "css", "js", "skill", "md", "txt")) fence else replyText
                        val out = File(cacheManager.sharedPacksDir, "rawedit_${ts}.${requestedExt}")
                        runCatching {
                            out.parentFile?.mkdirs()
                            out.writeText(fileBody, Charsets.UTF_8)
                            genFilePath = out.absolutePath
                            genFileName = out.name
                            appliedBadges.add("Created file: ${out.name}")
                        }
                    }
                }
            }

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

            // 3b. Hardened GitHub Create Repo / Write File / Delete File
            GITHUB_CREATE_REPO_REGEX.findAll(replyText).toList().forEach { m ->
                val attrs = m.groupValues[1]
                val repoName = extractAttr(attrs, "name")
                val desc = extractAttr(attrs, "description")
                val priv = extractAttr(attrs, "private").equals("true", ignoreCase = true)
                if (repoName.isNotEmpty()) {
                    GitHubToolFetcher.createRepo(userProfile.githubAccessToken, repoName, desc, priv)
                        .onSuccess { full ->
                            appliedBadges.add("Created GitHub repo: $full")
                            replyText += "\n\nCreated repo: **$full**"
                        }.onFailure { err ->
                            replyText += "\n\n*(GitHub create note: ${err.message})*"
                        }
                }
                replyText = replyText.replace(m.value, "").trim()
            }
            GITHUB_WRITE_FILE_REGEX.findAll(replyText).toList().forEach { m ->
                val attrs = m.groupValues[1]
                val repoSlug = extractAttr(attrs, "repo")
                val filePath = extractAttr(attrs, "path")
                val msg = extractAttr(attrs, "message")
                val content = m.groupValues[2]
                val parts = repoSlug.split("/", limit = 2)
                if (parts.size == 2 && filePath.isNotBlank()) {
                    GitHubToolFetcher.createOrUpdateRepoFile(userProfile.githubAccessToken, parts[0], parts[1], filePath, content, msg)
                        .onSuccess { target ->
                            appliedBadges.add("Committed to GitHub: $target")
                            replyText += "\n\nCommitted: `$target`"
                        }.onFailure { err ->
                            replyText += "\n\n*(GitHub write note: ${err.message})*"
                        }
                }
                replyText = replyText.replace(m.value, "").trim()
            }
            GITHUB_DELETE_FILE_REGEX.findAll(replyText).toList().forEach { m ->
                val attrs = m.groupValues[1]
                val repoSlug = extractAttr(attrs, "repo")
                val filePath = extractAttr(attrs, "path")
                val msg = extractAttr(attrs, "message")
                val parts = repoSlug.split("/", limit = 2)
                if (parts.size == 2 && filePath.isNotBlank()) {
                    GitHubToolFetcher.deleteRepoFile(userProfile.githubAccessToken, parts[0], parts[1], filePath, msg)
                        .onSuccess { resMsg ->
                            appliedBadges.add(resMsg)
                            replyText += "\n\n$resMsg"
                        }.onFailure { err ->
                            replyText += "\n\n*(GitHub delete note: ${err.message})*"
                        }
                }
                replyText = replyText.replace(m.value, "").trim()
            }

            // 3c. Web Search / Fetch / Download Dependencies
            WEB_DOWNLOAD_REGEX.findAll(replyText).toList().forEach { m ->
                val attrs = m.groupValues[1]
                val url = extractAttr(attrs, "url")
                val fn = extractAttr(attrs, "filename").ifBlank { url.substringAfterLast('/').substringBefore('?').ifBlank { "dep.js" } }
                if (url.isNotBlank()) {
                    WebAgentTools.downloadWebDependency(url, fn, cacheManager)
                        .onSuccess { f ->
                            appliedBadges.add("Downloaded: ${f.name}")
                            genFilePath = f.absolutePath
                            genFileName = f.name
                        }.onFailure { err ->
                            replyText += "\n\n*(Download error: ${err.message})*"
                        }
                }
                replyText = replyText.replace(m.value, "").trim()
            }
            WEB_SEARCH_REGEX.findAll(replyText).toList().forEach { m ->
                val q = extractAttr(m.groupValues[1], "query")
                if (q.isNotBlank()) {
                    WebAgentTools.searchWeb(q, maxResults = 4).onSuccess { hits ->
                        appliedBadges.add("Searched web: $q")
                        if (hits.isNotEmpty()) {
                            replyText += "\n\n### Web Sources\n" + hits.joinToString("\n") { "- **${it.title}** (`${it.url}`): ${it.snippet}" }
                        }
                    }
                }
                replyText = replyText.replace(m.value, "").trim()
            }
            WEB_FETCH_REGEX.findAll(replyText).toList().forEach { m ->
                val u = extractAttr(m.groupValues[1], "url")
                if (u.isNotBlank()) {
                    WebAgentTools.fetchWebPageText(u, maxChars = 2500).onSuccess { txt ->
                        appliedBadges.add("Read page: $u")
                        if (txt.isNotBlank()) {
                            replyText += "\n\n> Fetched from `$u`: ${txt.take(400)}..."
                        }
                    }
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
                appliedActions = appliedBadges,
                generatedFilePath = genFilePath,
                generatedFileName = genFileName,
                websitePreviewPath = webPreviewPath
            )

            ChatbotTurnOutcome(
                assistantMessage = assistantMsg,
                updatedProject = updatedProject,
                updatedPromptNotes = updatedPromptNotes
            )
        }
    }
}
