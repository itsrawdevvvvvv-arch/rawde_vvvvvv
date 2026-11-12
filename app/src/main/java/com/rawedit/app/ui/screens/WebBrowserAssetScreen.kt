package com.rawedit.app.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.rawedit.app.domain.CutoutPadRatios
import com.rawedit.app.domain.CutoutProcessor
import com.rawedit.app.domain.DownloadedWebMedia
import com.rawedit.app.domain.IconSearchItem
import com.rawedit.app.domain.WebAssetFetcher
import com.rawedit.app.domain.WebCaptureHelper
import com.rawedit.app.domain.WebUiElementBox
import com.rawedit.app.ui.components.BigPrimaryButton
import com.rawedit.app.ui.components.CircleActionButton
import com.rawedit.app.ui.components.RawSecondaryButton
import com.rawedit.app.ui.components.RawToastBanner
import com.rawedit.app.ui.icons.RawIcon
import com.rawedit.app.ui.icons.RawIconType
import com.rawedit.app.ui.theme.BricolageFamily
import com.rawedit.app.ui.theme.JetBrainsMonoFamily
import com.rawedit.app.ui.theme.RawColors
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

private enum class BrowserMode {
    WEB,
    ICONS
}

private data class QuickBookmark(
    val label: String,
    val url: String
)

private val QUICK_BOOKMARKS = listOf(
    QuickBookmark("Google", "https://www.google.com"),
    QuickBookmark("DuckDuckGo", "https://duckduckgo.com"),
    QuickBookmark("GitHub", "https://github.com/login"),
    QuickBookmark("Lucide", "https://lucide.dev/icons"),
    QuickBookmark("Simple Icons", "https://simpleicons.org"),
    QuickBookmark("Stripe Docs", "https://docs.stripe.com")
)

private val ICON_QUICK_QUERIES = listOf(
    "login",
    "cursor",
    "lock",
    "chrome",
    "github",
    "bell",
    "settings",
    "sparkle"
)

private sealed interface ExtractedPageElement {
    val key: String
    val label: String
    val tag: String

    data class InlineSvg(
        override val key: String,
        override val label: String,
        override val tag: String,
        val rawSvg: String,
        val thumb: Bitmap?
    ) : ExtractedPageElement

    data class RemoteImg(
        override val key: String,
        override val label: String,
        override val tag: String,
        val url: String,
        val dimensions: String
    ) : ExtractedPageElement
}

private data class PendingScreenshotCapture(
    val bitmap: Bitmap,
    val suggestedName: String,
    val description: String,
    val safeZone: String,
    val elementCount: Int
)

