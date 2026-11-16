package com.rawedit.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.http.SslError
import android.os.Message
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.widget.Toast
import androidx.compose.material3.Surface
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.zIndex
import android.annotation.SuppressLint
import android.os.Build
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import com.rawedit.app.domain.ChatbotBackgroundManager
import android.webkit.WebChromeClient
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import android.webkit.WebViewClient
import androidx.compose.ui.viewinterop.AndroidView
import com.rawedit.app.domain.ChatbotFileTools
import java.io.File
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rawedit.app.data.CacheManager
import com.rawedit.app.data.ChatUploadedFile
import com.rawedit.app.data.local.LocalAssetEntity
import com.rawedit.app.data.local.ProjectEntity
import com.rawedit.app.data.local.UserPreferences
import com.rawedit.app.data.local.UserProfile
import com.rawedit.app.data.repository.RawEditRepository
import com.rawedit.app.domain.AspectPreset
import com.rawedit.app.domain.ChatMessageItem
import com.rawedit.app.domain.ChatbotMode
import com.rawedit.app.domain.DesignRequestConfig
import com.rawedit.app.domain.OpenDesignDeliverableType
import com.rawedit.app.domain.ChatSessionMeta
import com.rawedit.app.domain.ChatbotEngine
import com.rawedit.app.domain.GeminiWaterfallRouter
import com.rawedit.app.domain.PaletteState
import com.rawedit.app.ui.components.BigPrimaryButton
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DESIGN_STARTER_OPTIONS = listOf(
    "Design a bold poster (PNG)" to "Create a high-contrast poster using my active palette and uploaded custom fonts",
    "Create motion graphics video" to "Create a 6-second deterministic HTML motion graphics reel using my uploaded fonts and assets",
    "Build a 4:5 slide deck" to "Design a clean 4-slide visual carousel deck in Open Design style",
    "Open Manual Design Studio" to "OPEN_DESIGN_STUDIO"
)

private val SCRIPT_STARTER_OPTIONS = listOf(
    "Write Hinglish reel script" to "Write a 60-70 word Hinglish reel script on the latest trending AI video tool using live web data",
    "3 viral hooks for my SRT" to "Write 3 scroll-stopping Hinglish hooks based on my active project script",
    "Rewrite script with H-V-P-A" to "Rewrite my active project script using H-V-P-A retention structure"
)

