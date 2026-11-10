package com.rawedit.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rawedit.app.data.CacheManager
import com.rawedit.app.data.local.ProjectEntity
import com.rawedit.app.data.model.LayerJson
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
import com.rawedit.app.ui.motion.RawPressButton
import com.rawedit.app.ui.motion.SplitFlapText
import com.rawedit.app.ui.theme.JetBrainsMonoFamily
import com.rawedit.app.ui.theme.RawColors
import com.rawedit.app.ui.theme.RawTypography
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

    var srtText by remember(project.id) { mutableStateOf(project.srtText) }
    var srtCueCount by remember(project.id) { mutableStateOf(project.srtCueCount) }
    var srtError by remember { mutableStateOf<String?>(null) }

    var topic by remember(project.id) { mutableStateOf(project.topic) }

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
            val raw = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
            }.getOrDefault("")
            val parsed = SrtParser.parse(raw)
            if (parsed.valid) {
                srtText = parsed.promptSnippet
                srtCueCount = parsed.cueCount
            } else {
                srtError = parsed.error ?: "Invalid SRT file."
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
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (loadingAudio) {
                            CircularProgressIndicator(
                                color = RawColors.TextPrimary,
                                modifier = Modifier.size(30.dp),
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("Reading voiceover...", style = RawTypography.bodyMedium)
                        } else if (audioPath.isNotBlank()) {
                            RawIcon(RawIconType.CHECK, size = 28.dp, tint = RawColors.SuccessMint)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(audioName, style = RawTypography.titleMedium, maxLines = 1)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "${FormatHelpers.formatDuration(audioDuration)}  •  ${FormatHelpers.formatFileSize(audioSize)}  •  Tap to change",
                                style = RawTypography.labelMedium
                            )
                        } else {
                            RawIcon(RawIconType.MIC, size = 28.dp, tint = RawColors.TextPrimary)
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("Tap to pick voiceover", style = RawTypography.titleMedium)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("MP3, M4A, WAV, AAC  •  1s to 20m", style = RawTypography.bodyMedium)
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
                        text = if (srtCueCount > 0) "$srtCueCount subtitle cues attached" else "Gives Claude exact timing per word",
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

            // Topic text field (up to 1000 chars)
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("What is this video about? (optional)", style = RawTypography.titleMedium)
                    Text("${topic.length}/1000", style = RawTypography.labelSmall)
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = topic,
                    onValueChange = { if (it.length <= 1000) topic = it },
                    placeholder = {
                        Text(
                            "Topic, key hook idea, or words to emphasise",
                            color = RawColors.TextMuted
                        )
                    },
                    minLines = 3,
                    maxLines = 5,
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

        Spacer(modifier = Modifier.height(12.dp))
        BigPrimaryButton(
            text = "Save voiceover",
            enabled = audioPath.isNotBlank() && audioDuration >= 1.0,
            leadingIcon = RawIconType.CHECK,
            onClick = {
                val baseTitle = audioName.substringBeforeLast('.').ifBlank { project.title }
                onSave(
                    project.copy(
                        title = baseTitle,
                        audioUri = audioPath,
                        audioName = audioName,
                        audioSizeBytes = audioSize,
                        audioDurationSec = audioDuration,
                        srtText = srtText,
                        srtCueCount = srtCueCount,
                        topic = topic.trim()
                    )
                )
            }
        )
    }
}

@Composable
fun CopyPromptScreen(
    project: ProjectEntity,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val promptText = remember(project) {
        PromptGenerator.buildPrompt(
            fileName = project.audioName,
            durationSeconds = project.audioDurationSec.coerceAtLeast(1.0),
            topic = project.topic,
            srtText = project.srtText
        )
    }
    var copied by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RawColors.BgPrimary)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        CircleActionButton(icon = RawIconType.BACK, onClick = onBack)

        KineticHeading(
            words = listOf("Copy", "prompt"),
            style = RawTypography.headlineLarge
        )

        Text(
            text = "Paste this into Claude. It will ask you 3 quick questions, then give you the code.",
            style = RawTypography.bodyMedium
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(RawColors.CardSurface)
                .border(1.dp, RawColors.Divider, RoundedCornerShape(22.dp))
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = promptText,
                fontFamily = JetBrainsMonoFamily,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = RawColors.TextPrimary
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RawPressButton(
                onClick = {
                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cb.setPrimaryClip(ClipData.newPlainText("raw_edit prompt", promptText))
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    copied = true
                    scope.launch {
                        delay(2000)
                        copied = false
                    }
                },
                modifier = Modifier.weight(1.4f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .clip(RoundedCornerShape(29.dp))
                        .background(if (copied) RawColors.SuccessMint else RawColors.PrimaryBtnBg),
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
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, promptText)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share prompt"))
                },
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .clip(RoundedCornerShape(29.dp))
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

    var codeText by remember(project.id) { mutableStateOf(project.code) }
    var validating by remember { mutableStateOf(false) }
    var validationResult by remember { mutableStateOf<CodeValidationResult?>(null) }

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

    LaunchedEffect(Unit) {
        if (codeText.isNotBlank()) {
            runValidation(codeText)
        }
    }

    fun buildUpdatedProject(res: CodeValidationResult): ProjectEntity {
        val layersEncoded = if (res.hasEditableLayers) LayerJson.encode(res.layers) else ""
        return project.copy(
            code = res.cleanedHtml,
            layersJson = layersEncoded,
            codeDurationSec = res.durationSec,
            hasEditableLayers = res.hasEditableLayers
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
                text = "${codeText.length} chars",
                style = RawTypography.labelMedium
            )
        }

        KineticHeading(
            words = listOf("Have", "a", "code?"),
            style = RawTypography.headlineLarge
        )

        // Action row: Paste + Load sample + Clear
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            RawPressButton(
                onClick = {
                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val item = cb.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                    if (item.isNotBlank()) {
                        codeText = item
                        runValidation(item)
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
                    val sample = runCatching {
                        context.assets.open("sample.html").bufferedReader().use { it.readText() }
                    }.getOrDefault("")
                    if (sample.isNotBlank()) {
                        codeText = sample
                        runValidation(sample)
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
                        validationResult = null
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

        // Big paste text box
        OutlinedTextField(
            value = codeText,
            onValueChange = {
                codeText = it
                validationResult = null
            },
            placeholder = {
                Text("Paste HTML code from Claude here...", color = RawColors.TextMuted)
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

        // Validation status box
        if (validating) {
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
            validationResult?.let { res ->
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

        val valid = validationResult?.takeIf { it.isValid }
        if (valid != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RawPressButton(
                    onClick = { onValidatedAndSaved(buildUpdatedProject(valid)) },
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
                            onCreateNow(buildUpdatedProject(valid))
                        } else {
                            onValidatedAndSaved(buildUpdatedProject(valid))
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
