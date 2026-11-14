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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rawedit.app.domain.AiProvider
import com.rawedit.app.domain.GeminiTaskRoute
import com.rawedit.app.domain.GeminiWaterfallRouter
import com.rawedit.app.domain.GroqClient
import com.rawedit.app.domain.GroqCodec
import com.rawedit.app.domain.GroqCooldownException
import com.rawedit.app.domain.GroqHardLimitException
import com.rawedit.app.domain.GroqLimiter
import com.rawedit.app.domain.GroqQuotaReport
import com.rawedit.app.domain.ModelHealthStatus
import com.rawedit.app.ui.components.BigPrimaryButton
import com.rawedit.app.ui.components.CircleActionButton
import com.rawedit.app.ui.components.RawSecondaryButton
import com.rawedit.app.ui.icons.PixelMascotKind
import com.rawedit.app.ui.icons.RawIcon
import com.rawedit.app.ui.icons.RawIconType
import com.rawedit.app.ui.icons.RawPixelMascot
import com.rawedit.app.ui.theme.BricolageFamily
import com.rawedit.app.ui.theme.JetBrainsMonoFamily
import kotlinx.coroutines.launch

// Monochrome EduWave Design Tokens
private object EduWaveColors {
    val CanvasBg = Color(0xFF080808)
    val CardBg = Color(0xFF161616)
    val ElevatedInput = Color(0xFF242424)
    val BorderSubtle = Color(0xFF343434)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextMuted = Color(0xFF9A9A9A)
    val StatusReady = Color(0xFF4E9B6E)
    val StatusBusy = Color(0xFFE28D38)
    val StatusCooldown = Color(0xFFD85858)
    val AccentTan = Color(0xFFCAA07D)
}

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

    // Provider mode state
    var selectedProvider by remember(currentAiProvider) {
        mutableStateOf(
            when (currentAiProvider) {
                AiProvider.GROQ_CLOUD.id -> AiProvider.GROQ_CLOUD
                AiProvider.AUTO_HYBRID.id -> AiProvider.AUTO_HYBRID
                else -> AiProvider.GEMINI_FREE
            }
        )
    }

    // Gemini key & task route state
    var geminiKeyDraft by remember(currentGeminiKey) { mutableStateOf(currentGeminiKey) }
    var revealGeminiKey by remember { mutableStateOf(false) }
    var selectedTaskRoute by remember(currentGeminiTaskRoute) {
        mutableStateOf(
            when (currentGeminiTaskRoute) {
                GeminiTaskRoute.MAX_QUOTA_SAVER.id -> GeminiTaskRoute.MAX_QUOTA_SAVER
                GeminiTaskRoute.MAX_QUALITY.id -> GeminiTaskRoute.MAX_QUALITY
                else -> GeminiTaskRoute.BALANCED_AUTO
            }
        )
    }
    var geminiTesting by remember { mutableStateOf(false) }
    var geminiHealthList by remember { mutableStateOf<List<ModelHealthStatus>>(emptyList()) }

    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isStatusError by remember { mutableStateOf(false) }

    val hasExistingGeminiKey = currentGeminiKey.isNotBlank()

    // Initial check of Gemini model health if key is present
    LaunchedEffect(currentGeminiKey) {
        if (currentGeminiKey.isNotBlank() && GeminiWaterfallRouter.isValidApiKey(currentGeminiKey)) {
            geminiHealthList = geminiRouter.testApiKeyHealth(currentGeminiKey)
        }
    }

    fun runGeminiTestAndSave() {
        val clean = GeminiWaterfallRouter.sanitizeApiKey(geminiKeyDraft)
        if (!GeminiWaterfallRouter.isValidApiKey(clean)) {
            isStatusError = true
            statusMessage = "Gemini key must start with 'AIza' or 'AQ' (get it free at aistudio.google.com)."
            return
        }
        geminiTesting = true
        statusMessage = null
        isStatusError = false
        scope.launch {
            val health = geminiRouter.testApiKeyHealth(clean)
            geminiTesting = false
            geminiHealthList = health
            val available = health.any { it.isAvailable }
            if (available) {
                onSaveGeminiKey(clean)
                isStatusError = false
                val masked = GeminiWaterfallRouter.maskApiKey(clean)
                statusMessage = "Gemini Free Key Verified & Saved ($masked) • 1,040 RPD Waterfall Active"
            } else {
                isStatusError = true
                val errLabel = health.firstOrNull { !it.isAvailable }?.statusLabel
                    ?: health.firstOrNull()?.statusLabel
                    ?: "Verification failed."
                statusMessage = "Gemini Key verification failed: $errLabel"
            }
        }
    }

    fun forceSaveGeminiKey() {
        val clean = GeminiWaterfallRouter.sanitizeApiKey(geminiKeyDraft)
        if (clean.isNotBlank()) {
            onSaveGeminiKey(clean)
            isStatusError = false
            val masked = GeminiWaterfallRouter.maskApiKey(clean)
            statusMessage = "Gemini Key Saved ($masked) • Free Waterfall Active"
        }
    }

    Dialog(
        onDismissRequest = { if (!geminiTesting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(EduWaveColors.CardBg)
                .border(1.dp, EduWaveColors.BorderSubtle, RoundedCornerShape(24.dp))
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Row with Bot mascot and Close Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RawPixelMascot(kind = PixelMascotKind.LENS_BOT, size = 32.dp)
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(EduWaveColors.ElevatedInput)
                            .border(1.dp, EduWaveColors.BorderSubtle, CircleShape)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "GEMINI 1,040 RPD",
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = EduWaveColors.AccentTan
                        )
                    }
                }
                CircleActionButton(
                    icon = RawIconType.CLOSE,
                    size = 36.dp,
                    enabled = !geminiTesting,
                    onClick = onDismiss
                )
            }

            // Title & Subtitle
            Text(
                text = "Google Gemini AI Engine",
                fontFamily = BricolageFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = EduWaveColors.TextPrimary
            )
            Text(
                text = "Zero-downtime Google Gemini Free-Tier waterfall (1,040 RPD) across Flash-Lite and Flash models with instant automatic failover.",
                fontFamily = BricolageFamily,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = EduWaveColors.TextMuted
            )

            // GEMINI FREE-TIER WATERFALL SECTION
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(EduWaveColors.ElevatedInput)
                    .border(1.dp, EduWaveColors.BorderSubtle, RoundedCornerShape(20.dp))
                    .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "GOOGLE AI STUDIO (GEMINI FREE)",
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = EduWaveColors.AccentTan
                        )
                        Text(
                            text = "Get free key ↗",
                            fontFamily = BricolageFamily,
                            fontSize = 12.sp,
                            color = EduWaveColors.AccentTan,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier.clickable {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/docs/api-key"))
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                runCatching { context.startActivity(intent) }
                            }
                        )
                    }

                    // Masked Input with Paste Pill
                    OutlinedTextField(
                        value = geminiKeyDraft,
                        onValueChange = { geminiKeyDraft = it },
                        singleLine = true,
                        visualTransformation = if (revealGeminiKey) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        label = { Text("Gemini API Key (AIza... / AQ...)", fontSize = 11.5.sp) },
                        placeholder = { Text("AIzaSy... or AQ...", color = EduWaveColors.TextMuted, fontSize = 13.sp) },
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(EduWaveColors.CardBg)
                                        .clickable {
                                            val text = clipboardManager.getText()?.text.orEmpty()
                                            val sanitized = GeminiWaterfallRouter.sanitizeApiKey(text)
                                            if (sanitized.startsWith("AIza") || sanitized.startsWith("AQ") || sanitized.length >= 20) {
                                                geminiKeyDraft = sanitized
                                            }
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("Paste", fontSize = 10.5.sp, fontFamily = JetBrainsMonoFamily, color = EduWaveColors.AccentTan)
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable { revealGeminiKey = !revealGeminiKey }
                                        .padding(6.dp)
                                ) {
                                    RawIcon(
                                        type = if (revealGeminiKey) RawIconType.EYE_OFF else RawIconType.EYE,
                                        size = 17.dp,
                                        tint = EduWaveColors.TextMuted
                                    )
                                }
                            }
                        },
                        textStyle = TextStyle(fontFamily = JetBrainsMonoFamily, fontSize = 13.sp, color = EduWaveColors.TextPrimary),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EduWaveColors.AccentTan,
                            unfocusedBorderColor = EduWaveColors.BorderSubtle,
                            focusedContainerColor = EduWaveColors.CardBg,
                            unfocusedContainerColor = EduWaveColors.CardBg,
                            cursorColor = EduWaveColors.AccentTan
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Task Route Selector Pills
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "TASK ROUTE STRATEGY",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 10.5.sp,
                            color = EduWaveColors.TextMuted
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            GeminiTaskRoute.values().forEach { route ->
                                val active = selectedTaskRoute == route
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(if (active) EduWaveColors.AccentTan else EduWaveColors.CardBg)
                                        .border(1.dp, if (active) Color.Transparent else EduWaveColors.BorderSubtle, RoundedCornerShape(50))
                                        .clickable {
                                            selectedTaskRoute = route
                                            onSaveGeminiTaskRoute(route.id)
                                        }
                                        .padding(horizontal = 11.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = route.label,
                                        fontFamily = BricolageFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.5.sp,
                                        color = if (active) Color(0xFF080808) else EduWaveColors.TextPrimary
                                    )
                                }
                            }
                        }
                        Text(
                            text = selectedTaskRoute.detail,
                            fontFamily = BricolageFamily,
                            fontSize = 11.5.sp,
                            color = EduWaveColors.TextMuted
                        )
                    }

                    // 4. Live Model Health & RPD Meter Card
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(EduWaveColors.CardBg)
                            .border(1.dp, EduWaveColors.BorderSubtle, RoundedCornerShape(14.dp))
                            .padding(11.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Text(
                            text = "LIVE MODEL HEALTH & RPD METER",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = EduWaveColors.TextMuted
                        )

                        // Row 1: 3.5-flash-lite
                        val liteUsed = geminiRouter.getUsedToday("gemini-3.5-flash-lite")
                        val liteStatus = geminiHealthList.firstOrNull { it.modelId == "gemini-3.5-flash-lite" }?.statusLabel ?: "READY (500 RPD)"
                        ModelHealthRow(
                            modelName = "gemini-3.5-flash-lite (Workhorse)",
                            statusLabel = liteStatus,
                            usedText = "$liteUsed / 500 RPD",
                            isAvailable = !geminiRouter.isModelInCooldown("gemini-3.5-flash-lite")
                        )

                        // Row 2: 3.1-flash-lite
                        val lite31Used = geminiRouter.getUsedToday("gemini-3.1-flash-lite")
                        val lite31Status = geminiHealthList.firstOrNull { it.modelId == "gemini-3.1-flash-lite" }?.statusLabel ?: "READY (500 RPD)"
                        ModelHealthRow(
                            modelName = "gemini-3.1-flash-lite (Backup Lite)",
                            statusLabel = lite31Status,
                            usedText = "$lite31Used / 500 RPD",
                            isAvailable = !geminiRouter.isModelInCooldown("gemini-3.1-flash-lite")
                        )

                        // Row 3: 3.6-flash
                        val flash36Used = geminiRouter.getUsedToday("gemini-3.6-flash")
                        val flash36Status = geminiHealthList.firstOrNull { it.modelId == "gemini-3.6-flash" }?.statusLabel ?: "READY (20 RPD)"
                        ModelHealthRow(
                            modelName = "gemini-3.6-flash (Creative)",
                            statusLabel = flash36Status,
                            usedText = "$flash36Used / 20 RPD",
                            isAvailable = !geminiRouter.isModelInCooldown("gemini-3.6-flash")
                        )

                        // Row 4: 3.8-flash
                        val flashUsed = geminiRouter.getUsedToday("gemini-3.8-flash")
                        val flashStatus = geminiHealthList.firstOrNull { it.modelId == "gemini-3.8-flash" }?.statusLabel ?: "READY (20 RPD)"
                        ModelHealthRow(
                            modelName = "gemini-3.8-flash (High Reasoning)",
                            statusLabel = flashStatus,
                            usedText = "$flashUsed / 20 RPD",
                            isAvailable = !geminiRouter.isModelInCooldown("gemini-3.8-flash")
                        )

                        Text(
                            text = "Daily free quota resets at 1:30 PM IST (Midnight PT) • Auto-cascades on 503 High Demand",
                            fontFamily = BricolageFamily,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = EduWaveColors.TextMuted
                        )
                    }

                    // Test & Save Button
                    BigPrimaryButton(
                        text = if (geminiTesting) "Verifying with Google..." else "Test & Save Gemini Key",
                        height = 46.dp,
                        leadingIcon = RawIconType.KEY,
                        enabled = !geminiTesting,
                        onClick = { runGeminiTestAndSave() }
                    )

                    // Fallback button if Google API check had a transient error or propagation delay
                    if (isStatusError && geminiKeyDraft.isNotBlank() && !geminiTesting) {
                        RawSecondaryButton(
                            text = "Save Key Anyway (Skip Verification)",
                            height = 38.dp,
                            enabled = !geminiTesting,
                            onClick = { forceSaveGeminiKey() }
                        )
                    }

                    if (hasExistingGeminiKey) {
                        RawSecondaryButton(
                            text = "Clear Gemini key",
                            height = 38.dp,
                            enabled = !geminiTesting,
                            onClick = {
                                onClearGeminiKey()
                                geminiKeyDraft = ""
                                geminiHealthList = emptyList()
                                statusMessage = "Gemini key cleared."
                            }
                        )
                    }
                }



            // Status message banner
            statusMessage?.let { msg ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isStatusError) Color(0xFF2D1616) else Color(0xFF1B261D))
                        .border(1.dp, if (isStatusError) EduWaveColors.StatusCooldown else EduWaveColors.StatusReady, RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = msg,
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.5.sp,
                        lineHeight = 16.5.sp,
                        color = if (isStatusError) Color(0xFFFF8B8B) else Color(0xFF8CE09D)
                    )
                }
            }

            // Done action button
            RawSecondaryButton(
                text = "Done",
                height = 46.dp,
                enabled = !geminiTesting,
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ModelHealthRow(
    modelName: String,
    statusLabel: String,
    usedText: String,
    isAvailable: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = modelName,
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = EduWaveColors.TextPrimary
            )
            Text(
                text = usedText,
                fontFamily = JetBrainsMonoFamily,
                fontSize = 11.sp,
                color = EduWaveColors.TextMuted
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(
                    when {
                        statusLabel.contains("READY") -> Color(0x2E4E9B6E)
                        statusLabel.contains("BUSY") || statusLabel.contains("HIGH DEMAND") -> Color(0x33E28D38)
                        else -> Color(0x33D85858)
                    }
                )
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                text = statusLabel.take(28),
                fontFamily = JetBrainsMonoFamily,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = when {
                    statusLabel.contains("READY") -> EduWaveColors.StatusReady
                    statusLabel.contains("BUSY") || statusLabel.contains("HIGH DEMAND") -> EduWaveColors.StatusBusy
                    else -> EduWaveColors.StatusCooldown
                }
            )
        }
    }
}

// Backward-compatible alias for existing callers
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
    AiQuotaDialog(
        currentApiKey = currentApiKey,
        currentModelPref = currentModelPref,
        initialQuota = initialQuota,
        startInSwitchMode = startInSwitchMode,
        onSaveKeySession = onSaveKeySession,
        onSaveModelPref = onSaveModelPref,
        onClearKey = onClearKey,
        onDismiss = onDismiss
    )
}
