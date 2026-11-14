package com.rawedit.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rawedit.app.data.CacheManager
import com.rawedit.app.data.local.LocalAssetEntity
import com.rawedit.app.data.local.ProjectEntity
import com.rawedit.app.data.local.UserProfile
import com.rawedit.app.domain.CodeValidator
import com.rawedit.app.domain.GeminiRouteMode
import com.rawedit.app.domain.GeminiWaterfallRouter
import com.rawedit.app.domain.GitHubService
import com.rawedit.app.domain.GroqCodec
import com.rawedit.app.domain.PaletteState
import com.rawedit.app.domain.PromptGenerator
import com.rawedit.app.domain.ReelEditingStyle
import com.rawedit.app.ui.components.CircleActionButton
import com.rawedit.app.ui.components.FormatHelpers
import com.rawedit.app.ui.components.RawSecondaryButton
import com.rawedit.app.ui.components.RawToastBanner
import com.rawedit.app.ui.motion.RawPressButton
import com.rawedit.app.ui.icons.PixelMascotKind
import com.rawedit.app.ui.icons.RawIcon
import com.rawedit.app.ui.icons.RawIconType
import com.rawedit.app.ui.icons.RawPixelMascot
import com.rawedit.app.ui.theme.BricolageFamily
import com.rawedit.app.ui.theme.JetBrainsMonoFamily
import com.rawedit.app.ui.theme.RawColors
import com.rawedit.app.ui.theme.RawTypography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val attachedFileName: String? = null,
    val attachedFileSize: String? = null,
    val codeSnippet: String? = null,
    val timestampMs: Long = System.currentTimeMillis()
)

