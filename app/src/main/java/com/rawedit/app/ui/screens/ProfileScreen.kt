package com.rawedit.app.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.rawedit.app.data.CacheManager
import com.rawedit.app.data.local.UserPreferences
import com.rawedit.app.data.local.UserProfile
import com.rawedit.app.data.local.VideoEntity
import com.rawedit.app.data.repository.RawEditRepository
import com.rawedit.app.ui.components.CircleAvatarCropperDialog
import com.rawedit.app.ui.components.DomeButtonKind
import com.rawedit.app.ui.components.DomeCard
import com.rawedit.app.ui.components.DomeChip
import com.rawedit.app.ui.components.DomeHeadPart
import com.rawedit.app.ui.components.DomeHeadline
import com.rawedit.app.ui.components.DomeMono
import com.rawedit.app.ui.components.DomeMuted
import com.rawedit.app.ui.components.DomePillButton
import com.rawedit.app.ui.components.DomePillCard
import com.rawedit.app.ui.components.StaggerIn
import com.rawedit.app.ui.icons.RawIcon
import com.rawedit.app.ui.icons.RawIconType
import com.rawedit.app.ui.theme.BricolageFamily
import com.rawedit.app.ui.theme.JetBrainsMonoFamily
import com.rawedit.app.ui.theme.RawColors
import kotlinx.coroutines.launch

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
    onOpenChatbot: () -> Unit = {},
    onToggleDomeHomeOnly: (Boolean) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var editingId by remember { mutableStateOf(false) }
    var nameDraft by remember(userProfile.userName) { mutableStateOf(userProfile.userName) }
    var pendingCropBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var cacheBytes by remember { mutableLongStateOf(0L) }
    var toast by remember { mutableStateOf<String?>(null) }
    var showAbout by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        cacheBytes = runCatching { cacheManager.getTempCacheSizeBytes() }.getOrDefault(0L)
    }
    LaunchedEffect(toast) {
        if (toast != null) {
            kotlinx.coroutines.delay(1900)
            toast = null
        }
    }

    val galleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch { pendingCropBitmap = cacheManager.loadBitmapWithExif(uri) }
        }
    }

    val initials = userProfile.userName
        .trim()
        .split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifBlank { "R" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 28.dp)
    ) {
        StaggerIn(0) {
            DomeHeadline(
                parts = listOf(DomeHeadPart.Plain("Profile")),
                size = 44.dp,
                lineHeightFactor = 0.95f
            )
        }

        // ---------------- creator ID card
        StaggerIn(1) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        androidx.compose.ui.graphics.Brush.linearGradient(
                            listOf(Color(0x40C89F72), Color(0xF5171718))
                        )
                    )
                    .border(1.dp, Color(0x66C89F72), RoundedCornerShape(28.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DomeMono(
                            text = "CREATOR ID · #001",
                            size = 10,
                            color = RawColors.Tan
                        )
                        DomeChip(
                            text = if (editingId) "Close" else "Edit ID",
                            active = true
                        ) { editingId = !editingId }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(CircleShape)
                                .background(RawColors.Tan)
                                .clickable {
                                    galleryPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            val photo = userProfile.profilePhotoPath
                            if (photo != null) {
                                AsyncImage(
                                    model = photo,
                                    contentDescription = "Creator photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(CircleShape)
                                )
                            } else {
                                Text(
                                    text = initials,
                                    fontFamily = BricolageFamily,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = RawColors.Ink
                                )
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = userProfile.userName.ifBlank { "Creator" },
                                fontFamily = BricolageFamily,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = RawColors.Cream,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            DomeMono(
                                text = "@" + handleFor(userProfile.userName),
                                size = 11,
                                color = RawColors.Tan
                            )
                            Spacer(Modifier.height(3.dp))
                            DomeMono(
                                text = "DIR · 1080P OFFLINE · ${videos.size} REELS",
                                size = 9.5f.toInt()
                            )
                        }
                    }

                    if (editingId) {
                        Spacer(Modifier.height(12.dp))
                        BasicTextField(
                            value = nameDraft,
                            onValueChange = { nameDraft = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 12.sp,
                                color = RawColors.Cream
                            ),
                            cursorBrush = SolidColor(RawColors.Tan),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(RawColors.RaisedCard)
                                .border(1.dp, RawColors.Divider, RoundedCornerShape(16.dp))
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        DomePillButton(
                            text = "Save Creator ID",
                            kind = DomeButtonKind.Primary,
                            enabled = nameDraft.isNotBlank(),
                            onClick = {
                                onUpdateName(nameDraft.trim())
                                editingId = false
                                toast = "Creator ID saved"
                            }
                        )
                    }
                }
            }
        }

        // ---------------- settings group
        StaggerIn(2) {
            DomePillCard(
                title = "AI & Quota",
                subtitle = if (userProfile.hasGroqKey) "Groq / Gemini · key connected" else "Add a key for AI review",
                leading = { SettingsIcon(RawIconType.SPARK) },
                trailing = if (userProfile.hasGroqKey) "Connected" else "Add",
                onClick = onOpenGroqKey
            )
        }
        StaggerIn(3) {
            DomePillCard(
                title = "AI Chatbot",
                subtitle = "Script, design & repo assistant",
                leading = { SettingsIcon(RawIconType.GLOBE) },
                trailingIcon = RawIconType.CHEVRON_RIGHT,
                onClick = onOpenChatbot
            )
        }
        StaggerIn(4) {
            DomePillCard(
                title = "Clear cache",
                subtitle = "Temp files: ${formatSize(cacheBytes)}",
                leading = { SettingsIcon(RawIconType.TRASH) },
                trailing = "Clear",
                onClick = {
                    scope.launch {
                        cacheManager.clearAllTempCache()
                        cacheBytes = runCatching { cacheManager.getTempCacheSizeBytes() }.getOrDefault(0L)
                        toast = "Cache cleared"
                    }
                }
            )
        }
        StaggerIn(5) {
            DomePillCard(
                title = "Show character only on Home",
                subtitle = if (userProfile.domeHomeOnly) "Hidden on other screens" else "Character follows you everywhere",
                leading = { SettingsIcon(RawIconType.SPARK) },
                trailing = if (userProfile.domeHomeOnly) "On" else "Off",
                onClick = { onToggleDomeHomeOnly(!userProfile.domeHomeOnly) }
            )
        }

        StaggerIn(6) {
            DomePillCard(
                title = "About raw_edit",
                subtitle = "Version 1.58.0 · offline renderer",
                leading = { SettingsIcon(RawIconType.INFO) },
                trailingIcon = RawIconType.CHEVRON_RIGHT,
                onClick = { showAbout = true }
            )
        }

        StaggerIn(6) {
            Spacer(Modifier.height(6.dp))
            DomeCard(tan = false) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RawIcon(type = RawIconType.LOCK, size = 16.dp, tint = RawColors.Tan)
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Everything stays on this phone",
                            fontFamily = BricolageFamily,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = RawColors.Cream
                        )
                        DomeMuted("Renders, drafts and assets are local only.")
                    }
                }
            }
        }
    }

    if (showAbout) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showAbout = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(RawColors.CardSurface)
                    .border(1.dp, RawColors.Divider, RoundedCornerShape(28.dp))
                    .padding(20.dp)
            ) {
                Text(
                    text = "About raw_edit",
                    fontFamily = BricolageFamily,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = RawColors.Cream
                )
                Spacer(Modifier.height(6.dp))
                DomeMono(text = "Version 1.58.0 · offline renderer + optional AI tools", size = 11)
                Spacer(Modifier.height(14.dp))
                listOf(
                    "Jetpack Compose — Apache-2.0",
                    "Kotlin standard library — Apache-2.0",
                    "AndroidX Media3 — Apache-2.0",
                    "Bricolage Grotesque & JetBrains Mono — OFL 1.1"
                ).forEach {
                    DomeMuted(it, modifier = Modifier.padding(bottom = 4.dp))
                }
                Spacer(Modifier.height(14.dp))
                DomePillButton(
                    text = "Close",
                    kind = DomeButtonKind.Ghost,
                    onClick = { showAbout = false }
                )
            }
        }
    }

    toast?.let { message ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 120.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(RawColors.Cream)
                    .padding(horizontal = 16.dp, vertical = 9.dp)
            ) {
                Text(
                    text = message,
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = RawColors.Ink
                )
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
                    val path = cacheManager.saveProfileAvatar(cropped)
                    onUpdatePhoto(path)
                    toast = "Photo updated"
                }
            }
        )
    }
}

@Composable
private fun SettingsIcon(type: RawIconType) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(RawColors.RaisedCard),
        contentAlignment = Alignment.Center
    ) {
        RawIcon(type = type, size = 16.dp, tint = RawColors.Tan, strokeWidth = 1.9.dp)
    }
}

private fun handleFor(name: String): String {
    val cleaned = name.trim().lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
    return cleaned.ifBlank { "raw_edit" }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1.0) "%.1f MB".format(mb) else "%.0f KB".format(bytes / 1024.0)
}
