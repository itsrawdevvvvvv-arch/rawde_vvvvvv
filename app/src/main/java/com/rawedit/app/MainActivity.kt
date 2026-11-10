package com.rawedit.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import com.rawedit.app.data.local.ProjectEntity
import com.rawedit.app.data.local.UserProfile
import com.rawedit.app.ui.components.MainTab
import com.rawedit.app.ui.components.RawBottomNavBar
import com.rawedit.app.ui.motion.GhostWordWipeOverlay
import com.rawedit.app.ui.motion.RawScreenTransition
import com.rawedit.app.ui.screens.AddVoiceScreen
import com.rawedit.app.ui.screens.CodePasteScreen
import com.rawedit.app.ui.screens.CopyPromptScreen
import com.rawedit.app.ui.screens.CreateHubScreen
import com.rawedit.app.ui.screens.EditorScreen
import com.rawedit.app.ui.screens.HomeScreen
import com.rawedit.app.ui.screens.MyReelsScreen
import com.rawedit.app.ui.screens.OnboardingScreen
import com.rawedit.app.ui.screens.ProfileScreen
import com.rawedit.app.ui.screens.ProfileSetupScreen
import com.rawedit.app.ui.screens.RenderingScreen
import com.rawedit.app.ui.screens.ResultScreen
import com.rawedit.app.ui.theme.RawColors
import com.rawedit.app.ui.theme.RawEditTheme
import kotlinx.coroutines.launch

private sealed interface AppRoute {
    data class Tabs(val tab: MainTab) : AppRoute
    data class CreateHub(val projectId: Long) : AppRoute
    data class AddVoice(val projectId: Long) : AppRoute
    data class CopyPrompt(val projectId: Long) : AppRoute
    data class CodePaste(val projectId: Long) : AppRoute
    data class Editor(val projectId: Long, val fromResult: Boolean = false) : AppRoute
    data class Rendering(val projectId: Long) : AppRoute
    data class Result(val videoId: Long, val celebrate: Boolean) : AppRoute
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bgArgb = RawColors.BgPrimary.toArgb()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(bgArgb),
            navigationBarStyle = SystemBarStyle.dark(bgArgb)
        )
        val container = (application as RawEditApplication).container

        setContent {
            RawEditTheme {
                RawEditAppRoot(container = container)
            }
        }
    }
}

@Composable
private fun RawEditAppRoot(container: AppContainer) {
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
            CircularProgressIndicator(color = RawColors.TextPrimary)
        }
        return
    }

    var onboardingPassed by remember(profile.onboardingDone) {
        mutableStateOf(profile.onboardingDone)
    }

    if (!profile.onboardingDone) {
        if (!onboardingPassed) {
            OnboardingScreen(
                onFinished = { onboardingPassed = true }
            )
        } else {
            ProfileSetupScreen(
                cacheManager = container.cacheManager,
                onComplete = { name, photoPath ->
                    scope.launch {
                        container.userPreferences.completeOnboardingAndProfile(name, photoPath)
                    }
                }
            )
        }
        return
    }

    MainAuthenticatedHost(
        container = container,
        userProfile = profile
    )
}