private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebBrowserAssetScreen(
    apiKey: String,
    modelPref: String,
    quotaExhausted: Boolean,
    onBack: () -> Unit,
    onSaveCapturedBitmap: (
        bitmap: Bitmap,
        isCutout: Boolean,
        suggestedName: String,
        defaultDescription: String?,
        defaultSafeZone: String?
    ) -> Unit,
    onOpenGroqKey: () -> Unit,
    onSwitchGroqKey: () -> Unit,
    onRefreshGroqQuota: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var mode by remember { mutableStateOf(BrowserMode.WEB) }
    var desktopMode by remember { mutableStateOf(false) }
    var urlInput by remember { mutableStateOf("https://www.google.com") }
    var activeUrl by remember { mutableStateOf("https://www.google.com") }
    var pageTitle by remember { mutableStateOf("Google") }
    var loadProgress by remember { mutableIntStateOf(100) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var toastMsg by remember { mutableStateOf<String?>(null) }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val defaultUserAgent = remember(context) {
        runCatching { WebSettings.getDefaultUserAgent(context) }.getOrDefault("")
    }

    var isCapturing by remember { mutableStateOf(false) }
    var pendingShot by remember { mutableStateOf<PendingScreenshotCapture?>(null) }
    var showElementsSheet by remember { mutableStateOf(false) }
    var isExtractingElements by remember { mutableStateOf(false) }
    var extractedElements by remember { mutableStateOf<List<ExtractedPageElement>>(emptyList()) }
    var isFetchingRemoteItem by remember { mutableStateOf(false) }
    var cutoutTarget by remember { mutableStateOf<CutoutStudioInput?>(null) }

    // Iconify Search state
    var iconQuery by remember { mutableStateOf("login") }
    var isSearchingIcons by remember { mutableStateOf(false) }
    var iconResults by remember { mutableStateOf<List<Pair<IconSearchItem, ImageBitmap?>>>(emptyList()) }
    var iconSearchError by remember { mutableStateOf<String?>(null) }
    var iconsInitialLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(toastMsg) {
        if (toastMsg != null) {
            delay(2200)
            toastMsg = null
        }
    }

    BackHandler(enabled = mode == BrowserMode.WEB && canGoBack && cutoutTarget == null && pendingShot == null && !showElementsSheet) {
        webViewRef?.goBack()
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.apply {
                stopLoading()
                removeAllViews()
                destroy()
            }
            webViewRef = null
        }
    }

    fun navigateBrowser(raw: String) {
        focusManager.clearFocus()
        val normalized = WebCaptureHelper.normalizeUrlInput(raw)
        urlInput = normalized
        activeUrl = normalized
        mode = BrowserMode.WEB
        webViewRef?.loadUrl(normalized)
    }

    fun applyDesktopMode(enabled: Boolean) {
        desktopMode = enabled
        val wv = webViewRef ?: return
        wv.settings.apply {
            userAgentString = if (enabled) DESKTOP_USER_AGENT else defaultUserAgent.ifBlank { null }
            useWideViewPort = true
            loadWithOverviewMode = enabled
        }
        wv.reload()
        toastMsg = if (enabled) "Desktop 1280px mode enabled" else "Mobile view enabled"
    }

    fun runIconSearch(queryStr: String) {
        focusManager.clearFocus()
        iconQuery = queryStr
        isSearchingIcons = true
        iconSearchError = null
        scope.launch {
            val res = WebAssetFetcher.searchIcons(queryStr, limit = 24)
            res.onSuccess { items ->
                if (items.isEmpty()) {
                    iconResults = emptyList()
                    iconSearchError = "No open-source SVG icons matched \"$queryStr\"."
                } else {
                    val svgsForGrid = items.map { item ->
                        if (item.supportsTint) {
                            SvgTintHelper.tintForPreview(item.rawSvg, "#F7ECE6")
                        } else {
                            item.rawSvg
                        }
                    }
                    val bitmaps = runCatching {
                        CutoutProcessor.rasterizeSvgGrid(
                            context = context,
                            rawSvgs = svgsForGrid,
                            tilePx = 144,
                            pad = CutoutPadRatios(0.08f, 0.08f, 0.08f, 0.08f),
                            tintHex = null,
                            cols = 6
                        )
                    }.getOrDefault(List(items.size) { null })

                    iconResults = items.mapIndexed { idx, item ->
                        item to bitmaps.getOrNull(idx)?.asImageBitmap()
                    }
                }
            }.onFailure { err ->
                iconSearchError = err.message ?: "Icon search failed. Check network."
            }
            isSearchingIcons = false
        }
    }

    LaunchedEffect(mode) {
        if (mode == BrowserMode.ICONS && !iconsInitialLoaded) {
            iconsInitialLoaded = true
            runIconSearch(iconQuery)
        }
    }

    fun openRemoteMediaInCutout(url: String, suggestedName: String, descHint: String? = null) {
        if (isFetchingRemoteItem) return
        isFetchingRemoteItem = true
        scope.launch {
            val res = WebAssetFetcher.downloadMedia(url, suggestedName)
            isFetchingRemoteItem = false
            res.onSuccess { media ->
                showElementsSheet = false
                cutoutTarget = when (media) {
                    is DownloadedWebMedia.Svg -> CutoutStudioInput.SvgInput(
                        rawSvg = media.rawSvg,
                        suggestedName = media.suggestedName,
                        initialTintHex = if (media.rawSvg.contains("currentColor", ignoreCase = true)) "#F7ECE6" else null,
                        defaultDescription = descHint ?: "Extracted SVG vector logo converted to crisp transparent PNG."
                    )

                    is DownloadedWebMedia.Raster -> CutoutStudioInput.BitmapInput(
                        sourceBitmap = media.bitmap,
                        suggestedName = media.suggestedName,
                        defaultKeepOriginal = false,
                        defaultDescription = descHint ?: "Extracted web element from ${WebCaptureHelper.extractHostSlug(activeUrl)}."
                    )
                }
            }.onFailure { err ->
                toastMsg = err.message ?: "Could not fetch element"
            }
        }
    }

    fun snapWebScreenshot() {
        val wv = webViewRef ?: return
        if (isCapturing || wv.width <= 0 || wv.height <= 0) return
        isCapturing = true
        scope.launch {
            val rawW = wv.width
            val rawH = wv.height
            val scale = minOf(1f, 1440f / maxOf(rawW, rawH).toFloat())
            val outW = (rawW * scale).toInt().coerceAtLeast(1)
            val outH = (rawH * scale).toInt().coerceAtLeast(1)

            val snapBmp = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(snapBmp)
            canvas.drawColor(android.graphics.Color.WHITE)
            canvas.scale(scale, scale)
            canvas.translate(-wv.scrollX.toFloat(), -wv.scrollY.toFloat())
            wv.draw(canvas)

            // Collect visible interactive DOM coordinates scaled into snapBmp pixels
            val jsRes = CompletableDeferred<String>()
            wv.evaluateJavascript(DOM_COORDS_JS) { value ->
                jsRes.complete(value ?: "\"[]\"")
            }
            val rawJsonStr = jsRes.await()
            val uiBoxes = parseDomBoxes(rawJsonStr, outW, outH)
            val slug = "${WebCaptureHelper.extractHostSlug(activeUrl)}_shot"
            val (desc, safeZone) = WebCaptureHelper.formatScreenshotMetadata(
                url = activeUrl,
                pageTitle = pageTitle,
                imgW = outW,
                imgH = outH,
                elements = uiBoxes
            )

            pendingShot = PendingScreenshotCapture(
                bitmap = snapBmp,
                suggestedName = slug,
                description = desc,
                safeZone = safeZone,
                elementCount = uiBoxes.size
            )
            isCapturing = false
        }
    }

    fun grabPageElements() {
        val wv = webViewRef ?: return
        if (isExtractingElements) return
        isExtractingElements = true
        showElementsSheet = true
        scope.launch {
            val jsRes = CompletableDeferred<String>()
            wv.evaluateJavascript(DOM_ELEMENTS_JS) { value ->
                jsRes.complete(value ?: "\"{}\"")
            }
            val rawJson = jsRes.await()
            val parsed = parseExtractedDomElements(rawJson)
            val svgs = parsed.first
            val remotes = parsed.second

            val svgThumbs = if (svgs.isNotEmpty()) {
                runCatching {
                    CutoutProcessor.rasterizeSvgGrid(
                        context = context,
                        rawSvgs = svgs.map { it.second },
                        tilePx = 128,
                        pad = CutoutPadRatios(0.08f, 0.08f, 0.08f, 0.08f),
                        cols = 5
                    )
                }.getOrDefault(List(svgs.size) { null })
            } else {
                emptyList()
            }

            val combined = ArrayList<ExtractedPageElement>(svgs.size + remotes.size)
            svgs.forEachIndexed { i, (label, svgStr) ->
                combined.add(
                    ExtractedPageElement.InlineSvg(
                        key = "svg_${i}_${label.hashCode()}",
                        label = label,
                        tag = "SVG",
                        rawSvg = svgStr,
                        thumb = svgThumbs.getOrNull(i)
                    )
                )
            }
            remotes.forEachIndexed { i, item ->
                combined.add(
                    ExtractedPageElement.RemoteImg(
                        key = "img_${i}_${item.url.hashCode()}",
                        label = item.label,
                        tag = item.tag,
                        url = item.url,
                        dimensions = item.dims
                    )
                )
            }
            extractedElements = combined
            isExtractingElements = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RawColors.BgPrimary)
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Chrome Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircleActionButton(icon = RawIconType.BACK, size = 44.dp, onClick = onBack)

                // URL or Icon Search Input Pill
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(CircleShape)
                        .background(RawColors.RaisedCard)
                        .border(1.dp, RawColors.Divider, CircleShape)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RawIcon(
                        type = if (mode == BrowserMode.WEB) RawIconType.GLOBE else RawIconType.SEARCH,
                        size = 16.dp,
                        tint = RawColors.Tan
                    )
                    BasicTextField(
                        value = if (mode == BrowserMode.WEB) urlInput else iconQuery,
                        onValueChange = {
                            if (mode == BrowserMode.WEB) urlInput = it else iconQuery = it
                        },
                        singleLine = true,
                        cursorBrush = SolidColor(RawColors.Tan),
                        textStyle = TextStyle(
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 12.5.sp,
                            color = RawColors.TextPrimary
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(
                            onGo = {
                                if (mode == BrowserMode.WEB) navigateBrowser(urlInput) else runIconSearch(iconQuery)
                            }
                        ),
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                val currentTxt = if (mode == BrowserMode.WEB) urlInput else iconQuery
                                if (currentTxt.isEmpty()) {
                                    Text(
                                        text = if (mode == BrowserMode.WEB) "Enter URL or search web..." else "Search icons (e.g. login, cursor)...",
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 12.sp,
                                        color = RawColors.TextMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                innerTextField()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    if ((mode == BrowserMode.WEB && urlInput.isNotEmpty()) ||
                        (mode == BrowserMode.ICONS && iconQuery.isNotEmpty())
                    ) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .clickable {
                                    if (mode == BrowserMode.WEB) urlInput = "" else iconQuery = ""
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            RawIcon(RawIconType.CLOSE, size = 13.dp, tint = RawColors.TextMuted)
                        }
                    }
                }

                if (mode == BrowserMode.WEB) {
                    CircleActionButton(
                        icon = RawIconType.DESKTOP,
                        size = 44.dp,
                        backgroundColor = if (desktopMode) RawColors.Tan else RawColors.RaisedCard,
                        iconTint = if (desktopMode) RawColors.Ink else RawColors.TextPrimary,
                        onClick = { applyDesktopMode(!desktopMode) }
                    )
                }
            }

            // Mode Switcher + Bookmarks Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val modes = listOf(BrowserMode.WEB to "Live Browser", BrowserMode.ICONS to "Icon Finder")
                modes.forEach { (m, label) ->
                    val sel = mode == m
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (sel) RawColors.Cream else RawColors.CardSurface)
                            .clickable { mode = m }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        RawIcon(
                            type = if (m == BrowserMode.WEB) RawIconType.GLOBE else RawIconType.SEARCH,
                            size = 13.dp,
                            tint = if (sel) RawColors.Ink else RawColors.Tan
                        )
                        Text(
                            text = label,
                            fontFamily = BricolageFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (sel) RawColors.Ink else RawColors.TextPrimary
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .height(18.dp)
                        .border(0.5.dp, RawColors.DividerStrong, CircleShape)
                )

                if (mode == BrowserMode.WEB) {
                    QUICK_BOOKMARKS.forEach { bm ->
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(RawColors.RaisedCard)
                                .clickable { navigateBrowser(bm.url) }
                                .padding(horizontal = 11.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = bm.label,
                                fontFamily = BricolageFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.sp,
                                color = RawColors.TextMuted
                            )
                        }
                    }
                } else {
                    ICON_QUICK_QUERIES.forEach { kw ->
                        val activeKw = iconQuery.equals(kw, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (activeKw) RawColors.AccentSoft else RawColors.RaisedCard)
                                .clickable { runIconSearch(kw) }
                                .padding(horizontal = 11.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = kw,
                                fontFamily = BricolageFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = if (activeKw) RawColors.Tan else RawColors.TextMuted
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Thin Loading Progress Strip in Browser mode
            if (mode == BrowserMode.WEB && loadProgress in 1..99) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp)
                        .background(RawColors.CardSurface)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(loadProgress / 100f)
                            .height(2.5.dp)
                            .background(RawColors.Tan)
                    )
                }
            }

            // Content Area: Keep AndroidView WebView mounted cleanly when switching to Icon Finder and back
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            setBackgroundColor(android.graphics.Color.WHITE)
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                allowFileAccess = false
                                allowContentAccess = false
                                setSupportMultipleWindows(false)
                                builtInZoomControls = true
                                displayZoomControls = false
                                useWideViewPort = true
                                loadWithOverviewMode = desktopMode
                                mediaPlaybackRequiresUserGesture = true
                            }
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    loadProgress = newProgress
                                }

                                override fun onReceivedTitle(view: WebView?, title: String?) {
                                    if (!title.isNullOrBlank()) pageTitle = title
                                }
                            }
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    val scheme = request?.url?.scheme?.lowercase(Locale.US).orEmpty()
                                    if (scheme != "http" && scheme != "https") {
                                        return true
                                    }
                                    return false
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    val u = url ?: activeUrl
                                    activeUrl = u
                                    urlInput = u
                                    canGoBack = view?.canGoBack() == true
                                    canGoForward = view?.canGoForward() == true
                                    if (desktopMode && view != null) {
                                        view.evaluateJavascript(FORCE_DESKTOP_VIEWPORT_JS, null)
                                    }
                                }
                            }
                            setOnLongClickListener { view ->
                                val wv = view as? WebView ?: return@setOnLongClickListener false
                                val hit = wv.hitTestResult
                                val type = hit.type
                                val extra = hit.extra
                                if ((type == WebView.HitTestResult.IMAGE_TYPE ||
                                        type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) &&
                                    !extra.isNullOrBlank()
                                ) {
                                    openRemoteMediaInCutout(
                                        url = extra,
                                        suggestedName = "${WebCaptureHelper.extractHostSlug(activeUrl)}_img"
                                    )
                                    true
                                } else {
                                    false
                                }
                            }
                            loadUrl(activeUrl)
                            webViewRef = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                if (mode == BrowserMode.ICONS) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(RawColors.BgPrimary)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "OPEN-SOURCE SVG ICONS & LOGOS",
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = RawColors.Tan
                            )
                            Text(
                                text = "Tap any -> AI Cutout PNG",
                                fontFamily = BricolageFamily,
                                fontSize = 12.sp,
                                color = RawColors.TextMuted
                            )
                        }

                        if (isSearchingIcons) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Fetching & rendering vector icons...",
                                    fontFamily = BricolageFamily,
                                    fontSize = 15.sp,
                                    color = RawColors.TextMuted
                                )
                            }
                        } else if (iconSearchError != null) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = iconSearchError!!,
                                    fontFamily = BricolageFamily,
                                    fontSize = 14.5.sp,
                                    color = RawColors.ErrorCoral
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                RawSecondaryButton(
                                    text = "Retry search",
                                    height = 46.dp,
                                    fillWidth = false,
                                    onClick = { runIconSearch(iconQuery) }
                                )
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 104.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                items(iconResults, key = { it.first.fullId }) { (item, thumb) ->
                                    Column(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(RawColors.CardSurface)
                                            .border(1.dp, RawColors.Divider, RoundedCornerShape(20.dp))
                                            .clickable {
                                                cutoutTarget = CutoutStudioInput.SvgInput(
                                                    rawSvg = item.rawSvg,
                                                    suggestedName = "${item.prefix}_${item.name}",
                                                    initialTintHex = if (item.supportsTint) "#F7ECE6" else null,
                                                    defaultDescription = "Iconify SVG (${item.collectionTitle}: ${item.name}, ${item.licenseTitle} license) converted to transparent PNG."
                                                )
                                            }
                                            .padding(10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(56.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(RawColors.RaisedCard),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (thumb != null) {
                                                Image(
                                                    bitmap = thumb,
                                                    contentDescription = item.name,
                                                    contentScale = ContentScale.Fit,
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .padding(6.dp)
                                                )
                                            } else {
                                                RawIcon(RawIconType.SPARK, size = 20.dp, tint = RawColors.Tan)
                                            }
                                        }
                                        Text(
                                            text = item.name,
                                            fontFamily = BricolageFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = RawColors.TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = item.prefix,
                                            fontFamily = JetBrainsMonoFamily,
                                            fontSize = 10.sp,
                                            color = RawColors.TextMuted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom Web Capture Control Dock
        if (mode == BrowserMode.WEB) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(RawColors.BgPrimary.copy(alpha = 0.94f))
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircleActionButton(
                    icon = RawIconType.CHEVRON_LEFT,
                    size = 42.dp,
                    enabled = canGoBack,
                    onClick = { webViewRef?.goBack() }
                )
                CircleActionButton(
                    icon = RawIconType.REFRESH,
                    size = 42.dp,
                    onClick = { webViewRef?.reload() }
                )
                RawSecondaryButton(
                    text = if (isExtractingElements) "Scanning..." else "Grab Icons",
                    height = 46.dp,
                    leadingIcon = RawIconType.CUTOUT,
                    enabled = !isExtractingElements && !isFetchingRemoteItem,
                    onClick = { grabPageElements() },
                    modifier = Modifier.weight(1f)
                )
                BigPrimaryButton(
                    text = if (isCapturing) "Snapping..." else "Snap UI",
                    height = 46.dp,
                    leadingIcon = RawIconType.CAMERA,
                    enabled = !isCapturing,
                    onClick = { snapWebScreenshot() },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        RawToastBanner(
            message = toastMsg,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 84.dp)
        )
    }

    // Extracted Page Elements Drawer Dialog
    if (showElementsSheet) {
        Dialog(
            onDismissRequest = { if (!isFetchingRemoteItem) showElementsSheet = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp)
                    .clip(RoundedCornerShape(30.dp))
                    .background(RawColors.CardSurface)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Page Icons & Images",
                            fontFamily = BricolageFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = RawColors.TextPrimary
                        )
                        Text(
                            text = if (isExtractingElements) {
                                "Scanning page SVG & image assets..."
                            } else {
                                "Found ${extractedElements.size} elements. Tap any to create AI-verified cutout."
                            },
                            fontFamily = BricolageFamily,
                            fontSize = 13.sp,
                            color = RawColors.TextMuted
                        )
                    }
                    CircleActionButton(
                        icon = RawIconType.CLOSE,
                        size = 40.dp,
                        enabled = !isFetchingRemoteItem,
                        onClick = { showElementsSheet = false }
                    )
                }

                if (isExtractingElements) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Rasterizing extracted SVG previews...",
                            fontFamily = BricolageFamily,
                            fontSize = 14.5.sp,
                            color = RawColors.Tan
                        )
                    }
                } else if (extractedElements.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No <svg> or <img> elements found on this page yet. Try scrolling or Icon Finder.",
                            fontFamily = BricolageFamily,
                            fontSize = 14.sp,
                            color = RawColors.TextMuted
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp)
                    ) {
                        items(extractedElements, key = { it.key }) { item ->
                            Column(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(RawColors.RaisedCard)
                                    .clickable(enabled = !isFetchingRemoteItem) {
                                        when (item) {
                                            is ExtractedPageElement.InlineSvg -> {
                                                showElementsSheet = false
                                                cutoutTarget = CutoutStudioInput.SvgInput(
                                                    rawSvg = item.rawSvg,
                                                    suggestedName = "${WebCaptureHelper.extractHostSlug(activeUrl)}_${item.label.take(16)}",
                                                    defaultDescription = "Vector icon grabbed from ${WebCaptureHelper.extractHostSlug(activeUrl)}."
                                                )
                                            }

                                            is ExtractedPageElement.RemoteImg -> {
                                                openRemoteMediaInCutout(
                                                    url = item.url,
                                                    suggestedName = "${WebCaptureHelper.extractHostSlug(activeUrl)}_${item.label.take(16)}"
                                                )
                                            }
                                        }
                                    }
                                    .padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF28282D)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    when (item) {
                                        is ExtractedPageElement.InlineSvg -> {
                                            if (item.thumb != null) {
                                                Image(
                                                    bitmap = item.thumb.asImageBitmap(),
                                                    contentDescription = item.label,
                                                    contentScale = ContentScale.Fit,
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .padding(6.dp)
                                                )
                                            } else {
                                                RawIcon(RawIconType.CUTOUT, size = 20.dp, tint = RawColors.Tan)
                                            }
                                        }

                                        is ExtractedPageElement.RemoteImg -> {
                                            val req = remember(item.url) {
                                                ImageRequest.Builder(context)
                                                    .data(item.url)
                                                    .size(160, 160)
                                                    .build()
                                            }
                                            AsyncImage(
                                                model = req,
                                                contentDescription = item.label,
                                                contentScale = ContentScale.Fit,
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(4.dp)
                                            )
                                        }
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(RawColors.AccentSoft)
                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = item.tag,
                                        fontFamily = JetBrainsMonoFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.5.sp,
                                        color = RawColors.Tan
                                    )
                                }
                                Text(
                                    text = item.label,
                                    fontFamily = BricolageFamily,
                                    fontSize = 11.sp,
                                    color = RawColors.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Captured Webpage Screenshot Preview Dialog
    pendingShot?.let { shot ->
        Dialog(
            onDismissRequest = { pendingShot = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .clip(RoundedCornerShape(30.dp))
                    .background(RawColors.CardSurface)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Website Screenshot Ready",
                    fontFamily = BricolageFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 23.sp,
                    color = RawColors.TextPrimary
                )
                Text(
                    text = "${shot.bitmap.width}x${shot.bitmap.height} PNG \u2022 ${shot.elementCount} interactive UI targets mapped for animation",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 11.5.sp,
                    color = RawColors.Tan
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(RawColors.RaisedCard)
                        .border(1.dp, RawColors.Divider, RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = shot.bitmap.asImageBitmap(),
                        contentDescription = "Captured webpage UI screenshot",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Text(
                    text = shot.description,
                    fontFamily = BricolageFamily,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = RawColors.TextMuted,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RawSecondaryButton(
                        text = "Retake",
                        height = 48.dp,
                        onClick = { pendingShot = null },
                        modifier = Modifier.weight(0.8f)
                    )
                    RawSecondaryButton(
                        text = "Cutout",
                        height = 48.dp,
                        leadingIcon = RawIconType.CUTOUT,
                        onClick = {
                            val current = shot
                            pendingShot = null
                            cutoutTarget = CutoutStudioInput.BitmapInput(
                                sourceBitmap = current.bitmap,
                                suggestedName = current.suggestedName,
                                defaultKeepOriginal = false,
                                defaultDescription = current.description,
                                defaultSafeZone = current.safeZone
                            )
                        },
                        modifier = Modifier.weight(0.95f)
                    )
                    BigPrimaryButton(
                        text = "Save Screen",
                        height = 48.dp,
                        leadingIcon = RawIconType.CHECK,
                        onClick = {
                            val current = shot
                            pendingShot = null
                            onSaveCapturedBitmap(
                                current.bitmap,
                                false,
                                current.suggestedName,
                                current.description,
                                current.safeZone
                            )
                            toastMsg = "Screenshot attached to project"
                        },
                        modifier = Modifier.weight(1.25f)
                    )
                }
            }
        }
    }

    // PNG Cutout Studio with Groq Vision Edge Audit
    cutoutTarget?.let { target ->
        CutoutStudioDialog(
            input = target,
            apiKey = apiKey,
            modelPref = modelPref,
            quotaExhausted = quotaExhausted,
            onOpenGroqKey = onOpenGroqKey,
            onSwitchGroqKey = onSwitchGroqKey,
            onRefreshGroqQuota = onRefreshGroqQuota,
            onDismiss = { cutoutTarget = null },
            onConfirmSave = { finalBmp, isCutout, name, desc, safe ->
                cutoutTarget = null
                onSaveCapturedBitmap(finalBmp, isCutout, name, desc, safe)
                toastMsg = if (isCutout) "Cutout saved & attached" else "Image saved & attached"
            }
        )
    }
}

private object SvgTintHelper {
    fun tintForPreview(rawSvg: String, hex: String): String {
        return com.rawedit.app.domain.SvgSanitizerAndPadding.sanitizeSvg(rawSvg, hex)
    }
}

private data class DomRemoteCandidate(
    val label: String,
    val tag: String,
    val url: String,
    val dims: String
)

private fun parseDomBoxes(rawEvalResult: String, imgW: Int, imgH: Int): List<WebUiElementBox> {
    val arrStr = unquoteJsJson(rawEvalResult) ?: return emptyList()
    val arr = runCatching { JSONArray(arrStr) }.getOrNull() ?: return emptyList()
    val out = ArrayList<WebUiElementBox>(arr.length())
    for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        val label = o.optString("l", "").trim()
        val role = o.optString("r", "button").trim()
        val nx = o.optInt("x", 0).coerceIn(0, 1000)
        val ny = o.optInt("y", 0).coerceIn(0, 1000)
        val nw = o.optInt("w", 0).coerceIn(0, 1000)
        val nh = o.optInt("h", 0).coerceIn(0, 1000)
        out.add(
            WebUiElementBox(
                label = label,
                role = role,
                leftPx = (nx * imgW) / 1000,
                topPx = (ny * imgH) / 1000,
                widthPx = (nw * imgW) / 1000,
                heightPx = (nh * imgH) / 1000
            )
        )
    }
    return out
}

private fun parseExtractedDomElements(
    rawEvalResult: String
): Pair<List<Pair<String, String>>, List<DomRemoteCandidate>> {
    val objStr = unquoteJsJson(rawEvalResult) ?: return emptyList<Pair<String, String>>() to emptyList()
    val root = runCatching { JSONObject(objStr) }.getOrNull() ?: return emptyList<Pair<String, String>>() to emptyList()
    val svgArr = root.optJSONArray("svgs") ?: JSONArray()
    val imgArr = root.optJSONArray("imgs") ?: JSONArray()

    val svgs = ArrayList<Pair<String, String>>(svgArr.length())
    val seenSvg = HashSet<String>()
    for (i in 0 until svgArr.length()) {
        val o = svgArr.optJSONObject(i) ?: continue
        val rawSvg = o.optString("svg", "").trim()
        if (rawSvg.length < 20 || !seenSvg.add(rawSvg.take(300))) continue
        val lbl = o.optString("label", "icon_${i + 1}").ifBlank { "icon_${i + 1}" }
        svgs.add(lbl to rawSvg)
    }

    val remotes = ArrayList<DomRemoteCandidate>(imgArr.length())
    val seenUrls = HashSet<String>()
    for (i in 0 until imgArr.length()) {
        val o = imgArr.optJSONObject(i) ?: continue
        val url = o.optString("url", "").trim()
        if (url.isBlank() || !seenUrls.add(url)) continue
        remotes.add(
            DomRemoteCandidate(
                label = o.optString("label", "image_${i + 1}").ifBlank { "image_${i + 1}" },
                tag = o.optString("tag", "IMG").ifBlank { "IMG" },
                url = url,
                dims = o.optString("dims", "")
            )
        )
    }
    return svgs to remotes
}

private fun unquoteJsJson(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty() || trimmed == "null") return null
    return if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
        runCatching { JSONArray("[$trimmed]").getString(0) }.getOrNull()
    } else {
        trimmed
    }
}

