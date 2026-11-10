package com.rawedit.app.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.FileProvider
import com.rawedit.app.BuildConfig
import com.rawedit.app.data.CacheManager
import com.rawedit.app.data.local.UserProfile
import com.rawedit.app.data.local.VideoEntity
import com.rawedit.app.ui.components.BigPrimaryButton
import com.rawedit.app.ui.components.CircleActionButton
import com.rawedit.app.ui.components.CircleAvatarCropperDialog
import com.rawedit.app.ui.components.FormatHelpers
import com.rawedit.app.ui.components.RawSecondaryButton
import com.rawedit.app.ui.components.RawToastBanner
import com.rawedit.app.ui.components.UserAvatarView
import com.rawedit.app.ui.icons.PixelMascotKind
import com.rawedit.app.ui.icons.PixelRawEditLogo
import com.rawedit.app.ui.icons.RawIcon
import com.rawedit.app.ui.icons.RawIconType
import com.rawedit.app.ui.icons.RawPixelMascot
import com.rawedit.app.ui.motion.RawEasings
import com.rawedit.app.ui.motion.RawPressButton
import com.rawedit.app.ui.motion.rememberAnimationsEnabled
import com.rawedit.app.ui.theme.InterFontFamily
import com.rawedit.app.ui.theme.JetBrainsMonoFamily
import com.rawedit.app.ui.theme.RawColors
import com.rawedit.app.ui.theme.RawTypography
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    userProfile: UserProfile,
    videos: List<VideoEntity>,
    cacheManager: CacheManager,
    onUpdateName: (String) -> Unit,
    onUpdatePhoto: (String?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val animEnabled = rememberAnimationsEnabled()

    var editingName by remember { mutableStateOf(false) }
    var nameDraft by remember(userProfile.userName) { mutableStateOf(userProfile.userName) }
    var showPhotoMenu by remember { mutableStateOf(false) }
    var showAboutSheet by remember { mutableStateOf(false) }
    var pendingCropBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var cameraTempUri by remember { mutableStateOf<Uri?>(null) }

    var cacheSizeBytes by remember { mutableLongStateOf(0L) }
    var toastMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(toastMsg) {
        if (toastMsg != null) {
            delay(1900)
            toastMsg = null
        }
    }

    val avatarAnim = remember { Animatable(1f) }
    LaunchedEffect(userProfile.profilePhotoPath) {
        if (animEnabled) {
            avatarAnim.snapTo(0.45f)
            avatarAnim.animateTo(1f, tween(320, easing = RawEasings.OvershootSettle))
        }
    }

    LaunchedEffect(Unit) {
        cacheSizeBytes = cacheManager.getTempCacheSizeBytes()
    }

    val galleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                pendingCropBitmap = cacheManager.loadBitmapWithExif(uri)
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { ok ->
        val uri = cameraTempUri
        if (ok && uri != null) {
            scope.launch {
                pendingCropBitmap = cacheManager.loadBitmapWithExif(uri)
            }
        }
    }

    val displayName = if (editingName) nameDraft.ifBlank { userProfile.userName } else userProfile.userName.ifBlank { "Creator" }
    val totalVideoSec = remember(videos) { videos.sumOf { it.durationSec } }
    val lastReelDate = remember(videos) {
        videos.maxOfOrNull { it.createdAt }?.let { FormatHelpers.formatDate(it) } ?: "-"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RawColors.BgPrimary)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 112.dp)
        ) {
            // Mint Radial Hero (.ph) + Overlapping Straight ID Card (.idc)
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 180.dp)
                            .clipToBounds()
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        RawColors.Mint.copy(alpha = 0.11f),
                                        Color.Transparent
                                    )
                                )
                            )
                    ) {
                        // Watermark "ID" kept inside matchParentSize so long names can wrap safely
                        Box(modifier = Modifier.matchParentSize()) {
                            Text(
                                text = "ID",
                                fontFamily = InterFontFamily,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 176.sp,
                                letterSpacing = (-10).sp,
                                color = RawColors.TextPrimary.copy(alpha = 0.04f),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 6.dp, y = (-12).dp)
                            )
                        }

                        // Bottom hairline separator
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(RawColors.Divider)
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 36.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                PixelRawEditLogo(width = 42.dp, height = 33.dp)
                                RawPixelMascot(
                                    kind = PixelMascotKind.PEEKING_CAT,
                                    size = 54.dp,
                                    noBlink = true
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Hello again, $displayName!",
                                style = RawTypography.headlineLarge.copy(fontSize = 29.sp)
                            )
                        }
                    }

                    // Straight Overlapping ID Card (.idc + .spine)
                    Box(
                        modifier = Modifier
                            .zIndex(2f)
                            .offset(y = (-24).dp)
                            .padding(horizontal = 18.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(RawColors.CardSurface)
                            .border(1.dp, RawColors.DividerStrong, RoundedCornerShape(24.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min)
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "PROFILE  •  REEL MAKER",
                                        fontFamily = JetBrainsMonoFamily,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 10.5.sp,
                                        letterSpacing = 1.0.sp,
                                        color = RawColors.TextPrimary
                                    )
                                    RawPixelMascot(kind = PixelMascotKind.PHANTOM_BUDDY, size = 30.dp)
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.graphicsLayer {
                                            scaleX = avatarAnim.value
                                            scaleY = avatarAnim.value
                                        }
                                    ) {
                                        UserAvatarView(
                                            name = displayName,
                                            photoPath = userProfile.profilePhotoPath,
                                            size = 84.dp,
                                            onClick = { showPhotoMenu = true }
                                        )
                                        CircleActionButton(
                                            icon = RawIconType.EDIT,
                                            size = 30.dp,
                                            onClick = { showPhotoMenu = true },
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .offset(x = 6.dp, y = 4.dp)
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.clickable { editingName = !editingName }
                                        ) {
                                            Text(
                                                text = displayName,
                                                fontFamily = InterFontFamily,
                                                fontWeight = FontWeight.Black,
                                                fontSize = 24.sp,
                                                letterSpacing = (-1.0).sp,
                                                color = RawColors.TextPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            RawIcon(
                                                type = RawIconType.EDIT,
                                                size = 15.dp,
                                                tint = RawColors.TextMuted
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "joined ${FormatHelpers.formatDate(userProfile.joinDateMillis)}",
                                            fontFamily = JetBrainsMonoFamily,
                                            fontSize = 11.sp,
                                            color = RawColors.TextMuted
                                        )
                                    }
                                }

                                AnimatedVisibility(
                                    visible = editingName,
                                    enter = fadeIn(tween(200)) + expandVertically(tween(220, easing = RawEasings.Morph)),
                                    exit = fadeOut(tween(140)) + shrinkVertically(tween(180))
                                ) {
                                    Column {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        OutlinedTextField(
                                            value = nameDraft,
                                            onValueChange = {
                                                if (it.length <= 32) {
                                                    nameDraft = it
                                                }
                                            },
                                            singleLine = true,
                                            placeholder = { Text("Your name", color = RawColors.TextMuted) },
                                            shape = RoundedCornerShape(14.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = RawColors.MintFocus,
                                                unfocusedBorderColor = RawColors.Divider,
                                                focusedContainerColor = RawColors.RaisedCard,
                                                unfocusedContainerColor = RawColors.RaisedCard,
                                                focusedTextColor = RawColors.TextPrimary,
                                                unfocusedTextColor = RawColors.TextPrimary,
                                                cursorColor = RawColors.Mint
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        RawPressButton(
                                            onClick = {
                                                val trimmed = nameDraft.trim()
                                                if (trimmed.isNotEmpty()) {
                                                    onUpdateName(trimmed)
                                                }
                                                editingName = false
                                            }
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .height(34.dp)
                                                    .clip(RoundedCornerShape(99.dp))
                                                    .background(RawColors.Mint)
                                                    .padding(horizontal = 16.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "Done",
                                                    style = RawTypography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                                                    color = RawColors.PrimaryBtnText
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Right vertical "ID CARD" spine (.spine)
                            Box(
                                modifier = Modifier
                                    .width(42.dp)
                                    .fillMaxHeight()
                                    .background(RawColors.RaisedCard),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "ID CARD",
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp,
                                    letterSpacing = 3.5.sp,
                                    color = RawColors.TextMuted,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.rotate(-90f)
                                )
                            }
                        }
                    }
                }
            }

            // Stats card (.card.pad)
            item {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 18.dp)
                        .offset(y = (-6).dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(RawColors.CardSurface)
                        .border(1.dp, RawColors.Divider, RoundedCornerShape(22.dp))
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column {
                            Text(
                                text = "${videos.size}",
                                fontFamily = InterFontFamily,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 48.sp,
                                lineHeight = 48.sp,
                                letterSpacing = (-2.4).sp,
                                color = RawColors.TextPrimary
                            )
                            Text(
                                text = "reels made",
                                style = RawTypography.labelMedium
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = FormatHelpers.formatDurationLong(totalVideoSec),
                                fontFamily = InterFontFamily,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 32.sp,
                                letterSpacing = (-1.4).sp,
                                color = RawColors.TextPrimary
                            )
                            Text(
                                text = "Total video time",
                                style = RawTypography.labelMedium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(RawColors.Divider))
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Last reel date", style = RawTypography.labelMedium)
                        Text(
                            text = lastReelDate,
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = RawColors.TextPrimary
                        )
                    }
                }
            }

            // Version & about app (.card.pr)
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .padding(horizontal = 18.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(RawColors.CardSurface)
                        .border(1.dp, RawColors.Divider, RoundedCornerShape(22.dp))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Version & about app",
                            style = RawTypography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = BuildConfig.VERSION_NAME,
                            style = RawTypography.labelMedium
                        )
                    }
                    RawSecondaryButton(
                        text = "About app",
                        height = 42.dp,
                        leadingIcon = RawIconType.INFO,
                        fillWidth = false,
                        onClick = { showAboutSheet = true }
                    )
                }
            }

            // Clear cache (.card.pr + coral TRASH squircle)
            item {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .padding(horizontal = 18.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(RawColors.CardSurface)
                        .border(1.dp, RawColors.Divider, RoundedCornerShape(22.dp))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Clear cache",
                            style = RawTypography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "Temp files: ${FormatHelpers.formatFileSize(cacheSizeBytes)}",
                            style = RawTypography.labelMedium
                        )
                    }
                    CircleActionButton(
                        icon = RawIconType.TRASH,
                        size = 36.dp,
                        iconTint = RawColors.ErrorCoral,
                        onClick = {
                            scope.launch {
                                cacheManager.clearAllTempCache()
                                cacheSizeBytes = cacheManager.getTempCacheSizeBytes()
                                toastMsg = "Cache cleared"
                            }
                        }
                    )
                }
            }
        }

        RawToastBanner(
            message = toastMsg,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 96.dp)
        )
    }

    if (showPhotoMenu) {
        ModalBottomSheet(
            onDismissRequest = { showPhotoMenu = false },
            containerColor = RawColors.CardSurface,
            contentColor = RawColors.TextPrimary
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Profile photo",
                    style = RawTypography.titleLarge.copy(fontWeight = FontWeight.ExtraBold)
                )
                Spacer(modifier = Modifier.height(8.dp))
                SheetActionRow(
                    icon = RawIconType.IMAGE,
                    label = "Gallery",
                    onClick = {
                        showPhotoMenu = false
                        galleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                )
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(RawColors.Divider))
                SheetActionRow(
                    icon = RawIconType.CAMERA,
                    label = "Camera",
                    onClick = {
                        showPhotoMenu = false
                        val file = cacheManager.createCameraTempFile()
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        cameraTempUri = uri
                        cameraLauncher.launch(uri)
                    }
                )
                if (userProfile.profilePhotoPath != null) {
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(RawColors.Divider))
                    SheetActionRow(
                        icon = RawIconType.TRASH,
                        label = "Remove photo",
                        tint = RawColors.ErrorCoral,
                        onClick = {
                            showPhotoMenu = false
                            onUpdatePhoto(null)
                        }
                    )
                }
                Spacer(modifier = Modifier.height(22.dp))
            }
        }
    }

    if (showAboutSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAboutSheet = false },
            containerColor = RawColors.CardSurface,
            contentColor = RawColors.TextPrimary
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PixelRawEditLogo(width = 42.dp, height = 33.dp)
                Text(
                    text = "About raw_edit",
                    style = RawTypography.headlineMedium.copy(fontWeight = FontWeight.Black)
                )
                Text(
                    text = "Version ${BuildConfig.VERSION_NAME}  •  100% offline  •  no ads",
                    style = RawTypography.labelMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "LICENSES",
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = RawColors.TextPrimary
                )
                listOf(
                    "Jetpack Compose - Apache-2.0",
                    "Kotlin standard library - Apache-2.0",
                    "AndroidX Media3 - Apache-2.0",
                    "Coil image loading - Apache-2.0",
                    "Bundled Google Fonts - SIL OFL 1.1"
                ).forEachIndexed { i, lic ->
                    if (i > 0) {
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(RawColors.Divider))
                    }
                    Text(
                        text = lic,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = RawColors.TextPrimary,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                BigPrimaryButton(
                    text = "Close",
                    onClick = { showAboutSheet = false }
                )
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    pendingCropBitmap?.let { bmp ->
        CircleAvatarCropperDialog(
            sourceBitmap = bmp,
            onCancel = { pendingCropBitmap = null },
            onConfirmCrop = { cropped ->
                pendingCropBitmap = null
                scope.launch {
                    val savedPath = cacheManager.saveProfileAvatar(cropped)
                    onUpdatePhoto(savedPath)
                    toastMsg = "Photo updated"
                }
            }
        )
    }
}
