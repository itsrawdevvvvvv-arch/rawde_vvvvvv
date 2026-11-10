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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
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
import com.rawedit.app.ui.icons.RawIcon
import com.rawedit.app.ui.icons.RawIconType
import com.rawedit.app.ui.motion.KineticHeading
import com.rawedit.app.ui.motion.RawEasings
import com.rawedit.app.ui.motion.RawPressButton
import com.rawedit.app.ui.motion.SplitFlapText
import com.rawedit.app.ui.motion.rememberAnimationsEnabled
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RawColors.BgPrimary)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(20.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircleActionButton(icon = RawIconType.BACK, onClick = onBack)

            KineticHeading(
                words = listOf("Add", "your", "voice"),
                style = RawTypography.headlineLarge
            )

            // Dashed drop area for MP3/M4A/WAV/AAC
            RawPressButton(
                onClick = {
                    audioPicker.launch(arrayOf("audio/*", "application/ogg"))
                }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(156.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(RawColors.HeroSurface)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawRoundRect(
                            color = if (audioError != null) RawColors.ErrorCoral else RawColors.PillGrey,
                            style = Stroke(
                                width = 2.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f), 0f)
                            ),
                            cornerRadius = CornerRadius(24.dp.toPx())
                        )
                    }
                    val dropState = when {
                        loadingAudio -> 1
                        audioPath.isNotBlank() -> 2
                        else -> 0
                    }
                    AnimatedContent(
                        targetState = dropState,
                        transitionSpec = {
                            (fadeIn(tween(220, easing = RawEasings.Morph)) +
                                scaleIn(initialScale = 0.96f, animationSpec = tween(220, easing = RawEasings.Morph)))
                                .togetherWith(
                                    fadeOut(tween(150)) +
                                        scaleOut(targetScale = 0.96f, animationSpec = tween(150))
                                )
                        },
                        label = "voice_drop_morph"
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
                                        color = RawColors.TextPrimary,
                                        modifier = Modifier.size(30.dp),
                                        strokeWidth = 2.5.dp
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text("Reading voiceover...", style = RawTypography.bodyMedium)
                                }
                                2 -> {
                                    RawIcon(RawIconType.CHECK, size = 28.dp, tint = RawColors.SuccessMint)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(audioName, style = RawTypography.titleMedium, maxLines = 1)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "${FormatHelpers.formatDuration(audioDuration)}  •  ${FormatHelpers.formatFileSize(audioSize)}  •  Tap to change",
                                        style = RawTypography.labelMedium
                                    )
                                }
                                else -> {
                                    RawIcon(RawIconType.MIC, size = 28.dp, tint = RawColors.TextPrimary)
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text("Tap to pick voiceover", style = RawTypography.titleMedium)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("MP3, M4A, WAV, AAC  •  1s to 20m", style = RawTypography.bodyMedium)
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
                        .background(RawColors.ErrorCoral.copy(alpha = 0.14f))
                        .border(1.dp, RawColors.ErrorCoral.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Text(text = msg, style = RawTypography.bodyMedium, color = RawColors.ErrorCoral)
                }
            }

            // Optional SRT button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(RawColors.CardSurface)
                    .border(1.dp, RawColors.Divider, RoundedCornerShape(20.dp))
                    .clickable { srtPicker.launch(arrayOf("*/*")) }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Add SRT file (optional)", style = RawTypography.titleMedium)
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = if (srtCueCount > 0) "$srtCueCount subtitle cues attached" else "Gives Cloud AI exact timing per word",
                        style = RawTypography.bodyMedium,
                        color = if (srtCueCount > 0) RawColors.SuccessMint else RawColors.TextMuted
                    )
                }
                if (srtCueCount > 0) {
                    Text(
                        text = "Remove",
                        style = RawTypography.labelMedium,
                        color = RawColors.ErrorCoral,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                srtText = ""
                                srtCueCount = 0
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                } else {
                    RawIcon(RawIconType.PLUS, size = 20.dp)
                }
            }

            srtError?.let { msg ->
                Text(text = msg, style = RawTypography.bodyMedium, color = RawColors.ErrorCoral)
            }

            // Unlimited voiceover script / topic / notes input
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Script / topic notes (optional)",
                        style = RawTypography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${topic.length} chars • unlimited",
                            style = RawTypography.labelSmall,
                            color = RawColors.TextMuted
                        )
                        Text(
                            text = "Paste",
                            style = RawTypography.labelSmall,
                            color = RawColors.PrimaryBtnText,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(RawColors.PrimaryBtnBg)
                                .clickable {
                                    scope.launch {
                                        val txt = readClipboardText(context)
                                        if (txt.isNotBlank()) {
                                            topic = txt
                                            forceRawEditTopic = false
                                        }
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (topic.length > LARGE_EDITOR_THRESHOLD && !forceRawEditTopic) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(RawColors.CardSurface)
                            .border(1.dp, RawColors.Divider, RoundedCornerShape(18.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${topic.length} chars stored (unlimited)",
                                style = RawTypography.labelMedium,
                                color = RawColors.SuccessMint
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Edit raw",
                                    style = RawTypography.labelSmall,
                                    color = RawColors.TextMuted,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(RawColors.RaisedCard)
                                        .clickable { forceRawEditTopic = true }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                                Text(
                                    text = "Clear",
                                    style = RawTypography.labelSmall,
                                    color = RawColors.ErrorCoral,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(RawColors.RaisedCard)
                                        .clickable {
                                            topic = ""
                                            forceRawEditTopic = false
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        val preview = topic.take(900) + "\n\n... (${topic.length - 1300} middle chars kept) ...\n\n" + topic.takeLast(400)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = preview,
                                style = RawTypography.labelMedium,
                                color = RawColors.TextMuted
                            )
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = topic,
                        onValueChange = { topic = it },
                        placeholder = {
                            Text(
                                "Paste full voiceover script, topic, hook idea, or words to emphasise (unlimited)",
                                color = RawColors.TextMuted
                            )
                        },
                        minLines = 3,
                        maxLines = 7,
                        shape = RoundedCornerShape(18.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RawColors.TextPrimary,
                            unfocusedBorderColor = RawColors.Divider,
                            focusedContainerColor = RawColors.CardSurface,
                            unfocusedContainerColor = RawColors.CardSurface,
                            focusedTextColor = RawColors.TextPrimary,
                            unfocusedTextColor = RawColors.TextPrimary,
                            cursorColor = RawColors.TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        BigPrimaryButton(
            text = if (savingVoice) "Saving..." else "Save voiceover",
            enabled = audioPath.isNotBlank() && audioDuration >= 1.0 && !savingVoice,
            leadingIcon = RawIconType.CHECK,
            onClick = {
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
    var promptNotice by remember { mutableStateOf<String?>(null) }

    var zipBusy by remember { mutableStateOf(false) }
    var zipSaved by remember { mutableStateOf(false) }
    var zipStatus by remember { mutableStateOf<String?>(null) }
    var zipError by remember { mutableStateOf<String?>(null) }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RawColors.BgPrimary)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircleActionButton(icon = RawIconType.BACK, onClick = onBack)

        KineticHeading(
            words = listOf("Copy", "prompt"),
            style = RawTypography.headlineLarge
        )

        Text(
            text = "1) Attach the Style Pack ZIP in Cloud AI  •  2) Paste this prompt  •  3) Answer 3 questions & copy the HTML code.",
            style = RawTypography.bodyMedium
        )

        // Style Pack ZIP Card (kinetic-explainer-style.zip)
        val zipBorder by animateColorAsState(
            targetValue = if (zipSaved) RawColors.SuccessMint.copy(alpha = 0.55f) else RawColors.Divider,
            animationSpec = tween(if (animEnabled) 240 else 0, easing = RawEasings.Morph),
            label = "zip_card_border"
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(RawColors.HeroSurface)
                .border(1.dp, zipBorder, RoundedCornerShape(20.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = CacheManager.STYLE_PACK_ASSET_NAME,
                        style = RawTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Upload this ZIP with the prompt for better Cloud AI video design",
                        style = RawTypography.labelSmall,
                        color = RawColors.TextMuted
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (zipSaved) RawColors.SuccessMint.copy(alpha = 0.16f) else RawColors.RaisedCard)
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (zipSaved) "Saved" else "552 KB",
                        style = RawTypography.labelSmall,
                        color = if (zipSaved) RawColors.SuccessMint else RawColors.TextPrimary
                    )
                }
            }

            zipStatus?.let { msg ->
                Text(text = msg, style = RawTypography.labelSmall, color = RawColors.SuccessMint)
            }
            zipError?.let { err ->
                Text(text = err, style = RawTypography.labelSmall, color = RawColors.ErrorCoral)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val dlBg by animateColorAsState(
                    targetValue = if (zipSaved) RawColors.SuccessMint else RawColors.PrimaryBtnBg,
                    animationSpec = tween(if (animEnabled) 220 else 0, easing = RawEasings.Morph),
                    label = "zip_dl_bg"
                )
                RawPressButton(
                    enabled = !zipBusy,
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
                                    // Fallback to SAF picker if MediaStore Downloads is restricted on OEM ROM
                                    zipCreateLauncher.launch(CacheManager.STYLE_PACK_ASSET_NAME)
                                }
                            }
                        } else {
                            // Android 9 (API 28): standard SAF Save As picker needs zero storage permissions
                            zipCreateLauncher.launch(CacheManager.STYLE_PACK_ASSET_NAME)
                        }
                    },
                    modifier = Modifier.weight(1.25f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .clip(RoundedCornerShape(23.dp))
                            .background(dlBg),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RawIcon(
                            type = if (zipSaved) RawIconType.CHECK else RawIconType.DOWNLOAD,
                            size = 18.dp,
                            tint = RawColors.PrimaryBtnText
                        )
                        Spacer(modifier = Modifier.width(7.dp))
                        Text(
                            text = when {
                                zipBusy -> "Saving..."
                                zipSaved -> "Downloaded"
                                else -> "Download ZIP"
                            },
                            style = RawTypography.labelLarge,
                            color = RawColors.PrimaryBtnText
                        )
                    }
                }

                RawPressButton(
                    enabled = !zipBusy,
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
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .clip(RoundedCornerShape(23.dp))
                            .background(RawColors.RaisedCard),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RawIcon(type = RawIconType.SHARE, size = 17.dp)
                        Spacer(modifier = Modifier.width(7.dp))
                        Text("Share ZIP", style = RawTypography.labelLarge)
                    }
                }
            }
        }

        // Prompt preview box
        Box(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 120.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(RawColors.CardSurface)
                .border(1.dp, RawColors.Divider, RoundedCornerShape(20.dp))
                .verticalScroll(rememberScrollState())
                .padding(14.dp)
        ) {
            Text(
                text = displayPrompt,
                fontFamily = JetBrainsMonoFamily,
                fontSize = 11.5.sp,
                lineHeight = 17.sp,
                color = RawColors.TextPrimary
            )
        }

        promptNotice?.let { msg ->
            Text(text = msg, style = RawTypography.labelSmall, color = RawColors.ErrorCoral)
        }

        // Copy prompt + Share prompt row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val copyBg by animateColorAsState(
                targetValue = if (copied) RawColors.SuccessMint else RawColors.PrimaryBtnBg,
                animationSpec = tween(220, easing = RawEasings.Morph),
                label = "copy_btn_bg"
            )
            RawPressButton(
                onClick = {
                    promptNotice = null
                    val copiedOk = runCatching {
                        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cb.setPrimaryClip(ClipData.newPlainText("raw_edit prompt", promptText))
                    }.isSuccess
                    if (copiedOk) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        copied = true
                        scope.launch {
                            delay(2000)
                            copied = false
                        }
                    } else {
                        promptNotice = "Prompt is too large for clipboard — tap Share instead."
                    }
                },
                modifier = Modifier.weight(1.4f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(copyBg),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RawIcon(
                        type = if (copied) RawIconType.CHECK else RawIconType.COPY,
                        size = 20.dp,
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

            RawPressButton(
                onClick = {
                    promptNotice = null
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
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(RawColors.RaisedCard),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RawIcon(type = RawIconType.SHARE, size = 20.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Share", style = RawTypography.labelLarge)
                }
            }
        }
    }
}

