package com.rawedit.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rawedit.app.data.CacheManager
import com.rawedit.app.data.ChatUploadedFile
import com.rawedit.app.data.local.LocalAssetEntity
import com.rawedit.app.data.local.ProjectEntity
import com.rawedit.app.data.local.UserPreferences
import com.rawedit.app.data.local.UserProfile
import com.rawedit.app.data.repository.RawEditRepository
import com.rawedit.app.domain.ChatMessageItem
import com.rawedit.app.domain.ChatbotEngine
import com.rawedit.app.domain.GeminiWaterfallRouter
import com.rawedit.app.domain.PaletteState
import com.rawedit.app.ui.components.BigPrimaryButton
import com.rawedit.app.ui.components.CircleActionButton
import com.rawedit.app.ui.components.FormatHelpers
import com.rawedit.app.ui.components.RawSecondaryButton
import com.rawedit.app.ui.components.RawToastBanner
import com.rawedit.app.ui.icons.PixelMascotKind
import com.rawedit.app.ui.icons.RawIcon
import com.rawedit.app.ui.icons.RawIconType
import com.rawedit.app.ui.icons.RawPixelMascot
import com.rawedit.app.ui.motion.KineticHeading
import com.rawedit.app.ui.motion.RawPressButton
import com.rawedit.app.ui.theme.BricolageFamily
import com.rawedit.app.ui.theme.JetBrainsMonoFamily
import com.rawedit.app.ui.theme.RawColors
import com.rawedit.app.ui.theme.RawTypography
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val QUICK_CHAT_PROMPTS = listOf(
    "Suggest 5 viral next reel ideas from my SRT & scripts",
    "Tweak my Cloud AI prompt for punchier kinetic animations",
    "Customize SKILL.md inside kinetic-explainer-style.zip",
    "Fetch a motion graphics skill from GitHub",
    "Audit & improve my active project's script and pacing"
)