@Composable
fun ChatbotScreen(
    userProfile: UserProfile,
    project: ProjectEntity?,
    allAssets: List<LocalAssetEntity> = emptyList(),
    activePalette: PaletteState? = null,
    cacheManager: CacheManager,
    geminiRouter: GeminiWaterfallRouter,
    onBack: () -> Unit,
    onOpenApiKey: () -> Unit,
    onApplyHtmlPatch: ((newHtml: String) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val messages = remember {
        mutableStateListOf(
            ChatMessage(
                isUser = false,
                text = "Hey! I'm RawEdit Copilot, your AI creative director and assistant.\n\n" +
                    "I can see your script/SRT notes, suggest next reel concepts, tweak and customize your AI prompt, tailor your style bundle, fetch open-source tools from GitHub, and inspect uploaded files up to 500 MB (stored strictly locally).\n\n" +
                    "How can I help with your reel today?"
            )
        )
    }

    var inputText by remember { mutableStateOf("") }
    var isThinking by remember { mutableStateOf(false) }
    var toastMsg by remember { mutableStateOf<String?>(null) }
    var attachedFile by remember { mutableStateOf<File?>(null) }
    val listState = rememberLazyListState()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val res = cacheManager.importChatbotUpload(uri)
            res.onSuccess { f ->
                attachedFile = f
                toastMsg = "Attached ${f.name} (${FormatHelpers.formatFileSize(f.length())})"
            }.onFailure { err ->
                toastMsg = err.message ?: "Could not attach file."
            }
        }
    }

    val customFonts = remember { cacheManager.listCustomFonts().map { it.name } }

    val systemContextPrompt = remember(project, userProfile, activePalette, allAssets) {
        val projTitle = project?.title?.ifBlank { "Untitled Reel" } ?: "No active project"
        val projDur = project?.audioDurationSec ?: 0.0
        val srtSample = project?.srtText?.take(1500) ?: "None"
        val scriptTopic = project?.topic?.take(1000) ?: "None"
        val paletteInfo = activePalette?.let { p ->
            "Palette #${p.numFormatted} (${p.mood}): " + p.colors.joinToString(", ") { "${it.name} (${it.hex})" }
        } ?: "Default Minimalist Palette"
        val assetsSummary = if (allAssets.isNotEmpty()) {
            allAssets.joinToString(", ") { "${it.relativePath} (${it.kind})" }
        } else {
            "None"
        }
        val ghStatus = if (userProfile.hasGithubToken) "GitHub token configured (Active)" else "No GitHub token saved"

        """
You are RawEdit Copilot, an elite AI creative director, motion designer, and prompt engineer embedded directly inside the RawEdit Android offline editing app.
You have full real-time access to the user's project context:
- Project Title: $projTitle
- Duration: ${String.format(Locale.US, "%.2f", projDur)}s
- Topic / Script Notes: $scriptTopic
- SRT Transcript: $srtSample
- Color Palette: $paletteInfo
- Custom Fonts: ${customFonts.joinToString(", ").ifBlank { "System defaults" }}
- Attached Assets: $assetsSummary
- GitHub Status: $ghStatus
- Style: Kinetic Explainer (`kinetic-explainer-style.zip`)

CAPABILITIES & DIRECTIVES:
1. REEL IDEAS & BRAINSTORMING:
   - When asked for reel ideas, analyze their SRT or script. Propose high-hook headlines, scene-by-scene kinetic visual beats, audio transitions, and typography dynamics.
2. PROMPT & STYLE ZIP CUSTOMIZATION:
   - Help users tweak prompt instructions for Claude, Gemini, or ChatGPT.
   - If they want to change style zip rules, explain which tokens.css variables or engine.js parameters to tune.
3. GITHUB SKILLS & TOOL FETCHING:
   - If the user asks about GitHub tools, search repositories, or mentions skills, tell them you can fetch repositories and code using their GitHub token.
   - If they have a token, explain how tools can be saved to their local app cache.
4. CODE & FIXES:
   - Provide concrete SEARCH/REPLACE patches in `<<<SEARCH ... ===REPLACE ... >>>` format whenever they ask for HTML fixes.
5. TONE:
   - Confident, helpful, creative, precise. No corporate fluff. Keep responses actionable.
        """.trimIndent()
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    fun handleSend(textToSend: String) {
        val trimmed = textToSend.trim()
        val file = attachedFile
        if (trimmed.isEmpty() && file == null) return

        if (!userProfile.hasGeminiKey) {
            toastMsg = "Add your Gemini API key in Profile to chat."
            onOpenApiKey()
            return
        }

        val userMsg = ChatMessage(
            isUser = true,
            text = trimmed.ifBlank { "Attached file: ${file?.name}" },
            attachedFileName = file?.name,
            attachedFileSize = file?.let { FormatHelpers.formatFileSize(it.length()) }
        )
        messages.add(userMsg)
        inputText = ""
        attachedFile = null
        isThinking = true

        scope.launch {
            try {
                // If user asks for GitHub tools and provides a query or repo
                val isGhQuery = trimmed.contains("github", ignoreCase = true) ||
                    trimmed.startsWith("search ", ignoreCase = true) ||
                    trimmed.startsWith("fetch ", ignoreCase = true)

                val promptWithHistory = buildString {
                    appendLine("User message: $trimmed")
                    if (file != null) {
                        appendLine("User attached local file: ${file.name} (Size: ${FormatHelpers.formatFileSize(file.length())}, Path: ${file.absolutePath})")
                    }
                }

                val result = geminiRouter.generate(
                    apiKey = userProfile.geminiApiKey,
                    prompt = promptWithHistory,
                    systemInstruction = systemContextPrompt,
                    routeMode = GeminiRouteMode.CREATIVE_FLASH,
                    expectJson = false
                )

                isThinking = false
                result.onSuccess { genRes ->
                    val reply = genRes.text.trim()
                    messages.add(
                        ChatMessage(
                            isUser = false,
                            text = reply
                        )
                    )
                }.onFailure { err ->
                    messages.add(
                        ChatMessage(
                            isUser = false,
                            text = "Error from Gemini: ${err.message ?: "Failed to generate response. Check your API key."}"
                        )
                    )
                }
            } catch (e: Exception) {
                isThinking = false
                messages.add(
                    ChatMessage(
                        isUser = false,
                        text = "Something went wrong: ${e.message}"
                    )
                )
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RawColors.BgPrimary)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircleActionButton(
                        icon = RawIconType.BACK,
                        size = 40.dp,
                        onClick = onBack
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RawPixelMascot(kind = PixelMascotKind.LENS_BOT, size = 36.dp)
                        Column {
                            Text(
                                text = "RAWEDIT COPILOT",
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp,
                                letterSpacing = 0.8.sp,
                                color = RawColors.Tan
                            )
                            Text(
                                text = "Full App AI \u2022 Ideas & Tools",
                                fontFamily = BricolageFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = RawColors.TextPrimary
                            )
                        }
                    }
                }

                if (userProfile.hasGithubToken) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(RawColors.AccentSoft)
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            RawIcon(RawIconType.CODE, size = 12.dp, tint = RawColors.Tan)
                            Text(
                                text = "GitHub Active",
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.5.sp,
                                color = RawColors.Tan
                            )
                        }
                    }
                }
            }

            // Quick suggestion chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickPromptChip(label = "Suggest reel ideas from my SRT") {
                    handleSend("Based on my current project SRT subtitles and script notes, suggest 3 killer visual reel concepts with hooks and scene breakdowns.")
                }
                QuickPromptChip(label = "Tweak AI prompt for Claude") {
                    handleSend("Help me customize and sharpen the AI generation prompt for my current reel to achieve punchier typography.")
                }
                QuickPromptChip(label = "Customize style zip tokens") {
                    handleSend("How do I customize the CSS tokens and motion physics in kinetic-explainer-style.zip for my reel?")
                }
                QuickPromptChip(label = "Search GitHub for motion tools") {
                    handleSend("What are the best open-source GitHub tools and skills for kinetic typography and reel editing?")
                }
            }

            // Chat Messages List
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    ChatBubbleItem(
                        message = msg,
                        onCopy = { txt ->
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cb.setPrimaryClip(ClipData.newPlainText("rawedit_chat", txt))
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            toastMsg = "Copied to clipboard"
                        },
                        onApplyPatch = onApplyHtmlPatch?.let { apply ->
                            { patchText ->
                                val patches = GroqCodec.parseClaudeSearchReplacePatches(patchText)
                                if (patches.isNotEmpty() && project != null) {
                                    scope.launch {
                                        val currentHtml = withContext(Dispatchers.IO) {
                                            cacheManager.resolveHtmlCode(project.code)
                                        }
                                        val outcome = GroqCodec.applyExactOnceEdits(currentHtml, patches)
                                        if (outcome.appliedCount > 0) {
                                            val validation = CodeValidator.validateFull(context, cacheManager, outcome.updatedHtml)
                                            if (validation.isValid) {
                                                apply(validation.cleanedHtml)
                                                toastMsg = "Applied ${outcome.appliedCount} patch(es) to HTML!"
                                            } else {
                                                toastMsg = "Patch failed validation: ${validation.summaryMessage}"
                                            }
                                        } else {
                                            toastMsg = "No patch targets matched current HTML."
                                        }
                                    }
                                } else {
                                    toastMsg = "No valid patches found in text."
                                }
                            }
                        }
                    )
                }

                if (isThinking) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .background(RawColors.CardSurface)
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = RawColors.Tan,
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = "Thinking with Gemini Free Waterfall...",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 12.5.sp,
                                color = RawColors.TextMuted
                            )
                        }
                    }
                }
            }

            // Attached file chip (if any)
            attachedFile?.let { file ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(RawColors.AccentSoft)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        RawIcon(RawIconType.FILE, size = 16.dp, tint = RawColors.Tan)
                        Text(
                            text = "${file.name} (${FormatHelpers.formatFileSize(file.length())})",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 12.sp,
                            color = RawColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    CircleActionButton(
                        icon = RawIconType.CLOSE,
                        size = 28.dp,
                        backgroundColor = Color.Transparent,
                        iconTint = RawColors.TextMuted,
                        onClick = { attachedFile = null }
                    )
                }
            }

            // Input Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // File Upload Button (Max 500 MB)
                CircleActionButton(
                    icon = RawIconType.FILE,
                    size = 46.dp,
                    backgroundColor = if (attachedFile != null) RawColors.AccentSoft else RawColors.CardSurface,
                    iconTint = if (attachedFile != null) RawColors.Tan else RawColors.TextMuted,
                    onClick = {
                        filePickerLauncher.launch(arrayOf("*/*"))
                    }
                )

                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text(
                            text = if (attachedFile != null) "Add note for file..." else "Ask anything about your reel, prompt, GitHub tools...",
                            style = RawTypography.bodyMedium,
                            color = RawColors.TextMuted,
                            fontSize = 13.5.sp
                        )
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = RawColors.CardSurface,
                        unfocusedContainerColor = RawColors.CardSurface,
                        focusedBorderColor = RawColors.Tan,
                        unfocusedBorderColor = RawColors.Divider,
                        cursorColor = RawColors.Tan,
                        focusedTextColor = RawColors.TextPrimary,
                        unfocusedTextColor = RawColors.TextPrimary
                    ),
                    maxLines = 4,
                    textStyle = RawTypography.bodyMedium.copy(fontSize = 14.sp)
                )

                CircleActionButton(
                    icon = RawIconType.SPARK,
                    size = 46.dp,
                    backgroundColor = if (inputText.isNotBlank() || attachedFile != null) RawColors.Tan else RawColors.RaisedCard,
                    iconTint = if (inputText.isNotBlank() || attachedFile != null) RawColors.Ink else RawColors.TextMuted,
                    onClick = {
                        handleSend(inputText)
                    }
                )
            }
        }

        RawToastBanner(
            message = toastMsg,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 76.dp)
        )
    }
}