private val FORCE_DESKTOP_VIEWPORT_JS = """
    (function(){
      var m = document.querySelector('meta[name="viewport"]');
      if (!m) {
        m = document.createElement('meta');
        m.name = 'viewport';
        document.head && document.head.appendChild(m);
      }
      if (m) m.content = 'width=1280';
    })();
""".trimIndent()

private val DOM_COORDS_JS = """
    (function(){
      var els = document.querySelectorAll('button,input,textarea,select,a[href],[role="button"],h1,h2');
      var vw = window.innerWidth || document.documentElement.clientWidth || 1;
      var vh = window.innerHeight || document.documentElement.clientHeight || 1;
      var out = [];
      for (var i = 0; i < els.length && out.length < 18; i++) {
        var el = els[i];
        var r = el.getBoundingClientRect();
        if (!r || r.width < 16 || r.height < 12) continue;
        if (r.bottom <= 0 || r.right <= 0 || r.top >= vh || r.left >= vw) continue;
        var cs = getComputedStyle(el);
        if (cs.display === 'none' || cs.visibility === 'hidden' || parseFloat(cs.opacity) === 0) continue;
        var tag = (el.tagName || '').toLowerCase();
        var lbl = (el.innerText || el.value || el.placeholder || el.getAttribute('aria-label') || el.getAttribute('title') || tag).trim().replace(/\s+/g, ' ').slice(0, 32);
        if (!lbl) lbl = tag;
        var role = (tag === 'input' || tag === 'textarea' || tag === 'select') ? 'field' : (tag === 'h1' || tag === 'h2' ? 'heading' : 'button');
        out.push({
          l: lbl,
          r: role,
          x: Math.round((Math.max(0, r.left) / vw) * 1000),
          y: Math.round((Math.max(0, r.top) / vh) * 1000),
          w: Math.round((Math.min(vw - Math.max(0, r.left), r.width) / vw) * 1000),
          h: Math.round((Math.min(vh - Math.max(0, r.top), r.height) / vh) * 1000)
        });
      }
      return JSON.stringify(out);
    })();
""".trimIndent()