@Composable
fun RawChatbotScreen(
    userProfile: UserProfile,
    allProjects: List<ProjectEntity>,
    initialProjectId: Long? = null,
    allAssets: List<LocalAssetEntity>,
    activePalette: PaletteState,
    cacheManager: CacheManager,
    userPreferences: UserPreferences,
    repository: RawEditRepository,
    geminiRouter: GeminiWaterfallRouter,
    onOpenApiKeySettings: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var selectedProjectId by remember(initialProjectId, allProjects) {
        mutableStateOf(initialProjectId ?: allProjects.firstOrNull()?.id)
    }
    val activeProject = remember(selectedProjectId, allProjects) {
        allProjects.firstOrNull { it.id == selectedProjectId } ?: allProjects.firstOrNull()
    }

    var messages by remember {
        mutableStateOf(
            ChatbotEngine.loadChatHistory(cacheManager, "global").ifEmpty {
                listOf(
                    ChatMessageItem(
                        role = "assistant",
                        text = "Hey! I'm your **RAW EDIT Studio AI**. I have full access to your projects, pasted SRTs & scripts, your `kinetic-explainer-style.zip` Style Pack, custom fonts, and GitHub skills.\n\n" +
                            "• Ask me to **suggest ideas for your next reel** based on your scripts/SRTs\n" +
                            "• Ask me to **tweak the Cloud AI prompt** or **customize files inside the Style Pack ZIP**\n" +
                            "• Upload any local file up to **500 MB** (stored 100% locally on your phone)\n" +
                            "• Paste a GitHub repo or query to **fetch skills & tools from GitHub**"
                    )
                )
            }
        )
    }

    var inputText by remember { mutableStateOf("") }
    var pendingAttachment by remember { mutableStateOf<ChatUploadedFile?>(null) }
    var uploadingFile by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var toastMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(toastMsg) {
        if (toastMsg != null) {
            delay(2200)
            toastMsg = null
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            runCatching { listState.animateScrollToItem(messages.lastIndex) }
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            uploadingFile = true
            scope.launch {
                val res = cacheManager.importChatAttachment(uri)
                uploadingFile = false
                res.onSuccess { uploaded ->
                    pendingAttachment = uploaded
                    toastMsg = "Attached ${uploaded.fileName} (${FormatHelpers.formatFileSize(uploaded.sizeBytes)})"
                }.onFailure { err ->
                    toastMsg = err.message ?: "Could not attach file (max 500 MB)"
                }
            }
        }
    }

    fun sendMessage(customPrompt: String? = null) {
        val textToSend = (customPrompt ?: inputText).trim()
        val attached = pendingAttachment
        if (textToSend.isEmpty() && attached == null) return
        if (sending) return

        if (!userProfile.hasGeminiKey) {
            onOpenApiKeySettings()
            return
        }

        val effectiveUserText = if (textToSend.isEmpty() && attached != null) {
            "Analyze this uploaded file (${attached.fileName}) and help me use it in my reel workflow."
        } else {
            textToSend
        }

        val userMsg = ChatMessageItem(
            role = "user",
            text = effectiveUserText,
            attachmentName = attached?.fileName.orEmpty(),
            attachmentPath = attached?.localPath.orEmpty(),
            attachmentSize = attached?.sizeBytes ?: 0L
        )
        val updatedWithUser = messages + userMsg
        messages = updatedWithUser
        ChatbotEngine.saveChatHistory(cacheManager, updatedWithUser, "global")

        if (customPrompt == null) inputText = ""
        pendingAttachment = null
        sending = true

        scope.launch {
            val res = ChatbotEngine.sendAppChatMessage(
                context = context,
                geminiRouter = geminiRouter,
                cacheManager = cacheManager,
                userPreferences = userPreferences,
                repository = repository,
                userProfile = userProfile,
                allProjects = allProjects,
                activeProject = activeProject,
                allAssets = allAssets,
                activePalette = activePalette,
                history = updatedWithUser,
                userText = effectiveUserText,
                uploadedFile = attached
            )
            sending = false
            res.onSuccess { outcome ->
                val nextList = messages + outcome.assistantMessage
                messages = nextList
                ChatbotEngine.saveChatHistory(cacheManager, nextList, "global")
                if (outcome.assistantMessage.appliedActions.isNotEmpty()) {
                    toastMsg = outcome.assistantMessage.appliedActions.first()
                }
            }.onFailure { err ->
                val errMsg = ChatMessageItem(
                    role = "assistant",
                    text = "Error: ${err.message ?: "Could not reach Gemini AI. Check your API key in Settings."}"
                )
                val nextList = messages + errMsg
                messages = nextList
                ChatbotEngine.saveChatHistory(cacheManager, nextList, "global")
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RawColors.BgPrimary)
            .statusBarsPadding()
            .imePadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp)
                    .padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircleActionButton(icon = RawIconType.BACK, onClick = onBack, size = 46.dp)
                    Column {
                        Text(
                            text = "RAW EDIT AI CHATBOT",
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            color = RawColors.Tan
                        )
                        Text(
                            text = if (activeProject != null) "Context: ${activeProject.title}" else "Full App + Style ZIP + GitHub",
                            fontFamily = BricolageFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.5.sp,
                            color = RawColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircleActionButton(
                        icon = RawIconType.TRASH,
                        size = 40.dp,
                        iconTint = RawColors.ErrorCoral,
                        onClick = {
                            ChatbotEngine.clearChatHistory(cacheManager, "global")
                            messages = listOf(
                                ChatMessageItem(
                                    role = "assistant",
                                    text = "Chat cleared! Ask me for next reel ideas from your SRT/scripts, tweak your prompt or Style Pack ZIP, upload a file (up to 500 MB), or fetch tools from GitHub."
                                )
                            )
                            toastMsg = "Chat history cleared"
                        }
                    )
                    RawPixelMascot(kind = PixelMascotKind.CODE_BOT, size = 38.dp)
                }
            }

            // Active Project & Status Bar
            if (allProjects.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PROJECT:",
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.5.sp,
                        color = RawColors.TextMuted
                    )
                    allProjects.take(10).forEach { proj ->
                        val isSel = activeProject?.id == proj.id
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (isSel) RawColors.Tan else RawColors.RaisedCard)
                                .clickable { selectedProjectId = proj.id }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = proj.title.ifBlank { "Draft #${proj.id}" },
                                fontFamily = BricolageFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = if (isSel) RawColors.Ink else RawColors.TextPrimary
                            )
                        }
                    }
                    if (cacheManager.hasStylePackOverrides() || userProfile.customPromptNotes.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(RawColors.ErrorSoft)
                                .clickable {
                                    cacheManager.resetStylePackOverrides()
                                    scope.launch { userPreferences.saveCustomPromptNotes("") }
                                    toastMsg = "Reset Style ZIP & Prompt to defaults"
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Reset ZIP/Prompt Tweaks",
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = RawColors.ErrorCoral
                            )
                        }
                    }
                }
            }

            // Quick Action Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QUICK_CHAT_PROMPTS.forEach { quick ->
                    RawPressButton(
                        onClick = { sendMessage(quick) },
                        enabled = !sending
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(RawColors.CardSurface)
                                .border(1.dp, RawColors.Divider, CircleShape)
                                .padding(horizontal = 13.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = quick,
                                fontFamily = BricolageFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.5.sp,
                                color = RawColors.Tan
                            )
                        }
                    }
                }
            }

            // Messages List
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    ChatBubbleRow(msg = msg)
                }
                if (sending) {
                    item {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(RawColors.CardSurface)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                color = RawColors.Tan,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Thinking & checking app context...",
                                fontFamily = BricolageFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = RawColors.TextMuted
                            )
                        }
                    }
                }
            }

            // Pending File Attachment Banner
            pendingAttachment?.let { att ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(RawColors.AccentSoft)
                        .border(1.dp, RawColors.AccentBorder, RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        RawIcon(RawIconType.FILE, size = 16.dp, tint = RawColors.Tan)
                        Column {
                            Text(
                                text = att.fileName,
                                fontFamily = BricolageFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                color = RawColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${FormatHelpers.formatFileSize(att.sizeBytes)} • Saved locally on phone (500 MB max)",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 10.5.sp,
                                color = RawColors.Tan
                            )
                        }
                    }
                    CircleActionButton(
                        icon = RawIconType.CLOSE,
                        size = 30.dp,
                        onClick = { pendingAttachment = null }
                    )
                }
            }

            // Input Dock with Upload File (up to 500 MB) + Text Input + Send
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(RawColors.CardSurface)
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Upload File Button (250MB - 500MB limit)
                    CircleActionButton(
                        icon = if (uploadingFile) RawIconType.CLOCK else RawIconType.PLUS,
                        size = 46.dp,
                        backgroundColor = RawColors.RaisedCard,
                        iconTint = RawColors.Tan,
                        enabled = !uploadingFile && !sending,
                        onClick = { filePicker.launch(arrayOf("*/*")) }
                    )

                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = {
                            Text(
                                text = "Ask for reel ideas, tweak prompt/ZIP, or fetch GitHub tools...",
                                color = RawColors.TextMuted,
                                fontSize = 13.5.sp
                            )
                        },
                        textStyle = TextStyle(
                            fontFamily = BricolageFamily,
                            fontSize = 14.5.sp,
                            color = RawColors.TextPrimary
                        ),
                        maxLines = 4,
                        shape = RoundedCornerShape(22.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RawColors.Tan,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = RawColors.RaisedCard,
                            unfocusedContainerColor = RawColors.RaisedCard,
                            cursorColor = RawColors.Tan
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    CircleActionButton(
                        icon = RawIconType.ARROW_RIGHT,
                        size = 46.dp,
                        backgroundColor = RawColors.Tan,
                        iconTint = RawColors.Ink,
                        enabled = !sending && (inputText.isNotBlank() || pendingAttachment != null),
                        onClick = { sendMessage(null) }
                    )
                }

                Text(
                    text = "Local file upload up to 500 MB • Everything stored locally on phone",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.sp,
                    color = RawColors.TextMuted,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }

        RawToastBanner(
            message = toastMsg,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 92.dp)
        )
    }
}

