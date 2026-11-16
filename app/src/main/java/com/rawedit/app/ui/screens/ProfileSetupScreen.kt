package com.rawedit.app.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.rawedit.app.data.CacheManager
import com.rawedit.app.ui.components.CircleAvatarCropperDialog
import com.rawedit.app.ui.components.DomeButtonKind
import com.rawedit.app.ui.components.DomeCard
import com.rawedit.app.ui.components.DomeEyebrow
import com.rawedit.app.ui.components.DomeHeadPart
import com.rawedit.app.ui.components.DomeHeadline
import com.rawedit.app.ui.components.DomeMono
import com.rawedit.app.ui.components.DomeMuted
import com.rawedit.app.ui.components.DomePillButton
import com.rawedit.app.ui.components.StaggerIn
import com.rawedit.app.ui.icons.RawIcon
import com.rawedit.app.ui.icons.RawIconType
import com.rawedit.app.ui.theme.BricolageFamily
import com.rawedit.app.ui.theme.JetBrainsMonoFamily
import com.rawedit.app.ui.theme.RawColors
import kotlinx.coroutines.launch

@Composable
fun ProfileSetupScreen(
    cacheManager: CacheManager,
    onComplete: (name: String, photoPath: String?) -> Unit
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var savedPhotoPath by remember { mutableStateOf<String?>(null) }
    var pendingCropBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val galleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch { pendingCropBitmap = cacheManager.loadBitmapWithExif(uri) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RawColors.BgPrimary)
            .statusBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 18.dp, bottom = 28.dp)
    ) {
        StaggerIn(0) { DomeEyebrow("02 · Profile Setup") }

        StaggerIn(1) {
            DomeHeadline(
                parts = listOf(DomeHeadPart.Plain("Who's\ncreating?")),
                size = 44.dp,
                lineHeightFactor = 0.95f
            )
        }

        StaggerIn(2) {
            DomeCard(tan = true, onClick = { galleryPicker.launch(null) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(RawColors.Tan),
                        contentAlignment = Alignment.Center
                    ) {
                        val path = savedPhotoPath
                        if (path != null) {
                            AsyncImage(
                                model = path,
                                contentDescription = "Profile photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            Text(
                                text = initialsOf(name.ifBlank { "Creator" }),
                                fontFamily = BricolageFamily,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = RawColors.Ink
                            )
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Creator Profile",
                            fontFamily = BricolageFamily,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = RawColors.Cream
                        )
                        DomeMono(text = "Local studio · tap to add photo", size = 10)
                    }
                    RawIcon(type = RawIconType.CAMERA, size = 18.dp, tint = RawColors.Tan)
                }
            }
        }

        StaggerIn(3) {
            BasicTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                textStyle = TextStyle(
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 13.sp,
                    color = RawColors.Cream
                ),
                cursorBrush = SolidColor(RawColors.Tan),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(RawColors.CardSurface)
                    .border(1.dp, RawColors.Divider, RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 14.dp)
                    .padding(bottom = 0.dp),
                decorationBox = { inner ->
                    Box {
                        if (name.isBlank()) {
                            Text(
                                text = "Your name",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 13.sp,
                                color = RawColors.TextMuted
                            )
                        }
                        inner()
                    }
                }
            )
            Spacer(Modifier.height(14.dp))
        }

        StaggerIn(4) {
            DomePillButton(
                text = "Continue",
                kind = DomeButtonKind.Light,
                trailingArrow = true,
                enabled = name.isNotBlank(),
                onClick = { onComplete(name.trim(), savedPhotoPath) }
            )
        }

        StaggerIn(5) {
            Spacer(Modifier.height(10.dp))
            DomeMuted("Your name and photo stay on this phone — nothing is uploaded.")
        }
    }

    pendingCropBitmap?.let { bmp ->
        CircleAvatarCropperDialog(
            sourceBitmap = bmp,
            onCancel = { pendingCropBitmap = null },
            onConfirmCrop = { cropped ->
                pendingCropBitmap = null
                scope.launch { savedPhotoPath = cacheManager.saveProfileAvatar(cropped) }
            }
        )
    }
}

private fun initialsOf(name: String): String =
    name.trim()
        .split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifBlank { "R" }