private val MINIMAL_STARTER_OPTIONS = listOf(
    "Analyze my Instagram page" to "OPEN_BROWSER_IG",
    "Tweak prompt & style ZIP" to "Help me tweak my Cloud AI prompt and Style Pack ZIP",
    "Export guide as PDF" to "Create a clean downloadable PDF checklist for viral reel pacing",
    "Open Design Studio (Images & Motion)" to "OPEN_DESIGN_STUDIO"
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
    onOpenDesignStudio: () -> Unit = {},
    onCreateProjectFromHtml: (htmlPath: String, title: String, aspectId: String) -> Unit = { _, _, _ -> },
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val localClipboard = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val activeProject = remember(initialProjectId, allProjects) {
        allProjects.firstOrNull { it.id == initialProjectId } ?: allProjects.firstOrNull()
    }

    var sessions by remember { mutableStateOf(ChatbotEngine.listChatSessions(cacheManager)) }
    var currentSessionId by remember {
        mutableStateOf(sessions.firstOrNull()?.id ?: "chat_${System.currentTimeMillis()}")
    }
    var activeMode by remember { mutableStateOf(ChatbotMode.RAW_DESIGN) }
    var designType by remember { mutableStateOf(OpenDesignDeliverableType.IMAGE) }
    var designAspect by remember { mutableStateOf(AspectPreset.ALL_PRESETS[3]) }
    var designUsePalette by remember { mutableStateOf(true) }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var viewingFile by remember { mutableStateOf<File?>(null) }
    var selectingText by remember { mutableStateOf<String?>(null) }
    var previewingWebsite by remember { mutableStateOf<File?>(null) }
    var showInAppBrowser by remember { mutableStateOf(false) }
    var browserInitialUrl by remember { mutableStateOf("https://www.instagram.com/rawde_vvvvvv/") }
    var pendingBrowserContext by remember { mutableStateOf<String?>(null) }
    var pendingBrowserTitle by remember { mutableStateOf<String?>(null) }

    var inputText by remember { mutableStateOf("") }
    var pendingAttachment by remember { mutableStateOf<ChatUploadedFile?>(null) }
    var uploadingFile by remember { mutableStateOf(false) }
    val activeBgSessions by ChatbotBackgroundManager.activeSessions.collectAsState()
    val bgUpdateTick by ChatbotBackgroundManager.updateTick.collectAsState()
    val bgToast by ChatbotBackgroundManager.lastToast.collectAsState()
    val sending = currentSessionId in activeBgSessions
    var showAttachChooser by remember { mutableStateOf(false) }
    var renamingSession by remember { mutableStateOf<ChatSessionMeta?>(null) }
    var renameDraft by remember { mutableStateOf("") }
    var toastMsg by remember { mutableStateOf<String?>(null) }

    var messages by remember(currentSessionId, bgUpdateTick) {
        mutableStateOf(ChatbotEngine.loadChatHistory(cacheManager, currentSessionId))
    }
    LaunchedEffect(bgUpdateTick) {
        messages = ChatbotEngine.loadChatHistory(cacheManager, currentSessionId)
        sessions = ChatbotEngine.listChatSessions(cacheManager)
    }
    LaunchedEffect(bgToast) {
        if (bgToast != null) {
            toastMsg = bgToast
            ChatbotBackgroundManager.clearToast()
        }
    }

    LaunchedEffect(toastMsg) {
        if (toastMsg != null) {
            delay(2000)
            toastMsg = null
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            runCatching { listState.animateScrollToItem(messages.lastIndex) }
        }
    }

    fun startNewChat() {
        if (sending) return
        val newId = "chat_${System.currentTimeMillis()}"
        currentSessionId = newId
        messages = emptyList()
        pendingAttachment = null
        inputText = ""
        showHistoryDialog = false
    }

    fun copyFullText(text: String) {
        runCatching { localClipboard.setText(AnnotatedString(text)) }
        runCatching {
            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cb.setPrimaryClip(ClipData.newPlainText("RAW EDIT AI", text))
        }
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        toastMsg = "Copied to clipboard"
    }

    val galleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
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
        val browserCtx = pendingBrowserContext
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
        ChatbotEngine.saveChatHistory(cacheManager, updatedWithUser, currentSessionId)
        ChatbotEngine.upsertChatSessionMeta(cacheManager, currentSessionId, updatedWithUser)
        sessions = ChatbotEngine.listChatSessions(cacheManager)

        if (customPrompt == null) inputText = ""
        pendingAttachment = null
        pendingBrowserContext = null
        pendingBrowserTitle = null

        ChatbotBackgroundManager.launchChatJob(
            appContext = context.applicationContext,
            sessionId = currentSessionId,
            geminiRouter = geminiRouter,
            cacheManager = cacheManager,
            userPreferences = userPreferences,
            repository = repository,
            userProfile = userProfile,
            allProjects = allProjects,
            activeProject = activeProject,
            allAssets = allAssets,
            activePalette = activePalette,
            historySnapshot = updatedWithUser,
            userText = effectiveUserText,
            uploadedFile = attached,
            browserPageContext = browserCtx,
            mode = activeMode,
            designConfig = DesignRequestConfig(
                type = designType,
                aspect = designAspect,
                useStudioPalette = designUsePalette
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RawColors.BgPrimary)
            .statusBarsPadding()
            .imePadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Minimal Top Bar with History & New Chat
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircleActionButton(
                        icon = RawIconType.GLOBE,
                        size = 40.dp,
                        onClick = { showInAppBrowser = true }
                    )
                    CircleActionButton(
                        icon = RawIconType.CLOCK,
                        size = 40.dp,
                        onClick = {
                            sessions = ChatbotEngine.listChatSessions(cacheManager)
                            showHistoryDialog = true
                        }
                    )
                    CircleActionButton(
                        icon = RawIconType.PLUS,
                        size = 40.dp,
                        backgroundColor = RawColors.AccentSoft,
                        iconTint = RawColors.Tan,
                        onClick = { startNewChat() }
                    )
                }
            }

            // Mode Tabs: RAW Design | RAW Script | RAW Assistant
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 4.dp)
                    .clip(CircleShape)
                    .background(RawColors.CardSurface)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ChatbotMode.entries.forEach { m ->
                    val sel = activeMode == m
                    Surface(
                        onClick = { activeMode = m },
                        shape = CircleShape,
                        color = if (sel) RawColors.Tan else Color.Transparent,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = m.label,
                            fontFamily = BricolageFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (sel) RawColors.Ink else RawColors.TextMuted,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }

            // Main Content
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
                            val starters = when (activeMode) {
                                ChatbotMode.RAW_DESIGN -> DESIGN_STARTER_OPTIONS
                                ChatbotMode.RAW_SCRIPT -> SCRIPT_STARTER_OPTIONS
                                ChatbotMode.RAW_ASSISTANT -> MINIMAL_STARTER_OPTIONS
                            }
                            starters.forEach { (label, prompt) ->
                                RawPressButton(onClick = {
                                    if (prompt == "OPEN_BROWSER_IG") {
                                        browserInitialUrl = "https://www.instagram.com/rawde_vvvvvv/"
                                        showInAppBrowser = true
                                    } else if (prompt == "OPEN_DESIGN_STUDIO") {
                                        onOpenDesignStudio()
                                    } else {
                                        sendMessage(prompt)
                                    }
                                }) {
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
                            ChatBubbleRow(
                                msg = msg,
                                onCopyFull = { copyFullText(msg.text) },
                                onSelectText = { keyboard?.hide(); selectingText = msg.text },
                                onViewFile = { path ->
                                    keyboard?.hide()
                                    val f = File(path)
                                    if (f.exists()) viewingFile = f else toastMsg = "File not found"
                                },
                                onSaveFile = { path, name ->
                                    val f = File(path)
                                    if (f.exists() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                        val mime = when (f.extension.lowercase()) {
                                            "pdf" -> "application/pdf"
                                            "html" -> "text/html"
                                            "json" -> "application/json"
                                            "png" -> "image/png"
                                            else -> "text/plain"
                                        }
                                        scope.launch {
                                            cacheManager.saveFileToDownloads(f, name.ifBlank { f.name }, mime)
                                                .onSuccess { toastMsg = "Saved to Downloads: ${f.name}" }
                                                .onFailure { toastMsg = it.message ?: "Save failed" }
                                        }
                                    } else {
                                        toastMsg = "Saved locally: ${f.name}"
                                    }
                                },
                                onOpenAsMotionProject = { htmlPath, aspectId ->
                                    onCreateProjectFromHtml(htmlPath, "OpenDesign Motion", aspectId)
                                },
                                onPreviewWebsite = { path ->
                                    keyboard?.hide()
                                    val f = File(path)
                                    if (f.exists()) {
                                        previewingWebsite = f
                                    } else {
                                        val fallback = Regex("(<!DOCTYPE html[\\s\\S]*?</html>|<html[\\s\\S]*?</html>)", RegexOption.IGNORE_CASE)
                                            .find(msg.text)?.groupValues?.getOrNull(1)
                                        if (!fallback.isNullOrBlank()) {
                                            val out = File(context.cacheDir, "preview_${msg.id.hashCode()}.html")
                                            runCatching { out.writeText(fallback, Charsets.UTF_8) }
                                            previewingWebsite = out
                                        } else {
                                            Toast.makeText(context, "Website file not found", Toast.LENGTH_SHORT).show()
                                            toastMsg = "Website file not found"
                                        }
                                    }
                                }
                            )
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


            pendingBrowserTitle?.let { pageTitle ->
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
                        RawIcon(RawIconType.GLOBE, size = 15.dp, tint = RawColors.Tan)
                        Text(
                            text = "Page: $pageTitle",
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
                        onClick = {
                            pendingBrowserContext = null
                            pendingBrowserTitle = null
                        }
                    )
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

            if (activeMode == ChatbotMode.RAW_DESIGN) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OpenDesignDeliverableType.entries.forEach { t ->
                            val sel = designType == t
                            Surface(
                                onClick = { designType = t },
                                shape = CircleShape,
                                color = if (sel) RawColors.Tan else RawColors.CardSurface
                            ) {
                                Text(
                                    text = t.label,
                                    fontFamily = BricolageFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = if (sel) RawColors.Ink else RawColors.Cream,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                        Surface(
                            onClick = onOpenDesignStudio,
                            shape = CircleShape,
                            color = RawColors.AccentSoft
                        ) {
                            Text(
                                text = "Manual Studio",
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.5.sp,
                                color = RawColors.Tan,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AspectPreset.ALL_PRESETS.take(5).forEach { ap ->
                            val sel = designAspect.id == ap.id
                            Surface(
                                onClick = { designAspect = ap },
                                shape = CircleShape,
                                color = if (sel) RawColors.Cream else RawColors.RaisedCard
                            ) {
                                Text(
                                    text = ap.label,
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.5.sp,
                                    color = if (sel) RawColors.Ink else RawColors.TextPrimary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Surface(
                            onClick = { designUsePalette = !designUsePalette },
                            shape = CircleShape,
                            color = if (designUsePalette) RawColors.AccentSoft else RawColors.RaisedCard
                        ) {
                            Text(
                                text = if (designUsePalette) "Palette: Studio" else "Palette: Custom",
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.5.sp,
                                color = if (designUsePalette) RawColors.Tan else RawColors.TextMuted,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
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
                        onClick = { showAttachChooser = true }
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

        selectingText?.let { rawTxt ->
            BackHandler { selectingText = null }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(40f)
                    .background(Color.Black.copy(alpha = 0.72f))
                    .clickable(onClick = { selectingText = null }),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(RawColors.CardSurface)
                        .border(1.dp, RawColors.Divider, RoundedCornerShape(26.dp))
                        .clickable(enabled = false, onClick = {})
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Select & Copy Text",
                            fontFamily = BricolageFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = RawColors.TextPrimary
                        )
                        CircleActionButton(icon = RawIconType.CLOSE, size = 34.dp, onClick = { selectingText = null })
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 140.dp, max = 400.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(RawColors.BgPrimary)
                            .verticalScroll(rememberScrollState())
                            .padding(14.dp)
                    ) {
                        SelectionContainer {
                            Text(
                                text = rawTxt,
                                style = TextStyle(
                                    fontFamily = BricolageFamily,
                                    fontSize = 14.5.sp,
                                    lineHeight = 21.sp,
                                    color = RawColors.TextPrimary
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    BigPrimaryButton(
                        text = "Copy all",
                        height = 48.dp,
                        leadingIcon = RawIconType.COPY,
                        onClick = {
                            copyFullText(rawTxt)
                            selectingText = null
                        }
                    )
                }
            }
        }

        previewingWebsite?.let { htmlFile ->
            BackHandler { previewingWebsite = null }
            Box(modifier = Modifier.fillMaxSize().zIndex(50f)) {
                ChatWebsitePreviewOverlay(
                    htmlFile = htmlFile,
                    onSave = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            scope.launch {
                                cacheManager.saveFileToDownloads(htmlFile, htmlFile.name, "text/html")
                                    .onSuccess { toastMsg = "Saved ${htmlFile.name} to Downloads" }
                            }
                        }
                    },
                    onDismiss = { previewingWebsite = null }
                )
            }
        }

        if (showInAppBrowser) {
            BackHandler { showInAppBrowser = false }
            Box(modifier = Modifier.fillMaxSize().zIndex(60f)) {
                InAppAiBrowserDialog(
                    initialUrl = browserInitialUrl,
                    onSendPageToAi = { url, title, pageText ->
                        pendingBrowserTitle = title.ifBlank { url }
                        pendingBrowserContext = "URL: $url\nTITLE: $title\nCONTENT:\n$pageText"
                        showInAppBrowser = false
                        if (inputText.isBlank()) {
                            inputText = if (url.contains("instagram.com", ignoreCase = true)) {
                                "Analyze this Instagram page & reels and suggest 5 new viral reel ideas + scripts in RAWDEV style."
                            } else {
                                "Use this webpage data to help me."
                            }
                        }
                        toastMsg = "Page captured for AI"
                    },
                    onDismiss = { showInAppBrowser = false }
                )
            }
        }
    }


    viewingFile?.let { file ->
        ChatFileViewerDialog(
            file = file,
            onCopy = { txt -> copyFullText(txt) },
            onDismiss = { viewingFile = null }
        )
    }








    if (showAttachChooser) {
        Dialog(onDismissRequest = { showAttachChooser = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(RawColors.CardSurface)
                    .border(1.dp, RawColors.Divider, RoundedCornerShape(24.dp))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Attach to Chat",
                    fontFamily = BricolageFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = RawColors.TextPrimary
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(RawColors.RaisedCard)
                        .clickable {
                            showAttachChooser = false
                            galleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                        }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    RawIcon(RawIconType.IMAGE, size = 18.dp, tint = RawColors.Tan)
                    Text("Gallery (Photos & Videos)", fontFamily = BricolageFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp, color = RawColors.TextPrimary)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(RawColors.RaisedCard)
                        .clickable {
                            showAttachChooser = false
                            filePicker.launch(arrayOf("*/*"))
                        }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    RawIcon(RawIconType.FILE, size = 18.dp, tint = RawColors.Tan)
                    Text("Files (PDF, ZIP, Skill, Docs)", fontFamily = BricolageFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp, color = RawColors.TextPrimary)
                }
            }
        }
    }

    renamingSession?.let { target ->
        Dialog(onDismissRequest = { renamingSession = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(RawColors.CardSurface)
                    .border(1.dp, RawColors.Divider, RoundedCornerShape(24.dp))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Rename Chat",
                    fontFamily = BricolageFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = RawColors.TextPrimary
                )
                OutlinedTextField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = BricolageFamily, fontSize = 15.sp, color = RawColors.TextPrimary),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RawColors.Tan,
                        unfocusedBorderColor = RawColors.Divider,
                        focusedContainerColor = RawColors.BgPrimary,
                        unfocusedContainerColor = RawColors.BgPrimary,
                        cursorColor = RawColors.Tan
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                BigPrimaryButton(
                    text = "Save",
                    height = 46.dp,
                    onClick = {
                        ChatbotEngine.renameChatSession(cacheManager, target.id, renameDraft)
                        sessions = ChatbotEngine.listChatSessions(cacheManager)
                        renamingSession = null
                        toastMsg = "Chat renamed"
                    }
                )
            }
        }
    }

    if (showHistoryDialog) {
        ChatHistoryDialog(
            sessions = sessions,
            currentSessionId = currentSessionId,
            onSelectSession = { s ->
                currentSessionId = s.id
                messages = ChatbotEngine.loadChatHistory(cacheManager, s.id)
                showHistoryDialog = false
            },
            onRenameSession = { s ->
                renamingSession = s
                renameDraft = s.title
            },
            onDeleteSession = { s ->
                ChatbotEngine.deleteChatSession(cacheManager, s.id)
                sessions = ChatbotEngine.listChatSessions(cacheManager)
                if (currentSessionId == s.id) {
                    startNewChat()
                }
            },
            onNewChat = { startNewChat() },
            onDismiss = { showHistoryDialog = false }
        )
    }
}

@Composable
private fun ChatHistoryDialog(
    sessions: List<ChatSessionMeta>,
    currentSessionId: String,
    onSelectSession: (ChatSessionMeta) -> Unit,
    onRenameSession: (ChatSessionMeta) -> Unit,
    onDeleteSession: (ChatSessionMeta) -> Unit,
    onNewChat: () -> Unit,
    onDismiss: () -> Unit
) {
    val fmt = remember { SimpleDateFormat("MMM d, h:mm a", Locale.US) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(RawColors.CardSurface)
                .border(1.dp, RawColors.Divider, RoundedCornerShape(28.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Chat History",
                    fontFamily = BricolageFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = RawColors.TextPrimary
                )
                CircleActionButton(icon = RawIconType.CLOSE, size = 34.dp, onClick = onDismiss)
            }

            BigPrimaryButton(
                text = "New chat",
                height = 46.dp,
                leadingIcon = RawIconType.PLUS,
                onClick = onNewChat
            )

            if (sessions.isEmpty()) {
                Text(
                    text = "No saved chats yet.",
                    fontFamily = BricolageFamily,
                    fontSize = 14.sp,
                    color = RawColors.TextMuted,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    sessions.forEach { s ->
                        val isCurr = s.id == currentSessionId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isCurr) RawColors.AccentSoft else RawColors.RaisedCard)
                                .border(
                                    width = 1.dp,
                                    color = if (isCurr) RawColors.Tan else Color.Transparent,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable { onSelectSession(s) }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = s.title,
                                    fontFamily = BricolageFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.5.sp,
                                    color = if (isCurr) RawColors.Tan else RawColors.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (s.updatedAtMs > 0L) {
                                    Text(
                                        text = fmt.format(Date(s.updatedAtMs)),
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 11.sp,
                                        color = RawColors.TextMuted
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                CircleActionButton(
                                    icon = RawIconType.EDIT,
                                    size = 32.dp,
                                    iconTint = RawColors.Tan,
                                    onClick = { onRenameSession(s) }
                                )
                                CircleActionButton(
                                    icon = RawIconType.TRASH,
                                    size = 32.dp,
                                    iconTint = RawColors.ErrorCoral,
                                    onClick = { onDeleteSession(s) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubbleRow(
    msg: ChatMessageItem,
    onCopyFull: () -> Unit,
    onSelectText: () -> Unit,
    onViewFile: (String) -> Unit,
    onSaveFile: (String, String) -> Unit,
    onOpenAsMotionProject: (String, String) -> Unit = { _, _ -> },
    onPreviewWebsite: (String) -> Unit
) {
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
                        topStart = 20.dp,
                        topEnd = 20.dp,
                        bottomStart = if (isUser) 20.dp else 6.dp,
                        bottomEnd = if (isUser) 6.dp else 20.dp
                    )
                )
                .background(if (isUser) RawColors.Tan else RawColors.CardSurface)
                .padding(horizontal = 15.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (msg.attachmentName.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isUser) Color.Black.copy(alpha = 0.14f) else RawColors.RaisedCard)
                        .clickable(enabled = msg.attachmentPath.isNotBlank()) { onViewFile(msg.attachmentPath) }
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

            if (isUser) {
                Text(
                    text = msg.text,
                    fontFamily = BricolageFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.5.sp,
                    lineHeight = 20.5.sp,
                    color = RawColors.Ink
                )
            } else {
                RichFormattedMessage(text = msg.text)
            }

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


            val contextForPreview = LocalContext.current
            val effectivePreviewPath = remember(msg.id, msg.websitePreviewPath, msg.generatedFilePath, msg.attachmentPath, msg.text) {
                msg.websitePreviewPath.ifBlank {
                    when {
                        msg.generatedFilePath.endsWith(".html", true) -> msg.generatedFilePath
                        msg.attachmentPath.endsWith(".html", true) -> msg.attachmentPath
                        !isUser && (msg.text.contains("<!DOCTYPE html", ignoreCase = true) || msg.text.contains("<html", ignoreCase = true)) -> {
                            val extracted = Regex("(<!DOCTYPE html[\\s\\S]*?</html>|<html[\\s\\S]*?</html>)", RegexOption.IGNORE_CASE)
                                .find(msg.text)?.groupValues?.getOrNull(1)
                            if (!extracted.isNullOrBlank()) {
                                val f = File(contextForPreview.cacheDir, "preview_${msg.id.hashCode()}.html")
                                runCatching { f.writeText(extracted, Charsets.UTF_8) }
                                f.absolutePath
                            } else ""
                        }
                        else -> ""
                    }
                }
            }
            if (!isUser && msg.generatedImagePath.isNotBlank() && File(msg.generatedImagePath).exists()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(File(msg.generatedImagePath))
                        .build(),
                    contentDescription = "Generated OpenDesign image",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, RawColors.Divider, RoundedCornerShape(14.dp))
                        .clickable { onViewFile(msg.generatedImagePath) }
                )
            }

            if (effectivePreviewPath.isNotBlank()) {
                Surface(
                    onClick = { onPreviewWebsite(effectivePreviewPath) },
                    shape = RoundedCornerShape(14.dp),
                    color = RawColors.Tan,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RawIcon(RawIconType.GLOBE, size = 16.dp, tint = RawColors.Ink)
                            Text(
                                text = "Preview Website Live",
                                fontFamily = BricolageFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                color = RawColors.Ink
                            )
                        }
                        RawIcon(RawIconType.ARROW_RIGHT, size = 14.dp, tint = RawColors.Ink)
                    }
                }
                if (!isUser && msg.openDesignType == "motion") {
                    Surface(
                        onClick = { onOpenAsMotionProject(effectivePreviewPath, msg.openDesignAspectId.ifBlank { "16:9" }) },
                        shape = RoundedCornerShape(12.dp),
                        color = RawColors.AccentSoft,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Open as MP4 Project",
                            fontFamily = BricolageFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp,
                            color = RawColors.Tan,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 9.dp)
                        )
                    }
                }
            }

            if (msg.generatedFilePath.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(RawColors.RaisedCard)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        RawIcon(RawIconType.FILE, size = 14.dp, tint = RawColors.Tan)
                        Text(
                            text = msg.generatedFileName.ifBlank { "file" },
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            color = RawColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (!msg.generatedFileName.endsWith(".pdf", true)) {
                            Surface(
                                onClick = { onViewFile(msg.generatedFilePath) },
                                shape = CircleShape,
                                color = RawColors.CardSurface
                            ) {
                                Text(
                                    "View",
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = RawColors.Tan,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                        Surface(
                            onClick = { onSaveFile(msg.generatedFilePath, msg.generatedFileName) },
                            shape = CircleShape,
                            color = RawColors.AccentSoft
                        ) {
                            Text(
                                "Save",
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = RawColors.Tan,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            // Select & Full Message Copy Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                Surface(
                    onClick = onSelectText,
                    shape = CircleShape,
                    color = if (isUser) Color.Black.copy(alpha = 0.14f) else RawColors.RaisedCard
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                    ) {
                        Text(
                            text = "Select",
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            color = if (isUser) RawColors.Ink else RawColors.TextPrimary
                        )
                    }
                }
                Surface(
                    onClick = onCopyFull,
                    shape = CircleShape,
                    color = if (isUser) Color.Black.copy(alpha = 0.14f) else RawColors.RaisedCard
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        RawIcon(
                            type = RawIconType.COPY,
                            size = 12.dp,
                            tint = if (isUser) RawColors.Ink else RawColors.Tan
                        )
                        Text(
                            text = "Copy",
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            color = if (isUser) RawColors.Ink else RawColors.TextPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RichFormattedMessage(text: String) {
    val numberedRegex = remember { Regex("^(\\d+\\.)\\s+(.*)$") }
    val styled = remember(text) {
        buildAnnotatedString {
            val lines = text.replace("\r\n", "\n").lines()
            var inCodeBlock = false
            lines.forEachIndexed { idx, rawLine ->
                val trimmed = rawLine.trim()
                if (trimmed.startsWith("```")) {
                    inCodeBlock = !inCodeBlock
                    if (inCodeBlock) {
                        val lang = trimmed.removePrefix("```").trim()
                        if (lang.isNotEmpty()) {
                            withStyle(SpanStyle(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold, fontSize = 10.5.sp, color = RawColors.Tan)) {
                                append(lang.uppercase(Locale.US))
                            }
                            append("\n")
                        }
                    }
                    return@forEachIndexed
                }
                if (inCodeBlock) {
                    withStyle(
                        SpanStyle(
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 12.5.sp,
                            color = RawColors.Cream,
                            background = RawColors.RaisedCard
                        )
                    ) {
                        append(rawLine)
                    }
                    if (idx < lines.lastIndex) append("\n")
                    return@forEachIndexed
                }
                when {
                    trimmed.isEmpty() -> {}
                    trimmed == "---" || trimmed == "***" -> {
                        withStyle(SpanStyle(color = RawColors.DividerStrong)) {
                            append("──────────────")
                        }
                    }
                    trimmed.startsWith("### ") -> {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 15.5.sp, color = RawColors.Tan)) {
                            append(parseInlineMarkdown(trimmed.removePrefix("### ").trim()))
                        }
                    }
                    trimmed.startsWith("## ") -> {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 17.5.sp, color = RawColors.Cream)) {
                            append(parseInlineMarkdown(trimmed.removePrefix("## ").trim()))
                        }
                    }
                    trimmed.startsWith("# ") -> {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 19.sp, color = RawColors.Tan)) {
                            append(parseInlineMarkdown(trimmed.removePrefix("# ").trim()))
                        }
                    }
                    trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("\u2022 ") -> {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = RawColors.Tan)) {
                            append("\u2022  ")
                        }
                        append(parseInlineMarkdown(trimmed.drop(2).trim()))
                    }
                    numberedRegex.matches(trimmed) -> {
                        val m = numberedRegex.find(trimmed)!!
                        withStyle(SpanStyle(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold, color = RawColors.Tan)) {
                            append("${m.groupValues[1]} ")
                        }
                        append(parseInlineMarkdown(m.groupValues[2]))
                    }
                    trimmed.startsWith("> ") -> {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = RawColors.Tan)) {
                            append("\u2503 ")
                        }
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = RawColors.Cream)) {
                            append(parseInlineMarkdown(trimmed.removePrefix("> ").trim()))
                        }
                    }
                    else -> {
                        append(parseInlineMarkdown(trimmed))
                    }
                }
                if (idx < lines.lastIndex) append("\n")
            }
        }
    }
    Text(
        text = styled,
        fontFamily = BricolageFamily,
        fontSize = 14.5.sp,
        lineHeight = 22.sp,
        color = RawColors.TextPrimary
    )
}

private fun parseInlineMarkdown(input: String): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < input.length) {
            if (i + 1 < input.length && input[i] == '=' && input[i + 1] == '=') {
                val end = input.indexOf("==", i + 2)
                if (end != -1) {
                    withStyle(
                        SpanStyle(
                            fontWeight = FontWeight.Bold,
                            color = RawColors.Cream,
                            background = RawColors.Tan.copy(alpha = 0.28f)
                        )
                    ) {
                        append(" ${input.substring(i + 2, end)} ")
                    }
                    i = end + 2
                    continue
                }
            }
            if (i + 1 < input.length && input[i] == '*' && input[i + 1] == '*') {
                val end = input.indexOf("**", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = RawColors.Cream)) {
                        append(input.substring(i + 2, end))
                    }
                    i = end + 2
                    continue
                }
            }
            if (input[i] == '*' && (i + 1 >= input.length || input[i + 1] != '*')) {
                val end = input.indexOf('*', i + 1)
                if (end != -1 && end > i + 1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = RawColors.Cream)) {
                        append(input.substring(i + 1, end))
                    }
                    i = end + 1
                    continue
                }
            }
            if (input[i] == '`') {
                val end = input.indexOf('`', i + 1)
                if (end != -1) {
                    withStyle(
                        SpanStyle(
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.SemiBold,
                            color = RawColors.Tan,
                            background = RawColors.RaisedCard
                        )
                    ) {
                        append(" ${input.substring(i + 1, end)} ")
                    }
                    i = end + 1
                    continue
                }
            }
            if (input[i] == '[') {
                val closeBracket = input.indexOf("](", i + 1)
                if (closeBracket != -1) {
                    val closeParen = input.indexOf(')', closeBracket + 2)
                    if (closeParen != -1) {
                        val label = input.substring(i + 1, closeBracket)
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = RawColors.Tan)) {
                            append(label)
                        }
                        i = closeParen + 1
                        continue
                    }
                }
            }
            append(input[i])
            i++
        }
    }
}


