package com.rawedit.app.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rawedit.app.domain.CutoutAuditMath
import com.rawedit.app.domain.CutoutAuditResult
import com.rawedit.app.domain.CutoutPadRatios
import com.rawedit.app.domain.CutoutProcessor
import com.rawedit.app.domain.GroqClient
import com.rawedit.app.domain.GroqCodec
import com.rawedit.app.ui.components.BigPrimaryButton
import com.rawedit.app.ui.components.CircleActionButton
import com.rawedit.app.ui.components.RawSecondaryButton
import com.rawedit.app.ui.icons.RawIcon
import com.rawedit.app.ui.icons.RawIconType
import com.rawedit.app.ui.theme.BricolageFamily
import com.rawedit.app.ui.theme.JetBrainsMonoFamily
import com.rawedit.app.ui.theme.RawColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

sealed interface CutoutStudioInput {
    val suggestedName: String
    val defaultDescription: String?
    val defaultSafeZone: String?

    data class SvgInput(
        val rawSvg: String,
        override val suggestedName: String,
        val initialTintHex: String? = null,
        override val defaultDescription: String? = null,
        override val defaultSafeZone: String? = null
    ) : CutoutStudioInput

    data class BitmapInput(
        val sourceBitmap: Bitmap,
        override val suggestedName: String,
        val defaultKeepOriginal: Boolean = false,
        override val defaultDescription: String? = null,
        override val defaultSafeZone: String? = null
    ) : CutoutStudioInput
}

private data class TintSwatchOption(
    val label: String,
    val hex: String?,
    val dotColor: Color?
)

private val SVG_TINT_OPTIONS = listOf(
    TintSwatchOption("Original", null, null),
    TintSwatchOption("Cream", "#F7ECE6", RawColors.Cream),
    TintSwatchOption("Tan", "#CAA07D", RawColors.Tan),
    TintSwatchOption("White", "#FFFFFF", Color.White),
    TintSwatchOption("Dark", "#111112", RawColors.Ink)
)

