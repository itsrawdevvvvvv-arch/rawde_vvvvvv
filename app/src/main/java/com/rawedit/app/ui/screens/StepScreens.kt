package com.rawedit.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.rawedit.app.data.CacheManager
import com.rawedit.app.data.local.ProjectEntity
import com.rawedit.app.domain.CodeValidationResult
import com.rawedit.app.domain.CodeValidator
import com.rawedit.app.domain.PromptGenerator
import com.rawedit.app.domain.SrtParser
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
import com.rawedit.app.ui.motion.RawEasings
import com.rawedit.app.ui.motion.RawPressButton
import com.rawedit.app.ui.motion.SplitFlapText
import com.rawedit.app.ui.motion.rememberAnimationsEnabled
import com.rawedit.app.ui.theme.InterFontFamily
import com.rawedit.app.ui.theme.JetBrainsMonoFamily
import com.rawedit.app.ui.theme.RawColors
import com.rawedit.app.ui.theme.RawTypography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val LARGE_EDITOR_THRESHOLD = 15_000

private suspend fun readClipboardText(context: Context): String {
    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = cb.primaryClip ?: return ""
    val items = (0 until clip.itemCount).mapNotNull { clip.getItemAt(it) }
    return withContext(Dispatchers.IO) {
        buildString {
            for (item in items) {
                val direct = item.text?.toString()?.takeIf { it.isNotEmpty() }
                    ?: item.coerceToText(context)?.toString().orEmpty()
                if (direct.isNotBlank()) {
                    append(direct)
                } else {
                    val uri = item.uri
                    if (uri != null) {
                        val streamText = runCatching {
                            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                        }.getOrDefault("")
                        append(streamText)
                    }
                }
            }
        }
    }
}