@Composable
private fun ChatFileViewerDialog(
    file: File,
    onCopy: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val content = remember(file.absolutePath) { ChatbotFileTools.inspectLocalFile(file) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(RawColors.BgPrimary)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircleActionButton(icon = RawIconType.CLOSE, size = 40.dp, onClick = onDismiss)
                Text(
                    text = file.name,
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = RawColors.Tan,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
                )
                CircleActionButton(
                    icon = RawIconType.COPY,
                    size = 40.dp,
                    onClick = { onCopy(content) }
                )
            }
            SelectionContainer {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(RawColors.CardSurface)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    if (file.extension.lowercase() in setOf("png", "jpg", "jpeg", "webp")) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current).data(file).build(),
                            contentDescription = file.name,
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        )
                    } else if (file.extension.lowercase() in setOf("md", "skill")) {
                        RichFormattedMessage(text = content)
                    } else {
                        Text(
                            text = content,
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = RawColors.TextPrimary
                        )
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ChatWebsitePreviewOverlay(
    htmlFile: File,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    val htmlText = remember(htmlFile.absolutePath) {
        runCatching { htmlFile.readText(Charsets.UTF_8) }.getOrDefault("<html><body><h3>Empty</h3></body></html>")
    }
    var desktopMode by remember { mutableStateOf(false) }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(RawColors.BgPrimary)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircleActionButton(icon = RawIconType.CLOSE, size = 38.dp, onClick = onDismiss)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (!desktopMode) RawColors.Tan else RawColors.RaisedCard)
                            .clickable { desktopMode = false }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Mobile", fontFamily = BricolageFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (!desktopMode) RawColors.Ink else RawColors.TextPrimary)
                    }
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (desktopMode) RawColors.Tan else RawColors.RaisedCard)
                            .clickable { desktopMode = true }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Desktop", fontFamily = BricolageFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (desktopMode) RawColors.Ink else RawColors.TextPrimary)
                    }
                }
                CircleActionButton(icon = RawIconType.DOWNLOAD, size = 38.dp, onClick = onSave)
            }

            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setBackgroundColor(android.graphics.Color.WHITE)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = true
                        settings.allowContentAccess = true
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = desktopMode
                        webViewClient = WebViewClient()
                        webChromeClient = WebChromeClient()
                        loadDataWithBaseURL("https://rawedit.local/", htmlText, "text/html", "UTF-8", null)
                    }
                },
                update = { wv ->
                    if (wv.settings.useWideViewPort != desktopMode) {
                        wv.settings.useWideViewPort = desktopMode
                        wv.reload()
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        }
    
}


