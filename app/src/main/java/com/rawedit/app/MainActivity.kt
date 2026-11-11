package com.rawedit.app

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.rawedit.app.data.local.LocalAssetEntity
import com.rawedit.app.data.local.ProjectEntity
import com.rawedit.app.data.local.UserProfile
import com.rawedit.app.data.repository.RawEditRepository
import com.rawedit.app.domain.PaletteJsonCodec
import com.rawedit.app.domain.PaletteState
import com.rawedit.app.domain.PuterClient
import com.rawedit.app.domain.PuterCodec
import com.rawedit.app.domain.PuterOutOfCreditsException
import com.rawedit.app.ui.components.MainTab
import com.rawedit.app.ui.components.RawBottomNavBar
import com.rawedit.app.ui.components.RawToastBanner
import com.rawedit.app.ui.motion.RawScreenTransition
import com.rawedit.app.ui.motion.RawTabMorphTransition
import com.rawedit.app.ui.screens.AddVoiceScreen
import com.rawedit.app.ui.screens.AssetLockerScreen
import com.rawedit.app.ui.screens.CodePasteScreen
import com.rawedit.app.ui.screens.CopyPromptScreen
import com.rawedit.app.ui.screens.CreateHubScreen
import com.rawedit.app.ui.screens.HomeScreen
import com.rawedit.app.ui.screens.KeyframeSheetScreen
import com.rawedit.app.ui.screens.MyReelsScreen
import com.rawedit.app.ui.screens.OnboardingScreen
import com.rawedit.app.ui.screens.PaletteScreen
import com.rawedit.app.ui.screens.ProfileScreen
import com.rawedit.app.ui.screens.ProfileSetupScreen
import com.rawedit.app.ui.screens.PuterAuthDialog
import com.rawedit.app.ui.screens.RenderingScreen
import com.rawedit.app.ui.screens.ResultScreen
import com.rawedit.app.ui.screens.WebBrowserAssetScreen
import com.rawedit.app.ui.theme.RawColors
import com.rawedit.app.ui.theme.RawEditTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class RootStage {
    ONBOARDING,
    PROFILE_SETUP,
    MAIN
}

private sealed interface AppRoute {
    data object Tabs : AppRoute
    data class CreateHub(val projectId: Long) : AppRoute
    data class AddVoice(val projectId: Long) : AppRoute
    data class AssetLocker(val projectId: Long) : AppRoute
    data class WebBrowser(val projectId: Long) : AppRoute
    data class CopyPrompt(val projectId: Long) : AppRoute
    data class CodePaste(val projectId: Long) : AppRoute
    data class KeyframeSheet(val projectId: Long) : AppRoute
    data class Rendering(val projectId: Long) : AppRoute
    data class Result(val videoId: Long, val celebrate: Boolean) : AppRoute
}

class MainActivity : ComponentActivity() {
    private val incomingDeepLinkToken = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleAuthIntent(intent)
        val bgArgb = RawColors.BgPrimary.toArgb()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(bgArgb),
            navigationBarStyle = SystemBarStyle.dark(bgArgb)
        )
        val container = (application as RawEditApplication).container

        setContent {
            RawEditTheme {
                RawEditAppRoot(
                    container = container,
                    pendingAuthTokenFlow = incomingDeepLinkToken,
                    onConsumePendingAuthToken = { incomingDeepLinkToken.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthIntent(intent)
    }

    private fun handleAuthIntent(intent: Intent?) {
        val uriStr = intent?.dataString ?: return
        val token = PuterCodec.extractTokenFromCallbackUri(uriStr)
        if (!token.isNullOrBlank()) {
            incomingDeepLinkToken.value = token
        }
    }
}

@Composable
private fun RawEditAppRoot(
    container: AppContainer,
    pendingAuthTokenFlow: StateFlow<String?>,
    onConsumePendingAuthToken: () -> Unit
) {
    val profileFlow = remember { container.userPreferences.userProfile }
    val profileOrNull by profileFlow.collectAsState(initial = null)
    val scope = rememberCoroutineScope()

    val profile = profileOrNull
    if (profile == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(RawColors.BgPrimary),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = RawColors.Tan)
        }
        return
    }

    var onboardingPassed by remember(profile.onboardingDone) {
        mutableStateOf(profile.onboardingDone)
    }

    val rootStage = when {
        profile.onboardingDone -> RootStage.MAIN
        !onboardingPassed -> RootStage.ONBOARDING
        else -> RootStage.PROFILE_SETUP
    }

    RawScreenTransition(
        targetState = rootStage,
        isForward = true
    ) { stage ->
        when (stage) {
            RootStage.ONBOARDING -> OnboardingScreen(
                onFinished = { onboardingPassed = true }
            )

            RootStage.PROFILE_SETUP -> ProfileSetupScreen(
                cacheManager = container.cacheManager,
                onComplete = { name, photoPath ->
                    scope.launch {
                        container.userPreferences.completeOnboardingAndProfile(name, photoPath)
                    }
                }
            )

            RootStage.MAIN -> MainAuthenticatedHost(
                container = container,
                userProfile = profile,
                pendingAuthTokenFlow = pendingAuthTokenFlow,
                onConsumePendingAuthToken = onConsumePendingAuthToken
            )
        }
    }
}

