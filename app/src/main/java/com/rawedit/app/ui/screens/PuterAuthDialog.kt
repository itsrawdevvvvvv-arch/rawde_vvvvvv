package com.rawedit.app.ui.screens

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rawedit.app.domain.PuterAuthBridge
import com.rawedit.app.domain.PuterClient
import com.rawedit.app.domain.PuterCodec
import com.rawedit.app.domain.PuterLoopbackSession
import com.rawedit.app.domain.PuterOutOfCreditsException
import com.rawedit.app.domain.PuterUsageReport
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
fun PuterAuthDialog(
    currentToken: String,
    currentUsername: String,
    currentModelPref: String,
    initialUsage: PuterUsageReport?,
    startInSwitchMode: Boolean,
    onAuthConnected: (token: String, username: String, modelPref: String, usage: PuterUsageReport?) -> Unit,
    onSaveModelPref: (String) -> Unit,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var tokenDraft by remember(currentToken) { mutableStateOf(currentToken) }
    var usernameState by remember(currentUsername) { mutableStateOf(currentUsername) }
    var modelPrefDraft by remember(currentModelPref) {
        mutableStateOf(PuterCodec.normalizeModelPref(currentModelPref))
    }
    var usageState by remember(initialUsage) { mutableStateOf(initialUsage) }

    var refreshingCredits by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var waitingBrowserAuth by remember { mutableStateOf(false) }
    var showManualToken by remember { mutableStateOf(false) }
    var showRawToken by remember { mutableStateOf(false) }

    var activeBrowserSession by remember { mutableStateOf<PuterLoopbackSession?>(null) }
    var activeJob by remember { mutableStateOf<Job?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var statusOk by remember { mutableStateOf(false) }

    val selectedOption = remember(modelPrefDraft) {
        PuterCodec.optionForPref(modelPrefDraft)
    }

    val isSignedIn = currentToken.isNotBlank()
    val isExhausted = usageState?.isExhausted == true

    LaunchedEffect(currentToken) {
        if (currentToken.isNotBlank() && !refreshingCredits) {
            refreshingCredits = true
            val who = PuterClient.fetchWhoami(currentToken).getOrNull()
            if (!who.isNullOrBlank()) {
                usernameState = who
            }
            val latestUsage = PuterClient.fetchUsage(currentToken).getOrNull()
            if (latestUsage != null) {
                usageState = latestUsage
            }
            refreshingCredits = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            activeJob?.cancel()
            activeBrowserSession?.closeQuietly()
        }
    }

    fun refreshUsageNow() {
        val tok = currentToken.ifBlank { tokenDraft.trim() }
        if (tok.isBlank() || refreshingCredits) return
        refreshingCredits = true
        statusMessage = null
        scope.launch {
            val who = PuterClient.fetchWhoami(tok).getOrNull()
            if (!who.isNullOrBlank()) usernameState = who
            val res = PuterClient.fetchUsage(tok)
            refreshingCredits = false
            res.onSuccess { report ->
                usageState = report
                statusOk = !report.isExhausted
                statusMessage = if (report.isExhausted) {
                    "0 credits left on @$usernameState. Switch account below to continue."
                } else {
                    "Credits refreshed: ${report.formattedRemaining()} left (${report.remainingPercent}%)"
                }
            }.onFailure { err ->
                statusOk = false
                statusMessage = err.message ?: "Could not fetch Puter credits."
            }
        }
    }

    fun startChromeSignIn(expectDifferentFromUser: String?) {
        activeJob?.cancel()
        activeBrowserSession?.closeQuietly()
        statusMessage = null
        waitingBrowserAuth = true

        val session = try {
            PuterAuthBridge.startLoopbackSession()
        } catch (t: Throwable) {
            waitingBrowserAuth = false
            statusOk = false
            statusMessage = t.message ?: "Could not open local Puter auth receiver."
            return
        }
        activeBrowserSession = session
        PuterAuthBridge.openUrlInChromeOrBrowser(context, session.authUrl)

        activeJob = scope.launch {
            try {
                val receivedToken = PuterAuthBridge.awaitCallbackToken(session)
                activeBrowserSession = null
                waitingBrowserAuth = false
                if (receivedToken.isNullOrBlank()) {
                    statusOk = false
                    statusMessage = "Browser sign-in timed out or was cancelled. You can retry anytime."
                    return@launch
                }

                testing = true
                val newUser = PuterClient.fetchWhoami(receivedToken).getOrNull().orEmpty().ifBlank { "puter_user" }
                val newUsage = PuterClient.fetchUsage(receivedToken).getOrNull()
                testing = false

                if (!expectDifferentFromUser.isNullOrBlank() &&
                    newUser.equals(expectDifferentFromUser, ignoreCase = true) &&
                    newUsage?.isExhausted == true
                ) {
                    statusOk = false
                    statusMessage = "Chrome signed back into @$newUser which still has 0 credits. Tap 'Log out on Puter in Chrome' first, then sign in with another Google or Puter account."
                    return@launch
                }

                tokenDraft = receivedToken
                usernameState = newUser
                usageState = newUsage
                statusOk = true
                statusMessage = "Signed in as @$newUser"
                onAuthConnected(receivedToken, newUser, modelPrefDraft, newUsage)
            } catch (ce: CancellationException) {
                waitingBrowserAuth = false
                testing = false
                throw ce
            }
        }
    }

    Dialog(
        onDismissRequest = {
            activeJob?.cancel()
            activeBrowserSession?.closeQuietly()
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
                            .background(if (isExhausted) RawColors.ErrorSoft else RawColors.AccentSoft)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isExhausted) "CREDITS DEPLETED" else selectedOption.badgeLabel,
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (isExhausted) RawColors.ErrorCoral else RawColors.Tan
                        )
                    }
                }
                CircleActionButton(
                    icon = RawIconType.CLOSE,
                    size = 34.dp,
                    onClick = {
                        activeJob?.cancel()
                        activeBrowserSession?.closeQuietly()
                        onDismiss()
                    }
                )
            }

            Text(
                text = when {
                    startInSwitchMode || isExhausted -> "Switch Puter Account"
                    isSignedIn -> "Puter.js AI Account"
                    else -> "Sign in with Puter / Google"
                },
                fontFamily = BricolageFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 25.sp,
                lineHeight = 28.sp,
                letterSpacing = (-0.7).sp,
                color = RawColors.TextPrimary
            )

            Text(
                text = if (isSignedIn) {
                    "Powers Studio Palettes, Asset Descriptions, Cutout Edge Check & Frame Auto-Fix with no API key setup needed."
                } else {
                    "Opens Chrome so you can sign up or sign in once with Google or Puter, then redirects straight back signed in to raw_edit."
                },
                style = RawTypography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
                color = RawColors.TextMuted
            )

            // Signed-in status + live Puter.js Credits Meter card
            if (isSignedIn) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(RawColors.RaisedCard)
                        .border(
                            width = 1.dp,
                            color = if (isExhausted) RawColors.ErrorBorder else RawColors.Divider,
                            shape = RoundedCornerShape(22.dp)
                        )
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "@${usernameState.ifBlank { "puter_user" }}",
                                fontFamily = BricolageFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = RawColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Connected via Puter.js",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 11.5.sp,
                                color = RawColors.TextMuted
                            )
                        }
                        CircleActionButton(
                            icon = RawIconType.REFRESH,
                            size = 36.dp,
                            enabled = !refreshingCredits,
                            onClick = { refreshUsageNow() }
                        )
                    }

                    val report = usageState
                    if (refreshingCredits && report == null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                color = RawColors.Tan,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Checking remaining Puter credits...",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 12.sp,
                                color = RawColors.TextMuted
                            )
                        }
                    } else if (report != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Column {
                                Text(
                                    text = report.formattedRemaining(),
                                    fontFamily = BricolageFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 26.sp,
                                    color = when {
                                        report.isExhausted -> RawColors.ErrorCoral
                                        report.isLow -> RawColors.ErrorCoral
                                        else -> RawColors.Tan
                                    }
                                )
                                Text(
                                    text = if (report.monthlyAllowanceRaw > 0.0) {
                                        "left of ${report.formattedMonthlyAllowance()} monthly quota"
                                    } else {
                                        "available Puter balance"
                                    },
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 11.5.sp,
                                    color = RawColors.TextMuted
                                )
                            }
                            if (report.totalCapacityRaw > 0.0) {
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(if (report.isExhausted) RawColors.ErrorSoft else RawColors.AccentSoft)
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "${report.remainingPercent}% LEFT",
                                        fontFamily = JetBrainsMonoFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = if (report.isExhausted || report.isLow) RawColors.ErrorCoral else RawColors.Tan
                                    )
                                }
                            }
                        }

                        if (report.totalCapacityRaw > 0.0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(7.dp)
                                    .clip(CircleShape)
                                    .background(RawColors.BgPrimary)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(report.remainingFraction)
                                        .height(7.dp)
                                        .clip(CircleShape)
                                        .background(if (report.isExhausted || report.isLow) RawColors.ErrorCoral else RawColors.Tan)
                                )
                            }
                        }

                        if (report.purchasedRemainingRaw > 0.0) {
                            Text(
                                text = "Includes ${report.formattedPurchasedRemaining()} add-on credits",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 11.5.sp,
                                color = RawColors.TextMuted
                            )
                        }
                    }

                    if (isExhausted || startInSwitchMode) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(RawColors.ErrorSoft)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = if (isExhausted) {
                                    "Your free Puter credits ran out on @${usernameState.ifBlank { "account" }}. Log out on puter.com in Chrome (if Chrome auto-selects this account), then sign in with another Google or Puter account."
                                } else {
                                    "To switch accounts in Chrome: tap 'Log out on Puter' if Chrome remembers @${usernameState.ifBlank { "account" }}, then sign in with another Google or Puter account."
                                },
                                fontFamily = BricolageFamily,
                                fontSize = 12.5.sp,
                                lineHeight = 17.5.sp,
                                color = RawColors.ErrorCoral
                            )
                            Text(
                                text = "Step 1: Open Puter in Chrome to log out first \u2197",
                                fontFamily = BricolageFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = RawColors.Cream,
                                textDecoration = TextDecoration.Underline,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { PuterAuthBridge.openPuterSiteForAccountLogout(context) }
                                    .padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            if (waitingBrowserAuth) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(RawColors.AccentSoft)
                        .border(1.dp, RawColors.AccentBorder, RoundedCornerShape(18.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        CircularProgressIndicator(
                            color = RawColors.Tan,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Waiting for Chrome sign-in & return...",
                            fontFamily = BricolageFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.5.sp,
                            color = RawColors.Cream
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(RawColors.RaisedCard)
                            .clickable {
                                activeJob?.cancel()
                                activeBrowserSession?.closeQuietly()
                                waitingBrowserAuth = false
                            }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "Cancel",
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            color = RawColors.TextPrimary
                        )
                    }
                }
            }

            // Primary Browser OAuth / Account Switch CTA
            BigPrimaryButton(
                text = when {
                    waitingBrowserAuth -> "Re-open Chrome Sign-In"
                    isSignedIn -> "Switch Account via Chrome / Google"
                    else -> "Continue with Google / Puter"
                },
                height = 54.dp,
                leadingIcon = RawIconType.GLOBE,
                enabled = !testing,
                onClick = {
                    val oldUser = if (isSignedIn) usernameState.ifBlank { null } else null
                    startChromeSignIn(expectDifferentFromUser = oldUser)
                }
            )

            // Model Route selector
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "AI MODEL ROUTE",
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = RawColors.TextMuted
                    )
                    Text(
                        text = "GPT-4o / 5 / Claude",
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
                    PuterCodec.MODEL_OPTIONS.forEach { opt ->
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
                                    statusMessage = null
                                    PuterClient.clearModelCooldowns()
                                    onSaveModelPref(opt.prefId)
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

            // Collapsible manual token entry fallback
            Text(
                text = if (showManualToken) "Hide manual Puter token paste" else "Paste Puter auth token manually \u2197",
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                color = RawColors.TextMuted,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { showManualToken = !showManualToken }
                    .padding(vertical = 2.dp)
            )

            if (showManualToken) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Open copyauth token page in Chrome \u2197",
                        fontFamily = BricolageFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = RawColors.Tan,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { PuterAuthBridge.openUrlInChromeOrBrowser(context, PuterCodec.COPY_AUTH_URL) }
                    )

                    OutlinedTextField(
                        value = tokenDraft,
                        onValueChange = {
                            tokenDraft = it
                            statusMessage = null
                        },
                        singleLine = true,
                        placeholder = {
                            Text(
                                text = "Paste Puter token...",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 13.sp,
                                color = RawColors.TextMuted
                            )
                        },
                        textStyle = TextStyle(
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            color = RawColors.TextPrimary
                        ),
                        visualTransformation = if (showRawToken) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { showRawToken = !showRawToken }
                                    .padding(8.dp)
                            ) {
                                RawIcon(
                                    type = if (showRawToken) RawIconType.EYE_OFF else RawIconType.EYE,
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
                }
            }

            // Test & verify connection row
            if (tokenDraft.trim().isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RawSecondaryButton(
                        text = if (testing) "Testing..." else "Test & Save",
                        height = 44.dp,
                        leadingIcon = RawIconType.BOLT,
                        enabled = !testing,
                        fillWidth = false,
                        onClick = {
                            activeJob?.cancel()
                            testing = true
                            statusMessage = null
                            val clean = PuterCodec.normalizeAuthToken(tokenDraft)
                            val pref = modelPrefDraft
                            PuterClient.clearModelCooldowns()
                            activeJob = scope.launch {
                                try {
                                    val res = PuterClient.testConnectionDetailed(clean, pref)
                                    testing = false
                                    res.onSuccess { info ->
                                        usernameState = info.username
                                        usageState = info.usage
                                        statusOk = true
                                        val leftLabel = info.usage?.let { " \u2022 ${it.formattedRemaining()} left" }.orEmpty()
                                        statusMessage = "Connected as @${info.username} (${info.latencyMs} ms, ${info.modelUsed})$leftLabel"
                                        onAuthConnected(clean, info.username, pref, info.usage)
                                    }.onFailure { err ->
                                        statusOk = false
                                        statusMessage = err.message ?: "Connection failed"
                                        if (err is PuterOutOfCreditsException) {
                                            usageState = PuterClient.fetchUsage(clean).getOrNull() ?: usageState
                                        }
                                    }
                                } catch (ce: CancellationException) {
                                    testing = false
                                    throw ce
                                }
                            }
                        }
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "api.puter.com",
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.5.sp,
                        color = RawColors.TextMuted,
                        maxLines = 1
                    )
                }
            }

            statusMessage?.let { msg ->
                val shape = RoundedCornerShape(14.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(if (statusOk) RawColors.AccentSoft else RawColors.ErrorSoft)
                        .border(
                            width = 1.dp,
                            color = if (statusOk) RawColors.AccentBorder else RawColors.ErrorBorder,
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
                        color = if (statusOk) RawColors.Tan else RawColors.ErrorCoral
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isSignedIn) {
                    RawSecondaryButton(
                        text = "Sign out",
                        height = 50.dp,
                        isDanger = true,
                        onClick = {
                            activeJob?.cancel()
                            activeBrowserSession?.closeQuietly()
                            onSignOut()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                RawSecondaryButton(
                    text = "Done",
                    height = 50.dp,
                    onClick = {
                        activeJob?.cancel()
                        activeBrowserSession?.closeQuietly()
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