@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun InAppAiBrowserDialog(
    initialUrl: String,
    onSendPageToAi: (url: String, title: String, pageText: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var urlDraft by remember(initialUrl) { mutableStateOf(initialUrl) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var desktopMode by remember { mutableStateOf(false) }
    var loadProgress by remember { mutableIntStateOf(0) }
    var pageError by remember { mutableStateOf<String?>(null) }

    val mobileUa = "Mozilla/5.0 (Linux; Android 13; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    val desktopUa = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RawColors.BgPrimary)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CircleActionButton(icon = RawIconType.CLOSE, size = 38.dp, onClick = onDismiss)
            OutlinedTextField(
                value = urlDraft,
                onValueChange = { urlDraft = it },
                singleLine = true,
                textStyle = TextStyle(fontFamily = JetBrainsMonoFamily, fontSize = 12.sp, color = RawColors.TextPrimary),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RawColors.Tan,
                    unfocusedBorderColor = RawColors.Divider,
                    focusedContainerColor = RawColors.CardSurface,
                    unfocusedContainerColor = RawColors.CardSurface,
                    cursorColor = RawColors.Tan
                ),
                modifier = Modifier.weight(1f)
            )
            CircleActionButton(
                icon = RawIconType.ARROW_RIGHT,
                size = 38.dp,
                backgroundColor = RawColors.RaisedCard,
                onClick = {
                    pageError = null
                    val target = if (urlDraft.startsWith("http")) urlDraft else "https://$urlDraft"
                    webViewRef?.loadUrl(target)
                }
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = {
                    desktopMode = !desktopMode
                    webViewRef?.let { wv ->
                        wv.settings.userAgentString = if (desktopMode) desktopUa else mobileUa
                        wv.settings.useWideViewPort = true
                        wv.reload()
                    }
                },
                shape = CircleShape,
                color = if (desktopMode) RawColors.Tan else RawColors.RaisedCard
            ) {
                Text(
                    text = if (desktopMode) "Desktop ON" else "Desktop",
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = if (desktopMode) RawColors.Ink else RawColors.TextPrimary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
            Surface(
                onClick = {
                    val u = webViewRef?.url ?: urlDraft
                    val target = if (u.startsWith("http")) u else "https://$u"
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)))
                    }
                },
                shape = CircleShape,
                color = RawColors.RaisedCard
            ) {
                Text(
                    text = "Open in Chrome",
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = RawColors.Tan,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
            Surface(
                onClick = {
                    pageError = null
                    webViewRef?.reload()
                },
                shape = CircleShape,
                color = RawColors.RaisedCard
            ) {
                Text(
                    text = "Reload",
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = RawColors.TextPrimary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        if (loadProgress in 1..99) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(RawColors.RaisedCard)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(loadProgress / 100f)
                        .height(3.dp)
                        .background(RawColors.Tan)
                )
            }
        }

        pageError?.let { err ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(RawColors.ErrorSoft)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = err,
                    fontFamily = BricolageFamily,
                    fontSize = 12.sp,
                    color = RawColors.ErrorCoral,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    setBackgroundColor(android.graphics.Color.WHITE)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.allowContentAccess = true
                    settings.allowFileAccess = true
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.setSupportMultipleWindows(true)
                    settings.javaScriptCanOpenWindowsAutomatically = true
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        @Suppress("DEPRECATION")
                        settings.forceDark = WebSettings.FORCE_DARK_OFF
                    }
                    settings.userAgentString = mobileUa
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            loadProgress = newProgress
                        }

                        override fun onCreateWindow(
                            view: WebView?,
                            isDialog: Boolean,
                            isUserGesture: Boolean,
                            resultMsg: Message?
                        ): Boolean {
                            val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                            val temp = WebView(ctx)
                            temp.webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(v: WebView?, req: WebResourceRequest?): Boolean {
                                    val popupUrl = req?.url?.toString().orEmpty()
                                    if (popupUrl.startsWith("http://") || popupUrl.startsWith("https://")) {
                                        view?.loadUrl(popupUrl)
                                    }
                                    return true
                                }
                            }
                            transport.webView = temp
                            resultMsg.sendToTarget()
                            return true
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val u = request?.url?.toString().orEmpty()
                            if (u.startsWith("http://") || u.startsWith("https://")) {
                                return false
                            }
                            return true
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            if (!url.isNullOrBlank()) urlDraft = url
                            CookieManager.getInstance().flush()
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            if (request?.isForMainFrame == true) {
                                pageError = "Page load issue (${error?.description ?: "network error"}). Try Desktop or Open in Chrome."
                            }
                        }

                        override fun onReceivedSslError(
                            view: WebView?,
                            handler: SslErrorHandler?,
                            error: SslError?
                        ) {
                            handler?.cancel()
                            pageError = "SSL error loading page. Try Open in Chrome."
                        }

                        override fun onRenderProcessGone(
                            view: WebView?,
                            detail: RenderProcessGoneDetail?
                        ): Boolean {
                            pageError = "Browser renderer restarted. Tap Reload."
                            return true
                        }
                    }
                    loadUrl(initialUrl)
                    webViewRef = this
                }
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(RawColors.CardSurface)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleActionButton(
                icon = RawIconType.CHEVRON_LEFT,
                size = 42.dp,
                onClick = { if (webViewRef?.canGoBack() == true) webViewRef?.goBack() }
            )
            BigPrimaryButton(
                text = "Send Page to AI",
                height = 46.dp,
                leadingIcon = RawIconType.SPARK,
                onClick = {
                    val wv = webViewRef
                    if (wv != null) {
                        wv.evaluateJavascript(
                            "(function(){ return document.body ? document.body.innerText.slice(0, 9000) : ''; })()"
                        ) { rawJson ->
                            val decoded = rawJson?.trim()?.removeSurrounding(""")
                                ?.replace("\\n", "\n")
                                ?.replace("\\"", """)
                                .orEmpty()
                            onSendPageToAi(wv.url ?: urlDraft, wv.title ?: "", decoded)
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}
