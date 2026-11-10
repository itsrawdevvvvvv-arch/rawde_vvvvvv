package com.rawedit.app.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.rawedit.app.data.CacheManager
import com.rawedit.app.ui.components.BigPrimaryButton
import com.rawedit.app.ui.components.CircleAvatarCropperDialog
import com.rawedit.app.ui.components.RawSecondaryButton
import com.rawedit.app.ui.components.UserAvatarView
import com.rawedit.app.ui.icons.PixelMascotKind
import com.rawedit.app.ui.icons.PixelRawEditLogo
import com.rawedit.app.ui.icons.RawIconType
import com.rawedit.app.ui.icons.RawPixelMascot
import com.rawedit.app.ui.motion.KineticHeading
import com.rawedit.app.ui.theme.BricolageFamily
import com.rawedit.app.ui.theme.JetBrainsMonoFamily
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
                .verticalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, bottom = 120.dp)
        ) {
            // Top bar matching R.setup
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PixelRawEditLogo(width = 42.dp, height = 33.dp)
                RawPixelMascot(kind = PixelMascotKind.PHANTOM_BUDDY, size = 38.dp)
            }

            Spacer(modifier = Modifier.height(6.dp))
            KineticHeading(
                words = listOf("Set", "up", "profile"),
                style = RawTypography.headlineLarge
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Everything stays locally on your phone",
                style = RawTypography.bodyLarge.copy(fontSize = 16.5.sp),
                color = RawColors.TextMuted
            )

            Spacer(modifier = Modifier.height(22.dp))

            // Avatar card (.card.pad with .ava-w Mint halo)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(RawColors.CardSurface)
                    .border(1.dp, RawColors.Divider, RoundedCornerShape(20.dp))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(126.dp)
                        .border(1.dp, RawColors.Mint.copy(alpha = 0.28f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    UserAvatarView(
                        name = name.ifBlank { "R" },
                        photoPath = savedPhotoPath,
                        size = 112.dp,
                        onClick = {
                            galleryPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Profile photo (optional)",
                    style = RawTypography.titleMedium
                )
                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    RawSecondaryButton(
                        text = "Gallery",
                        height = 50.dp,
                        leadingIcon = RawIconType.IMAGE,
                        onClick = {
                            galleryPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                    RawSecondaryButton(
                        text = "Camera",
                        height = 50.dp,
                        leadingIcon = RawIconType.CAMERA,
                        onClick = {
                            val tempFile = cacheManager.createCameraTempFile()
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                tempFile
                            )
                            cameraTempUri = uri
                            cameraLauncher.launch(uri)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (savedPhotoPath != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    RawSecondaryButton(
                        text = "Remove photo",
                        height = 50.dp,
                        isDanger = true,
                        onClick = { savedPhotoPath = null }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Your name + live N/32 mono counter (.lbl)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Your name",
                    fontFamily = BricolageFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = RawColors.TextPrimary
                )
                Text(
                    text = "${name.length}/32",
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    style = TextStyle(fontFeatureSettings = "tnum"),
                    color = RawColors.TextMuted
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 32) name = it },
                placeholder = {
                    Text(
                        "What should we call you?",
                        fontFamily = BricolageFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 18.sp,
                        color = RawColors.TextMuted
                    )
                },
                textStyle = TextStyle(
                    fontFamily = BricolageFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp,
                    color = RawColors.TextPrimary
                ),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RawColors.MintFocus,
                    unfocusedBorderColor = RawColors.DividerStrong,
                    focusedContainerColor = RawColors.RaisedCard,
                    unfocusedContainerColor = RawColors.RaisedCard,
                    focusedTextColor = RawColors.TextPrimary,
                    unfocusedTextColor = RawColors.TextPrimary,
                    cursorColor = RawColors.Mint
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(28.dp))
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                RawPixelMascot(
                    kind = PixelMascotKind.PEEKING_CAT,
                    size = 78.dp,
                    awakeOverride = true
                )
            }
        }

        // Sticky gradient CTA footer (.stk)
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
                text = "Continue to raw_edit",
                height = 62.dp,
                enabled = name.trim().isNotEmpty(),
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
