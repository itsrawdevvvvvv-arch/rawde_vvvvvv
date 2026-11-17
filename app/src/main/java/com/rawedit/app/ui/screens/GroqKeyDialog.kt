package com.rawedit.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rawedit.app.domain.AiProvider
import com.rawedit.app.domain.GeminiTaskRoute
import com.rawedit.app.domain.GeminiWaterfallRouter
import com.rawedit.app.domain.GroqCodec
import com.rawedit.app.domain.GroqQuotaReport
import com.rawedit.app.ui.components.BigPrimaryButton
import com.rawedit.app.ui.components.CircleActionButton
import com.rawedit.app.ui.components.RawSecondaryButton
import com.rawedit.app.ui.icons.RawIcon
import com.rawedit.app.ui.icons.RawIconType
import com.rawedit.app.ui.theme.BricolageFamily
import com.rawedit.app.ui.theme.JetBrainsMonoFamily
import com.rawedit.app.ui.theme.RawColors
import kotlinx.coroutines.launch

@Composable
fun AiQuotaDialog(
    currentApiKey: String = "",
    currentModelPref: String = GroqCodec.PREF_AUTO,
    initialQuota: GroqQuotaReport? = null,
    startInSwitchMode: Boolean = false,
    currentGeminiKey: String = "",
    currentAiProvider: String = AiProvider.GEMINI_FREE.id,
    currentGeminiTaskRoute: String = GeminiTaskRoute.BALANCED_AUTO.id,
    onSaveKeySession: (apiKey: String, modelPref: String, quota: GroqQuotaReport?) -> Unit = { _, _, _ -> },
    onSaveModelPref: (String) -> Unit = {},
    onSaveGeminiKey: (String) -> Unit = {},
    onSaveAiProvider: (String) -> Unit = {},
    onSaveGeminiTaskRoute: (String) -> Unit = {},
    onClearKey: () -> Unit = {},
    onClearGeminiKey: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val geminiRouter = remember { GeminiWaterfallRouter(context) }

    var geminiKeyDraft by remember(currentGeminiKey) { mutableStateOf(currentGeminiKey) }
    var revealGeminiKey by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    fun saveAndVerify() {
        val clean = GeminiWaterfallRouter.sanitizeApiKey(geminiKeyDraft)
        if (!GeminiWaterfallRouter.isValidApiKey(clean)) {
            isError = true
            statusMessage = "Enter a valid Gemini key (starts with AIza or AQ)."
            return
        }
        testing = true
        isError = false
        statusMessage = null
        scope.launch {
            val health = geminiRouter.testApiKeyHealth(clean)
            testing = false
            if (health.any { it.isAvailable }) {
                onSaveGeminiKey(clean)
                onDismiss()
            } else {
                // Save anyway if format is valid so user isn't blocked by transient rate checks, or show error
                val errLabel = health.firstOrNull { !it.isAvailable }?.statusLabel ?: "Could not verify key."
                isError = true
                statusMessage = errLabel
            }
        }
    }

    Dialog(
        onDismissRequest = { if (!testing) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(RawColors.CardSurface)
                .border(1.dp, RawColors.Divider, RoundedCornerShape(28.dp))
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Gemini API Key",
                    fontFamily = BricolageFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = RawColors.TextPrimary
                )
                CircleActionButton(
                    icon = RawIconType.CLOSE,
                    size = 36.dp,
                    enabled = !testing,
                    onClick = onDismiss
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Stored locally on your phone.",
                    fontFamily = BricolageFamily,
                    fontSize = 13.5.sp,
                    color = RawColors.TextMuted
                )
                Text(
                    text = "Get free key \u2197",
                    fontFamily = BricolageFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = RawColors.Tan,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey"))
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(intent) }
                    }
                )
            }

            OutlinedTextField(
                value = geminiKeyDraft,
                onValueChange = {
                    geminiKeyDraft = it
                    statusMessage = null
                },
                singleLine = true,
                visualTransformation = if (revealGeminiKey) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                placeholder = {
                    Text("AIza...", color = RawColors.TextMuted, fontSize = 14.sp)
                },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(RawColors.RaisedCard)
                                .clickable {
                                    val pasted = GeminiWaterfallRouter.sanitizeApiKey(
                                        clipboardManager.getText()?.text.orEmpty()
                                    )
                                    if (pasted.isNotBlank()) {
                                        geminiKeyDraft = pasted
                                        statusMessage = null
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "Paste",
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = RawColors.Tan
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { revealGeminiKey = !revealGeminiKey }
                                .padding(8.dp)
                        ) {
                            RawIcon(
                                type = if (revealGeminiKey) RawIconType.EYE_OFF else RawIconType.EYE,
                                size = 17.dp,
                                tint = RawColors.TextMuted
                            )
                        }
                    }
                },
                textStyle = TextStyle(
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 13.5.sp,
                    color = RawColors.TextPrimary
                ),
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RawColors.Tan,
                    unfocusedBorderColor = RawColors.Divider,
                    focusedContainerColor = RawColors.BgPrimary,
                    unfocusedContainerColor = RawColors.BgPrimary,
                    cursorColor = RawColors.Tan
                ),
                modifier = Modifier.fillMaxWidth()
            )

            statusMessage?.let { msg ->
                Text(
                    text = msg,
                    fontFamily = BricolageFamily,
                    fontSize = 13.sp,
                    color = if (isError) RawColors.ErrorCoral else RawColors.Tan
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (currentGeminiKey.isNotBlank()) {
                    RawSecondaryButton(
                        text = "Clear",
                        height = 50.dp,
                        isDanger = true,
                        enabled = !testing,
                        onClick = {
                            onClearGeminiKey()
                            onDismiss()
                        },
                        modifier = Modifier.weight(0.45f)
                    )
                }
                BigPrimaryButton(
                    text = if (testing) "Verifying..." else "Save key",
                    height = 50.dp,
                    enabled = !testing && geminiKeyDraft.isNotBlank(),
                    onClick = { saveAndVerify() },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
