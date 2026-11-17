package com.rawedit.app.ui.screens

import android.annotation.SuppressLint
import android.os.Build
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.rawedit.app.data.CacheManager
import com.rawedit.app.domain.AspectPreset
import com.rawedit.app.domain.OpenDesignDeliverableType
import com.rawedit.app.domain.OpenDesignEngine
import com.rawedit.app.domain.PaletteState
import com.rawedit.app.render.LocalAssetServer
import com.rawedit.app.ui.components.BigPrimaryButton
import com.rawedit.app.ui.components.CircleActionButton
import com.rawedit.app.ui.components.RawToastBanner
import com.rawedit.app.ui.icons.RawIconType
import com.rawedit.app.ui.theme.BricolageFamily
import com.rawedit.app.ui.theme.JetBrainsMonoFamily
import com.rawedit.app.ui.theme.RawColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun OpenDesignScreen(
    activePalette: PaletteState,
    cacheManager: CacheManager,
    onCreateMotionProject: (htmlCode: String, title: String, aspect: AspectPreset) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedType by remember { mutableStateOf(OpenDesignDeliverableType.IMAGE) }
    var selectedAspect by remember { mutableStateOf(AspectPreset.ALL_PRESETS[3]) } // 1:1 default for design
    var kicker by remember { mutableStateOf("RAWDEV STUDIO") }
    var headline by remember { mutableStateOf("DESIGN & MOTION\nWITHOUT LIMITS") }
    var subtext by remember { mutableStateOf("Local-first Open Design engine for posters, slides, UI & 1080p motion reels.") }

    val bgHex = activePalette.colors.getOrNull(2)?.hex ?: "#111114"
    val accentHex = activePalette.colors.getOrNull(1)?.hex ?: "#C89F72"
    val textHex = activePalette.colors.getOrNull(0)?.hex ?: "#F3EFE6"

    var exporting by remember { mutableStateOf(false) }
    var toastMsg by remember { mutableStateOf<String?>(null) }
    var previewWebView by remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(toastMsg) {
        if (toastMsg != null) {
            delay(2200)
            toastMsg = null
        }
    }

    val htmlCode = remember(selectedType, selectedAspect, kicker, headline, subtext, bgHex, accentHex, textHex) {
        OpenDesignEngine.buildManualDesignHtml(
            type = selectedType,
            width = selectedAspect.width,
            height = selectedAspect.height,
            kicker = kicker,
            headline = headline,
            subtext = subtext,
            bgHex = bgHex,
            accentHex = accentHex,
            textHex = textHex
        )
    }
    val latestHtml by rememberUpdatedState(htmlCode)

    val server = remember(cacheManager, selectedAspect) {
        LocalAssetServer(context, cacheManager, selectedAspect.width, selectedAspect.height) {
            LocalAssetServer(context, cacheManager, selectedAspect.width, selectedAspect.height) { "" }.buildInjectedHtml(latestHtml)
        }
    }

    LaunchedEffect(htmlCode) {
        previewWebView?.loadUrl(LocalAssetServer.ENTRY_URL)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RawColors.BgPrimary)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircleActionButton(icon = RawIconType.BACK, size = 40.dp, onClick = onBack)
                    Column(modifier = Modifier.weight(1f)) {
                        com.rawedit.app.ui.components.DomeHeadline(
                            parts = listOf(
                                com.rawedit.app.ui.components.DomeHeadPart.Plain("Design\n"),
                                com.rawedit.app.ui.components.DomeHeadPart.Tag("studio")
                            ),
                            size = 34.dp
                        )
                        Text("Local-first DESIGN.md + Motion.md", fontFamily = JetBrainsMonoFamily, fontSize = 11.sp, color = RawColors.Tan)
                    }
                }
            }

            // Deliverable Type Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OpenDesignDeliverableType.entries.forEach { t ->
                    val isSel = selectedType == t
                    Surface(
                        onClick = { selectedType = t },
                        shape = CircleShape,
                        color = if (isSel) RawColors.Tan else RawColors.CardSurface
                    ) {
                        Text(
                            text = t.label,
                            fontFamily = BricolageFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp,
                            color = if (isSel) RawColors.Ink else RawColors.TextPrimary,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            // Aspect Ratio Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                AspectPreset.ALL_PRESETS.take(5).forEach { ap ->
                    val isSel = selectedAspect.id == ap.id
                    Surface(
                        onClick = { selectedAspect = ap },
                        shape = CircleShape,
                        color = if (isSel) RawColors.Cream else RawColors.RaisedCard
                    ) {
                        Text(
                            text = ap.label,
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (isSel) RawColors.Ink else RawColors.TextPrimary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Live Preview Stage
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .aspectRatio(selectedAspect.ratioFloat)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, RawColors.Divider, RoundedCornerShape(16.dp))
                ) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                server.configureSandboxedWebView(this)
                                settings.useWideViewPort = true
                                settings.loadWithOverviewMode = true
                                setInitialScale(1)
                                loadUrl(LocalAssetServer.ENTRY_URL)
                                previewWebView = this
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Manual Design Inputs
            OutlinedTextField(
                value = kicker,
                onValueChange = { kicker = it },
                label = { Text("Badge / Kicker") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = RawColors.Tan, unfocusedBorderColor = RawColors.Divider),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = headline,
                onValueChange = { headline = it },
                label = { Text("Headline") },
                maxLines = 2,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = RawColors.Tan, unfocusedBorderColor = RawColors.Divider),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = subtext,
                onValueChange = { subtext = it },
                label = { Text("Subtitle / Body") },
                maxLines = 3,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = RawColors.Tan, unfocusedBorderColor = RawColors.Divider),
                modifier = Modifier.fillMaxWidth()
            )

            // Export Actions
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BigPrimaryButton(
                    text = if (exporting) "Exporting..." else "Export PNG Image",
                    height = 48.dp,
                    enabled = !exporting,
                    onClick = {
                        exporting = true
                        scope.launch {
                            val res = OpenDesignEngine.renderHtmlToPng(
                                context = context,
                                cacheManager = cacheManager,
                                rawHtml = htmlCode,
                                width = selectedAspect.width,
                                height = selectedAspect.height,
                                fileNamePrefix = "opendesign_${selectedAspect.label.replace(":", "x")}"
                            )
                            res.onSuccess { pngFile ->
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    cacheManager.saveFileToDownloads(pngFile, pngFile.name, "image/png")
                                        .onSuccess { toastMsg = "Saved PNG to Downloads: ${pngFile.name}" }
                                        .onFailure { toastMsg = "Saved locally: ${pngFile.name}" }
                                } else {
                                    toastMsg = "Saved PNG: ${pngFile.name}"
                                }
                            }.onFailure { err ->
                                toastMsg = err.message ?: "PNG export failed"
                            }
                            exporting = false
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    onClick = {
                        val out = File(cacheManager.sharedPacksDir, "opendesign_${System.currentTimeMillis()}.html")
                        out.writeText(htmlCode, Charsets.UTF_8)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            scope.launch {
                                cacheManager.saveFileToDownloads(out, out.name, "text/html")
                                    .onSuccess { toastMsg = "Saved HTML to Downloads" }
                            }
                        } else {
                            toastMsg = "Saved HTML: ${out.name}"
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = RawColors.CardSurface,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Save HTML",
                        fontFamily = BricolageFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        color = RawColors.Cream,
                        modifier = Modifier.padding(vertical = 14.dp).fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
                Surface(
                    onClick = {
                        onCreateMotionProject(htmlCode, headline.lineSequence().firstOrNull().orEmpty().ifBlank { "OpenDesign Motion" }, selectedAspect)
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = RawColors.AccentSoft,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Open in Motion Project",
                        fontFamily = BricolageFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        color = RawColors.Tan,
                        modifier = Modifier.padding(vertical = 14.dp).fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }

        RawToastBanner(
            message = toastMsg,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        )
    }
}
