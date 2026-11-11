package com.rawedit.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rawedit.app.domain.GeminiClient
import com.rawedit.app.domain.GeminiInteractionsCodec
import com.rawedit.app.ui.components.BigPrimaryButton
import com.rawedit.app.ui.components.CircleActionButton
import com.rawedit.app.ui.components.RawSecondaryButton
import com.rawedit.app.ui.icons.PixelMascotKind
import com.rawedit.app.ui.icons.RawIcon
import com.rawedit.app.ui.icons.RawIconType
import com.rawedit.app.ui.icons.RawPixelMascot
import com.rawedit.app.ui.theme.BricolageFamily
import com.rawedit.app.ui.theme.JetBrainsMonoFamily
import com.rawedit.app.ui.theme.RawColors
import com.rawedit.app.ui.theme.RawTypography
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun GeminiApiKeyDialog(
    currentKey: String,
    currentModelPref: String,
    onSaveKey: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var keyDraft by remember(currentKey) { mutableStateOf(currentKey) }
    var modelPrefDraft by remember(currentModelPref) {
        mutableStateOf(GeminiInteractionsCodec.normalizeModelPref(currentModelPref))
    }
    var showKey by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var activeTestJob by remember { mutableStateOf<Job?>(null) }
    var testMessage by remember { mutableStateOf<String?>(null) }
    var testOk by remember { mutableStateOf(false) }

    val selectedOption = remember(modelPrefDraft) {
        GeminiInteractionsCodec.optionForPref(modelPrefDraft)
    }

    DisposableEffect(Unit) {
        onDispose { activeTestJob?.cancel() }
    }

    Dialog(
        onDismissRequest = {
            activeTestJob?.cancel()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(RawColors.CardSurface)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    RawPixelMascot(kind = PixelMascotKind.LENS_BOT, size = 32.dp)
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(RawColors.AccentSoft)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = selectedOption.badgeLabel,
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = RawColors.Tan
                        )
                    }
                }
                CircleActionButton(
                    icon = RawIconType.CLOSE,
                    size = 34.dp,
                    onClick = {
                        activeTestJob?.cancel()
                        onDismiss()
                    }
                )
            }

            Text(
                text = "Google AI Studio Key",
                fontFamily = BricolageFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 25.sp,
                lineHeight = 28.sp,
                letterSpacing = (-0.7).sp,
                color = RawColors.TextPrimary
            )

            Text(
                text = "Used only when you trigger Palette generation, Asset auto-description, Cutout check, or Contact Sheet AI review. Stored locally on your phone.",
                style = RawTypography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
                color = RawColors.TextMuted
            )

            Text(
                text = "Get key in AI Studio \u2197",
                fontFamily = BricolageFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.5.sp,
                color = RawColors.Tan,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable {
                        runCatching {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://aistudio.google.com/apikey")
                            )
                            context.startActivity(intent)
                        }
                    }
                    .padding(vertical = 4.dp)
            )

            OutlinedTextField(
                value = keyDraft,
                onValueChange = {
                    keyDraft = it
                    testMessage = null
                },
                singleLine = true,
                placeholder = {
                    Text(
                        text = "AIzaSy...",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 13.5.sp,
                        color = RawColors.TextMuted
                    )
                },
                textStyle = TextStyle(
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.5.sp,
                    color = RawColors.TextPrimary
                ),
                visualTransformation = if (showKey) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { showKey = !showKey }
                            .padding(8.dp)
                    ) {
                        RawIcon(
                            type = if (showKey) RawIconType.EYE_OFF else RawIconType.EYE,
                            size = 18.dp,
                            tint = RawColors.TextMuted
                        )
                    }
                },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RawColors.AccentFocus,
                    unfocusedBorderColor = RawColors.DividerStrong,
                    focusedContainerColor = RawColors.RaisedCard,
                    unfocusedContainerColor = RawColors.RaisedCard,
                    focusedTextColor = RawColors.TextPrimary,
                    unfocusedTextColor = RawColors.TextPrimary,
                    cursorColor = RawColors.Tan
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "MODEL ROUTE",
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = RawColors.TextMuted
                    )
                    Text(
                        text = "Anti-timeout fallback",
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp,
                        color = RawColors.Tan
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GeminiInteractionsCodec.MODEL_OPTIONS.forEach { opt ->
                        val selected = modelPrefDraft == opt.prefId
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (selected) RawColors.Tan else RawColors.RaisedCard)
                                .border(
                                    width = 1.dp,
                                    color = if (selected) RawColors.Tan else RawColors.Divider,
                                    shape = CircleShape
                                )
                                .clickable(enabled = !testing) {
                                    modelPrefDraft = opt.prefId
                                    testMessage = null
                                    GeminiClient.clearModelCooldowns()
                                }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = opt.shortLabel,
                                fontFamily = BricolageFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp,
                                color = if (selected) RawColors.Ink else RawColors.TextPrimary,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                Text(
                    text = selectedOption.detail,
                    fontFamily = BricolageFamily,
                    fontSize = 12.sp,
                    lineHeight = 16.5.sp,
                    color = RawColors.TextMuted
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RawSecondaryButton(
                    text = if (testing) "Testing..." else "Test key",
                    height = 44.dp,
                    leadingIcon = RawIconType.BOLT,
                    enabled = !testing && keyDraft.trim().isNotEmpty(),
                    fillWidth = false,
                    onClick = {
                        activeTestJob?.cancel()
                        testing = true
                        testMessage = null
                        val clean = keyDraft.trim()
                        val pref = modelPrefDraft
                        GeminiClient.clearModelCooldowns()
                        activeTestJob = scope.launch {
                            try {
                                val res = GeminiClient.testConnectionDetailed(clean, pref)
                                testing = false
                                res.onSuccess { info ->
                                    testOk = true
                                    val fallbackNote = if (info.usedFallback) {
                                        "\nAuto-switched around busy primary model."
                                    } else {
                                        ""
                                    }
                                    testMessage = "Connected (${info.latencyMs} ms)  \u2022  ${info.modelUsed}$fallbackNote"
                                }.onFailure { err ->
                                    testOk = false
                                    testMessage = err.message ?: "Connection failed"
                                }
                            } catch (ce: CancellationException) {
                                testing = false
                                throw ce
                            }
                        }
                    }
                )
                if (testing) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(RawColors.ErrorSoft)
                            .clickable {
                                activeTestJob?.cancel()
                                testing = false
                                testOk = false
                                testMessage = "Connection test stopped."
                            }
                            .padding(horizontal = 11.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Stop",
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            color = RawColors.ErrorCoral
                        )
                    }
                } else {
                    Text(
                        text = "Interactions API",
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.5.sp,
                        color = RawColors.TextMuted,
                        maxLines = 1
                    )
                }
            }

            testMessage?.let { msg ->
                val shape = RoundedCornerShape(14.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(if (testOk) RawColors.AccentSoft else RawColors.ErrorSoft)
                        .border(
                            width = 1.dp,
                            color = if (testOk) RawColors.AccentBorder else RawColors.ErrorBorder,
                            shape = shape
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = msg,
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = if (testOk) RawColors.Tan else RawColors.ErrorCoral
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (currentKey.isNotBlank()) {
                    RawSecondaryButton(
                        text = "Remove",
                        height = 50.dp,
                        isDanger = true,
                        onClick = {
                            activeTestJob?.cancel()
                            onSaveKey("", modelPrefDraft)
                        },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    RawSecondaryButton(
                        text = "Cancel",
                        height = 50.dp,
                        onClick = {
                            activeTestJob?.cancel()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                BigPrimaryButton(
                    text = "Save",
                    height = 50.dp,
                    enabled = !testing,
                    onClick = {
                        activeTestJob?.cancel()
                        onSaveKey(keyDraft.trim(), modelPrefDraft)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