@Composable
private fun ChatBubbleRow(msg: ChatMessageItem) {
    val isUser = msg.role == "user"
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 330.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 22.dp,
                        topEnd = 22.dp,
                        bottomStart = if (isUser) 22.dp else 6.dp,
                        bottomEnd = if (isUser) 6.dp else 22.dp
                    )
                )
                .background(if (isUser) RawColors.Tan else RawColors.CardSurface)
                .padding(horizontal = 15.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (msg.attachmentName.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isUser) Color.Black.copy(alpha = 0.14f) else RawColors.RaisedCard)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RawIcon(
                        type = RawIconType.FILE,
                        size = 14.dp,
                        tint = if (isUser) RawColors.Ink else RawColors.Tan
                    )
                    Text(
                        text = "${msg.attachmentName} (${FormatHelpers.formatFileSize(msg.attachmentSize)})",
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = if (isUser) RawColors.Ink else RawColors.Tan
                    )
                }
            }

            Text(
                text = msg.text,
                fontFamily = BricolageFamily,
                fontWeight = if (isUser) FontWeight.SemiBold else FontWeight.Medium,
                fontSize = 14.5.sp,
                lineHeight = 20.5.sp,
                color = if (isUser) RawColors.Ink else RawColors.TextPrimary
            )

            if (msg.appliedActions.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    msg.appliedActions.forEach { badge ->
                        Row(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(RawColors.AccentSoft)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            RawIcon(RawIconType.CHECK, size = 12.dp, tint = RawColors.Tan)
                            Text(
                                text = badge,
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.5.sp,
                                color = RawColors.Tan
                            )
                        }
                    }
                }
            }
        }
    }
}
