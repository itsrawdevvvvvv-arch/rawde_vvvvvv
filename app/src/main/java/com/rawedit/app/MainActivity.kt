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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import com.rawedit.app.data.local.ProjectEntity
import com.rawedit.app.data.local.UserProfile
import com.rawedit.app.ui.components.MainTab
import com.rawedit.app.ui.components.RawBottomNavBar
import com.rawedit.app.ui.motion.RawScreenTransition
import com.rawedit.app.ui.motion.RawTabMorphTransition
import com.rawedit.app.ui.screens.AddVoiceScreen
import com.rawedit.app.ui.screens.CodePasteScreen
import com.rawedit.app.ui.screens.CopyPromptScreen
import com.rawedit.app.ui.screens.CreateHubScreen
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

private enum class RootStage {
    ONBOARDING,
    PROFILE_SETUP,
    MAIN
}

private sealed interface AppRoute {
    data object Tabs : AppRoute
    data class CreateHub(val projectId: Long) : AppRoute
    data class AddVoice(val projectId: Long) : AppRoute
    data class CopyPrompt(val projectId: Long) : AppRoute
    data class CodePaste(val projectId: Long) : AppRoute
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
                userProfile = profile
            )
        }
    }
}

@Composable
private fun MainAuthenticatedHost(
    container: AppContainer,
    userProfile: UserProfile
) {
    val scope = rememberCoroutineScope()
    val projects by container.repository.allProjects.collectAsState(initial = emptyList())
    val videos by container.repository.allVideos.collectAsState(initial = emptyList())

    var backStack by remember { mutableStateOf<List<AppRoute>>(listOf(AppRoute.Tabs)) }
    var currentTab by remember { mutableStateOf(MainTab.HOME) }
    var isForward by remember { mutableStateOf(true) }
    val currentRoute = backStack.last()

    fun pushRoute(route: AppRoute) {
        isForward = true
        backStack = backStack + route
    }

    fun popRoute() {
        if (backStack.size > 1) {
            isForward = false
            backStack = backStack.dropLast(1)
        }
    }

    fun switchTab(target: MainTab) {
        if (target == MainTab.CREATE) {
            scope.launch {
                val activeDraft = projects.firstOrNull { it.state == ProjectEntity.STATE_IN_PROGRESS }
                val projectId = activeDraft?.id ?: container.repository.createEmptyProject()
                pushRoute(AppRoute.CreateHub(projectId))
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
                                MainTab.HOME -> HomeScreen(
                                    userProfile = userProfile,
                                    projects = projects,
                                    videos = videos,
                                    onOpenProfile = { switchTab(MainTab.PROFILE) },
                                    onStartNewReel = { _ ->
                                        scope.launch {
                                            val id = container.repository.createEmptyProject()
                                            pushRoute(AppRoute.CreateHub(id))
                                        }
                                    },
                                    onOpenProject = { id, _ ->
                                        pushRoute(AppRoute.CreateHub(id))
                                    },
                                    onOpenVideoResult = { videoId, _ ->
                                        pushRoute(AppRoute.Result(videoId, celebrate = false))
                                    }
                                )

                                MainTab.CREATE -> Unit

                                MainTab.REELS -> MyReelsScreen(
                                    videos = videos,
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
                                    }
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
                        CreateHubScreen(
                            project = proj,
                            onBack = ::popRoute,
                            onOpenAddVoice = { pushRoute(AppRoute.AddVoice(proj.id)) },
                            onOpenCopyPrompt = { pushRoute(AppRoute.CopyPrompt(proj.id)) },
                            onOpenCodePaste = { pushRoute(AppRoute.CodePaste(proj.id)) },
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
                                    isForward = true
                                    backStack = backStack.dropLast(1) + AppRoute.Rendering(updated.id)
                                }
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
    }
}
