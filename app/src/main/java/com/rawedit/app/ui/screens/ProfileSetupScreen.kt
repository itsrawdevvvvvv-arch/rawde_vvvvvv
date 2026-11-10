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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.rawedit.app.data.CacheManager
import com.rawedit.app.ui.components.BigPrimaryButton
import com.rawedit.app.ui.components.CircleAvatarCropperDialog
import com.rawedit.app.ui.components.RawEditLogoBadge
import com.rawedit.app.ui.components.UserAvatarView
import com.rawedit.app.ui.icons.RawIcon
import com.rawedit.app.ui.icons.RawIconType
import com.rawedit.app.ui.motion.KineticHeading
import com.rawedit.app.ui.motion.RawPressButton
import com.rawedit.app.ui.theme.DotGridSurface
import com.rawedit.app.ui.theme.RawColors
import com.rawedit.app.ui.theme.RawTypography
import kotlinx.coroutines.launch

@Composable
fun ProfileSetupScreen(
    cacheManager: CacheManager,
    onComplete: (name: String, photoPath: String?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var savedPhotoPath by remember { mutableStateOf<String?>(null) }
    var pendingCropBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var cameraTempUri by remember { mutableStateOf<Uri?>(null) }

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
    ) { success ->
        val uri = cameraTempUri
        if (success && uri != null) {
            scope.launch {
                pendingCropBitmap = cacheManager.loadBitmapWithExif(uri)
            }
        }
    }

    DotGridSurface(
        modifier = Modifier.fillMaxSize(),
        backgroundColor = RawColors.BgPrimary,
        dotColor = RawColors.HeroSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                RawEditLogoBadge(logoSize = 36.dp)
                Spacer(modifier = Modifier.height(28.dp))
                KineticHeading(
                    words = listOf("Set", "up", "profile"),
                    style = RawTypography.displayLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Everything stays locally on your phone.",
                    style = RawTypography.bodyLarge,
                    color = RawColors.TextMuted
                )

                Spacer(modifier = Modifier.height(34.dp))

                // Avatar picker section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(RawColors.CardSurface)
                        .border(1.dp, RawColors.Divider, RoundedCornerShape(24.dp))
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    UserAvatarView(
                        name = name.ifBlank { "R" },
                        photoPath = savedPhotoPath,
                        size = 92.dp,
                        onClick = {
                            galleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Profile photo (optional)", style = RawTypography.bodyMedium)
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        RawPressButton(
                            onClick = {
                                galleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                        ) {
                            Row(
                                modifier = Modifier
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(RawColors.RaisedCard)
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                RawIcon(RawIconType.IMAGE, size = 18.dp)
                                Text("Gallery", style = RawTypography.labelLarge)
                            }
                        }
                        RawPressButton(
                            onClick = {
                                val tempFile = cacheManager.createCameraTempFile()
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    tempFile
                                )
                                cameraTempUri = uri
                                cameraLauncher.launch(uri)
                            }
                        ) {
                            Row(
                                modifier = Modifier
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(RawColors.RaisedCard)
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                RawIcon(RawIconType.CAMERA, size = 18.dp)
                                Text("Camera", style = RawTypography.labelLarge)
                            }
                        }
                    }
                    if (savedPhotoPath != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Remove photo",
                            style = RawTypography.bodyMedium,
                            color = RawColors.ErrorCoral,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { savedPhotoPath = null }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                Text("Your name", style = RawTypography.titleMedium)
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 32) name = it },
                    placeholder = { Text("Enter your name", color = RawColors.TextMuted) },
                    singleLine = true,
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

            BigPrimaryButton(
                text = "Continue to raw_edit",
                enabled = name.trim().isNotEmpty(),
                leadingIcon = RawIconType.ARROW_RIGHT,
                onClick = { onComplete(name.trim(), savedPhotoPath) }
            )
        }
    }

    pendingCropBitmap?.let { bmp ->
        CircleAvatarCropperDialog(
            sourceBitmap = bmp,
            onCancel = { pendingCropBitmap = null },
            onConfirmCrop = { cropped ->
                pendingCropBitmap = null
                scope.launch {
                    savedPhotoPath = cacheManager.saveProfileAvatar(cropped)
                }
            }
        )
    }
}