@Composable
fun AddVoiceScreen(
    project: ProjectEntity,
    cacheManager: CacheManager,
    onBack: () -> Unit,
    onSave: (ProjectEntity) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val animEnabled = rememberAnimationsEnabled()

    var audioPath by remember(project.id) { mutableStateOf(project.audioUri) }
    var audioName by remember(project.id) { mutableStateOf(project.audioName) }
    var audioSize by remember(project.id) { mutableStateOf(project.audioSizeBytes) }
    var audioDuration by remember(project.id) { mutableStateOf(project.audioDurationSec) }
    var audioError by remember { mutableStateOf<String?>(null) }
    var loadingAudio by remember { mutableStateOf(false) }

    var srtText by remember(project.id) { mutableStateOf(cacheManager.resolveSrtText(project.srtText)) }
    var srtCueCount by remember(project.id) { mutableStateOf(project.srtCueCount) }
    var srtError by remember { mutableStateOf<String?>(null) }

    var topic by remember(project.id) { mutableStateOf(cacheManager.resolveTopicText(project.topic)) }
    var forceRawEditTopic by remember { mutableStateOf(false) }
    var savingVoice by remember { mutableStateOf(false) }
    var toastMsg by remember { mutableStateOf<String?>(null) }

    val dzShake = remember { Animatable(0f) }

    LaunchedEffect(toastMsg) {
        if (toastMsg != null) {
            delay(1900)
            toastMsg = null
        }
    }

    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            loadingAudio = true
            audioError = null
            scope.launch {
                val res = cacheManager.importAudio(uri)
                loadingAudio = false
                res.onSuccess { imported ->
                    audioPath = imported.localPath
                    audioName = imported.displayName
                    audioSize = imported.sizeBytes
                    audioDuration = imported.durationSec
                }.onFailure { err ->
                    audioError = err.message ?: "Could not read audio file."
                }
            }
        }
    }

    val srtPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            srtError = null
            scope.launch {
                val (raw, parsed) = withContext(Dispatchers.IO) {
                    val text = runCatching {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                    }.getOrDefault("")
                    text to SrtParser.parse(text)
                }
                if (raw.isBlank()) {
                    srtError = "Selected SRT file is empty or unreadable."
                } else if (parsed.valid) {
                    srtText = parsed.promptSnippet
                    srtCueCount = parsed.cueCount
                } else {
                    srtError = parsed.error ?: "Invalid SRT file."
                }
            }
        }
    }

    val isSelected = audioPath.isNotBlank() && !loadingAudio
    val dashPhase = if (animEnabled && !isSelected) {
        val inf = rememberInfiniteTransition(label = "dz_dash")
        val p by inf.animateFloat(
            initialValue = 0f,
            targetValue = 28f,
            animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
            label = "dash"
        )
        p
    } else {
        0f
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RawColors.BgPrimary)
            .statusBarsPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Top bar with centered/right MIC_BOT
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircleActionButton(icon = RawIconType.BACK, onClick = onBack, size = 42.dp)
                RawPixelMascot(kind = PixelMascotKind.MIC_BOT, size = 40.dp)
            }

            KineticHeading(
                words = listOf("Add", "your", "voice"),
                style = RawTypography.headlineLarge
            )

            // Dashed Mint Dropzone (.dz)
            RawPressButton(
                onClick = { audioPicker.launch(arrayOf("audio/*", "application/ogg")) },
                modifier = Modifier.graphicsLayer { translationX = dzShake.value }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(if (isSelected) RawColors.MintSoft else RawColors.CardSurface)
                        .then(
                            if (isSelected) {
                                Modifier.border(1.dp, RawColors.MintBorder, RoundedCornerShape(22.dp))
                            } else Modifier
                        )
                ) {
                    if (!isSelected) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawRoundRect(
                                color = if (audioError != null) RawColors.ErrorCoral else RawColors.Mint.copy(alpha = 0.42f),
                                style = Stroke(
                                    width = 1.6.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f), dashPhase)
                                ),
                                cornerRadius = CornerRadius(22.dp.toPx())
                            )
                        }
                    }

                    val dropState = when {
                        loadingAudio -> 1
                        isSelected -> 2
                        else -> 0
                    }
                    AnimatedContent(
                        targetState = dropState,
                        transitionSpec = {
                            if (!animEnabled) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                            else (fadeIn(tween(200)) + scaleIn(initialScale = 0.96f, animationSpec = tween(200)))
                                .togetherWith(fadeOut(tween(140)) + scaleOut(targetScale = 0.96f, animationSpec = tween(140)))
                        },
                        label = "voice_dz_state"
                    ) { s ->
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            when (s) {
                                1 -> {
                                    CircularProgressIndicator(
                                        color = RawColors.Mint,
                                        modifier = Modifier.size(32.dp),
                                        strokeWidth = 2.5.dp
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("Reading voiceover...", style = RawTypography.titleMedium)
                                }
                                2 -> {
                                    Box(
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(CircleShape)
                                            .background(RawColors.Mint),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        RawIcon(RawIconType.CHECK, size = 26.dp, tint = RawColors.PrimaryBtnText)
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = audioName,
                                        style = RawTypography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = "${FormatHelpers.formatDuration(audioDuration)}  •  ${FormatHelpers.formatFileSize(audioSize)}",
                                        style = RawTypography.labelMedium
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Tap to change",
                                        fontSize = 12.5.sp,
                                        color = RawColors.TextMuted,
                                        textDecoration = TextDecoration.Underline
                                    )
                                }
                                else -> {
                                    Box(
                                        modifier = Modifier
                                            .size(58.dp)
                                            .clip(RoundedCornerShape(18.dp))
                                            .background(RawColors.MintSoft),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        RawIcon(RawIconType.SHARE, size = 28.dp, tint = RawColors.Mint)
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "Tap to pick voiceover",
                                        style = RawTypography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = "MP3, M4A, WAV, AAC  •  1s to 20m",
                                        style = RawTypography.labelMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            audioError?.let { msg ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(RawColors.ErrorSoft)
                        .border(1.dp, RawColors.ErrorBorder, RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Text(text = msg, style = RawTypography.bodyMedium, color = RawColors.ErrorCoral)
                }
            }

            // Optional SRT file card (.card.step)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(RawColors.CardSurface)
                    .border(1.dp, RawColors.Divider, RoundedCornerShape(22.dp))
                    .clickable { srtPicker.launch(arrayOf("*/*")) }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(RawColors.RaisedCard)
                        .border(1.dp, RawColors.Divider, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    RawIcon(RawIconType.SRT, size = 20.dp, tint = RawColors.TextPrimary)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Add SRT file (optional)",
                        style = RawTypography.titleMedium.copy(fontSize = 15.5.sp, fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (srtCueCount > 0) "$srtCueCount subtitle cues attached" else "Gives Cloud AI exact timing per word",
                        fontSize = 12.sp,
                        color = if (srtCueCount > 0) RawColors.Mint else RawColors.TextMuted
                    )
                }
                if (srtCueCount > 0) {
                    RawSecondaryButton(
                        text = "Remove",
                        height = 38.dp,
                        isDanger = true,
                        fillWidth = false,
                        onClick = {
                            srtText = ""
                            srtCueCount = 0
                        }
                    )
                } else {
                    CircleActionButton(
                        icon = RawIconType.PLUS,
                        size = 36.dp,
                        onClick = { srtPicker.launch(arrayOf("*/*")) }
                    )
                }
            }

            srtError?.let { msg ->
                Text(text = msg, style = RawTypography.bodyMedium, color = RawColors.ErrorCoral)
            }

            // Script / topic notes (.lbl + #nl + #nt)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Script / topic notes (optional)",
                    style = RawTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
                    color = RawColors.TextMuted,
                    modifier = Modifier.padding(start = 2.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${topic.length} chars • unlimited",
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        color = RawColors.TextMuted
                    )
                    RawSecondaryButton(
                        text = "Paste",
                        height = 34.dp,
                        leadingIcon = RawIconType.PASTE,
                        fillWidth = false,
                        onClick = {
                            scope.launch {
                                val txt = readClipboardText(context)
                                if (txt.isNotBlank()) {
                                    topic = txt
                                    forceRawEditTopic = false
                                    toastMsg = "Pasted ${txt.length} chars"
                                } else {
                                    toastMsg = "Clipboard is empty"
                                }
                            }
                        }
                    )
                }

                if (topic.length > LARGE_EDITOR_THRESHOLD && !forceRawEditTopic) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(RawColors.CardSurface)
                            .border(1.dp, RawColors.Divider, RoundedCornerShape(22.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val preview = topic.take(900) + "\n\n... (${topic.length - 1300} middle chars kept) ...\n\n" + topic.takeLast(400)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 110.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = preview,
                                style = RawTypography.labelMedium,
                                color = RawColors.TextMuted
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            RawSecondaryButton(
                                text = "Edit raw",
                                height = 40.dp,
                                onClick = { forceRawEditTopic = true },
                                modifier = Modifier.weight(1f)
                            )
                            RawSecondaryButton(
                                text = "Clear",
                                height = 40.dp,
                                isDanger = true,
                                onClick = {
                                    topic = ""
                                    forceRawEditTopic = false
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = topic,
                        onValueChange = { topic = it },
                        placeholder = { Text("Paste your script or topic here...", color = RawColors.TextMuted) },
                        textStyle = RawTypography.labelMedium.copy(color = RawColors.TextPrimary, fontSize = 12.sp),
                        minLines = 4,
                        maxLines = 7,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RawColors.MintFocus,
                            unfocusedBorderColor = RawColors.Divider,
                            focusedContainerColor = RawColors.RaisedCard,
                            unfocusedContainerColor = RawColors.RaisedCard,
                            cursorColor = RawColors.Mint
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Sticky gradient bottom footer (.stk)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, RawColors.BgPrimary.copy(alpha = 0.94f), RawColors.BgPrimary)
                    )
                )
                .navigationBarsPadding()
                .padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 18.dp)
        ) {
            BigPrimaryButton(
                text = if (savingVoice) "Saving..." else "Save voiceover",
                enabled = !savingVoice,
                onClick = {
                    if (audioPath.isBlank() || audioDuration < 1.0) {
                        toastMsg = "Pick a voiceover first"
                        if (animEnabled) {
                            scope.launch {
                                for (x in listOf(-10f, 10f, -6f, 0f)) {
                                    dzShake.animateTo(x, tween(55))
                                }
                            }
                        }
                        return@BigPrimaryButton
                    }
                    savingVoice = true
                    scope.launch {
                        val (storedTopic, storedSrt) = withContext(Dispatchers.IO) {
                            cacheManager.persistTopicText(project.id, topic) to
                                cacheManager.persistSrtText(project.id, srtText)
                        }
                        val baseTitle = audioName.substringBeforeLast('.').ifBlank { project.title }
                        onSave(
                            project.copy(
                                title = baseTitle,
                                audioUri = audioPath,
                                audioName = audioName,
                                audioSizeBytes = audioSize,
                                audioDurationSec = audioDuration,
                                srtText = storedSrt,
                                srtCueCount = srtCueCount,
                                topic = storedTopic
                            )
                        )
                    }
                }
            )
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
fun CopyPromptScreen(
    project: ProjectEntity,
    cacheManager: CacheManager,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val animEnabled = rememberAnimationsEnabled()

    val resolvedTopic = remember(project.topic) { cacheManager.resolveTopicText(project.topic) }
    val resolvedSrt = remember(project.srtText) { cacheManager.resolveSrtText(project.srtText) }
    val promptText = remember(project.audioName, project.audioDurationSec, resolvedTopic, resolvedSrt) {
        PromptGenerator.buildPrompt(
            fileName = project.audioName,
            durationSeconds = project.audioDurationSec.coerceAtLeast(1.0),
            topic = resolvedTopic,
            srtText = resolvedSrt
        )
    }
    val displayPrompt = remember(promptText) {
        if (promptText.length > 25_000) {
            promptText.take(12_000) +
                "\n\n... (${promptText.length - 14_000} middle chars included cleanly when you Copy or Share) ...\n\n" +
                promptText.takeLast(2_000)
        } else {
            promptText
        }
    }

    var copied by remember { mutableStateOf(false) }
    var toastMsg by remember { mutableStateOf<String?>(null) }

    var zipBusy by remember { mutableStateOf(false) }
    var zipSaved by remember { mutableStateOf(false) }
    var zipStatus by remember { mutableStateOf<String?>(null) }
    var zipError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(toastMsg) {
        if (toastMsg != null) {
            delay(1900)
            toastMsg = null
        }
    }

    val zipCreateLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { destUri ->
        if (destUri != null) {
            zipBusy = true
            zipError = null
            scope.launch {
                val res = cacheManager.saveStylePackToUri(destUri)
                zipBusy = false
                res.onSuccess {
                    zipSaved = true
                    zipStatus = "Saved ${CacheManager.STYLE_PACK_ASSET_NAME}"
                }.onFailure { err ->
                    zipError = err.message ?: "Could not save ZIP."
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RawColors.BgPrimary)
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircleActionButton(icon = RawIconType.BACK, onClick = onBack, size = 42.dp)
                RawPixelMascot(kind = PixelMascotKind.ZIP_BOT, size = 40.dp)
            }

            KineticHeading(
                words = listOf("Copy", "prompt"),
                style = RawTypography.headlineLarge
            )

            Text(
                text = "1) Attach Style Pack ZIP in Cloud AI • 2) Paste this prompt • 3) Answer 3 questions & copy HTML code.",
                style = RawTypography.bodyMedium
            )

            // Style Pack ZIP Card (.card.pad)
            val zipBorder by animateColorAsState(
                targetValue = if (zipSaved) RawColors.MintBorder else RawColors.Divider,
                animationSpec = tween(if (animEnabled) 220 else 0, easing = RawEasings.Morph),
                label = "zip_card_bd"
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(RawColors.CardSurface)
                    .border(1.dp, zipBorder, RoundedCornerShape(22.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(RawColors.RaisedCard)
                            .border(1.dp, RawColors.Divider, RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        RawIcon(RawIconType.ZIP, size = 20.dp, tint = RawColors.TextPrimary)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = CacheManager.STYLE_PACK_ASSET_NAME,
                            style = RawTypography.titleMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Style pack for Cloud AI",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp,
                            color = RawColors.TextMuted
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .background(if (zipSaved) RawColors.MintSoft else RawColors.RaisedCard)
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = if (zipSaved) "SAVED" else "552 KB",
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 9.5.sp,
                            letterSpacing = 0.7.sp,
                            color = if (zipSaved) RawColors.Mint else RawColors.TextMuted
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    RawSecondaryButton(
                        text = when {
                            zipBusy -> "Saving..."
                            zipSaved -> "Downloaded"
                            else -> "Download ZIP"
                        },
                        height = 42.dp,
                        enabled = !zipBusy,
                        leadingIcon = if (zipSaved) RawIconType.CHECK else RawIconType.DOWNLOAD,
                        onClick = {
                            zipError = null
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                zipBusy = true
                                scope.launch {
                                    val res = cacheManager.saveStylePackToDownloads()
                                    zipBusy = false
                                    res.onSuccess { msg ->
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        zipSaved = true
                                        zipStatus = msg
                                    }.onFailure {
                                        zipCreateLauncher.launch(CacheManager.STYLE_PACK_ASSET_NAME)
                                    }
                                }
                            } else {
                                zipCreateLauncher.launch(CacheManager.STYLE_PACK_ASSET_NAME)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )

                    RawSecondaryButton(
                        text = "Share ZIP",
                        height = 42.dp,
                        enabled = !zipBusy,
                        leadingIcon = RawIconType.SHARE,
                        onClick = {
                            zipError = null
                            scope.launch {
                                runCatching {
                                    val zipFile = cacheManager.prepareStylePackShareFile()
                                    val shareUri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        zipFile
                                    )
                                    val share = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/zip"
                                        putExtra(Intent.EXTRA_STREAM, shareUri)
                                        putExtra(Intent.EXTRA_SUBJECT, CacheManager.STYLE_PACK_ASSET_NAME)
                                        clipData = ClipData.newRawUri(CacheManager.STYLE_PACK_ASSET_NAME, shareUri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(share, "Share Style Pack ZIP"))
                                }.onFailure { err ->
                                    zipError = err.message ?: "Could not share ZIP."
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                zipStatus?.let { msg ->
                    Text(text = msg, style = RawTypography.labelMedium, color = RawColors.Mint)
                }
                zipError?.let { err ->
                    Text(text = err, style = RawTypography.labelMedium, color = RawColors.ErrorCoral)
                }
            }

            // #0E100E Monospace prompt container (.pre)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(290.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(RawColors.CodeSurface)
                    .border(1.dp, RawColors.Divider, RoundedCornerShape(18.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp)
            ) {
                Text(
                    text = displayPrompt,
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 11.5.sp,
                    lineHeight = 18.sp,
                    color = Color(0xFFB9C0B9)
                )
            }
        }

        // Sticky .row2 CTA bar (.stk)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, RawColors.BgPrimary.copy(alpha = 0.94f), RawColors.BgPrimary)
                    )
                )
                .navigationBarsPadding()
                .padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RawPressButton(
                    onClick = {
                        val copiedOk = runCatching {
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cb.setPrimaryClip(ClipData.newPlainText("raw_edit prompt", promptText))
                        }.isSuccess
                        if (copiedOk) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            copied = true
                            toastMsg = "Prompt copied"
                            scope.launch {
                                delay(2000)
                                copied = false
                            }
                        } else {
                            toastMsg = "Prompt too large — tap Share"
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(RawColors.Mint),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RawIcon(
                            type = if (copied) RawIconType.CHECK else RawIconType.COPY,
                            size = 18.dp,
                            tint = RawColors.PrimaryBtnText
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        SplitFlapText(
                            text = if (copied) "Copied" else "Copy prompt",
                            style = RawTypography.labelLarge,
                            color = RawColors.PrimaryBtnText
                        )
                    }
                }

                RawSecondaryButton(
                    text = "Share",
                    height = 52.dp,
                    leadingIcon = RawIconType.SHARE,
                    onClick = {
                        scope.launch {
                            if (promptText.length <= 120_000) {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, promptText)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share prompt"))
                            } else {
                                val txtFile = withContext(Dispatchers.IO) {
                                    File(cacheManager.sharedPacksDir, "raw_edit_prompt.txt").apply {
                                        writeText(promptText, Charsets.UTF_8)
                                    }
                                }
                                val shareUri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    txtFile
                                )
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_STREAM, shareUri)
                                    clipData = ClipData.newRawUri("raw_edit_prompt.txt", shareUri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share prompt file"))
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
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
fun CodePasteScreen(
    project: ProjectEntity,
    cacheManager: CacheManager,
    onBack: () -> Unit,
    onValidatedAndSaved: (ProjectEntity) -> Unit,
    onOpenKeyframeSheet: (ProjectEntity) -> Unit,
    onCreateNow: (ProjectEntity) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val animEnabled = rememberAnimationsEnabled()

    var codeText by remember(project.id) { mutableStateOf(cacheManager.resolveHtmlCode(project.code)) }
    var validating by remember { mutableStateOf(false) }
    var validationResult by remember { mutableStateOf<CodeValidationResult?>(null) }
    var forceRawEditLarge by remember { mutableStateOf(false) }
    var toastMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(toastMsg) {
        if (toastMsg != null) {
            delay(1900)
            toastMsg = null
        }
    }

    fun runValidation(text: String) {
        if (text.isBlank()) {
            validationResult = null
            return
        }
        validating = true
        scope.launch {
            val res = CodeValidator.validateFull(context, cacheManager, text)
            validationResult = res
            validating = false
        }
    }

    val htmlFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val loaded = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                    }.getOrDefault("")
                }
                if (loaded.isNotBlank()) {
                    codeText = loaded
                    forceRawEditLarge = false
                    runValidation(loaded)
                } else {
                    toastMsg = "Selected file is empty"
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (codeText.isNotBlank()) {
            runValidation(codeText)
        }
    }

    suspend fun buildUpdatedProject(res: CodeValidationResult): ProjectEntity = withContext(Dispatchers.IO) {
        val storedCode = cacheManager.persistHtmlCode(project.id, res.cleanedHtml)
        project.copy(
            code = storedCode,
            layersJson = "",
            codeDurationSec = res.durationSec,
            hasEditableLayers = false
        )
    }

    val valid = validationResult?.takeIf { it.isValid }
    val bottomClearance = if (valid != null) 168.dp else 112.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RawColors.BgPrimary)
            .statusBarsPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, bottom = bottomClearance),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircleActionButton(icon = RawIconType.BACK, onClick = onBack, size = 42.dp)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${codeText.length} chars (unlimited)",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp,
                        color = RawColors.TextMuted
                    )
                    RawPixelMascot(kind = PixelMascotKind.CODE_BOT, size = 38.dp)
                }
            }

            KineticHeading(
                words = listOf("Have", "a", "code?"),
                style = RawTypography.headlineLarge
            )

            // Compact horizontal action chips (.btn.g.sm)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RawSecondaryButton(
                    text = "Paste",
                    height = 40.dp,
                    leadingIcon = RawIconType.PASTE,
                    fillWidth = false,
                    onClick = {
                        scope.launch {
                            val full = readClipboardText(context)
                            if (full.isNotBlank()) {
                                codeText = full
                                forceRawEditLarge = false
                                runValidation(full)
                            } else {
                                toastMsg = "Clipboard empty — copy code or pick .html file"
                            }
                        }
                    }
                )
                RawSecondaryButton(
                    text = "Pick .html file",
                    height = 40.dp,
                    leadingIcon = RawIconType.FILE,
                    fillWidth = false,
                    onClick = { htmlFilePicker.launch(arrayOf("text/html", "text/plain", "*/*")) }
                )
                RawSecondaryButton(
                    text = "Load 6s sample",
                    height = 40.dp,
                    leadingIcon = RawIconType.FILM,
                    fillWidth = false,
                    onClick = {
                        scope.launch {
                            val sample = withContext(Dispatchers.IO) {
                                runCatching {
                                    context.assets.open("sample.html").bufferedReader().use { it.readText() }
                                }.getOrDefault("")
                            }
                            if (sample.isNotBlank()) {
                                codeText = sample
                                forceRawEditLarge = false
                                runValidation(sample)
                            }
                        }
                    }
                )
                if (codeText.isNotEmpty()) {
                    RawSecondaryButton(
                        text = "Clear",
                        height = 40.dp,
                        leadingIcon = RawIconType.CLOSE,
                        fillWidth = false,
                        onClick = {
                            codeText = ""
                            forceRawEditLarge = false
                            validationResult = null
                        }
                    )
                }
            }

            // Code box / collapsed card for large payloads
            if (codeText.length > LARGE_EDITOR_THRESHOLD && !forceRawEditLarge) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(RawColors.CardSurface)
                        .border(1.dp, RawColors.Divider, RoundedCornerShape(22.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "${codeText.count { it == '\n' } + 1} lines  •  ${codeText.length} chars (collapsed)",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.5.sp,
                        color = RawColors.Mint
                    )
                    val previewStr = codeText.take(1200) + "\n\n... (${codeText.length - 1700} middle chars kept intact) ...\n\n" + codeText.takeLast(500)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = previewStr,
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            color = RawColors.TextMuted
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RawSecondaryButton(
                            text = "Show editor",
                            height = 42.dp,
                            onClick = { forceRawEditLarge = true },
                            modifier = Modifier.weight(1f)
                        )
                        RawSecondaryButton(
                            text = "Clear",
                            height = 42.dp,
                            isDanger = true,
                            onClick = {
                                codeText = ""
                                forceRawEditLarge = false
                                validationResult = null
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            } else {
                OutlinedTextField(
                    value = codeText,
                    onValueChange = {
                        codeText = it
                        validationResult = null
                    },
                    placeholder = { Text("Paste HTML code here...", color = RawColors.TextMuted) },
                    textStyle = RawTypography.labelMedium.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 12.sp,
                        lineHeight = 19.sp,
                        color = RawColors.TextPrimary
                    ),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RawColors.MintFocus,
                        unfocusedBorderColor = RawColors.Divider,
                        focusedContainerColor = RawColors.RaisedCard,
                        unfocusedContainerColor = RawColors.RaisedCard,
                        cursorColor = RawColors.Mint
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                )
            }

            // Morphing Testing / Valid / Invalid Banners (.ban2)
            val statusKey = when {
                validating -> "testing"
                validationResult != null -> "res_${validationResult!!.isValid}_${validationResult!!.summaryMessage}"
                else -> "idle"
            }
            AnimatedContent(
                targetState = statusKey,
                transitionSpec = {
                    if (!animEnabled) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                    else (fadeIn(tween(200, easing = RawEasings.Morph)) + scaleIn(initialScale = 0.97f, animationSpec = tween(200)))
                        .togetherWith(fadeOut(tween(140)) + scaleOut(targetScale = 0.97f, animationSpec = tween(140)))
                },
                label = "code_ban_morph"
            ) { target ->
                when {
                    target == "testing" -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(RawColors.RaisedCard)
                                .border(1.dp, RawColors.Divider, RoundedCornerShape(16.dp))
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                color = RawColors.Mint,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Testing code in offline sandbox...",
                                style = RawTypography.bodyLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }
                    target != "idle" && validationResult != null -> {
                        val res = validationResult!!
                        val bg = if (res.isValid) RawColors.MintSoft else RawColors.ErrorSoft
                        val bd = if (res.isValid) RawColors.MintBorder else RawColors.ErrorBorder
                        val fg = if (res.isValid) RawColors.Mint else RawColors.ErrorCoral
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(bg)
                                .border(1.dp, bd, RoundedCornerShape(16.dp))
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            RawIcon(
                                type = if (res.isValid) RawIconType.CHECK else RawIconType.WARN,
                                size = 20.dp,
                                tint = fg
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = res.summaryMessage,
                                    style = RawTypography.bodyLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                                    color = fg
                                )
                                res.warning?.let { w ->
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = w,
                                        fontSize = 12.sp,
                                        color = fg.copy(alpha = 0.78f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Sticky gradient bottom actions (.stk)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, RawColors.BgPrimary.copy(alpha = 0.94f), RawColors.BgPrimary)
                    )
                )
                .navigationBarsPadding()
                .padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 18.dp)
        ) {
            if (valid != null) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    RawPressButton(
                        onClick = {
                            scope.launch {
                                onOpenKeyframeSheet(buildUpdatedProject(valid))
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .clip(RoundedCornerShape(99.dp))
                                .background(RawColors.RaisedCard)
                                .border(1.dp, RawColors.DividerStrong, RoundedCornerShape(99.dp)),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RawPixelMascot(
                                kind = PixelMascotKind.LENS_BOT,
                                size = 24.dp,
                                interactive = false,
                                noBob = true,
                                noBlink = true
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Contact sheet (auto-fit & PDF)",
                                style = RawTypography.labelLarge.copy(fontSize = 13.5.sp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RawSecondaryButton(
                            text = "Save",
                            height = 52.dp,
                            onClick = {
                                scope.launch {
                                    onValidatedAndSaved(buildUpdatedProject(valid))
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                        BigPrimaryButton(
                            text = if (project.audioUri.isNotBlank()) "Create video" else "Use code",
                            onClick = {
                                scope.launch {
                                    val updated = buildUpdatedProject(valid)
                                    if (project.audioUri.isNotBlank()) {
                                        onCreateNow(updated)
                                    } else {
                                        onValidatedAndSaved(updated)
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            } else {
                BigPrimaryButton(
                    text = if (validating) "Testing..." else "Check code",
                    enabled = !validating,
                    onClick = {
                        if (codeText.isBlank()) {
                            toastMsg = "Paste some code first"
                        } else {
                            runValidation(codeText)
                        }
                    }
                )
            }
        }

        RawToastBanner(
            message = toastMsg,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = if (valid != null) 144.dp else 86.dp)
        )
    }
}