@Composable
private fun QuickPromptChip(
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(RawColors.CardSurface)
            .border(1.dp, RawColors.Divider, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            fontFamily = BricolageFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 12.5.sp,
            color = RawColors.TextMuted
        )
    }
}

@Composable
private fun ChatBubbleItem(
    message: ChatMessage,
    onCopy: (String) -> Unit,
    onApplyPatch: ((String) -> Unit)?
) {
    val isUser = message.isUser
    val hasPatches = remember(message.text) {
        !isUser && message.text.contains("<<<SEARCH") && message.text.contains("===REPLACE")
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(if (isUser) 0.85f else 0.95f)
                .clip(
                    RoundedCornerShape(
                        topStart = 20.dp,
                        topEnd = 20.dp,
                        bottomStart = if (isUser) 20.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 20.dp
                    )
                )
                .background(if (isUser) RawColors.Tan else RawColors.CardSurface)
                .border(
                    width = 1.dp,
                    color = if (isUser) Color.Transparent else RawColors.Divider,
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (message.attachedFileName != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isUser) RawColors.Ink.copy(alpha = 0.12f) else RawColors.RaisedCard)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    RawIcon(RawIconType.FILE, size = 14.dp, tint = if (isUser) RawColors.Ink else RawColors.Tan)
                    Text(
                        text = "${message.attachedFileName} (${message.attachedFileSize.orEmpty()})",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.5.sp,
                        color = if (isUser) RawColors.Ink else RawColors.TextPrimary
                    )
                }
            }

            Text(
                text = message.text,
                fontFamily = BricolageFamily,
                fontSize = 14.5.sp,
                lineHeight = 21.sp,
                color = if (isUser) RawColors.Ink else RawColors.TextPrimary
            )

            if (!isUser) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (hasPatches && onApplyPatch != null) {
                        RawPressButton(onClick = { onApplyPatch(message.text) }) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(RawColors.AccentSoft)
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                RawIcon(RawIconType.CHECK, size = 12.dp, tint = RawColors.Tan)
                                Text(
                                    text = "Apply Patch to HTML",
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = RawColors.Tan
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    RawPressButton(onClick = { onCopy(message.text) }) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(RawColors.RaisedCard)
                                .padding(horizontal = 9.dp, vertical = 5.dp)
                        ) {
                            RawIcon(RawIconType.COPY, size = 12.dp, tint = RawColors.TextMuted)
                            Text(
                                text = "Copy",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 11.sp,
                                color = RawColors.TextMuted
                            )
                        }
                    }
                }
            }
        }
    }
}