@Composable
fun CodePasteScreen(
    project: ProjectEntity,
    cacheManager: CacheManager,
    onBack: () -> Unit,
    onValidatedAndSaved: (ProjectEntity) -> Unit,
    onCreateNow: (ProjectEntity) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var codeText by remember(project.id) { mutableStateOf(cacheManager.resolveHtmlCode(project.code)) }
    var validating by remember { mutableStateOf(false) }
    var validationResult by remember { mutableStateOf<CodeValidationResult?>(null) }
    var forceRawEditLarge by remember { mutableStateOf(false) }
    var clipboardNotice by remember { mutableStateOf<String?>(null) }

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
            clipboardNotice = null
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
                    clipboardNotice = "Selected file is empty or unreadable."
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (codeText.isNotBlank()) {
            runValidation(codeText)
        }
    }

    fun buildUpdatedProject(res: CodeValidationResult): ProjectEntity {
        val storedCode = cacheManager.persistHtmlCode(project.id, res.cleanedHtml)
        return project.copy(
            code = storedCode,
            layersJson = "",
            codeDurationSec = res.durationSec,
            hasEditableLayers = false
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RawColors.BgPrimary)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleActionButton(icon = RawIconType.BACK, onClick = onBack)
            Text(
                text = "${codeText.length} chars (unlimited)",
                style = RawTypography.labelMedium
            )
        }

        KineticHeading(
            words = listOf("Have", "a", "code?"),
            style = RawTypography.headlineLarge
        )

        // Action row: Paste + Pick .html + Load sample + Clear
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            RawPressButton(
                onClick = {
                    clipboardNotice = null
                    scope.launch {
                        val full = readClipboardText(context)
                        if (full.isNotBlank()) {
                            codeText = full
                            forceRawEditLarge = false
                            runValidation(full)
                        } else {
                            clipboardNotice = "Clipboard is empty — copy HTML code first or tap Pick .html file."
                            delay(3000)
                            clipboardNotice = null
                        }
                    }
                }
            ) {
                Row(
                    modifier = Modifier
                        .height(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(RawColors.PrimaryBtnBg)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RawIcon(RawIconType.PASTE, size = 16.dp, tint = RawColors.PrimaryBtnText)
                    Text("Paste", style = RawTypography.labelLarge, color = RawColors.PrimaryBtnText)
                }
            }

            RawPressButton(
                onClick = {
                    htmlFilePicker.launch(arrayOf("text/html", "text/plain", "*/*"))
                }
            ) {
                Box(
                    modifier = Modifier
                        .height(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(RawColors.RaisedCard)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Pick .html file", style = RawTypography.labelLarge)
                }
            }

            RawPressButton(
                onClick = {
                    clipboardNotice = null
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
            ) {
                Box(
                    modifier = Modifier
                        .height(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(RawColors.RaisedCard)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Load 6s sample", style = RawTypography.labelLarge)
                }
            }

            if (codeText.isNotEmpty()) {
                RawPressButton(
                    onClick = {
                        codeText = ""
                        forceRawEditLarge = false
                        validationResult = null
                        clipboardNotice = null
                    }
                ) {
                    Box(
                        modifier = Modifier
                            .height(44.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(RawColors.CardSurface)
                            .border(1.dp, RawColors.Divider, RoundedCornerShape(22.dp))
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Clear", style = RawTypography.labelLarge, color = RawColors.TextMuted)
                    }
                }
            }
        }

        clipboardNotice?.let { msg ->
            Text(text = msg, style = RawTypography.labelSmall, color = RawColors.TextMuted)
        }

        // Big unlimited paste box (uses lightweight head/tail preview above 15k chars so Android 9 never lags)
        if (codeText.length > LARGE_EDITOR_THRESHOLD && !forceRawEditLarge) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(RawColors.CardSurface)
                    .border(1.dp, RawColors.Divider, RoundedCornerShape(20.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${codeText.length} chars loaded (unlimited)",
                        style = RawTypography.labelLarge,
                        color = RawColors.SuccessMint
                    )
                    Text(
                        text = "Edit raw text",
                        style = RawTypography.labelSmall,
                        color = RawColors.TextMuted,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(RawColors.RaisedCard)
                            .clickable { forceRawEditLarge = true }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
                val previewStr = codeText.take(1400) + "\n\n... (${codeText.length - 2000} middle chars kept intact) ...\n\n" + codeText.takeLast(600)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
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
            }
        } else {
            OutlinedTextField(
                value = codeText,
                onValueChange = {
                    codeText = it
                    validationResult = null
                },
                placeholder = {
                    Text("Paste HTML code from Cloud AI here (unlimited length)...", color = RawColors.TextMuted)
                },
                textStyle = RawTypography.labelMedium.copy(
                    fontFamily = JetBrainsMonoFamily,
                    color = RawColors.TextPrimary
                ),
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RawColors.TextPrimary,
                    unfocusedBorderColor = RawColors.Divider,
                    focusedContainerColor = RawColors.CardSurface,
                    unfocusedContainerColor = RawColors.CardSurface,
                    cursorColor = RawColors.TextPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }

        val animEnabled = rememberAnimationsEnabled()
        val statusState = when {
            validating -> "validating"
            validationResult != null -> "result_${validationResult!!.isValid}_${validationResult!!.summaryMessage}"
            else -> "empty"
        }
        AnimatedContent(
            targetState = statusState,
            transitionSpec = {
                if (!animEnabled) {
                    fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                } else {
                    (fadeIn(tween(220, easing = RawEasings.Morph)) +
                        scaleIn(initialScale = 0.97f, animationSpec = tween(220, easing = RawEasings.Morph)))
                        .togetherWith(
                            fadeOut(tween(150)) +
                                scaleOut(targetScale = 0.97f, animationSpec = tween(150))
                        )
                }
            },
            label = "code_validation_morph"
        ) { target ->
            if (target == "validating") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(RawColors.HeroSurface)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        color = RawColors.TextPrimary,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Text("Testing code in offline sandbox...", style = RawTypography.bodyMedium)
                }
            } else {
                val res = validationResult
                if (res != null && target != "empty") {
                    val accent = if (res.isValid) RawColors.SuccessMint else RawColors.ErrorCoral
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(accent.copy(alpha = 0.13f))
                            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                            .padding(14.dp)
                    ) {
                        Text(text = res.summaryMessage, style = RawTypography.bodyMedium, color = accent)
                        res.warning?.let { warn ->
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = warn, style = RawTypography.labelSmall, color = RawColors.TextMuted)
                        }
                    }
                }
            }
        }

        val valid = validationResult?.takeIf { it.isValid }
        AnimatedContent(
            targetState = valid != null,
            transitionSpec = {
                if (!animEnabled) {
                    fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                } else {
                    (fadeIn(tween(220, easing = RawEasings.Morph)) +
                        scaleIn(initialScale = 0.96f, animationSpec = tween(220, easing = RawEasings.Morph)))
                        .togetherWith(
                            fadeOut(tween(140)) +
                                scaleOut(targetScale = 0.96f, animationSpec = tween(140))
                        )
                }
            },
            label = "code_cta_morph"
        ) { isValid ->
            val currentValid = valid
            if (isValid && currentValid != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    RawPressButton(
                        onClick = { onValidatedAndSaved(buildUpdatedProject(currentValid)) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clip(RoundedCornerShape(28.dp))
                                .background(RawColors.RaisedCard),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Save", style = RawTypography.labelLarge)
                        }
                    }
                    RawPressButton(
                        onClick = {
                            if (project.audioUri.isNotBlank()) {
                                onCreateNow(buildUpdatedProject(currentValid))
                            } else {
                                onValidatedAndSaved(buildUpdatedProject(currentValid))
                            }
                        },
                        modifier = Modifier.weight(1.4f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clip(RoundedCornerShape(28.dp))
                                .background(RawColors.PrimaryBtnBg),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RawIcon(RawIconType.PLAY, size = 18.dp, tint = RawColors.PrimaryBtnText)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (project.audioUri.isNotBlank()) "Create video" else "Use code",
                                style = RawTypography.labelLarge,
                                color = RawColors.PrimaryBtnText
                            )
                        }
                    }
                }
            } else {
                BigPrimaryButton(
                    text = "Check code",
                    enabled = codeText.isNotBlank() && !validating,
                    leadingIcon = RawIconType.CHECK,
                    onClick = { runValidation(codeText) }
                )
            }
        }
    }
}