private val DOM_ELEMENTS_JS = """
    (function(){
      var svgs = [];
      var imgs = [];
      var svgEls = document.querySelectorAll('svg');
      for (var i = 0; i < svgEls.length && svgs.length < 24; i++) {
        var s = svgEls[i];
        var r = s.getBoundingClientRect();
        if (r && (r.width < 10 || r.height < 10)) continue;
        var clone = s.cloneNode(true);
        try {
          var col = getComputedStyle(s).color;
          if (col && !clone.getAttribute('color')) clone.setAttribute('color', col);
          if (!clone.getAttribute('viewBox') && r && r.width > 0 && r.height > 0) {
            clone.setAttribute('viewBox', '0 0 ' + Math.round(r.width) + ' ' + Math.round(r.height));
          }
        } catch(e){}
        var xml = new XMLSerializer().serializeToString(clone);
        if (xml && xml.length <= 90000) {
          var lbl = (s.getAttribute('aria-label') || (s.querySelector('title') && s.querySelector('title').textContent) || ('svg_' + (svgs.length + 1))).trim().slice(0, 28);
          svgs.push({ label: lbl, svg: xml });
        }
      }
      var links = document.querySelectorAll('link[rel*="icon"]');
      for (var li = 0; li < links.length && imgs.length < 8; li++) {
        var href = links[li].getAttribute('href');
        if (!href) continue;
        try {
          var abs = new URL(href, location.href).href;
          imgs.push({ label: 'site_icon', tag: 'ICON', url: abs, dims: '' });
        } catch(e){}
      }
      var imgEls = document.querySelectorAll('img');
      for (var j = 0; j < imgEls.length && imgs.length < 28; j++) {
        var im = imgEls[j];
        var src = im.currentSrc || im.getAttribute('src');
        if (!src) continue;
        try { src = new URL(src, location.href).href; } catch(e){}
        var w = im.naturalWidth || Math.round(im.getBoundingClientRect().width || 0);
        var h = im.naturalHeight || Math.round(im.getBoundingClientRect().height || 0);
        if (w > 0 && w < 14 && h > 0 && h < 14) continue;
        var alt = (im.getAttribute('alt') || '').trim().slice(0, 24);
        var tag = src.toLowerCase().indexOf('.svg') !== -1 ? 'SVG' : 'IMG';
        imgs.push({ label: alt || ('image_' + (imgs.length + 1)), tag: tag, url: src, dims: w + 'x' + h });
      }
      return JSON.stringify({ svgs: svgs, imgs: imgs });
    })();
""".trimIndent()
