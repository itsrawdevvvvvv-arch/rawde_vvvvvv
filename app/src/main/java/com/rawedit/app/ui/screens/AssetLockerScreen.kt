package com.rawedit.app.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.rawedit.app.data.CacheManager
import com.rawedit.app.data.local.LocalAssetEntity
import com.rawedit.app.data.local.ProjectEntity
import com.rawedit.app.data.repository.RawEditRepository
import com.rawedit.app.domain.GroqClient
import com.rawedit.app.ui.components.BigPrimaryButton
import com.rawedit.app.ui.components.CircleActionButton
import com.rawedit.app.ui.components.FormatHelpers
import com.rawedit.app.ui.components.RawConfirmDialog
import com.rawedit.app.ui.components.RawSecondaryButton
import com.rawedit.app.ui.components.RawToastBanner
import com.rawedit.app.ui.icons.PixelMascotKind
import com.rawedit.app.ui.icons.RawIcon
import com.rawedit.app.ui.icons.RawIconType
import com.rawedit.app.ui.icons.RawPixelMascot
import com.rawedit.app.ui.motion.CascadeSkewCard
import com.rawedit.app.ui.motion.FeatheredScrollEdges
import com.rawedit.app.ui.motion.KineticHeading
import com.rawedit.app.ui.theme.BricolageFamily
import com.rawedit.app.ui.theme.JetBrainsMonoFamily
import com.rawedit.app.ui.theme.RawColors
import com.rawedit.app.ui.theme.RawTypography
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun AssetLockerScreen(
    project: ProjectEntity,
    allAssets: List<LocalAssetEntity>,
    apiKey: String,
    modelPref: String,
    quotaExhausted: Boolean,
    quotaBadgeText: String,
    cacheManager: CacheManager,
    onBack: () -> Unit,
    onImportAsset: (uri: Uri, isVideo: Boolean) -> Unit,
    onOpenWebBrowser: () -> Unit,
    onSaveCutoutBitmap: (
        bitmap: Bitmap,
        isCutout: Boolean,
        suggestedName: String,
        defaultDescription: String?,
        defaultSafeZone: String?
    ) -> Unit,
    onUpdateAssetNotes: (assetId: Long, description: String, safeZone: String) -> Unit,
    onToggleAttach: (assetId: Long) -> Unit,
    onDeleteAsset: (LocalAssetEntity) -> Unit,
    onOpenGroqKey: () -> Unit,
    onSwitchGroqKey: () -> Unit,
    onRefreshGroqQuota: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val attachedIds = remember(project.attachedAssetIds) {
        RawEditRepository.parseAttachedAssetIds(project.attachedAssetIds).toSet()
    }
    val attachedCount = allAssets.count { it.id in attachedIds }

    var describingAssetId by remember { mutableStateOf<Long?>(null) }
    var editingNoteTarget by remember { mutableStateOf<LocalAssetEntity?>(null) }
    var deleteConfirmTarget by remember { mutableStateOf<LocalAssetEntity?>(null) }
    var cutoutStudioTarget by remember { mutableStateOf<CutoutStudioInput?>(null) }
    var toastMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(toastMsg) {
        if (toastMsg != null) {
            delay(2000)
            toastMsg = null
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImportAsset(uri, false)
            toastMsg = "Importing image..."
        }
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImportAsset(uri, true)
            toastMsg = "Importing video clip..."
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RawColors.BgPrimary)
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp)
                    .padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircleActionButton(icon = RawIconType.BACK, onClick = onBack, size = 50.dp)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val hasKey = apiKey.isNotBlank()
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(
                                when {
                                    quotaExhausted -> RawColors.ErrorSoft
                                    hasKey -> RawColors.AccentSoft
                                    else -> RawColors.RaisedCard
                                }
                            )
                            .border(
                                width = 1.dp,
                                color = if (quotaExhausted) RawColors.ErrorBorder else Color.Transparent,
                                shape = CircleShape
                            )
                            .clickable {
                                if (quotaExhausted) onSwitchGroqKey() else onOpenGroqKey()
                            }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RawIcon(
                            type = RawIconType.KEY,
                            size = 14.dp,
                            tint = when {
                                quotaExhausted -> RawColors.ErrorCoral
                                hasKey -> RawColors.Tan
                                else -> RawColors.TextMuted
                            }
                        )
                        Text(
                            text = when {
                                !hasKey -> "Add Groq key"
                                quotaExhausted -> "Quota 0 \u2022 Switch"
                                else -> quotaBadgeText
                            },
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            color = when {
                                quotaExhausted -> RawColors.ErrorCoral
                                hasKey -> RawColors.Tan
                                else -> RawColors.TextMuted
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    RawPixelMascot(kind = PixelMascotKind.LENS_BOT, size = 38.dp)
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 128.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        KineticHeading(
                            words = listOf("Asset", "locker"),
                            style = RawTypography.headlineLarge,
                            highlightLastWord = true
                        )
                    }

                    item {
                        Text(
                            text = "Local images, PNG cutouts, and MP4 clips are intercepted cleanly offline under `assets/`.",
                            style = RawTypography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp),
                            color = RawColors.TextMuted
                        )
                    }

                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                RawSecondaryButton(
                                    text = "Pick Image / PNG",
                                    height = 50.dp,
                                    leadingIcon = RawIconType.IMAGE,
                                    onClick = { imagePicker.launch(arrayOf("image/*")) },
                                    modifier = Modifier.weight(1f)
                                )
                                RawSecondaryButton(
                                    text = "Pick Video Clip",
                                    height = 50.dp,
                                    leadingIcon = RawIconType.FILM,
                                    onClick = { videoPicker.launch(arrayOf("video/*")) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            RawSecondaryButton(
                                text = "In-App Web Capture & Icon Finder",
                                height = 50.dp,
                                leadingIcon = RawIconType.GLOBE,
                                onClick = onOpenWebBrowser
                            )
                        }
                    }

                    if (apiKey.isBlank()) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(RawColors.CardSurface)
                                    .clickable { onOpenGroqKey() }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                RawIcon(RawIconType.KEY, size = 18.dp, tint = RawColors.Tan)
                                Text(
                                    text = "Add a Groq API key to auto-describe images and safe typography zones.",
                                    style = RawTypography.bodyMedium.copy(fontSize = 13.5.sp, lineHeight = 19.sp),
                                    color = RawColors.TextMuted,
                                    modifier = Modifier.weight(1f)
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(RawColors.AccentSoft)
                                        .padding(horizontal = 11.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = "Add key",
                                        fontFamily = BricolageFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = RawColors.Tan
                                    )
                                }
                            }
                        }
                    } else if (quotaExhausted) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(RawColors.ErrorSoft)
                                    .border(1.dp, RawColors.ErrorBorder, RoundedCornerShape(24.dp))
                                    .clickable { onSwitchGroqKey() }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                RawIcon(RawIconType.WARN, size = 18.dp, tint = RawColors.ErrorCoral)
                                Text(
                                    text = "Groq quota ran out on this key. Switch API key to run AI auto-describe or cutout audit.",
                                    style = RawTypography.bodyMedium.copy(fontSize = 13.5.sp, lineHeight = 19.sp),
                                    color = RawColors.ErrorCoral,
                                    modifier = Modifier.weight(1f)
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(RawColors.Cream)
                                        .padding(horizontal = 11.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = "Switch",
                                        fontFamily = BricolageFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = RawColors.Ink
                                    )
                                }
                            }
                        }
                    }

                    if (allAssets.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(28.dp))
                                    .background(RawColors.CardSurface)
                                    .padding(horizontal = 22.dp, vertical = 30.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                RawPixelMascot(kind = PixelMascotKind.ZIP_BOT, size = 70.dp)
                                Text(
                                    text = "No local assets yet",
                                    fontFamily = BricolageFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 22.sp,
                                    color = RawColors.TextPrimary
                                )
                                Text(
                                    text = "Pick an image, transparent PNG cutout, video clip, or use In-App Web Capture & Icon Finder above to screenshot websites and extract icons with AI cutout refinement.",
                                    style = RawTypography.bodyMedium.copy(fontSize = 14.5.sp, lineHeight = 20.5.sp),
                                    color = RawColors.TextMuted
                                )
                            }
                        }
                    } else {
                        itemsIndexed(
                            items = allAssets,
                            key = { _, a -> a.id }
                        ) { idx, asset ->
                            val attached = asset.id in attachedIds
                            val isBusy = describingAssetId == asset.id
                            CascadeSkewCard(index = idx) {
                                AssetLockerCard(
                                    asset = asset,
                                    attached = attached,
                                    describing = isBusy,
                                    cacheManager = cacheManager,
                                    onToggleAttach = { onToggleAttach(asset.id) },
                                    onDelete = { deleteConfirmTarget = asset },
                                    onAutoDescribe = {
                                        if (apiKey.isBlank()) {
                                            onOpenGroqKey()
                                            return@AssetLockerCard
                                        }
                                        if (quotaExhausted) {
                                            onSwitchGroqKey()
                                            return@AssetLockerCard
                                        }
                                        val imgFile = if (asset.kind == LocalAssetEntity.KIND_CLIP) {
                                            asset.thumbPath.takeIf { it.isNotBlank() }?.let(::File)
                                        } else {
                                            cacheManager.getAssetFile(asset.fileName)
                                        }
                                        if (imgFile == null || !imgFile.exists()) {
                                            toastMsg = "Asset preview file unavailable"
                                            return@AssetLockerCard
                                        }
                                        describingAssetId = asset.id
                                        scope.launch {
                                            val res = GroqClient.describeAsset(
                                                apiKey = apiKey,
                                                asset = asset,
                                                imageFile = imgFile,
                                                modelPref = modelPref
                                            )
                                            describingAssetId = null
                                            res.onSuccess { (desc, safe) ->
                                                onUpdateAssetNotes(asset.id, desc, safe)
                                                onRefreshGroqQuota()
                                                toastMsg = "Groq AI note updated"
                                            }.onFailure { err ->
                                                toastMsg = err.message ?: "Auto-describe failed"
                                            }
                                        }
                                    },
                                    onEditNote = { editingNoteTarget = asset },
                                    onMakeCutout = {
                                        scope.launch {
                                            val bmpRes = cacheManager.loadAssetBitmapForCutout(asset.fileName)
                                            bmpRes.onSuccess { bmp ->
                                                cutoutStudioTarget = CutoutStudioInput.BitmapInput(
                                                    sourceBitmap = bmp,
                                                    suggestedName = "${asset.fileName.substringBeforeLast('.')}_cutout",
                                                    defaultKeepOriginal = false,
                                                    defaultDescription = asset.description,
                                                    defaultSafeZone = asset.safeZone
                                                )
                                            }.onFailure { err ->
                                                toastMsg = err.message ?: "Could not load image for cutout"
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                FeatheredScrollEdges(
                    listState = listState,
                    topHeight = 42.dp,
                    bottomHeight = 112.dp,
                    alwaysShowBottom = true
                )
            }
        }

        // Sticky gradient bottom Done bar
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
                text = if (attachedCount > 0) "Done ($attachedCount attached)" else "Done",
                height = 58.dp,
                leadingIcon = RawIconType.CHECK,
                onClick = onBack
            )
        }

        RawToastBanner(
            message = toastMsg,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 96.dp)
        )
    }

    editingNoteTarget?.let { target ->
        var descDraft by remember(target.id) { mutableStateOf(target.description) }
        var safeDraft by remember(target.id) { mutableStateOf(target.safeZone) }

        Dialog(
            onDismissRequest = { editingNoteTarget = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(horizontal = 22.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(RawColors.CardSurface)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Edit asset note",
                    fontFamily = BricolageFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    color = RawColors.TextPrimary
                )
                Text(
                    text = target.relativePath,
                    style = RawTypography.labelMedium,
                    color = RawColors.Tan
                )
                OutlinedTextField(
                    value = descDraft,
                    onValueChange = { descDraft = it },
                    label = { Text("Visual description", fontSize = 12.sp) },
                    minLines = 2,
                    maxLines = 4,
                    textStyle = TextStyle(
                        fontFamily = BricolageFamily,
                        fontSize = 14.5.sp,
                        color = RawColors.TextPrimary
                    ),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RawColors.AccentFocus,
                        unfocusedBorderColor = RawColors.DividerStrong,
                        focusedContainerColor = RawColors.RaisedCard,
                        unfocusedContainerColor = RawColors.RaisedCard,
                        cursorColor = RawColors.Tan
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = safeDraft,
                    onValueChange = { safeDraft = it },
                    label = { Text("Safe text placement zone", fontSize = 12.sp) },
                    minLines = 2,
                    maxLines = 3,
                    textStyle = TextStyle(
                        fontFamily = BricolageFamily,
                        fontSize = 14.5.sp,
                        color = RawColors.TextPrimary
                    ),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RawColors.AccentFocus,
                        unfocusedBorderColor = RawColors.DividerStrong,
                        focusedContainerColor = RawColors.RaisedCard,
                        unfocusedContainerColor = RawColors.RaisedCard,
                        cursorColor = RawColors.Tan
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    RawSecondaryButton(
                        text = "Cancel",
                        height = 48.dp,
                        onClick = { editingNoteTarget = null },
                        modifier = Modifier.weight(1f)
                    )
                    BigPrimaryButton(
                        text = "Save note",
                        height = 48.dp,
                        onClick = {
                            onUpdateAssetNotes(target.id, descDraft, safeDraft)
                            editingNoteTarget = null
                            toastMsg = "Asset note saved"
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    deleteConfirmTarget?.let { target ->
        RawConfirmDialog(
            title = "Delete this asset?",
            body = "Removes ${target.relativePath} from your offline Asset Locker.",
            confirmLabel = "Delete",
            isDanger = true,
            onConfirm = {
                onDeleteAsset(target)
                deleteConfirmTarget = null
                toastMsg = "Asset deleted"
            },
            onDismiss = { deleteConfirmTarget = null }
        )
    }

    cutoutStudioTarget?.let { target ->
        CutoutStudioDialog(
            input = target,
            apiKey = apiKey,
            modelPref = modelPref,
            quotaExhausted = quotaExhausted,
            onOpenGroqKey = onOpenGroqKey,
            onSwitchGroqKey = onSwitchGroqKey,
            onRefreshGroqQuota = onRefreshGroqQuota,
            onDismiss = { cutoutStudioTarget = null },
            onConfirmSave = { finalBmp, isCutout, suggestedName, desc, safe ->
                cutoutStudioTarget = null
                onSaveCutoutBitmap(finalBmp, isCutout, suggestedName, desc, safe)
                toastMsg = if (isCutout) "Cutout PNG saved" else "PNG saved"
            }
        )
    }
}

@Composable
private fun AssetLockerCard(
    asset: LocalAssetEntity,
    attached: Boolean,
    describing: Boolean,
    cacheManager: CacheManager,
    onToggleAttach: () -> Unit,
    onDelete: () -> Unit,
    onAutoDescribe: () -> Unit,
    onEditNote: () -> Unit,
    onMakeCutout: () -> Unit
) {
    val context = LocalContext.current
    val isCutout = asset.kind == LocalAssetEntity.KIND_CUTOUT
    val isClip = asset.kind == LocalAssetEntity.KIND_CLIP

    val previewFile = remember(asset.id, asset.fileName, asset.thumbPath) {
        if (isClip) {
            asset.thumbPath.takeIf { it.isNotBlank() }?.let(::File)
        } else {
            cacheManager.getAssetFile(asset.fileName)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(RawColors.CardSurface)
            .then(
                if (attached) {
                    Modifier.border(1.5.dp, RawColors.AccentBorder, RoundedCornerShape(28.dp))
                } else Modifier
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 68dp Preview Thumb (checkerboard for PNG cutouts, 3-frame storyboard for clips)
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(RawColors.RaisedCard),
                contentAlignment = Alignment.Center
            ) {
                if (isCutout) {
                    CheckerboardBackdrop(modifier = Modifier.fillMaxSize())
                }
                if (previewFile != null) {
                    val req = remember(previewFile.absolutePath, asset.thumbPath) {
                        ImageRequest.Builder(context)
                            .data(previewFile)
                            .size(192, 192)
                            .build()
                    }
                    AsyncImage(
                        model = req,
                        contentDescription = asset.relativePath,
                        contentScale = if (isCutout) ContentScale.Fit else ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    RawIcon(
                        type = if (isClip) RawIconType.FILM else RawIconType.IMAGE,
                        size = 24.dp,
                        tint = RawColors.TextMuted
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = asset.relativePath,
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = RawColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val badgeLabel = when (asset.kind) {
                        LocalAssetEntity.KIND_CUTOUT -> "CUTOUT"
                        LocalAssetEntity.KIND_CLIP -> "CLIP"
                        else -> "IMAGE"
                    }
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (isCutout || isClip) RawColors.AccentSoft else RawColors.RaisedCard)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = badgeLabel,
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.5.sp,
                            color = if (isCutout || isClip) RawColors.Tan else RawColors.TextMuted
                        )
                    }

                    val dimInfo = buildString {
                        append("${asset.width}x${asset.height}")
                        if (isClip && asset.durationSec > 0.0) {
                            append(" \u2022 ${FormatHelpers.formatDuration(asset.durationSec)}")
                        } else if (asset.sizeBytes > 0L) {
                            append(" \u2022 ${FormatHelpers.formatFileSize(asset.sizeBytes)}")
                        }
                    }
                    Text(
                        text = dimInfo,
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.5.sp,
                        style = TextStyle(fontFeatureSettings = "tnum"),
                        color = RawColors.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (attached) RawColors.Tan else RawColors.RaisedCard)
                        .clickable(onClick = onToggleAttach)
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = if (attached) "Attached" else "Attach",
                        fontFamily = BricolageFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp,
                        color = if (attached) RawColors.Ink else RawColors.TextPrimary,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                CircleActionButton(
                    icon = RawIconType.TRASH,
                    size = 32.dp,
                    iconTint = RawColors.ErrorCoral,
                    onClick = onDelete
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(RawColors.RaisedCard)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "VISUAL DESCRIPTION",
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 10.5.sp,
                color = RawColors.TextMuted
            )
            Text(
                text = asset.description.ifBlank { "No description yet. Run Auto-describe with Groq AI or edit note." },
                fontFamily = BricolageFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 13.5.sp,
                lineHeight = 19.sp,
                color = RawColors.TextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "SAFE TEXT ZONE",
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 10.5.sp,
                color = RawColors.Tan
            )
            Text(
                text = asset.safeZone.ifBlank { "Keep headlines clear of center subject." },
                fontFamily = BricolageFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = RawColors.TextMuted
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            RawSecondaryButton(
                text = if (describing) "Describing..." else "Auto-describe",
                height = 42.dp,
                leadingIcon = RawIconType.SPARK,
                enabled = !describing,
                onClick = onAutoDescribe,
                modifier = Modifier.weight(1f)
            )
            RawSecondaryButton(
                text = "Edit note",
                height = 42.dp,
                leadingIcon = RawIconType.EDIT,
                enabled = !describing,
                onClick = onEditNote,
                modifier = Modifier.weight(1f)
            )
        }
        if (!isClip) {
            RawSecondaryButton(
                text = if (isCutout) "Refine PNG Cutout (AI)" else "Make PNG Cutout (AI)",
                height = 42.dp,
                leadingIcon = RawIconType.CUTOUT,
                enabled = !describing,
                onClick = onMakeCutout
            )
        }
    }
}

@Composable
private fun CheckerboardBackdrop(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val cell = 8.dp.toPx()
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