@Composable
private fun MainAuthenticatedHost(
    container: AppContainer,
    userProfile: UserProfile
) {
    val scope = rememberCoroutineScope()
    val projects by container.repository.allProjects.collectAsState(initial = emptyList())
    val videos by container.repository.allVideos.collectAsState(initial = emptyList())

    var backStack by remember { mutableStateOf<List<AppRoute>>(listOf(AppRoute.Tabs(MainTab.HOME))) }
    val currentRoute = backStack.last()

    // Ghost-word wipe state for main tab switches
    var ghostWord by remember { mutableStateOf<String?>(null) }
    var ghostTrigger by remember { mutableIntStateOf(0) }

    fun pushRoute(route: AppRoute, @Suppress("UNUSED_PARAMETER") tapOrigin: Offset = Offset(540f, 960f)) {
        backStack = backStack + route
    }

    fun popRoute() {
        if (backStack.size > 1) {
            backStack = backStack.dropLast(1)
        }
    }

    fun switchTab(target: MainTab) {
        if (target == MainTab.CREATE) {
            scope.launch {
                val activeDraft = projects.firstOrNull { it.state == ProjectEntity.STATE_IN_PROGRESS }
                val projectId = activeDraft?.id ?: container.repository.createEmptyProject()
                pushRoute(AppRoute.CreateHub(projectId), Offset(420f, 2000f))
            }
            return
        }
        val currentTab = (currentRoute as? AppRoute.Tabs)?.tab
        if (currentTab != target) {
            ghostWord = target.ghostWord
            ghostTrigger++
            backStack = listOf(AppRoute.Tabs(target))
        }
    }

    BackHandler(enabled = backStack.size > 1 || (currentRoute is AppRoute.Tabs && currentRoute.tab != MainTab.HOME)) {
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
        RawScreenTransition(targetState = currentRoute) { route ->
            when (route) {
                is AppRoute.Tabs -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        when (route.tab) {
                            MainTab.HOME -> HomeScreen(
                                userProfile = userProfile,
                                projects = projects,
                                videos = videos,
                                onOpenProfile = { switchTab(MainTab.PROFILE) },
                                onStartNewReel = { origin ->
                                    scope.launch {
                                        val id = container.repository.createEmptyProject()
                                        pushRoute(AppRoute.CreateHub(id), origin)
                                    }
                                },
                                onOpenProject = { id, origin ->
                                    pushRoute(AppRoute.CreateHub(id), origin)
                                },
                                onOpenVideoResult = { videoId, origin ->
                                    pushRoute(AppRoute.Result(videoId, celebrate = false), origin)
                                }
                            )

                            MainTab.CREATE -> Unit // Handled by switchTab -> CreateHub

                            MainTab.REELS -> MyReelsScreen(
                                videos = videos,
                                onPlayVideo = { vid, origin ->
                                    pushRoute(AppRoute.Result(vid, celebrate = false), origin)
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
                                }
                            )
                        }

                        GhostWordWipeOverlay(word = ghostWord, triggerKey = ghostTrigger)

                        RawBottomNavBar(
                            currentTab = route.tab,
                            onSelectTab = ::switchTab,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }

                is AppRoute.CreateHub -> {
                    val project by container.repository.observeProject(route.projectId)
                        .collectAsState(initial = null)
                    project?.let { proj ->
                        CreateHubScreen(
                            project = proj,
                            onBack = ::popRoute,
                            onOpenAddVoice = { pushRoute(AppRoute.AddVoice(proj.id)) },
                            onOpenCopyPrompt = { pushRoute(AppRoute.CopyPrompt(proj.id)) },
                            onOpenCodePaste = { pushRoute(AppRoute.CodePaste(proj.id)) },
                            onOpenEditor = { pushRoute(AppRoute.Editor(proj.id)) },
                            onSwipeCreate = { origin ->
                                pushRoute(AppRoute.Rendering(proj.id), origin)
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

                is AppRoute.CopyPrompt -> {
                    val project by container.repository.observeProject(route.projectId)
                        .collectAsState(initial = null)
                    project?.let { proj ->
                        CopyPromptScreen(
                            project = proj,
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
                            onCreateNow = { updated ->
                                scope.launch {
                                    container.repository.saveProject(updated)
                                    backStack = backStack.dropLast(1) + AppRoute.Rendering(updated.id)
                                }
                            }
                        )
                    }
                }

                is AppRoute.Editor -> {
                    val project by container.repository.observeProject(route.projectId)
                        .collectAsState(initial = null)
                    project?.let { proj ->
                        EditorScreen(
                            project = proj,
                            cacheManager = container.cacheManager,
                            openedAfterRender = route.fromResult,
                            onAutoSaveDraft = { updated ->
                                scope.launch { container.repository.saveProject(updated) }
                            },
                            onDone = { updated ->
                                scope.launch {
                                    container.repository.saveProject(updated)
                                    popRoute()
                                }
                            },
                            onRenderUpdated = { updated ->
                                scope.launch {
                                    container.repository.saveProject(updated)
                                    backStack = backStack.dropLast(1) + AppRoute.Rendering(updated.id)
                                }
                            },
                            onBack = ::popRoute
                        )
                    }
                }

                is AppRoute.Rendering -> {
                    RenderingScreen(
                        projectId = route.projectId,
                        onCompleted = { videoId ->
                            backStack = listOf(
                                AppRoute.Tabs(MainTab.HOME),
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
                            onEditInTimeline = { projectId ->
                                pushRoute(AppRoute.Editor(projectId = projectId, fromResult = true))
                            },
                            onMakeAnother = {
                                scope.launch {
                                    val nextId = container.repository.createEmptyProject()
                                    backStack = listOf(
                                        AppRoute.Tabs(MainTab.HOME),
                                        AppRoute.CreateHub(nextId)
                                    )
                                }
                            },
                            onBack = {
                                if (backStack.size > 1) popRoute() else switchTab(MainTab.HOME)
                            }
                        )
                    }
                }
            }
        }
    }
}
