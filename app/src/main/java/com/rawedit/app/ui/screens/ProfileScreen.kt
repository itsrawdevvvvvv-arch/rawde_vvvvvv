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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import com.rawedit.app.data.BackupManager
import com.rawedit.app.data.CacheManager
import com.rawedit.app.data.local.UserPreferences
import com.rawedit.app.data.repository.RawEditRepository
import com.rawedit.app.domain.GitHubToolFetcher
import java.io.File
import com.rawedit.app.data.local.UserProfile
import com.rawedit.app.data.local.VideoEntity
import com.rawedit.app.domain.AiProvider
import com.rawedit.app.domain.GeminiTaskRoute
import com.rawedit.app.domain.GroqCodec
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
    userPreferences: UserPreferences,
    repository: RawEditRepository,
    onUpdateName: (String) -> Unit,
    onUpdatePhoto: (String?) -> Unit,
    onOpenGroqKey: () -> Unit,
    onSwitchGroqKey: () -> Unit,
    onRefreshGroqQuota: () -> Unit,
    onOpenChatbot: () -> Unit = {}
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

    var githubTokenDraft by remember(userProfile.githubAccessToken) { mutableStateOf(userProfile.githubAccessToken) }
    var githubVerifying by remember { mutableStateOf(false) }
    var githubStatusMsg by remember { mutableStateOf<String?>(null) }
    var githubRepoQuery by remember { mutableStateOf("") }
    var githubFetching by remember { mutableStateOf(false) }
    var githubSkillsList by remember { mutableStateOf(cacheManager.listGithubSkillFiles()) }
    var customFontsList by remember { mutableStateOf(cacheManager.listCustomFonts()) }
    var backupBusy by remember { mutableStateOf(false) }
    var pendingBackupFile by remember { mutableStateOf<File?>(null) }

    val fontPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val res = cacheManager.importCustomFonts(uri)
            res.onSuccess { files ->
                customFontsList = cacheManager.listCustomFonts()
                toastMsg = if (files.size == 1) "Imported font ${files.first().name}" else "Imported ${files.size} fonts"
            }.onFailure { err ->
                toastMsg = err.message ?: "Could not import font."
            }
        }
    }

    val backupSaveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { destUri ->
        val srcFile = pendingBackupFile
        pendingBackupFile = null
        if (destUri != null && srcFile != null && srcFile.exists()) {
            backupBusy = true
            scope.launch {
                val r = cacheManager.saveFileToUri(srcFile, destUri)
                backupBusy = false
                r.onSuccess { toastMsg = "Backup exported successfully" }
                    .onFailure { err -> toastMsg = err.message ?: "Export failed" }
            }
        }
    }

    val backupRestoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            backupBusy = true
            scope.launch {
                val res = BackupManager.restoreFromBackupUri(
                    context = context,
                    cacheManager = cacheManager,
                    userPreferences = userPreferences,
                    repository = repository,
                    sourceUri = uri
                )
                backupBusy = false
                res.onSuccess { sum ->
                    customFontsList = cacheManager.listCustomFonts()
                    githubSkillsList = cacheManager.listGithubSkillFiles()
                    toastMsg = "Restored ${sum.projectsCount} projects, ${sum.fontsCount} fonts, ${sum.skillsCount} skills"
                }.onFailure { err ->
                    toastMsg = err.message ?: "Could not restore backup ZIP"
                }
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

            // Google Gemini AI & Waterfall card
            item {
                Spacer(modifier = Modifier.height(4.dp))
                val hasKey = userProfile.hasGeminiKey
                val routeLabel = when (userProfile.geminiTaskRoute) {
                    GeminiTaskRoute.MAX_QUOTA_SAVER.id -> "Max Quota (1,000 RPD)"
                    GeminiTaskRoute.MAX_QUALITY.id -> "Max Creative (Flash 3.8)"
                    else -> "Balanced Waterfall"
                }
                val subtitle = if (hasKey) {
                    "${userProfile.maskGeminiKey()}  \u2022  1,040 RPD Free Waterfall"
                } else {
                    "Google AI Studio  \u2022  1,040 RPD Free"
                }
                val cardShape = RoundedCornerShape(28.dp)
                Column(
                    modifier = Modifier
                        .padding(horizontal = 18.dp)
                        .fillMaxWidth()
                        .clip(cardShape)
                        .background(RawColors.CardSurface)
                        .clickable(onClick = onOpenGroqKey)
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f).padding(end = 10.dp),
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
                                    text = "Google Gemini Free AI",
                                    fontFamily = BricolageFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = RawColors.TextPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = subtitle,
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 12.sp,
                                    color = if (hasKey) RawColors.Tan else RawColors.TextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (hasKey) {
                            RawSecondaryButton(
                                text = "Manage",
                                height = 42.dp,
                                fillWidth = false,
                                onClick = onOpenGroqKey
                            )
                        } else {
                            RawSecondaryButton(
                                text = "Add key",
                                height = 46.dp,
                                leadingIcon = RawIconType.KEY,
                                fillWidth = false,
                                onClick = onOpenGroqKey
                            )
                        }
                    }

                    if (hasKey) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(RawColors.RaisedCard)
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "FREE TIER WATERFALL POOL",
                                        fontFamily = JetBrainsMonoFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.5.sp,
                                        color = RawColors.TextMuted
                                    )
                                    Text(
                                        text = "1,040 RPD Free  \u2022  $routeLabel",
                                        fontFamily = BricolageFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = RawColors.Tan,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(RawColors.CardSurface)
                                        .clickable(onClick = onSwitchGroqKey)
                                        .padding(horizontal = 12.dp, vertical = 7.dp)
                                ) {
                                    Text(
                                        text = "Configure",
                                        fontFamily = BricolageFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp,
                                        color = RawColors.TextPrimary
                                    )
                                }
                            }

                            Text(
                                text = "Flash-Lite 3.5 (500 RPD) \u2022 Flash-Lite 3.1 (500 RPD) \u2022 Flash 3.6 (20 RPD) \u2022 Flash 3.8 (20 RPD)",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 11.sp,
                                color = RawColors.TextMuted,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            Text(
                                text = "Auto-failover on 429 quota exhaustion. Resets daily at PT midnight.",
                                fontFamily = BricolageFamily,
                                fontSize = 12.sp,
                                color = RawColors.TextMuted
                            )
                        }
                    }
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

            // GitHub Access Token & Skills Fetcher Card
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .padding(horizontal = 18.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(RawColors.CardSurface)
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "GitHub Access Token & Skills",
                                fontFamily = BricolageFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.5.sp,
                                color = RawColors.TextPrimary
                            )
                            Text(
                                text = if (userProfile.hasGithubToken) "Token saved (${userProfile.maskGithubToken()}) • Chatbot & app can fetch GitHub tools" else "Optional PAT so AI Chatbot can fetch private/public skills from GitHub",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 11.5.sp,
                                color = if (userProfile.hasGithubToken) RawColors.Tan else RawColors.TextMuted
                            )
                        }
                        RawSecondaryButton(
                            text = "AI Chat",
                            height = 38.dp,
                            leadingIcon = RawIconType.SPARK,
                            fillWidth = false,
                            onClick = onOpenChatbot
                        )
                    }

                    OutlinedTextField(
                        value = githubTokenDraft,
                        onValueChange = { githubTokenDraft = it },
                        singleLine = true,
                        label = { Text("GitHub Personal Access Token (ghp_...)", fontSize = 11.5.sp) },
                        placeholder = { Text("ghp_... or github_pat_...", fontSize = 13.sp, color = RawColors.TextMuted) },
                        textStyle = TextStyle(fontFamily = JetBrainsMonoFamily, fontSize = 12.5.sp, color = RawColors.TextPrimary),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RawColors.Tan,
                            unfocusedBorderColor = RawColors.Divider,
                            focusedContainerColor = RawColors.RaisedCard,
                            unfocusedContainerColor = RawColors.RaisedCard,
                            cursorColor = RawColors.Tan
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BigPrimaryButton(
                            text = if (githubVerifying) "Verifying..." else "Save & Verify Token",
                            height = 44.dp,
                            enabled = !githubVerifying,
                            onClick = {
                                val clean = githubTokenDraft.trim()
                                if (clean.isEmpty()) {
                                    scope.launch { userPreferences.clearGithubAccessToken() }
                                    githubStatusMsg = "Token cleared."
                                    return@BigPrimaryButton
                                }
                                githubVerifying = true
                                scope.launch {
                                    userPreferences.saveGithubAccessToken(clean)
                                    val res = GitHubToolFetcher.verifyGithubToken(clean)
                                    githubVerifying = false
                                    res.onSuccess { msg ->
                                        githubStatusMsg = msg
                                        toastMsg = "GitHub token saved ($msg)"
                                    }.onFailure { err ->
                                        githubStatusMsg = "Saved locally (verify note: ${err.message})"
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                        if (userProfile.hasGithubToken) {
                            RawSecondaryButton(
                                text = "Clear",
                                height = 44.dp,
                                isDanger = true,
                                fillWidth = false,
                                onClick = {
                                    githubTokenDraft = ""
                                    githubStatusMsg = null
                                    scope.launch { userPreferences.clearGithubAccessToken() }
                                    toastMsg = "GitHub token removed"
                                }
                            )
                        }
                    }

                    githubStatusMsg?.let { msg ->
                        Text(
                            text = msg,
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp,
                            color = RawColors.Tan
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = githubRepoQuery,
                            onValueChange = { githubRepoQuery = it },
                            singleLine = true,
                            placeholder = { Text("owner/repo or GitHub URL to download skill...", fontSize = 12.sp, color = RawColors.TextMuted) },
                            textStyle = TextStyle(fontFamily = JetBrainsMonoFamily, fontSize = 12.sp, color = RawColors.TextPrimary),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = RawColors.Tan,
                                unfocusedBorderColor = RawColors.Divider,
                                focusedContainerColor = RawColors.RaisedCard,
                                unfocusedContainerColor = RawColors.RaisedCard,
                                cursorColor = RawColors.Tan
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        RawSecondaryButton(
                            text = if (githubFetching) "..." else "Fetch",
                            height = 48.dp,
                            fillWidth = false,
                            enabled = !githubFetching && githubRepoQuery.isNotBlank(),
                            onClick = {
                                githubFetching = true
                                scope.launch {
                                    val res = GitHubToolFetcher.fetchAndSaveFromGithub(
                                        queryOrUrl = githubRepoQuery,
                                        githubToken = userProfile.githubAccessToken.ifBlank { githubTokenDraft },
                                        cacheManager = cacheManager
                                    )
                                    githubFetching = false
                                    res.onSuccess { items ->
                                        githubSkillsList = cacheManager.listGithubSkillFiles()
                                        githubRepoQuery = ""
                                        toastMsg = "Downloaded ${items.size} skill(s) from GitHub"
                                    }.onFailure { err ->
                                        toastMsg = err.message ?: "GitHub fetch failed"
                                    }
                                }
                            }
                        )
                    }

                    if (githubSkillsList.isNotEmpty()) {
                        Text(
                            text = "DOWNLOADED GITHUB SKILLS (${githubSkillsList.size})",
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.5.sp,
                            color = RawColors.Tan
                        )
                        githubSkillsList.forEach { sf ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(RawColors.RaisedCard)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = sf.name,
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 11.5.sp,
                                    color = RawColors.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                CircleActionButton(
                                    icon = RawIconType.TRASH,
                                    size = 32.dp,
                                    iconTint = RawColors.ErrorCoral,
                                    onClick = {
                                        cacheManager.deleteGithubSkillFile(sf.name)
                                        githubSkillsList = cacheManager.listGithubSkillFiles()
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Custom Fonts Import Card (.ttf / .otf / .zip)
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .padding(horizontal = 18.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(RawColors.CardSurface)
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Custom Fonts (${customFontsList.size})",
                                fontFamily = BricolageFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.5.sp,
                                color = RawColors.TextPrimary
                            )
                            Text(
                                text = "Import .ttf, .otf, or .zip font packs locally",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 11.5.sp,
                                color = RawColors.TextMuted
                            )
                        }
                        RawSecondaryButton(
                            text = "Import Font",
                            height = 42.dp,
                            leadingIcon = RawIconType.PLUS,
                            fillWidth = false,
                            onClick = { fontPicker.launch(arrayOf("*/*")) }
                        )
                    }
                    if (customFontsList.isNotEmpty()) {
                        customFontsList.forEach { fontFile ->
                            val family = fontFile.nameWithoutExtension.replace(
                                Regex("[-_](Bold|Regular|Medium|SemiBold|Light|Black|ExtraBold)", RegexOption.IGNORE_CASE),
                                ""
                            ).replace('-', ' ').replace('_', ' ').trim()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(RawColors.RaisedCard)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = fontFile.name,
                                        fontFamily = BricolageFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.5.sp,
                                        color = RawColors.TextPrimary
                                    )
                                    Text(
                                        text = "font-family: '$family'",
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 11.sp,
                                        color = RawColors.Tan
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    CircleActionButton(
                                        icon = RawIconType.COPY,
                                        size = 34.dp,
                                        onClick = {
                                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            cb.setPrimaryClip(ClipData.newPlainText("font", family))
                                            toastMsg = "Copied '$family'"
                                        }
                                    )
                                    CircleActionButton(
                                        icon = RawIconType.TRASH,
                                        size = 34.dp,
                                        iconTint = RawColors.ErrorCoral,
                                        onClick = {
                                            cacheManager.deleteCustomFont(fontFile.name)
                                            customFontsList = cacheManager.listCustomFonts()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Local Backup: Export Data & Import Data
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .padding(horizontal = 18.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(RawColors.CardSurface)
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "App Backup (Local Phone Storage)",
                        fontFamily = BricolageFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.5.sp,
                        color = RawColors.TextPrimary
                    )
                    Text(
                        text = "Export or restore all projects, scripts, SRTs, assets, fonts, skills & settings as a local ZIP backup.",
                        fontFamily = BricolageFamily,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = RawColors.TextMuted
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        BigPrimaryButton(
                            text = if (backupBusy) "Working..." else "Export Data",
                            height = 46.dp,
                            enabled = !backupBusy,
                            leadingIcon = RawIconType.DOWNLOAD,
                            onClick = {
                                backupBusy = true
                                scope.launch {
                                    val res = BackupManager.createBackupFile(context, cacheManager, userPreferences, repository)
                                    res.onSuccess { (zipFile, summary) ->
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                            val dl = cacheManager.saveFileToDownloads(zipFile, summary.zipFileName, "application/zip")
                                            backupBusy = false
                                            dl.onSuccess {
                                                toastMsg = "Backup saved to Downloads/raw_edit/${summary.zipFileName}"
                                            }.onFailure {
                                                pendingBackupFile = zipFile
                                                backupSaveLauncher.launch(summary.zipFileName)
                                            }
                                        } else {
                                            backupBusy = false
                                            pendingBackupFile = zipFile
                                            backupSaveLauncher.launch(summary.zipFileName)
                                        }
                                    }.onFailure { err ->
                                        backupBusy = false
                                        toastMsg = err.message ?: "Backup export failed"
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                        RawSecondaryButton(
                            text = "Import Data",
                            height = 46.dp,
                            enabled = !backupBusy,
                            leadingIcon = RawIconType.FILE,
                            onClick = { backupRestoreLauncher.launch(arrayOf("application/zip", "*/*")) },
                            modifier = Modifier.weight(1f)
                        )
                    }
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
