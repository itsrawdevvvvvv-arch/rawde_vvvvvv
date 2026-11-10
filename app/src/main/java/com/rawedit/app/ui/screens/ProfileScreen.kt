package com.rawedit.app.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.rawedit.app.BuildConfig
import com.rawedit.app.data.CacheManager
import com.rawedit.app.data.local.UserProfile
import com.rawedit.app.data.local.VideoEntity
import com.rawedit.app.ui.components.CircleAvatarCropperDialog
import com.rawedit.app.ui.components.FormatHelpers
import com.rawedit.app.ui.components.RawEditLogoBadge
import com.rawedit.app.ui.components.UserAvatarView
import com.rawedit.app.ui.icons.RawIcon
import com.rawedit.app.ui.icons.RawIconType
import com.rawedit.app.ui.motion.RawEasings
import com.rawedit.app.ui.motion.RawPressButton
import com.rawedit.app.ui.motion.rememberAnimationsEnabled
import com.rawedit.app.ui.theme.DotGridSurface
import com.rawedit.app.ui.theme.InterFontFamily
import com.rawedit.app.ui.theme.JetBrainsMonoFamily
import com.rawedit.app.ui.theme.RawColors
import com.rawedit.app.ui.theme.RawTypography
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
    var cacheNotice by remember { mutableStateOf<String?>(null) }

    // Photo shrink/expand transition animation (Section 4)
    val avatarAnim = remember { Animatable(1f) }
    LaunchedEffect(userProfile.profilePhotoPath) {
        if (animEnabled) {
            avatarAnim.snapTo(0.35f)
            avatarAnim.animateTo(1f, tween(340, easing = RawEasings.OvershootSettle))
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
    val lastReelDate = videos.firstOrNull()?.createdAt?.let { FormatHelpers.formatDate(it) } ?: "None yet"

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(RawColors.BgPrimary),
        contentPadding = PaddingValues(bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Dot-grid hero with ID Card treatment
        item {
            DotGridSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp)),
                backgroundColor = RawColors.HeroSurface
            ) {
                // Faded giant background letters echoing reference
                Text(
                    text = "ID",
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 180.sp,
                    color = RawColors.TextPrimary.copy(alpha = 0.03f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 12.dp, end = 12.dp)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                ) {
                    RawEditLogoBadge(logoSize = 34.dp)
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Hello again, $displayName!",
                        style = RawTypography.headlineLarge
                    )
                    Spacer(modifier = Modifier.height(18.dp))

                    // Tilted ID Card matching reference
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { rotationZ = -1.6f }
                            .clip(RoundedCornerShape(22.dp))
                            .background(RawColors.RaisedCard)
                            .border(1.dp, RawColors.PillGrey.copy(alpha = 0.7f), RoundedCornerShape(22.dp))
                            .animateContentSize(
                                animationSpec = if (animEnabled) {
                                    spring(dampingRatio = 0.8f, stiffness = 360f)
                                } else {
                                    tween(0)
                                }
                            )
                    ) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(18.dp)
                            ) {
                                Text(
                                    text = "PROFILE  •  REEL MAKER",
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 11.sp,
                                    color = RawColors.TextMuted
                                )
                                Spacer(modifier = Modifier.height(14.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
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
                                            size = 86.dp,
                                            onClick = { showPhotoMenu = true }
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "ID CARD OF",
                                            fontFamily = JetBrainsMonoFamily,
                                            fontSize = 11.sp,
                                            color = RawColors.TextMuted
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.clickable { editingName = !editingName }
                                        ) {
                                            Text(
                                                text = displayName,
                                                style = RawTypography.headlineMedium,
                                                maxLines = 1
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            RawIcon(RawIconType.EDIT, size = 16.dp, tint = RawColors.TextMuted)
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "joined ${FormatHelpers.formatDate(userProfile.joinDateMillis)}",
                                            style = RawTypography.bodyMedium
                                        )
                                    }
                                }

                                AnimatedVisibility(
                                    visible = editingName,
                                    enter = fadeIn(tween(220, easing = RawEasings.Morph)) +
                                        expandVertically(tween(240, easing = RawEasings.Morph)),
                                    exit = fadeOut(tween(140)) +
                                        shrinkVertically(tween(180, easing = RawEasings.Morph))
                                ) {
                                    Column {
                                        Spacer(modifier = Modifier.height(14.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedTextField(
                                                value = nameDraft,
                                                onValueChange = {
                                                    if (it.length <= 32) {
                                                        nameDraft = it
                                                        if (it.trim().isNotEmpty()) {
                                                            onUpdateName(it.trim())
                                                        }
                                                    }
                                                },
                                                singleLine = true,
                                                placeholder = { Text("Your name", color = RawColors.TextMuted) },
                                                shape = RoundedCornerShape(14.dp),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = RawColors.TextPrimary,
                                                    unfocusedBorderColor = RawColors.Divider,
                                                    focusedTextColor = RawColors.TextPrimary,
                                                    unfocusedTextColor = RawColors.TextPrimary
                                                ),
                                                modifier = Modifier.weight(1f)
                                            )
                                            RawPressButton(onClick = { editingName = false }) {
                                                Box(
                                                    modifier = Modifier
                                                        .height(48.dp)
                                                        .clip(RoundedCornerShape(14.dp))
                                                        .background(RawColors.PrimaryBtnBg)
                                                        .padding(horizontal = 14.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text("Done", style = RawTypography.labelLarge, color = RawColors.PrimaryBtnText)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Right vertical strip matching reference ID card
                            Box(
                                modifier = Modifier
                                    .width(34.dp)
                                    .fillMaxHeight()
                                    .background(RawColors.CardSurface)
                                    .padding(vertical = 18.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "ID CARD",
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 10.sp,
                                    color = RawColors.TextMuted,
                                    maxLines = 1,
                                    modifier = Modifier.rotate(90f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "tap photo to update  •  tap name to edit",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp,
                        color = RawColors.TextMuted,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        }

        // Bold Stat Cards (matching reference layout)
        item {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(RawColors.CardSurface)
                        .border(1.dp, RawColors.Divider, RoundedCornerShape(22.dp))
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "${videos.size}",
                            style = RawTypography.displayLarge.copy(fontSize = 38.sp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (videos.size == 1) "reel" else "reels",
                            style = RawTypography.titleMedium,
                            color = RawColors.TextMuted,
                            modifier = Modifier.padding(bottom = 5.dp)
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = FormatHelpers.formatDurationLong(totalVideoSec),
                            style = RawTypography.headlineMedium
                        )
                        Text(
                            text = "Total video time",
                            style = RawTypography.bodyMedium
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(RawColors.CardSurface)
                        .border(1.dp, RawColors.Divider, RoundedCornerShape(22.dp))
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Last reel date", style = RawTypography.bodyMedium)
                    Text(lastReelDate, style = RawTypography.titleMedium)
                }
            }
        }

        // Version & about app
        item {
            Column(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(RawColors.CardSurface)
                    .border(1.dp, RawColors.Divider, RoundedCornerShape(22.dp))
                    .padding(20.dp)
            ) {
                Text("Version & about app", style = RawTypography.bodyMedium)
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = BuildConfig.VERSION_NAME,
                        style = RawTypography.displayLarge.copy(fontSize = 32.sp)
                    )
                    RawPressButton(onClick = { showAboutSheet = true }) {
                        Box(
                            modifier = Modifier
                                .height(42.dp)
                                .clip(RoundedCornerShape(21.dp))
                                .background(RawColors.RaisedCard)
                                .padding(horizontal = 18.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("About app", style = RawTypography.labelLarge)
                        }
                    }
                }
            }
        }

        // Clear cache row
        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                RawPressButton(
                    onClick = {
                        scope.launch {
                            val freed = cacheManager.clearAllTempCache()
                            cacheSizeBytes = cacheManager.getTempCacheSizeBytes()
                            cacheNotice = "Cleared ${FormatHelpers.formatFileSize(freed)} of temp files."
                        }
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(RawColors.CardSurface)
                            .border(1.dp, RawColors.Divider, RoundedCornerShape(20.dp))
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Clear cache", style = RawTypography.titleMedium)
                            Text(
                                text = cacheNotice ?: "Temp files: ${FormatHelpers.formatFileSize(cacheSizeBytes)}",
                                style = RawTypography.bodyMedium
                            )
                        }
                        RawIcon(RawIconType.TRASH, size = 20.dp, tint = RawColors.TextMuted)
                    }
                }
            }
        }
    }

    if (showPhotoMenu) {
        ModalBottomSheet(
            onDismissRequest = { showPhotoMenu = false },
            containerColor = RawColors.HeroSurface,
            contentColor = RawColors.TextPrimary
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Profile photo", style = RawTypography.headlineMedium)
                SheetActionRow(
                    icon = RawIconType.IMAGE,
                    label = "Choose from gallery",
                    onClick = {
                        showPhotoMenu = false
                        galleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                )
                SheetActionRow(
                    icon = RawIconType.CAMERA,
                    label = "Take photo",
                    onClick = {
                        showPhotoMenu = false
                        val file = cacheManager.createCameraTempFile()
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        cameraTempUri = uri
                        cameraLauncher.launch(uri)
                    }
                )
                if (userProfile.profilePhotoPath != null) {
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
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    if (showAboutSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAboutSheet = false },
            containerColor = RawColors.HeroSurface,
            contentColor = RawColors.TextPrimary
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RawEditLogoBadge(logoSize = 36.dp)
                Text("About raw_edit", style = RawTypography.headlineMedium)
                Text(
                    text = "1. Add your MP3 voiceover (and optional SRT subtitles).\n2. Copy the prompt into Claude and paste the returned HTML code back here.\n3. Render a crisp 1920x1080 30 fps MP4 completely offline on your phone.",
                    style = RawTypography.bodyLarge
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text("Open-source & font licenses", style = RawTypography.titleMedium)
                Text(
                    text = "• Bundled Fonts (Montserrat, Open Sans, Poppins, Inter, Bebas Neue, Anton, Oswald, Playfair Display, Space Grotesk, JetBrains Mono): SIL Open Font License (OFL 1.1).\n" +
                        "• Jetpack Compose, Navigation, Room, DataStore, Media3 (ExoPlayer & Transformer), Coroutines, kotlinx.serialization, Coil: Apache License 2.0.\n" +
                        "• 100% free, zero ads, zero servers, zero tracking.",
                    style = RawTypography.bodyMedium
                )
                Spacer(modifier = Modifier.height(24.dp))
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
                }
            }
        )
    }
}