@Composable
private fun MainAuthenticatedHost(
    container: AppContainer,
    userProfile: UserProfile,
    pendingAuthTokenFlow: StateFlow<String?>,
    onConsumePendingAuthToken: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val projects by container.repository.allProjects.collectAsState(initial = emptyList())
    val videos by container.repository.allVideos.collectAsState(initial = emptyList())
    val allAssets by container.repository.allAssets.collectAsState(initial = emptyList())
    val pendingDeepLinkToken by pendingAuthTokenFlow.collectAsState()

    val activePalette = remember(userProfile.paletteJson, userProfile.paletteCounter) {
        PaletteJsonCodec.decode(userProfile.paletteJson)
            ?: PaletteState.DEFAULT_01.copy(number = userProfile.paletteCounter.coerceAtLeast(1))
    }

    var backStack by remember { mutableStateOf<List<AppRoute>>(listOf(AppRoute.Tabs)) }
    var currentTab by remember { mutableStateOf(MainTab.HOME) }
    var isForward by remember { mutableStateOf(true) }
    var navigatingNewDraft by remember { mutableStateOf(false) }
    var showPuterAuthDialog by remember { mutableStateOf(false) }
    var puterDialogSwitchMode by remember { mutableStateOf(false) }
    var hostToastMsg by remember { mutableStateOf<String?>(null) }
    val currentRoute = backStack.last()

    fun openPuterAuthDialog(switchMode: Boolean) {
        puterDialogSwitchMode = switchMode
        showPuterAuthDialog = true
    }

    fun refreshPuterCredits(silent: Boolean) {
        val tok = userProfile.puterAuthToken
        if (tok.isBlank()) return
        scope.launch {
            val who = PuterClient.fetchWhoami(tok).getOrNull()
            val res = PuterClient.fetchUsage(tok)
            res.onSuccess { report ->
                container.userPreferences.savePuterUsage(who, report)
                if (!silent) {
                    hostToastMsg = if (report.isExhausted) {
                        "0 Puter credits left — switch account"
                    } else {
                        "Puter credits left: ${report.formattedRemaining()}"
                    }
                }
            }.onFailure { err ->
                if (!silent) {
                    hostToastMsg = err.message ?: "Could not refresh Puter credits"
                }
            }
        }
    }

    fun markCreditsExhausted() {
        scope.launch {
            container.userPreferences.markPuterCreditsExhausted()
            hostToastMsg = "Puter credits ran out — switch account"
        }
    }

    LaunchedEffect(pendingDeepLinkToken) {
        val deepToken = pendingDeepLinkToken
        if (!deepToken.isNullOrBlank()) {
            onConsumePendingAuthToken()
            if (!showPuterAuthDialog && deepToken != userProfile.puterAuthToken) {
                val who = PuterClient.fetchWhoami(deepToken).getOrNull().orEmpty().ifBlank { "puter_user" }
                val usage = PuterClient.fetchUsage(deepToken).getOrNull()
                container.userPreferences.savePuterSession(
                    authToken = deepToken,
                    username = who,
                    modelPref = userProfile.puterModelPref,
                    usage = usage
                )
                val leftStr = usage?.let { " \u2022 ${it.formattedRemaining()} left" }.orEmpty()
                hostToastMsg = "Signed in as @$who$leftStr"
            }
        }
    }

    LaunchedEffect(userProfile.puterAuthToken) {
        if (userProfile.isPuterSignedIn) {
            val ageMs = System.currentTimeMillis() - userProfile.puterUsageUpdatedAtMs
            if (userProfile.puterUsageUpdatedAtMs <= 0L || ageMs > 90_000L) {
                val who = PuterClient.fetchWhoami(userProfile.puterAuthToken).getOrNull()
                val usage = PuterClient.fetchUsage(userProfile.puterAuthToken).getOrNull()
                if (usage != null) {
                    container.userPreferences.savePuterUsage(who, usage)
                }
            }
        }
    }

    LaunchedEffect(hostToastMsg) {
        if (hostToastMsg != null) {
            delay(2200)
            hostToastMsg = null
        }
    }

    fun pushRoute(route: AppRoute) {
        if (backStack.lastOrNull() == route) return
        isForward = true
        backStack = backStack + route
    }

    fun popRoute() {
        if (backStack.size > 1) {
            isForward = false
            backStack = backStack.dropLast(1)
        }
    }

    fun saveCapturedBitmapToLocker(
        projectId: Long,
        bitmap: Bitmap,
        isCutout: Boolean,
        suggestedName: String,
        defaultDescription: String?,
        defaultSafeZone: String?
    ) {
        scope.launch {
            val res = container.cacheManager.importBitmapAsLockerAsset(
                bitmap = bitmap,
                suggestedName = suggestedName,
                isCutout = isCutout,
                customDescription = defaultDescription,
                customSafeZone = defaultSafeZone
            )
            res.onSuccess { imp ->
                val entity = LocalAssetEntity(
                    fileName = imp.fileName,
                    relativePath = imp.relativePath,
                    kind = imp.kind,
                    mimeType = imp.mimeType,
                    width = imp.width,
                    height = imp.height,
                    durationSec = imp.durationSec,
                    sizeBytes = imp.sizeBytes,
                    thumbPath = imp.thumbPath,
                    description = imp.defaultDescription,
                    safeZone = imp.defaultSafeZone
                )
                val newId = container.repository.insertAsset(entity)
                container.repository.attachAssetToProject(projectId, newId)
                if (userProfile.isPuterSignedIn && !userProfile.puterCreditsExhausted) {
                    val visionFile = container.cacheManager.getAssetFile(imp.fileName)
                    if (visionFile != null && visionFile.exists()) {
                        val descRes = PuterClient.describeAsset(
                            authToken = userProfile.puterAuthToken,
                            asset = entity.copy(id = newId),
                            imageFile = visionFile,
                            modelPref = userProfile.puterModelPref
                        )
                        descRes.onSuccess { (desc, safe) ->
                            container.repository.updateAssetDescription(newId, desc, safe)
                            refreshPuterCredits(silent = true)
                            hostToastMsg = "Saved & described ${imp.relativePath}"
                        }.onFailure { err ->
                            if (err is PuterOutOfCreditsException) {
                                container.userPreferences.markPuterCreditsExhausted()
                                hostToastMsg = "Saved ${imp.relativePath} (out of Puter credits)"
                            } else {
                                hostToastMsg = "Saved ${imp.relativePath}"
                            }
                        }
                    } else {
                        hostToastMsg = "Saved ${imp.relativePath}"
                    }
                } else {
                    hostToastMsg = "Saved ${imp.relativePath}"
                }
            }.onFailure { err ->
                hostToastMsg = err.message ?: "Could not save captured asset"
            }
        }
    }

    fun switchTab(target: MainTab) {
        if (target == MainTab.CREATE) {
            if (navigatingNewDraft) return
            navigatingNewDraft = true
            scope.launch {
                try {
                    val activeDraft = projects.firstOrNull { it.state == ProjectEntity.STATE_IN_PROGRESS }
                    val projectId = activeDraft?.id ?: container.repository.createEmptyProject()
                    pushRoute(AppRoute.CreateHub(projectId))
                } finally {
                    navigatingNewDraft = false
                }
            }
            return
        }
        if (currentRoute != AppRoute.Tabs) {
            isForward = false
            currentTab = target
            backStack = listOf(AppRoute.Tabs)
        } else if (currentTab != target) {
            currentTab = target
        }
    }

    BackHandler(enabled = backStack.size > 1 || (currentRoute == AppRoute.Tabs && currentTab != MainTab.HOME)) {
        if (backStack.size > 1) {
            popRoute()
        } else {
            switchTab(MainTab.HOME)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RawColors.BgPrimary)
    ) {
        RawScreenTransition(
            targetState = currentRoute,
            isForward = isForward
        ) { route ->
            when (route) {
                AppRoute.Tabs -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        RawTabMorphTransition(
                            targetTab = currentTab,
                            modifier = Modifier.fillMaxSize()
                        ) { tab ->
                            when (tab) {
                                MainTab.HOME -> {
                                    HomeScreen(
                                        userProfile = userProfile,
                                        reelsCount = videos.size,
                                        onStartNewReel = { _ ->
                                            if (!navigatingNewDraft) {
                                                navigatingNewDraft = true
                                                scope.launch {
                                                    try {
                                                        val id = container.repository.createEmptyProject()
                                                        pushRoute(AppRoute.CreateHub(id))
                                                    } finally {
                                                        navigatingNewDraft = false
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }

                                MainTab.PALETTE -> PaletteScreen(
                                    userProfile = userProfile,
                                    cacheManager = container.cacheManager,
                                    onSavePaletteState = { counter, json ->
                                        scope.launch {
                                            container.userPreferences.savePaletteState(counter, json)
                                        }
                                    },
                                    onOpenPuterAuth = { openPuterAuthDialog(switchMode = false) },
                                    onSwitchPuterAccount = { openPuterAuthDialog(switchMode = true) },
                                    onRefreshPuterCredits = { refreshPuterCredits(silent = false) },
                                    onMarkCreditsExhausted = { markCreditsExhausted() }
                                )

                                MainTab.CREATE -> Unit

                                MainTab.REELS -> MyReelsScreen(
                                    projects = projects,
                                    videos = videos,
                                    onOpenProject = { id, _ ->
                                        pushRoute(AppRoute.CreateHub(id))
                                    },
                                    onDeleteProject = { proj ->
                                        scope.launch { container.repository.deleteProject(proj) }
                                    },
                                    onPlayVideo = { vid, _ ->
                                        pushRoute(AppRoute.Result(vid, celebrate = false))
                                    },
                                    onRenameVideo = { vid, newTitle ->
                                        scope.launch { container.repository.renameVideo(vid, newTitle) }
                                    },
                                    onDeleteVideo = { video ->
                                        scope.launch { container.repository.deleteVideo(video) }
                                    }
                                )

                                MainTab.PROFILE -> ProfileScreen(
                                    userProfile = userProfile,
                                    videos = videos,
                                    cacheManager = container.cacheManager,
                                    onUpdateName = { newName ->
                                        scope.launch { container.userPreferences.updateUserName(newName) }
                                    },
                                    onUpdatePhoto = { newPath ->
                                        scope.launch { container.userPreferences.updateProfilePhoto(newPath) }
                                    },
                                    onOpenPuterAuth = { openPuterAuthDialog(switchMode = false) },
                                    onSwitchPuterAccount = { openPuterAuthDialog(switchMode = true) },
                                    onRefreshPuterCredits = { refreshPuterCredits(silent = false) }
                                )
                            }
                        }

                        RawBottomNavBar(
                            currentTab = currentTab,
                            onSelectTab = ::switchTab,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }

                is AppRoute.CreateHub -> {
                    val project by container.repository.observeProject(route.projectId)
                        .collectAsState(initial = null)
                    project?.let { proj ->
                        val attachedCount = remember(proj.attachedAssetIds, allAssets) {
                            val ids = RawEditRepository.parseAttachedAssetIds(proj.attachedAssetIds).toSet()
                            allAssets.count { it.id in ids }
                        }
                        CreateHubScreen(
                            project = proj,
                            attachedAssetCount = attachedCount,
                            onBack = ::popRoute,
                            onDeleteProject = {
                                scope.launch {
                                    container.repository.deleteProject(proj)
                                    popRoute()
                                }
                            },
                            onOpenAddVoice = { pushRoute(AppRoute.AddVoice(proj.id)) },
                            onOpenAssetLocker = { pushRoute(AppRoute.AssetLocker(proj.id)) },
                            onOpenCopyPrompt = { pushRoute(AppRoute.CopyPrompt(proj.id)) },
                            onOpenCodePaste = { pushRoute(AppRoute.CodePaste(proj.id)) },
                            onOpenKeyframeSheet = { pushRoute(AppRoute.KeyframeSheet(proj.id)) },
                            onSwipeCreate = { _ ->
                                pushRoute(AppRoute.Rendering(proj.id))
                            }
                        )
                    }
                }

                is AppRoute.AddVoice -> {
                    val project by container.repository.observeProject(route.projectId)
                        .collectAsState(initial = null)
                    project?.let { proj ->
                        AddVoiceScreen(
                            project = proj,
                            cacheManager = container.cacheManager,
                            onBack = ::popRoute,
                            onSave = { updated ->
                                scope.launch {
                                    container.repository.saveProject(updated)
                                    popRoute()
                                }
                            }
                        )
                    }
                }

                is AppRoute.AssetLocker -> {
                    val project by container.repository.observeProject(route.projectId)
                        .collectAsState(initial = null)
                    project?.let { proj ->
                        AssetLockerScreen(
                            project = proj,
                            allAssets = allAssets,
                            authToken = userProfile.puterAuthToken,
                            puterUsername = userProfile.puterUsername,
                            modelPref = userProfile.puterModelPref,
                            creditsExhausted = userProfile.puterCreditsExhausted,
                            creditsBadgeText = userProfile.creditsSummaryText(),
                            cacheManager = container.cacheManager,
                            onBack = ::popRoute,
                            onImportAsset = { uri, isVideo ->
                                scope.launch {
                                    val res = container.cacheManager.importLockerAsset(uri, isVideo)
                                    res.onSuccess { imp ->
                                        val entity = LocalAssetEntity(
                                            fileName = imp.fileName,
                                            relativePath = imp.relativePath,
                                            kind = imp.kind,
                                            mimeType = imp.mimeType,
                                            width = imp.width,
                                            height = imp.height,
                                            durationSec = imp.durationSec,
                                            sizeBytes = imp.sizeBytes,
                                            thumbPath = imp.thumbPath,
                                            description = imp.defaultDescription,
                                            safeZone = imp.defaultSafeZone
                                        )
                                        val newId = container.repository.insertAsset(entity)
                                        container.repository.attachAssetToProject(proj.id, newId)
                                        if (userProfile.isPuterSignedIn && !userProfile.puterCreditsExhausted) {
                                            val visionFile = if (imp.kind == LocalAssetEntity.KIND_CLIP) {
                                                imp.thumbPath.takeIf { it.isNotBlank() }?.let(::File)
                                            } else {
                                                container.cacheManager.getAssetFile(imp.fileName)
                                            }
                                            if (visionFile != null && visionFile.exists()) {
                                                val descRes = PuterClient.describeAsset(
                                                    authToken = userProfile.puterAuthToken,
                                                    asset = entity.copy(id = newId),
                                                    imageFile = visionFile,
                                                    modelPref = userProfile.puterModelPref
                                                )
                                                descRes.onSuccess { (desc, safe) ->
                                                    container.repository.updateAssetDescription(newId, desc, safe)
                                                    refreshPuterCredits(silent = true)
                                                    hostToastMsg = "Added & described ${imp.relativePath}"
                                                }.onFailure { err ->
                                                    if (err is PuterOutOfCreditsException) {
                                                        container.userPreferences.markPuterCreditsExhausted()
                                                        hostToastMsg = "Added ${imp.relativePath} (out of Puter credits)"
                                                    } else {
                                                        hostToastMsg = "Added ${imp.relativePath} (Puter AI note skipped)"
                                                    }
                                                }
                                            } else {
                                                hostToastMsg = "Added ${imp.relativePath}"
                                            }
                                        } else {
                                            hostToastMsg = "Added ${imp.relativePath}"
                                        }
                                    }.onFailure { err ->
                                        hostToastMsg = err.message ?: "Could not import asset"
                                    }
                                }
                            },
                            onOpenWebBrowser = { pushRoute(AppRoute.WebBrowser(proj.id)) },
                            onSaveCutoutBitmap = { bmp, isCutout, name, desc, safe ->
                                saveCapturedBitmapToLocker(proj.id, bmp, isCutout, name, desc, safe)
                            },
                            onUpdateAssetNotes = { assetId, desc, safe ->
                                scope.launch {
                                    container.repository.updateAssetDescription(assetId, desc, safe)
                                }
                            },
                            onToggleAttach = { assetId ->
                                scope.launch {
                                    container.repository.toggleAssetForProject(proj.id, assetId)
                                }
                            },
                            onDeleteAsset = { asset ->
                                scope.launch { container.repository.deleteAsset(asset) }
                            },
                            onOpenPuterAuth = { openPuterAuthDialog(switchMode = false) },
                            onSwitchPuterAccount = { openPuterAuthDialog(switchMode = true) },
                            onRefreshPuterCredits = { refreshPuterCredits(silent = false) },
                            onMarkCreditsExhausted = { markCreditsExhausted() }
                        )
                    }
                }

                is AppRoute.WebBrowser -> {
                    WebBrowserAssetScreen(
                        authToken = userProfile.puterAuthToken,
                        modelPref = userProfile.puterModelPref,
                        creditsExhausted = userProfile.puterCreditsExhausted,
                        onBack = ::popRoute,
                        onSaveCapturedBitmap = { bmp, isCutout, name, desc, safe ->
                            saveCapturedBitmapToLocker(route.projectId, bmp, isCutout, name, desc, safe)
                        },
                        onOpenPuterAuth = { openPuterAuthDialog(switchMode = false) },
                        onSwitchPuterAccount = { openPuterAuthDialog(switchMode = true) },
                        onRefreshPuterCredits = { refreshPuterCredits(silent = false) },
                        onMarkCreditsExhausted = { markCreditsExhausted() }
                    )
                }

                is AppRoute.CopyPrompt -> {
                    val project by container.repository.observeProject(route.projectId)
                        .collectAsState(initial = null)
                    project?.let { proj ->
                        val attachedAssets = remember(proj.attachedAssetIds, allAssets) {
                            val ids = RawEditRepository.parseAttachedAssetIds(proj.attachedAssetIds).toSet()
                            allAssets.filter { it.id in ids }
                        }
                        CopyPromptScreen(
                            project = proj,
                            attachedAssets = attachedAssets,
                            palette = activePalette,
                            cacheManager = container.cacheManager,
                            onBack = ::popRoute
                        )
                    }
                }

                is AppRoute.CodePaste -> {
                    val project by container.repository.observeProject(route.projectId)
                        .collectAsState(initial = null)
                    project?.let { proj ->
                        CodePasteScreen(
                            project = proj,
                            cacheManager = container.cacheManager,
                            onBack = ::popRoute,
                            onValidatedAndSaved = { updated ->
                                scope.launch {
                                    container.repository.saveProject(updated)
                                    popRoute()
                                }
                            },
                            onOpenKeyframeSheet = { updated ->
                                scope.launch {
                                    container.repository.saveProject(updated)
                                    pushRoute(AppRoute.KeyframeSheet(updated.id))
                                }
                            },
                            onCreateNow = { updated ->
                                scope.launch {
                                    container.repository.saveProject(updated)
                                    isForward = true
                                    backStack = backStack.dropLast(1) + AppRoute.Rendering(updated.id)
                                }
                            }
                        )
                    }
                }

                is AppRoute.KeyframeSheet -> {
                    val project by container.repository.observeProject(route.projectId)
                        .collectAsState(initial = null)
                    project?.let { proj ->
                        KeyframeSheetScreen(
                            project = proj,
                            cacheManager = container.cacheManager,
                            authToken = userProfile.puterAuthToken,
                            modelPref = userProfile.puterModelPref,
                            creditsExhausted = userProfile.puterCreditsExhausted,
                            creditsBadgeText = userProfile.creditsSummaryText(),
                            onBack = ::popRoute,
                            onEditCode = {
                                if (backStack.getOrNull(backStack.lastIndex - 1) is AppRoute.CodePaste) {
                                    popRoute()
                                } else {
                                    isForward = true
                                    backStack = backStack.dropLast(1) + AppRoute.CodePaste(proj.id)
                                }
                            },
                            onApplyFixedHtml = { fixedHtml, durationSec ->
                                scope.launch {
                                    val stored = withContext(Dispatchers.IO) {
                                        container.cacheManager.persistHtmlCode(proj.id, fixedHtml)
                                    }
                                    container.repository.saveProject(
                                        proj.copy(
                                            code = stored,
                                            layersJson = "",
                                            codeDurationSec = durationSec,
                                            hasEditableLayers = false
                                        )
                                    )
                                }
                            },
                            onOpenPuterAuth = { openPuterAuthDialog(switchMode = false) },
                            onSwitchPuterAccount = { openPuterAuthDialog(switchMode = true) },
                            onRefreshPuterCredits = { refreshPuterCredits(silent = false) },
                            onMarkCreditsExhausted = { markCreditsExhausted() },
                            onRenderNow = {
                                isForward = true
                                backStack = backStack.dropLast(1) + AppRoute.Rendering(proj.id)
                            }
                        )
                    }
                }

                is AppRoute.Rendering -> {
                    RenderingScreen(
                        projectId = route.projectId,
                        onCompleted = { videoId ->
                            isForward = true
                            backStack = listOf(
                                AppRoute.Tabs,
                                AppRoute.Result(videoId, celebrate = true)
                            )
                        },
                        onCancelledOrBack = ::popRoute
                    )
                }

                is AppRoute.Result -> {
                    val video by container.repository.observeVideo(route.videoId)
                        .collectAsState(initial = null)
                    video?.let { v ->
                        ResultScreen(
                            video = v,
                            cacheManager = container.cacheManager,
                            playCelebration = route.celebrate,
                            onSavedToGallery = { galleryUri ->
                                scope.launch {
                                    container.repository.markVideoSavedToGallery(v.id, galleryUri)
                                }
                            },
                            onMakeAnother = {
                                scope.launch {
                                    val nextId = container.repository.createEmptyProject()
                                    isForward = true
                                    backStack = listOf(
                                        AppRoute.Tabs,
                                        AppRoute.CreateHub(nextId)
                                    )
                                }
                            },
                            onDeleteVideo = {
                                scope.launch {
                                    container.repository.deleteVideo(v)
                                    if (backStack.size > 1) {
                                        popRoute()
                                    } else {
                                        isForward = false
                                        currentTab = MainTab.REELS
                                        backStack = listOf(AppRoute.Tabs)
                                    }
                                }
                            },
                            onBack = {
                                if (backStack.size > 1) {
                                    popRoute()
                                } else {
                                    isForward = false
                                    currentTab = MainTab.HOME
                                    backStack = listOf(AppRoute.Tabs)
                                }
                            }
                        )
                    }
                }
            }
        }

        RawToastBanner(
            message = hostToastMsg,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = if (currentRoute == AppRoute.Tabs) 104.dp else 94.dp)
        )
    }

    if (showPuterAuthDialog) {
        PuterAuthDialog(
            currentToken = userProfile.puterAuthToken,
            currentUsername = userProfile.puterUsername,
            currentModelPref = userProfile.puterModelPref,
            initialUsage = userProfile.toUsageReportOrNull(),
            startInSwitchMode = puterDialogSwitchMode,
            onAuthConnected = { token, username, modelPref, usage ->
                scope.launch {
                    container.userPreferences.savePuterSession(
                        authToken = token,
                        username = username,
                        modelPref = modelPref,
                        usage = usage
                    )
                    showPuterAuthDialog = false
                    val leftStr = usage?.let { " \u2022 ${it.formattedRemaining()} left" }.orEmpty()
                    hostToastMsg = "Signed in as @$username$leftStr"
                }
            },
            onSaveModelPref = { modelPref ->
                scope.launch {
                    container.userPreferences.savePuterModelPref(modelPref)
                }
            },
            onSignOut = {
                scope.launch {
                    container.userPreferences.clearPuterSession()
                    showPuterAuthDialog = false
                    hostToastMsg = "Signed out of Puter"
                }
            },
            onDismiss = { showPuterAuthDialog = false }
        )
    }
}
