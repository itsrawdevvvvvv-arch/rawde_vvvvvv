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
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.rawedit.app.data.CacheManager
import com.rawedit.app.data.local.LocalAssetEntity
import com.rawedit.app.data.local.ProjectEntity
import com.rawedit.app.domain.CodeValidationResult
import com.rawedit.app.domain.CodeValidator
import com.rawedit.app.domain.GroqClient
import com.rawedit.app.domain.PaletteColorMath
import com.rawedit.app.domain.PaletteState
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
import com.rawedit.app.ui.motion.FeatheredScrollEdges
import com.rawedit.app.ui.motion.KineticHeading
import com.rawedit.app.ui.motion.RawEasings
import com.rawedit.app.ui.motion.RawPressButton
import com.rawedit.app.ui.motion.SplitFlapText
import com.rawedit.app.ui.motion.rememberAnimationsEnabled
import com.rawedit.app.ui.theme.BricolageFamily
import com.rawedit.app.ui.theme.JetBrainsMonoFamily
import com.rawedit.app.ui.theme.RawColors
import com.rawedit.app.ui.theme.RawTypography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipInputStream

private const val LARGE_EDITOR_THRESHOLD = 15_000
private const val HELPER_ENTRY_PATH = "kinetic-explainer-style/engine/raw-helpers.js"

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

private suspend fun readRawHelperFromStylePack(context: Context): String = withContext(Dispatchers.IO) {
    runCatching {
        context.assets.open(CacheManager.STYLE_PACK_ASSET_NAME).use { input ->
            ZipInputStream(input).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.name == HELPER_ENTRY_PATH) {
                        return@use zis.bufferedReader(Charsets.UTF_8).readText()
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
                ""
            }
        }
    }.getOrDefault("")
}

