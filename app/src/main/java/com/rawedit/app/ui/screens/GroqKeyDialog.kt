package com.rawedit.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rawedit.app.domain.GroqClient
import com.rawedit.app.domain.GroqCodec
import com.rawedit.app.domain.GroqQuotaExhaustedException
import com.rawedit.app.domain.GroqQuotaReport
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
import kotlinx.coroutines.launch

@Composable
fun GroqKeyDialog(
    currentApiKey: String,
    currentModelPref: String,
    initialQuota: GroqQuotaReport?,
    startInSwitchMode: Boolean = false,
    onSaveKeySession: (apiKey: String, modelPref: String, quota: GroqQuotaReport?) -> Unit,
    onSaveModelPref: (String) -> Unit,
    onClearKey: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val hasExistingKey = currentApiKey.isNotBlank()
    var keyDraft by remember(currentApiKey, startInSwitchMode) {
        mutableStateOf(if (startInSwitchMode) "" else currentApiKey)
    }
    var revealKey by remember { mutableStateOf(false) }
    var selectedPref by remember(currentModelPref) {
        mutableStateOf(GroqCodec.normalizeModelPref(currentModelPref))
    }
    var liveQuota by remember(initialQuota, currentApiKey) {
        mutableStateOf(initialQuota ?: GroqClient.getLatestQuota(currentApiKey))
    }
    var testing by remember { mutableStateOf(false) }
    var refreshingQuota by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isStatusError by remember { mutableStateOf(false) }

    val selectedOption = remember(selectedPref) {
        GroqCodec.optionForPref(selectedPref)
    }
    val quotaExhausted = liveQuota?.isExhausted == true

    fun runQuotaRefresh() {
        val targetKey = GroqCodec.normalizeApiKey(
            if (keyDraft.isNotBlank()) keyDraft else currentApiKey
        )
        if (targetKey.isEmpty() || refreshingQuota) return
        refreshingQuota = true
        statusMessage = null
        scope.launch {
            val res = GroqClient.refreshQuota(targetKey, selectedPref)
            refreshingQuota = false
            res.onSuccess { report ->
                liveQuota = report
                onSaveKeySession(targetKey, selectedPref, report)
                isStatusError = report.isExhausted
                statusMessage = if (report.isExhausted) {
                    "Quota exhausted on ${GroqCodec.maskApiKey(targetKey)}. Paste another Groq API key below."
                } else {
                    "Quota updated: ${report.formattedRequestsSummary()} | ${report.formattedTokensSummary()}"
                }
            }.onFailure { err ->
                isStatusError = true
                if (err is GroqQuotaExhaustedException && err.quota != null) {
                    liveQuota = err.quota
                    onSaveKeySession(targetKey, selectedPref, err.quota)
                }
                statusMessage = err.message ?: "Could not check Groq quota."
            }
        }
    }

    fun runTestAndSave() {
        val normalized = GroqCodec.normalizeApiKey(keyDraft)
        if (normalized.isEmpty()) {
            isStatusError = true
            statusMessage = "Paste your Groq API key (starts with gsk_) first."
            return
        }
        if (testing) return
        testing = true
        isStatusError = false
        statusMessage = null
        scope.launch {
            val res = GroqClient.testConnectionDetailed(normalized, selectedPref)
            testing = false
            res.onSuccess { info ->
                val quota = info.quota ?: GroqClient.getLatestQuota(normalized)
                liveQuota = quota
                onSaveKeySession(normalized, selectedPref, quota)
                isStatusError = false
                val fallbackNote = if (info.usedFallback) " [fallback]" else ""
                val quotaNote = quota?.takeIf { it.hasTelemetry }?.let { " | ${it.bannerLabel()}" }.orEmpty()
                statusMessage = "Connected in ${info.latencyMs}ms (${info.modelUsed}$fallbackNote, ${info.availableModelsCount} models)$quotaNote"
            }.onFailure { err ->
                isStatusError = true
                if (err is GroqQuotaExhaustedException && err.quota != null) {
                    liveQuota = err.quota
                }
                statusMessage = err.message ?: "Connection failed."
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
                .clip(RoundedCornerShape(32.dp))
                .background(RawColors.CardSurface)
                .verticalScroll(rememberScrollState())
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RawPixelMascot(kind = PixelMascotKind.LENS_BOT, size = 34.dp)
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (quotaExhausted) RawColors.ErrorSoft else RawColors.AccentSoft)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (quotaExhausted) "QUOTA EXHAUSTED" else selectedOption.badgeLabel,
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.5.sp,
                            color = if (quotaExhausted) RawColors.ErrorCoral else RawColors.Tan
                        )
                    }
                }
                CircleActionButton(
                    icon = RawIconType.CLOSE,
                    size = 38.dp,
                    enabled = !testing,
                    onClick = onDismiss
                )
            }

            Text(
                text = if (startInSwitchMode || quotaExhausted) "Switch Groq API Key" else "Groq API Key",
                fontFamily = BricolageFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                color = RawColors.TextPrimary
            )

            Text(
                text = if (startInSwitchMode || quotaExhausted) {
                    "Your current key hit its daily/minute quota. Create or copy a fresh gsk_... key in Groq Console and paste it below to keep inspecting frames, describing assets, and generating palettes."
                } else {
                    "Fast Llama 4 Vision & Llama 3.3 / GPT-OSS reasoning via api.groq.com. Offline render never leaves your phone."
                },
                fontFamily = BricolageFamily,
                fontSize = 13.5.sp,
                lineHeight = 19.sp,
                color = RawColors.TextMuted
            )

            Text(
                text = "Get free key at console.groq.com/keys \u2197",
                fontFamily = BricolageFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = RawColors.Tan,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable {
                    GroqCodec.openUrlInChromeOrBrowser(context, GroqCodec.KEYS_CONSOLE_URL)
                }
            )

            // Live Groq Quota & Key Meter Card
            if (hasExistingKey) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(RawColors.RaisedCard)
                        .border(
                            width = 1.dp,
                            color = if (quotaExhausted) RawColors.ErrorBorder else RawColors.DividerStrong,
                            shape = RoundedCornerShape(20.dp)
                        )
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
                                text = "ACTIVE KEY ${GroqCodec.maskApiKey(currentApiKey)}",
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.5.sp,
                                color = RawColors.TextMuted
                            )
                            Text(
                                text = liveQuota?.bannerLabel() ?: "Tap refresh to check quota",
                                fontFamily = BricolageFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 19.sp,
                                color = if (quotaExhausted || liveQuota?.isLow == true) {
                                    RawColors.ErrorCoral
                                } else {
                                    RawColors.Tan
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(RawColors.CardSurface)
                                .clickable(enabled = !refreshingQuota && !testing) { runQuotaRefresh() }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = if (refreshingQuota) "..." else "Refresh",
                                fontFamily = BricolageFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp,
                                color = RawColors.TextPrimary
                            )
                        }
                    }

                    val q = liveQuota
                    if (q != null && q.hasTelemetry) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape)
                                .background(RawColors.BgPrimary)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(q.primaryFraction)
                                    .height(6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (quotaExhausted || q.isLow) RawColors.ErrorCoral else RawColors.Tan
                                    )
                            )
                        }
                        Text(
                            text = "Requests: ${q.formattedRequestsSummary()}",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.5.sp,
                            color = RawColors.TextMuted
                        )
                        Text(
                            text = "Tokens: ${q.formattedTokensSummary()}",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.5.sp,
                            color = RawColors.TextMuted
                        )
                    }

                    if (quotaExhausted) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(RawColors.ErrorSoft)
                                .clickable {
                                    GroqCodec.openUrlInChromeOrBrowser(context, GroqCodec.LIMITS_CONSOLE_URL)
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RawIcon(RawIconType.WARN, size = 15.dp, tint = RawColors.ErrorCoral)
                            Text(
                                text = "Quota depleted. Paste a new gsk_... key below or view limits in Groq Console.",
                                fontFamily = BricolageFamily,
                                fontSize = 12.sp,
                                color = RawColors.ErrorCoral
                            )
                        }
                    }
                }
            }

            // API Key Input
            OutlinedTextField(
                value = keyDraft,
                onValueChange = { keyDraft = it },
                singleLine = true,
                visualTransformation = if (revealKey) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                label = {
                    Text(
                        text = if (startInSwitchMode) "New Groq API key (gsk_...)" else "Groq API key (gsk_...)",
                        fontSize = 12.sp
                    )
                },
                placeholder = {
                    Text(
                        text = "gsk_...",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 13.5.sp,
                        color = RawColors.TextMuted
                    )
                },
                trailingIcon = {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { revealKey = !revealKey }
                            .padding(8.dp)
                    ) {
                        RawIcon(
                            type = if (revealKey) RawIconType.EYE_OFF else RawIconType.EYE,
                            size = 18.dp,
                            tint = RawColors.TextMuted
                        )
                    }
                },
                textStyle = TextStyle(
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 13.5.sp,
                    color = RawColors.TextPrimary
                ),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RawColors.AccentFocus,
                    unfocusedBorderColor = RawColors.DividerStrong,
                    focusedContainerColor = RawColors.RaisedCard,
                    unfocusedContainerColor = RawColors.RaisedCard,
                    cursorColor = RawColors.Tan
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // Model route chips
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "GROQ MODEL ROUTE",
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = RawColors.TextMuted
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GroqCodec.MODEL_OPTIONS.forEach { opt ->
                        val selected = selectedPref == opt.prefId
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (selected) RawColors.Tan else RawColors.RaisedCard)
                                .border(
                                    width = if (selected) 0.dp else 1.dp,
                                    color = if (selected) Color.Transparent else RawColors.DividerStrong,
                                    shape = CircleShape
                                )
                                .clickable {
                                    selectedPref = opt.prefId
                                    onSaveModelPref(opt.prefId)
                                }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = opt.shortLabel,
                                fontFamily = BricolageFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp,
                                color = if (selected) RawColors.Ink else RawColors.TextPrimary
                            )
                        }
                    }
                }
                Text(
                    text = selectedOption.detail,
                    fontFamily = BricolageFamily,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = RawColors.TextMuted
                )
            }

            statusMessage?.let { msg ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isStatusError) RawColors.ErrorSoft else RawColors.AccentSoft)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = msg,
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.5.sp,
                        lineHeight = 16.5.sp,
                        color = if (isStatusError) RawColors.ErrorCoral else RawColors.Tan
                    )
                }
            }

            BigPrimaryButton(
                text = if (testing) "Testing key..." else "Test & Save Key",
                height = 52.dp,
                leadingIcon = RawIconType.KEY,
                enabled = !testing && !refreshingQuota,
                onClick = { runTestAndSave() }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (hasExistingKey) {
                    RawSecondaryButton(
                        text = "Clear key",
                        height = 46.dp,
                        enabled = !testing,
                        onClick = onClearKey,
                        modifier = Modifier.weight(1f)
                    )
                }
                RawSecondaryButton(
                    text = "Done",
                    height = 46.dp,
                    enabled = !testing,
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
