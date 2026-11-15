package com.rawedit.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.text.style.TextAlign
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
import com.rawedit.app.ui.components.CircleActionButton
import com.rawedit.app.ui.components.FormatHelpers
import com.rawedit.app.ui.components.RawToastBanner
import com.rawedit.app.ui.icons.PixelMascotKind
import com.rawedit.app.ui.icons.RawIcon
import com.rawedit.app.ui.icons.RawIconType
import com.rawedit.app.ui.icons.RawPixelMascot
import com.rawedit.app.ui.motion.RawPressButton
import com.rawedit.app.ui.theme.BricolageFamily
import com.rawedit.app.ui.theme.JetBrainsMonoFamily
import com.rawedit.app.ui.theme.RawColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val MINIMAL_STARTER_OPTIONS = listOf(
    "Suggest reel ideas" to "Suggest 5 viral next reel ideas from my SRT & scripts",
    "Tweak prompt & style ZIP" to "Help me tweak my Cloud AI prompt and Style Pack ZIP",
    "Fetch GitHub skill" to "Fetch a motion graphics skill from GitHub"
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

    val activeProject = remember(initialProjectId, allProjects) {
        allProjects.firstOrNull { it.id == initialProjectId } ?: allProjects.firstOrNull()
    }

    var messages by remember {
        mutableStateOf(ChatbotEngine.loadChatHistory(cacheManager, "global"))
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
                    toastMsg = "Attached ${uploaded.fileName}"
                }.onFailure { err ->
                    toastMsg = err.message ?: "Max file size is 500 MB"
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
            // Minimal Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircleActionButton(icon = RawIconType.BACK, onClick = onBack, size = 44.dp)
                Text(
                    text = "AI Assistant",
                    fontFamily = BricolageFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp,
                    color = RawColors.TextPrimary
                )
                if (messages.isNotEmpty()) {
                    CircleActionButton(
                        icon = RawIconType.TRASH,
                        size = 40.dp,
                        iconTint = RawColors.TextMuted,
                        onClick = {
                            ChatbotEngine.clearChatHistory(cacheManager, "global")
                            messages = emptyList()
                            toastMsg = "Cleared"
                        }
                    )
                } else {
                    Spacer(modifier = Modifier.size(40.dp))
                }
            }

            // Main Content: Minimalist Empty State OR Clean Message Stream
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (messages.isEmpty() && !sending) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        RawPixelMascot(kind = PixelMascotKind.CODE_BOT, size = 58.dp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "HOW CAN I\nHELP TODAY?",
                            fontFamily = BricolageFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 30.sp,
                            lineHeight = 33.sp,
                            letterSpacing = (-0.8).sp,
                            textAlign = TextAlign.Center,
                            color = RawColors.TextPrimary
                        )
                        Spacer(modifier = Modifier.height(26.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            MINIMAL_STARTER_OPTIONS.forEach { (label, prompt) ->
                                RawPressButton(onClick = { sendMessage(prompt) }) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(RawColors.CardSurface)
                                            .padding(horizontal = 18.dp, vertical = 16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = label,
                                            fontFamily = BricolageFamily,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 15.5.sp,
                                            color = RawColors.TextPrimary
                                        )
                                        RawIcon(
                                            type = RawIconType.ARROW_RIGHT,
                                            size = 16.dp,
                                            tint = RawColors.Tan
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(messages, key = { it.id }) { msg ->
                            ChatBubbleRow(msg = msg)
                        }
                        if (sending) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(RawColors.CardSurface)
                                        .padding(horizontal = 14.dp, vertical = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CircularProgressIndicator(
                                        color = RawColors.Tan,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Thinking...",
                                        fontFamily = BricolageFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        color = RawColors.TextMuted
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Pending File Attachment Pill
            pendingAttachment?.let { att ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(RawColors.AccentSoft)
                        .border(1.dp, RawColors.AccentBorder, RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        RawIcon(RawIconType.FILE, size = 15.dp, tint = RawColors.Tan)
                        Text(
                            text = "${att.fileName} (${FormatHelpers.formatFileSize(att.sizeBytes)})",
                            fontFamily = BricolageFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = RawColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    CircleActionButton(
                        icon = RawIconType.CLOSE,
                        size = 28.dp,
                        onClick = { pendingAttachment = null }
                    )
                }
            }

            // Minimal Floating Input Pill
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(RawColors.CardSurface)
                        .border(1.dp, RawColors.Divider, RoundedCornerShape(28.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CircleActionButton(
                        icon = if (uploadingFile) RawIconType.CLOCK else RawIconType.PLUS,
                        size = 40.dp,
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
                                text = "Ask anything...",
                                color = RawColors.TextMuted,
                                fontSize = 14.5.sp
                            )
                        },
                        textStyle = TextStyle(
                            fontFamily = BricolageFamily,
                            fontSize = 15.sp,
                            color = RawColors.TextPrimary
                        ),
                        maxLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            cursorColor = RawColors.Tan
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    CircleActionButton(
                        icon = RawIconType.ARROW_RIGHT,
                        size = 40.dp,
                        backgroundColor = RawColors.Tan,
                        iconTint = RawColors.Ink,
                        enabled = !sending && (inputText.isNotBlank() || pendingAttachment != null),
                        onClick = { sendMessage(null) }
                    )
                }
            }
        }

        RawToastBanner(
            message = toastMsg,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 86.dp)
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
                .widthIn(max = 320.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 20.dp,
                        topEnd = 20.dp,
                        bottomStart = if (isUser) 20.dp else 6.dp,
                        bottomEnd = if (isUser) 6.dp else 20.dp
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
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RawIcon(
                        type = RawIconType.FILE,
                        size = 13.dp,
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