@Composable
fun AddVoiceScreen(
    project: ProjectEntity,
    cacheManager: CacheManager,
    groqApiKey: String = "",
    onOpenGroqKey: () -> Unit = {},
    onBack: () -> Unit,
    onSave: (ProjectEntity) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val animEnabled = rememberAnimationsEnabled()
    val scrollState = rememberScrollState()

    var audioPath by remember(project.id) { mutableStateOf(project.audioUri) }
    var audioName by remember(project.id) { mutableStateOf(project.audioName) }
    var audioSize by remember(project.id) { mutableStateOf(project.audioSizeBytes) }
    var audioDuration by remember(project.id) { mutableStateOf(project.audioDurationSec) }
    var audioError by remember { mutableStateOf<String?>(null) }
    var loadingAudio by remember { mutableStateOf(false) }

    var srtText by remember(project.id) {
        mutableStateOf(if (project.srtText.startsWith(CacheManager.SRT_FILE_PREFIX)) "" else project.srtText)
    }
    var srtCueCount by remember(project.id) { mutableStateOf(project.srtCueCount) }
    var srtError by remember { mutableStateOf<String?>(null) }
    var transcribingAudio by remember { mutableStateOf(false) }

    var topic by remember(project.id) {
        mutableStateOf(if (project.topic.startsWith(CacheManager.TOPIC_FILE_PREFIX)) "" else project.topic)
    }
    var forceRawEditTopic by remember { mutableStateOf(false) }
    var savingVoice by remember { mutableStateOf(false) }
    var toastMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(project.id, project.srtText, project.topic) {
        val needsSrt = project.srtText.startsWith(CacheManager.SRT_FILE_PREFIX)
        val needsTopic = project.topic.startsWith(CacheManager.TOPIC_FILE_PREFIX)
        if (needsSrt || needsTopic) {
            val (loadedSrt, loadedTopic) = withContext(Dispatchers.IO) {
                (if (needsSrt) cacheManager.resolveSrtText(project.srtText) else srtText) to
                    (if (needsTopic) cacheManager.resolveTopicText(project.topic) else topic)
            }
            if (needsSrt) srtText = loadedSrt
            if (needsTopic) topic = loadedTopic
        }
    }

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
    val dashPhaseState: State<Float>? = if (animEnabled && !isSelected) {
        val inf = rememberInfiniteTransition(label = "dz_dash")
        inf.animateFloat(
            initialValue = 0f,
            targetValue = 28f,
            animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
            label = "dash"
        )
    } else {
        null
    }
    val dashIntervals = remember { floatArrayOf(16f, 12f) }

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
                .verticalScroll(scrollState)
                .padding(start = 18.dp, end = 18.dp, bottom = 128.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircleActionButton(icon = RawIconType.BACK, onClick = onBack, size = 48.dp)
                RawPixelMascot(kind = PixelMascotKind.MIC_BOT, size = 40.dp)
            }

            KineticHeading(
                words = listOf("Add", "your", "voice"),
                style = RawTypography.headlineLarge,
                highlightLastWord = true
            )

            // Dashed Tan Dropzone (.dz, 24dp)
            RawPressButton(
                onClick = { audioPicker.launch(arrayOf("audio/*", "application/ogg")) },
                modifier = Modifier.graphicsLayer { translationX = dzShake.value }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(216.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(if (isSelected) RawColors.AccentSoft else RawColors.CardSurface)
                        .then(
                            if (isSelected) {
                                Modifier.border(1.dp, RawColors.AccentBorder, RoundedCornerShape(24.dp))
                            } else Modifier
                        )
                ) {
                    if (!isSelected) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawRoundRect(
                                color = if (audioError != null) RawColors.ErrorCoral else RawColors.Tan.copy(alpha = 0.48f),
                                style = Stroke(
                                    width = 1.8.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(dashIntervals, dashPhaseState?.value ?: 0f)
                                ),
                                cornerRadius = CornerRadius(24.dp.toPx())
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
                                        color = RawColors.Tan,
                                        modifier = Modifier.size(34.dp),
                                        strokeWidth = 2.5.dp
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("Reading voiceover...", style = RawTypography.titleLarge)
                                }
                                2 -> {
                                    Box(
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(CircleShape)
                                            .background(RawColors.Tan),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        RawIcon(RawIconType.CHECK, size = 28.dp, tint = RawColors.Ink)
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = audioName,
                                        style = RawTypography.titleLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = "${FormatHelpers.formatDuration(audioDuration)}  \u2022  ${FormatHelpers.formatFileSize(audioSize)}",
                                        style = RawTypography.labelMedium
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Tap to change",
                                        fontFamily = BricolageFamily,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 14.5.sp,
                                        color = RawColors.TextMuted,
                                        textDecoration = TextDecoration.Underline
                                    )
                                }
                                else -> {
                                    Box(
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(CircleShape)
                                            .background(RawColors.AccentSoft),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        RawIcon(RawIconType.SHARE, size = 28.dp, tint = RawColors.Tan)
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "Tap to pick voiceover",
                                        style = RawTypography.titleLarge
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = "MP3, M4A, WAV, AAC  \u2022  1s to 20m",
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
                        .clip(RoundedCornerShape(20.dp))
                        .background(RawColors.ErrorSoft)
                        .border(1.dp, RawColors.ErrorBorder, RoundedCornerShape(20.dp))
                        .padding(14.dp)
                ) {
                    Text(text = msg, style = RawTypography.bodyMedium, color = RawColors.ErrorCoral)
                }
            }

            // Optional SRT file card (28dp borderless)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(RawColors.CardSurface)
                    .clickable { srtPicker.launch(arrayOf("*/*")) }
                    .padding(horizontal = 17.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(RawColors.RaisedCard),
                    contentAlignment = Alignment.Center
                ) {
                    RawIcon(RawIconType.SRT, size = 21.dp, tint = RawColors.TextPrimary)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Add SRT file (optional)",
                        fontFamily = BricolageFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.5.sp,
                        color = RawColors.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = if (srtCueCount > 0) "$srtCueCount subtitle cues attached" else "Gives Cloud AI exact timing per word",
                        fontFamily = BricolageFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp,
                        lineHeight = 18.sp,
                        color = if (srtCueCount > 0) RawColors.Tan else RawColors.TextMuted
                    )
                }
                if (srtCueCount > 0) {
                    RawSecondaryButton(
                        text = "Remove",
                        height = 46.dp,
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
                        size = 38.dp,
                        onClick = { srtPicker.launch(arrayOf("*/*")) }
                    )
                }
            }

            srtError?.let { msg ->
                Text(text = msg, style = RawTypography.bodyMedium, color = RawColors.ErrorCoral)
            }

            if (audioPath.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(RawColors.RaisedCard)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(RawColors.AccentSoft),
                        contentAlignment = Alignment.Center
                    ) {
                        RawIcon(type = RawIconType.SPARK, size = 18.dp, tint = RawColors.Tan)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Word-by-word transcription",
                            fontFamily = BricolageFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = RawColors.TextPrimary
                        )
                        Text(
                            text = if (transcribingAudio) "Transcribing with Groq Whisper..." else "Generate precise word timestamps via Groq AI",
                            fontFamily = BricolageFamily,
                            fontSize = 12.5.sp,
                            lineHeight = 16.5.sp,
                            color = RawColors.TextMuted
                        )
                    }
                    if (transcribingAudio) {
                        CircularProgressIndicator(
                            color = RawColors.Tan,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        RawSecondaryButton(
                            text = if (srtCueCount > 0) "Regenerate" else "Transcribe",
                            height = 38.dp,
                            fillWidth = false,
                            onClick = {
                                if (groqApiKey.isBlank()) {
                                    onOpenGroqKey()
                                } else {
                                    transcribingAudio = true
                                    srtError = null
                                    scope.launch {
                                        val f = File(audioPath)
                                        val res = GroqClient.transcribeAudioWordByWord(groqApiKey, f)
                                        transcribingAudio = false
                                        res.onSuccess { parsed ->
                                            srtText = parsed.cleanedSrt
                                            srtCueCount = parsed.cueCount
                                            if (topic.isBlank()) {
                                                topic = parsed.cleanedSrt.lines()
                                                    .filter { it.isNotBlank() && !it.contains("-->") && it.toIntOrNull() == null }
                                                    .joinToString(" ")
                                                    .take(200)
                                            }
                                            toastMsg = "Generated ${parsed.cueCount} word cues"
                                        }.onFailure { err ->
                                            srtError = err.message ?: "Transcription failed."
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }

            // Script / topic notes (.lbl + #nl + #nt)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Script / topic notes (optional)",
                    fontFamily = BricolageFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = RawColors.TextPrimary,
                    modifier = Modifier.padding(top = 6.dp, start = 2.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${topic.length} chars \u2022 unlimited",
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        style = TextStyle(fontFeatureSettings = "tnum"),
                        color = RawColors.TextMuted
                    )
                    RawSecondaryButton(
                        text = "Paste",
                        height = 38.dp,
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
                            .clip(RoundedCornerShape(28.dp))
                            .background(RawColors.CardSurface)
                            .padding(16.dp),
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
                                height = 46.dp,
                                onClick = { forceRawEditTopic = true },
                                modifier = Modifier.weight(1f)
                            )
                            RawSecondaryButton(
                                text = "Clear",
                                height = 46.dp,
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
                        textStyle = TextStyle(
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            lineHeight = 23.sp,
                            fontFeatureSettings = "tnum",
                            color = RawColors.TextPrimary
                        ),
                        minLines = 5,
                        maxLines = 8,
                        shape = RoundedCornerShape(20.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RawColors.AccentFocus,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = RawColors.RaisedCard,
                            unfocusedContainerColor = RawColors.RaisedCard,
                            cursorColor = RawColors.Tan
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        FeatheredScrollEdges(
            scrollState = scrollState,
            topHeight = 44.dp,
            bottomHeight = 118.dp,
            alwaysShowBottom = true
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 18.dp, end = 18.dp, bottom = 16.dp)
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
    attachedAssets: List<LocalAssetEntity>,
    palette: PaletteState,
    cacheManager: CacheManager,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val animEnabled = rememberAnimationsEnabled()
    val scrollState = rememberScrollState()

    var resolvedTopic by remember(project.id, project.topic) {
        mutableStateOf(if (project.topic.startsWith(CacheManager.TOPIC_FILE_PREFIX)) "" else project.topic)
    }
    var resolvedSrt by remember(project.id, project.srtText) {
        mutableStateOf(if (project.srtText.startsWith(CacheManager.SRT_FILE_PREFIX)) "" else project.srtText)
    }
    LaunchedEffect(project.topic, project.srtText) {
        val needsTopic = project.topic.startsWith(CacheManager.TOPIC_FILE_PREFIX)
        val needsSrt = project.srtText.startsWith(CacheManager.SRT_FILE_PREFIX)
        if (needsTopic || needsSrt) {
            val (loadedTopic, loadedSrt) = withContext(Dispatchers.IO) {
                (if (needsTopic) cacheManager.resolveTopicText(project.topic) else resolvedTopic) to
                    (if (needsSrt) cacheManager.resolveSrtText(project.srtText) else resolvedSrt)
            }
            if (needsTopic) resolvedTopic = loadedTopic
            if (needsSrt) resolvedSrt = loadedSrt
        }
    }

    val customFontNames = remember {
        cacheManager.listCustomFonts().map { it.nameWithoutExtension }
    }

    val promptText = remember(project.audioName, project.audioDurationSec, resolvedTopic, resolvedSrt, attachedAssets, palette, customFontNames) {
        PromptGenerator.buildPrompt(
            fileName = project.audioName,
            durationSeconds = project.audioDurationSec.coerceAtLeast(1.0),
            topic = resolvedTopic,
            srtText = resolvedSrt,
            assets = attachedAssets,
            palette = palette,
            customFonts = customFontNames
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

    var showHelperDialog by remember { mutableStateOf(false) }
    var helperCode by remember { mutableStateOf<String?>(null) }

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

    val paletteSwatchColor = remember(palette) {
        Color(PaletteColorMath.parseColorInt(palette.colors.firstOrNull()?.hex ?: "#CAA07D"))
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
                .verticalScroll(scrollState)
                .padding(start = 18.dp, end = 18.dp, bottom = 128.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircleActionButton(icon = RawIconType.BACK, onClick = onBack, size = 48.dp)
                RawPixelMascot(kind = PixelMascotKind.ZIP_BOT, size = 40.dp)
            }

            KineticHeading(
                words = listOf("Copy", "prompt"),
                style = RawTypography.headlineLarge,
                highlightLastWord = true
            )

            Text(
                text = "1) Attach Style Pack ZIP in Cloud AI \u2022 2) Paste this prompt \u2022 3) Answer 3 questions & copy HTML code.",
                style = RawTypography.bodyLarge.copy(fontSize = 15.5.sp),
                color = RawColors.TextMuted
            )

            // Context summary pills: attached assets + active Studio Palette
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (attachedAssets.isNotEmpty()) RawColors.AccentSoft else RawColors.RaisedCard)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RawIcon(
                        type = RawIconType.BOX,
                        size = 14.dp,
                        tint = if (attachedAssets.isNotEmpty()) RawColors.Tan else RawColors.TextMuted
                    )
                    Text(
                        text = "${attachedAssets.size} local assets",
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = if (attachedAssets.isNotEmpty()) RawColors.Tan else RawColors.TextMuted
                    )
                }

                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(RawColors.RaisedCard)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(paletteSwatchColor)
                    )
                    Text(
                        text = "Palette #${palette.numFormatted} \u00B7 ${palette.mood}",
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = RawColors.TextPrimary
                    )
                }
            }

            // Style Pack ZIP Card (28dp)
            val zipBorder by animateColorAsState(
                targetValue = if (zipSaved) RawColors.AccentBorder else Color.Transparent,
                animationSpec = tween(if (animEnabled) 220 else 0, easing = RawEasings.Morph),
                label = "zip_card_bd"
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(RawColors.CardSurface)
                    .then(
                        if (zipBorder != Color.Transparent) {
                            Modifier.border(1.dp, zipBorder, RoundedCornerShape(28.dp))
                        } else Modifier
                    )
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(RawColors.RaisedCard),
                        contentAlignment = Alignment.Center
                    ) {
                        RawIcon(RawIconType.ZIP, size = 21.dp, tint = RawColors.Tan)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = CacheManager.STYLE_PACK_ASSET_NAME,
                            fontFamily = BricolageFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.5.sp,
                            color = RawColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Includes engine/raw-helpers.js",
                            style = RawTypography.labelMedium
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (zipSaved) RawColors.AccentSoft else RawColors.RaisedCard)
                            .padding(horizontal = 11.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (zipSaved) "Saved" else "552 KB",
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.5.sp,
                            style = TextStyle(fontFeatureSettings = "tnum"),
                            color = if (zipSaved) RawColors.Tan else RawColors.TextMuted
                        )
                    }
                }

                // Helper inspect chip for RAW.syncVideo
                RawPressButton(
                    onClick = {
                        showHelperDialog = true
                        if (helperCode == null) {
                            scope.launch {
                                helperCode = readRawHelperFromStylePack(context)
                                    .ifBlank { "// raw-helpers.js missing from bundle" }
                            }
                        }
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(RawColors.RaisedCard)
                            .padding(horizontal = 13.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RawIcon(type = RawIconType.CODE, size = 14.dp, tint = RawColors.Tan)
                        Text(
                            text = "Inspect helper: RAW.syncVideo(...)",
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = RawColors.TextPrimary
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
                        height = 48.dp,
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
                        height = 48.dp,
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
                    Text(text = msg, style = RawTypography.labelMedium, color = RawColors.Tan)
                }
                zipError?.let { err ->
                    Text(text = err, style = RawTypography.labelMedium, color = RawColors.ErrorCoral)
                }
            }

            // Monospace prompt container (.pre, 24dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(310.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(RawColors.CodeSurface)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Text(
                    text = displayPrompt,
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    lineHeight = 21.sp,
                    style = TextStyle(fontFeatureSettings = "tnum"),
                    color = RawColors.TextPrimary.copy(alpha = 0.88f)
                )
            }
        }

        FeatheredScrollEdges(
            scrollState = scrollState,
            topHeight = 44.dp,
            bottomHeight = 118.dp,
            alwaysShowBottom = true
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 18.dp, end = 18.dp, bottom = 16.dp)
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
                            toastMsg = "Prompt too large \u2014 tap Share"
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                            .clip(CircleShape)
                            .background(RawColors.Cream),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RawIcon(
                            type = if (copied) RawIconType.CHECK else RawIconType.COPY,
                            size = 18.dp,
                            tint = RawColors.Ink
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        SplitFlapText(
                            text = if (copied) "Copied" else "Copy prompt",
                            style = RawTypography.labelLarge.copy(fontSize = 17.sp),
                            color = RawColors.Ink
                        )
                    }
                }

                RawSecondaryButton(
                    text = "Share",
                    height = 58.dp,
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

    if (showHelperDialog) {
        Dialog(
            onDismissRequest = { showHelperDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(RawColors.CardSurface)
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "raw-helpers.js",
                            fontFamily = BricolageFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = RawColors.TextPrimary
                        )
                        Text(
                            text = HELPER_ENTRY_PATH,
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.5.sp,
                            color = RawColors.Tan
                        )
                    }
                    CircleActionButton(
                        icon = RawIconType.CLOSE,
                        size = 36.dp,
                        onClick = { showHelperDialog = false }
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(RawColors.CodeSurface)
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp)
                ) {
                    Text(
                        text = helperCode ?: "Reading raw-helpers.js from ZIP...",
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = RawColors.TextPrimary.copy(alpha = 0.88f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    RawSecondaryButton(
                        text = "Close",
                        height = 48.dp,
                        onClick = { showHelperDialog = false },
                        modifier = Modifier.weight(1f)
                    )
                    BigPrimaryButton(
                        text = "Copy helper",
                        height = 48.dp,
                        enabled = !helperCode.isNullOrBlank(),
                        onClick = {
                            val src = helperCode ?: return@BigPrimaryButton
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cb.setPrimaryClip(ClipData.newPlainText("raw-helpers.js", src))
                            toastMsg = "Helper copied"
                            showHelperDialog = false
                        },
                        modifier = Modifier.weight(1f)
                    )
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
    onOpenKeyframeSheet: (ProjectEntity) -> Unit,
    onCreateNow: (ProjectEntity) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val animEnabled = rememberAnimationsEnabled()
    val scrollState = rememberScrollState()

    var codeText by remember(project.id) {
        mutableStateOf(if (project.code.startsWith(CacheManager.CODE_FILE_PREFIX)) "" else project.code)
    }
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

    LaunchedEffect(project.id, project.code) {
        if (project.code.startsWith(CacheManager.CODE_FILE_PREFIX)) {
            val loaded = withContext(Dispatchers.IO) { cacheManager.resolveHtmlCode(project.code) }
            codeText = loaded
            if (loaded.isNotBlank()) {
                runValidation(loaded)
            }
        } else if (codeText.isNotBlank() && validationResult == null) {
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
    val bottomClearance = if (valid != null) 172.dp else 118.dp

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
                .verticalScroll(scrollState)
                .padding(start = 18.dp, end = 18.dp, bottom = bottomClearance),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircleActionButton(icon = RawIconType.BACK, onClick = onBack, size = 48.dp)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${codeText.length} chars (unlimited)",
                        style = RawTypography.labelMedium
                    )
                    RawPixelMascot(kind = PixelMascotKind.CODE_BOT, size = 38.dp)
                }
            }

            KineticHeading(
                words = listOf("Have", "a", "code?"),
                style = RawTypography.headlineLarge,
                highlightLastWord = true
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RawSecondaryButton(
                    text = "Paste",
                    height = 46.dp,
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
                                toastMsg = "Clipboard empty \u2014 copy code or pick .html file"
                            }
                        }
                    }
                )
                RawSecondaryButton(
                    text = "Pick .html file",
                    height = 46.dp,
                    leadingIcon = RawIconType.FILE,
                    fillWidth = false,
                    onClick = { htmlFilePicker.launch(arrayOf("text/html", "text/plain", "*/*")) }
                )
                RawSecondaryButton(
                    text = "Load 6s sample",
                    height = 46.dp,
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
                        height = 46.dp,
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

            if (codeText.length > LARGE_EDITOR_THRESHOLD && !forceRawEditLarge) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(RawColors.CardSurface)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "${codeText.count { it == '\n' } + 1} lines  \u2022  ${codeText.length} chars (collapsed)",
                        style = RawTypography.labelMedium,
                        color = RawColors.Tan
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
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            style = TextStyle(fontFeatureSettings = "tnum"),
                            color = RawColors.TextMuted
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RawSecondaryButton(
                            text = "Show editor",
                            height = 46.dp,
                            onClick = { forceRawEditLarge = true },
                            modifier = Modifier.weight(1f)
                        )
                        RawSecondaryButton(
                            text = "Clear",
                            height = 46.dp,
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
                    textStyle = TextStyle(
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        lineHeight = 23.sp,
                        fontFeatureSettings = "tnum",
                        color = RawColors.TextPrimary
                    ),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RawColors.AccentFocus,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = RawColors.RaisedCard,
                        unfocusedContainerColor = RawColors.RaisedCard,
                        cursorColor = RawColors.Tan
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                )
            }

            // Morphing Testing / Valid / Invalid Banners
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
                                .clip(RoundedCornerShape(20.dp))
                                .background(RawColors.RaisedCard)
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                color = RawColors.Tan,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Testing code in offline sandbox...",
                                fontFamily = BricolageFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.5.sp,
                                color = RawColors.TextPrimary
                            )
                        }
                    }
                    target != "idle" && validationResult != null -> {
                        val res = validationResult!!
                        val bg = if (res.isValid) RawColors.AccentSoft else RawColors.ErrorSoft
                        val bd = if (res.isValid) RawColors.AccentBorder else RawColors.ErrorBorder
                        val fg = if (res.isValid) RawColors.Tan else RawColors.ErrorCoral
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(bg)
                                .border(1.dp, bd, RoundedCornerShape(20.dp))
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            RawIcon(
                                type = if (res.isValid) RawIconType.CHECK else RawIconType.WARN,
                                size = 22.dp,
                                tint = fg
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = res.summaryMessage,
                                    fontFamily = BricolageFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.5.sp,
                                    lineHeight = 21.sp,
                                    color = fg
                                )
                                res.warning?.let { w ->
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = w,
                                        fontFamily = BricolageFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        color = fg.copy(alpha = 0.85f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        FeatheredScrollEdges(
            scrollState = scrollState,
            topHeight = 44.dp,
            bottomHeight = if (valid != null) 168.dp else 118.dp,
            alwaysShowBottom = true
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 18.dp, end = 18.dp, bottom = 16.dp)
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
                                .height(54.dp)
                                .clip(CircleShape)
                                .background(RawColors.RaisedCard),
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
                                fontFamily = BricolageFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = RawColors.TextPrimary
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RawSecondaryButton(
                            text = "Save",
                            height = 58.dp,
                            onClick = {
                                scope.launch {
                                    onValidatedAndSaved(buildUpdatedProject(valid))
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                        BigPrimaryButton(
                            text = if (project.audioUri.isNotBlank()) "Create video" else "Use code",
                            height = 58.dp,
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
                .padding(bottom = if (valid != null) 156.dp else 94.dp)
        )
    }
}
