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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
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
import com.rawedit.app.ui.motion.FeatheredScrollEdges
import com.rawedit.app.ui.motion.RawEasings
import com.rawedit.app.ui.motion.RawPressButton
import com.rawedit.app.ui.motion.rememberAnimationsEnabled
import com.rawedit.app.ui.theme.BricolageCondensedFamily
import com.rawedit.app.ui.theme.BricolageFamily
import com.rawedit.app.ui.theme.JetBrainsMonoFamily
import com.rawedit.app.ui.theme.RawColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    userProfile: UserProfile,
    videos: List<VideoEntity>,
    cacheManager: CacheManager,
    onUpdateName: (String) -> Unit,
    onUpdatePhoto: (String?) -> Unit,
    onOpenApiKey: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val animEnabled = rememberAnimationsEnabled()
    val listState = rememberLazyListState()

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
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 128.dp)
        ) {
            // Hero (.ph) + Overlapping Straight ID Card (.idc)
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 174.dp)
                            .clipToBounds()
                            .background(RawColors.HeroSurface)
                    ) {
                        Box(modifier = Modifier.matchParentSize()) {
                            Text(
                                text = "ID",
                                fontFamily = BricolageFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 196.sp,
                                lineHeight = 196.sp,
                                letterSpacing = (-6).sp,
                                color = RawColors.TextPrimary.copy(alpha = 0.045f),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = (-4).dp, y = (-14).dp)
                            )
                        }

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
                                .padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 38.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp),
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
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Profile",
                                fontFamily = BricolageFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 42.sp,
                                lineHeight = 44.sp,
                                letterSpacing = (-1.4).sp,
                                color = RawColors.TextPrimary
                            )
                        }
                    }

                    // Straight Overlapping ID Card (.idc 28dp borderless + .spine)
                    Box(
                        modifier = Modifier
                            .zIndex(2f)
                            .offset(y = (-24).dp)
                            .padding(horizontal = 18.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(28.dp))
                            .background(RawColors.CardSurface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min)
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(18.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Profile  \u2022  reel maker",
                                        fontFamily = JetBrainsMonoFamily,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 13.sp,
                                        letterSpacing = 0.2.sp,
                                        color = RawColors.Tan
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
                                                fontFamily = BricolageFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 27.sp,
                                                lineHeight = 31.sp,
                                                color = RawColors.TextPrimary,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            RawIcon(
                                                type = RawIconType.EDIT,
                                                size = 16.dp,
                                                tint = RawColors.TextMuted
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "joined ${FormatHelpers.formatDate(userProfile.joinDateMillis)}",
                                            fontFamily = JetBrainsMonoFamily,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 13.sp,
                                            style = TextStyle(fontFeatureSettings = "tnum"),
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
                                            textStyle = TextStyle(
                                                fontFamily = BricolageFamily,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 19.sp,
                                                color = RawColors.TextPrimary
                                            ),
                                            placeholder = {
                                                Text(
                                                    text = "Your name",
                                                    fontFamily = BricolageFamily,
                                                    fontWeight = FontWeight.Medium,
                                                    fontSize = 17.sp,
                                                    color = RawColors.TextMuted
                                                )
                                            },
                                            shape = RoundedCornerShape(20.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = RawColors.AccentFocus,
                                                unfocusedBorderColor = Color.Transparent,
                                                focusedContainerColor = RawColors.RaisedCard,
                                                unfocusedContainerColor = RawColors.RaisedCard,
                                                focusedTextColor = RawColors.TextPrimary,
                                                unfocusedTextColor = RawColors.TextPrimary,
                                                cursorColor = RawColors.Tan
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
                                                    .height(38.dp)
                                                    .clip(CircleShape)
                                                    .background(RawColors.Cream)
                                                    .padding(horizontal = 18.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "Done",
                                                    fontFamily = BricolageFamily,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.5.sp,
                                                    color = RawColors.Ink
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .width(42.dp)
                                    .fillMaxHeight()
                                    .background(RawColors.RaisedCard),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "ID card",
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 11.sp,
                                    letterSpacing = 0.7.sp,
                                    color = RawColors.TextMuted,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier
                                        .wrapContentSize(unbounded = true)
                                        .rotate(-90f)
                                )
                            }
                        }
                    }
                }
            }

            // Stats card (28dp borderless)
            item {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 18.dp)
                        .offset(y = (-8).dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(RawColors.CardSurface)
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column {
                            Text(
                                text = "${videos.size}",
                                fontFamily = BricolageCondensedFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 58.sp,
                                lineHeight = 58.sp,
                                letterSpacing = (-1.6).sp,
                                style = TextStyle(fontFeatureSettings = "tnum"),
                                color = RawColors.TextPrimary
                            )
                            Text(
                                text = "reels made",
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp,
                                color = RawColors.TextMuted
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = FormatHelpers.formatDuration(totalVideoSec),
                                fontFamily = BricolageCondensedFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 42.sp,
                                lineHeight = 44.sp,
                                letterSpacing = (-1.1).sp,
                                style = TextStyle(fontFeatureSettings = "tnum"),
                                color = RawColors.Tan
                            )
                            Text(
                                text = "Total video time",
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp,
                                color = RawColors.TextMuted
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
                        Text(
                            text = "Last reel date",
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            color = RawColors.TextMuted
                        )
                        Text(
                            text = lastReelDate,
                            fontFamily = BricolageFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.5.sp,
                            color = RawColors.TextPrimary
                        )
                    }
                }
            }

            // Google AI Studio Key card (F5)
            item {
                Spacer(modifier = Modifier.height(4.dp))
                val hasKey = userProfile.geminiApiKey.isNotBlank()
                val keySubtitle = if (hasKey) {
                    val tail = userProfile.geminiApiKey.takeLast(4)
                    "Saved (\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022$tail)  \u2022  Gemini 2.0 Flash"
                } else {
                    "Not set  \u2022  optional for Gemini features"
                }
                Row(
                    modifier = Modifier
                        .padding(horizontal = 18.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(RawColors.CardSurface)
                        .clickable(onClick = onOpenApiKey)
                        .padding(horizontal = 18.dp, vertical = 18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f).padding(end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(if (hasKey) RawColors.AccentSoft else RawColors.RaisedCard),
                            contentAlignment = Alignment.Center
                        ) {
                            RawIcon(
                                type = RawIconType.KEY,
                                size = 19.dp,
                                tint = if (hasKey) RawColors.Tan else RawColors.TextMuted
                            )
                        }
                        Column {
                            Text(
                                text = "Google AI Studio Key",
                                fontFamily = BricolageFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = RawColors.TextPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = keySubtitle,
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.5.sp,
                                color = if (hasKey) RawColors.Tan else RawColors.TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    RawSecondaryButton(
                        text = if (hasKey) "Manage" else "Add key",
                        height = 46.dp,
                        fillWidth = false,
                        onClick = onOpenApiKey
                    )
                }
            }

            // Version & about app (28dp borderless)
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .padding(horizontal = 18.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(RawColors.CardSurface)
                        .padding(horizontal = 18.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "Version ${BuildConfig.VERSION_NAME}",
                            fontFamily = BricolageFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp,
                            color = RawColors.TextPrimary
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "offline stage  \u2022  no ads",
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            color = RawColors.TextMuted
                        )
                    }
                    RawSecondaryButton(
                        text = "About app",
                        height = 48.dp,
                        leadingIcon = RawIconType.INFO,
                        fillWidth = false,
                        onClick = { showAboutSheet = true }
                    )
                }
            }

            // Clear cache (28dp borderless)
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .padding(horizontal = 18.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(RawColors.CardSurface)
                        .padding(horizontal = 18.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Clear cache",
                            fontFamily = BricolageFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp,
                            color = RawColors.TextPrimary
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "Temp files: ${FormatHelpers.formatFileSize(cacheSizeBytes)}",
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            style = TextStyle(fontFeatureSettings = "tnum"),
                            color = RawColors.TextMuted
                        )
                    }
                    CircleActionButton(
                        icon = RawIconType.TRASH,
                        size = 40.dp,
                        backgroundColor = RawColors.ErrorSoft,
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

        FeatheredScrollEdges(
            listState = listState,
            topHeight = 40.dp,
            bottomHeight = 124.dp,
            alwaysShowBottom = true
        )

        RawToastBanner(
            message = toastMsg,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 104.dp)
        )
    }

    if (showPhotoMenu) {
        ModalBottomSheet(
            onDismissRequest = { showPhotoMenu = false },
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
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
                    fontFamily = BricolageFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = RawColors.TextPrimary
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
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
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
                    fontFamily = BricolageFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 27.sp,
                    letterSpacing = (-0.7).sp,
                    color = RawColors.TextPrimary
                )
                Text(
                    text = "Version ${BuildConfig.VERSION_NAME}  \u2022  offline render stage  \u2022  no ads",
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = RawColors.TextMuted
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Licenses",
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = RawColors.TextPrimary
                )
                listOf(
                    "Jetpack Compose - Apache-2.0",
                    "Kotlin standard library - Apache-2.0",
                    "AndroidX Media3 - Apache-2.0",
                    "Coil image loading - Apache-2.0",
                    "Bricolage Grotesque - SIL OFL 1.1",
                    "JetBrains Mono - SIL OFL 1.1"
                ).forEachIndexed { i, lic ->
                    if (i > 0) {
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(RawColors.Divider))
                    }
                    Text(
                        text = lic,
                        fontFamily = BricolageFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.5.sp,
                        color = RawColors.TextPrimary,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                BigPrimaryButton(
                    text = "Close",
                    height = 56.dp,
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