@Composable
fun CutoutStudioDialog(
    input: CutoutStudioInput,
    apiKey: String,
    modelPref: String = GroqCodec.PREF_AUTO,
    quotaExhausted: Boolean = false,
    onOpenGroqKey: () -> Unit,
    onSwitchGroqKey: () -> Unit,
    onRefreshGroqQuota: () -> Unit,
    onDismiss: () -> Unit,
    onConfirmSave: (
        bitmap: Bitmap,
        isCutout: Boolean,
        suggestedName: String,
        defaultDescription: String?,
        defaultSafeZone: String?
    ) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isSvg = input is CutoutStudioInput.SvgInput

    var keepOriginal by remember(input) {
        mutableStateOf(if (input is CutoutStudioInput.BitmapInput) input.defaultKeepOriginal else false)
    }
    var tolerance by remember(input) { mutableIntStateOf(26) }
    var currentPad by remember(input) { mutableStateOf(CutoutPadRatios(0.05f, 0.05f, 0.05f, 0.05f)) }
    var svgTintHex by remember(input) {
        mutableStateOf(if (input is CutoutStudioInput.SvgInput) input.initialTintHex else null)
    }
    var showOriginalTab by remember(input) { mutableStateOf(false) }

    var previewResultBmp by remember(input) { mutableStateOf<Bitmap?>(null) }
    var previewResultImage by remember(input) { mutableStateOf<ImageBitmap?>(null) }
    var previewSourceBmp by remember(input) {
        mutableStateOf(if (input is CutoutStudioInput.BitmapInput) input.sourceBitmap else null)
    }
    var previewSourceImage by remember(input) {
        mutableStateOf(if (input is CutoutStudioInput.BitmapInput) input.sourceBitmap.asImageBitmap() else null)
    }

    var detectedAlphaAlready by remember(input) { mutableStateOf(isSvg) }
    var isProcessing by remember(input) { mutableStateOf(true) }
    var isAiChecking by remember(input) { mutableStateOf(false) }
    var isSavingFinal by remember(input) { mutableStateOf(false) }
    var auditResult by remember(input) { mutableStateOf<CutoutAuditResult?>(null) }
    var auditError by remember(input) { mutableStateOf<String?>(null) }
    var manualRecheckNonce by remember(input) { mutableIntStateOf(0) }
    var initialAiDone by remember(input) { mutableStateOf(false) }

    suspend fun runRender(renderSize: Int): Bitmap {
        return when (input) {
            is CutoutStudioInput.SvgInput -> {
                if (previewSourceBmp == null && svgTintHex == null) {
                    val neutral = CutoutProcessor.rasterizeSingleSvgToCutout(
                        context = context,
                        rawSvg = input.rawSvg,
                        pad = CutoutPadRatios(0.02f, 0.02f, 0.02f, 0.02f),
                        tintHex = null,
                        sizePx = 512
                    ).resultBitmap
                    previewSourceBmp = neutral
                    previewSourceImage = neutral.asImageBitmap()
                }
                CutoutProcessor.rasterizeSingleSvgToCutout(
                    context = context,
                    rawSvg = input.rawSvg,
                    pad = currentPad,
                    tintHex = svgTintHex,
                    sizePx = renderSize
                ).resultBitmap
            }

            is CutoutStudioInput.BitmapInput -> {
                val res = CutoutProcessor.processBitmapToCutout(
                    source = input.sourceBitmap,
                    keepOriginal = keepOriginal,
                    removeBackground = !keepOriginal,
                    tolerance = tolerance,
                    pad = currentPad
                )
                detectedAlphaAlready = res.hadExistingTransparency
                res.resultBitmap
            }
        }
    }

    suspend fun runGroqAudit(origBmp: Bitmap?, cutoutBmp: Bitmap) {
        if (apiKey.isBlank() || quotaExhausted) return
        isAiChecking = true
        auditError = null
        val compareFile = runCatching {
            CutoutProcessor.buildComparisonSheetFile(context.cacheDir, origBmp, cutoutBmp)
        }.getOrNull()
        if (compareFile == null) {
            isAiChecking = false
            auditError = "Could not build comparison preview."
            return
        }
        val res = GroqClient.auditCutout(
            apiKey = apiKey,
            comparisonFile = compareFile,
            currentTolerance = tolerance,
            currentPad = currentPad,
            isSvgSource = isSvg,
            keepOriginalMode = keepOriginal,
            modelPref = modelPref
        )
        runCatching { compareFile.delete() }
        res.onSuccess { audit ->
            onRefreshGroqQuota()
            auditResult = audit
            if (audit.needsReprocess) {
                val adj = CutoutAuditMath.applyAdjustment(
                    currentPad = currentPad,
                    currentTolerance = tolerance,
                    isSvgSource = isSvg,
                    audit = audit
                )
                currentPad = adj.pad
                tolerance = adj.tolerance
                if (!isSvg) keepOriginal = adj.keepOriginal

                val refinedBmp = runRender(768)
                previewResultBmp = refinedBmp
                previewResultImage = refinedBmp.asImageBitmap()
            }
        }.onFailure { err ->
            auditError = err.message ?: "AI edge check failed"
        }
        isAiChecking = false
    }

    // Re-render preview whenever conversion controls change (debounced gently for slider smoothness)
    LaunchedEffect(input, keepOriginal, tolerance, currentPad, svgTintHex) {
        isProcessing = true
        if (input is CutoutStudioInput.BitmapInput) {
            delay(50)
        }
        val bmp = runCatching { runRender(768) }.getOrNull()
        if (bmp != null) {
            previewResultBmp = bmp
            previewResultImage = bmp.asImageBitmap()
            if (!initialAiDone && apiKey.isNotBlank() && !quotaExhausted) {
                initialAiDone = true
                runGroqAudit(previewSourceBmp, bmp)
            }
        }
        isProcessing = false
    }

    LaunchedEffect(manualRecheckNonce) {
        if (manualRecheckNonce > 0) {
            val bmp = previewResultBmp ?: return@LaunchedEffect
            runGroqAudit(previewSourceBmp, bmp)
        }
    }

    Dialog(
        onDismissRequest = { if (!isSavingFinal) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 18.dp, vertical = 20.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(RawColors.CardSurface)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "PNG Cutout Studio",
                        fontFamily = BricolageFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 23.sp,
                        letterSpacing = (-0.5).sp,
                        color = RawColors.TextPrimary
                    )
                    Text(
                        text = input.suggestedName,
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.5.sp,
                        color = RawColors.Tan,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                CircleActionButton(
                    icon = RawIconType.CLOSE,
                    size = 40.dp,
                    enabled = !isSavingFinal,
                    onClick = onDismiss
                )
            }

            // Cutout vs Source toggle + Checkerboard Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(RawColors.RaisedCard)
                    .border(1.dp, RawColors.Divider, RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.Center
            ) {
                CutoutCheckerboardBackdrop(modifier = Modifier.fillMaxSize())
                val shownImg = if (showOriginalTab && previewSourceImage != null) {
                    previewSourceImage
                } else {
                    previewResultImage
                }
                if (shownImg != null) {
                    Image(
                        bitmap = shownImg,
                        contentDescription = "Cutout preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp)
                    )
                }

                // Top-left status badge
                val dimText = previewResultBmp?.let { "${it.width}x${it.height}" } ?: "..."
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                        .clip(CircleShape)
                        .background(Color(0xCC0D0D0F))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isProcessing) "Converting..." else dimText,
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = RawColors.Cream
                    )
                }

                // Top-right Before/After switch if source bitmap exists
                if (previewSourceImage != null) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xCC0D0D0F))
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        val tabs = listOf(false to "Cutout", true to "Original")
                        tabs.forEach { (isOrig, label) ->
                            val sel = showOriginalTab == isOrig
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (sel) RawColors.Tan else Color.Transparent)
                                    .clickable { showOriginalTab = isOrig }
                                    .padding(horizontal = 9.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontFamily = BricolageFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = if (sel) RawColors.Ink else RawColors.Cream
                                )
                            }
                        }
                    }
                }
            }

            // Groq Vision Verification Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(RawColors.RaisedCard)
                    .then(
                        if (auditResult != null) {
                            Modifier.border(1.dp, RawColors.AccentBorder, RoundedCornerShape(18.dp))
                        } else Modifier
                    )
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RawIcon(RawIconType.SPARK, size = 16.dp, tint = RawColors.Tan)
                        val title = when {
                            isAiChecking -> "AI inspecting edges..."
                            quotaExhausted -> "Groq quota exhausted"
                            auditResult?.status == "ADJUST" -> "AI REFINED OUTPUT"
                            auditResult != null -> "AI VERIFIED CLEAN"
                            apiKey.isBlank() -> "Groq Cutout Check (Optional)"
                            else -> "AI edge audit ready"
                        }
                        Text(
                            text = title,
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (quotaExhausted) RawColors.ErrorCoral else RawColors.Tan
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (quotaExhausted) RawColors.ErrorSoft else RawColors.AccentSoft)
                            .clickable(enabled = !isAiChecking && !isProcessing && !isSavingFinal) {
                                when {
                                    apiKey.isBlank() -> onOpenGroqKey()
                                    quotaExhausted -> onSwitchGroqKey()
                                    else -> manualRecheckNonce++
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = when {
                                apiKey.isBlank() -> "Add key"
                                quotaExhausted -> "Switch key"
                                isAiChecking -> "Checking..."
                                else -> "Re-check"
                            },
                            fontFamily = BricolageFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            color = if (quotaExhausted) RawColors.ErrorCoral else RawColors.Tan
                        )
                    }
                }

                val statusLine = when {
                    isAiChecking -> "Checking whether strokes, inner details, or corners were unnecessarily cropped or erased."
                    quotaExhausted -> "Groq quota ran out on this key. Tap Switch key above to continue AI edge inspection."
                    auditError != null -> auditError!!
                    auditResult != null -> auditResult!!.diagnosis
                    apiKey.isBlank() -> "Add a Groq API key so Llama 4 Vision checks cutouts for accidental crops or erased inner details."
                    else -> "Tap Re-check after slider changes to re-verify edges."
                }
                Text(
                    text = statusLine,
                    fontFamily = BricolageFamily,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = if (auditError != null || quotaExhausted) RawColors.ErrorCoral else RawColors.TextMuted
                )
            }

            // SVG Tint Swatches (for SVGs) OR Cutout Mode + BG Tolerance Slider (for Bitmaps)
            if (isSvg) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "ICON / VECTOR COLOR TINT",
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = RawColors.TextMuted
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SVG_TINT_OPTIONS.forEach { opt ->
                            val selected = svgTintHex == opt.hex
                            Row(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (selected) RawColors.Tan else RawColors.RaisedCard)
                                    .clickable { svgTintHex = opt.hex }
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (opt.dotColor != null) {
                                    Box(
                                        modifier = Modifier
                                            .size(11.dp)
                                            .clip(CircleShape)
                                            .background(opt.dotColor)
                                            .border(0.8.dp, RawColors.DividerStrong, CircleShape)
                                    )
                                }
                                Text(
                                    text = opt.label,
                                    fontFamily = BricolageFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.5.sp,
                                    color = if (selected) RawColors.Ink else RawColors.TextPrimary
                                )
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val modes = listOf(false to "Auto Cutout", true to "Keep Original")
                    modes.forEach { (isOrigMode, label) ->
                        val selected = keepOriginal == isOrigMode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(CircleShape)
                                .background(if (selected) RawColors.Tan else RawColors.RaisedCard)
                                .clickable { keepOriginal = isOrigMode }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontFamily = BricolageFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (selected) RawColors.Ink else RawColors.TextPrimary
                            )
                        }
                    }
                }

                if (!keepOriginal && !detectedAlphaAlready) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "BG KEY TOLERANCE",
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = RawColors.TextMuted
                            )
                            Text(
                                text = tolerance.toString(),
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp,
                                color = RawColors.Tan
                            )
                        }
                        Slider(
                            value = tolerance.toFloat(),
                            onValueChange = { tolerance = it.roundToInt().coerceIn(6, 58) },
                            valueRange = 6f..58f,
                            colors = SliderDefaults.colors(
                                thumbColor = RawColors.Cream,
                                activeTrackColor = RawColors.Tan,
                                inactiveTrackColor = RawColors.RaisedCard
                            )
                        )
                    }
                }
            }

            // Margin Padding Presets (prevents tight edge clipping)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "SAFE EDGE BREATHING MARGIN",
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = RawColors.TextMuted
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val presets = listOf(0.02f to "Tight 2%", 0.06f to "Safe 6%", 0.12f to "Wide 12%")
                    presets.forEach { (pVal, label) ->
                        val avgPad = (currentPad.top + currentPad.right + currentPad.bottom + currentPad.left) * 0.25f
                        val selected = kotlin.math.abs(avgPad - pVal) < 0.022f
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(CircleShape)
                                .background(if (selected) RawColors.AccentSoft else RawColors.RaisedCard)
                                .border(
                                    width = if (selected) 1.dp else 0.dp,
                                    color = if (selected) RawColors.AccentBorder else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { currentPad = CutoutPadRatios(pVal, pVal, pVal, pVal) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontFamily = BricolageFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = if (selected) RawColors.Tan else RawColors.TextMuted
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Footer action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RawSecondaryButton(
                    text = "Cancel",
                    height = 50.dp,
                    enabled = !isSavingFinal,
                    onClick = onDismiss,
                    modifier = Modifier.weight(0.9f)
                )
                BigPrimaryButton(
                    text = if (isSavingFinal) "Saving..." else if (keepOriginal) "Save PNG" else "Save Cutout",
                    height = 50.dp,
                    leadingIcon = RawIconType.CHECK,
                    enabled = !isProcessing && !isSavingFinal && previewResultBmp != null,
                    onClick = {
                        if (isSavingFinal) return@BigPrimaryButton
                        isSavingFinal = true
                        scope.launch {
                            val finalBmp = runCatching {
                                if (isSvg) runRender(1024) else (previewResultBmp ?: runRender(1024))
                            }.getOrNull() ?: previewResultBmp
                            if (finalBmp != null) {
                                onConfirmSave(
                                    finalBmp,
                                    !keepOriginal,
                                    input.suggestedName,
                                    input.defaultDescription,
                                    input.defaultSafeZone
                                )
                            } else {
                                isSavingFinal = false
                            }
                        }
                    },
                    modifier = Modifier.weight(1.3f)
                )
            }
        }
    }
}

@Composable
private fun CutoutCheckerboardBackdrop(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val cell = 10.dp.toPx()
        val cols = (size.width / cell).toInt() + 1
        val rows = (size.height / cell).toInt() + 1
        val light = Color(0xFF3A393D)
        val dark = Color(0xFF202024)
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                drawRect(
                    color = if ((r + c) % 2 == 0) light else dark,
                    topLeft = Offset(c * cell, r * cell),
                    size = Size(cell, cell)
                )
            }
        }
    }
}
