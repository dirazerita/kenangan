package id.kenang.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import id.kenang.app.connectivity.ConnectivityMonitor
import id.kenang.app.ui.about.AboutScreen
import id.kenang.app.ui.analysis.AnalysisScreen
import id.kenang.app.ui.components.OfflineBanner
import id.kenang.app.ui.generation.GenerationScreen
import id.kenang.app.ui.home.HomeScreen
import id.kenang.app.ui.onboarding.OnboardingScreen
import id.kenang.app.ui.result.ResultScreen
import id.kenang.app.ui.settings.SettingsScreen
import id.kenang.app.ui.storyboard.StoryboardScreen
import id.kenang.app.ui.wizard.WizardScreen
import id.kenang.app.ui.theme.KenangTheme
import id.kenang.core.common.i18n.Strings
import id.kenang.core.data.SettingsRepository
import id.kenang.core.providers.fal.FalKeyPool
import org.koin.compose.koinInject

/** Simple in-memory navigation. */
sealed class Route {
    data object Onboarding : Route()
    data object Home : Route()
    data class Wizard(val projectId: String?) : Route()
    data class Analysis(val projectId: String) : Route()
    data class Storyboard(val projectId: String) : Route()
    data class Generation(val projectId: String) : Route()
    data class Result(val projectId: String) : Route()
    data object Settings : Route()
    data object About : Route()
    data object Upscale : Route()
    data object Ideas : Route()
}

@Composable
fun App() {
    val settings = koinInject<SettingsRepository>()
    val connectivity = koinInject<ConnectivityMonitor>()
    val keyPool = koinInject<FalKeyPool>()
    val generationEvents = koinInject<id.kenang.core.common.events.GenerationEvents>()

    // First launch goes straight to onboarding → Home. NO license UI (D-002).
    // -Dkenang.devRoute=home|settings|about|onboarding|wizard|storyboard:<projectId>
    // forces a start screen (dev/screenshots only).
    var route by remember {
        val dev = System.getProperty("kenang.devRoute")
        val devRoute = when {
            dev == "home" -> Route.Home
            dev == "settings" -> Route.Settings
            dev == "about" -> Route.About
            dev == "onboarding" -> Route.Onboarding
            dev == "wizard" -> Route.Wizard(null)
            dev?.startsWith("storyboard:") == true -> Route.Storyboard(dev.substringAfter(":"))
            else -> null
        }
        mutableStateOf(devRoute ?: if (settings.onboardingDone) Route.Home else Route.Onboarding)
    }
    val snackbar = remember { SnackbarHostState() }
    val online by connectivity.online.collectAsState()

    LaunchedEffect(Unit) { connectivity.start(this) }

    // Storyboard "Buat Video" → generation screen (subscribed for the whole
    // app lifetime — GenerationEvents has no replay, so this must outlive the
    // storyboard screen).
    LaunchedEffect(Unit) {
        generationEvents.start.collect { request ->
            route = Route.Generation(request.projectId)
        }
    }

    // Global toast for fal key failover (AD-14).
    LaunchedEffect(Unit) {
        keyPool.keySwitched.collect { switch ->
            snackbar.showSnackbar(
                Strings.KEYS_SWITCHED_TOAST
                    .replace("%1", switch.fromLabel)
                    .replace("%2", switch.toLabel),
            )
        }
    }

    KenangTheme {
        id.kenang.app.ui.theme.SkeuoBackground {
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            // Transparent container has no contentColorFor mapping — without an
            // explicit contentColor every default Text/Icon falls back to black.
            contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            Column(Modifier.fillMaxSize()) {
                OfflineBanner(visible = !online)
                when (route) {
                    Route.Onboarding -> OnboardingScreen(
                        snackbar = snackbar,
                        onFinished = {
                            settings.onboardingDone = true
                            route = Route.Home
                        },
                    )
                    Route.Home -> HomeScreen(
                        snackbar = snackbar,
                        online = online,
                        onNewProject = { route = Route.Wizard(null) },
                        onOpenProject = { projectId, status ->
                            route = when (status) {
                                "draft" -> Route.Wizard(projectId)
                                "analyzing" -> Route.Analysis(projectId)
                                "generating" -> Route.Generation(projectId)
                                "done" -> Route.Result(projectId)
                                else -> Route.Storyboard(projectId)
                            }
                        },
                        onSettings = { route = Route.Settings },
                        onAbout = { route = Route.About },
                        onUpscale = { route = Route.Upscale },
                        onIdeas = { route = Route.Ideas },
                    )
                    is Route.Wizard -> WizardScreen(
                        existingProjectId = (route as Route.Wizard).projectId,
                        onBack = { route = Route.Home },
                        onGoAnalysis = { id -> route = Route.Analysis(id) },
                    )
                    is Route.Analysis -> {
                        val id = (route as Route.Analysis).projectId
                        AnalysisScreen(
                            projectId = id,
                            onDone = { route = Route.Storyboard(id) },
                            onBackToWizard = { route = Route.Wizard(id) },
                        )
                    }
                    is Route.Storyboard -> StoryboardScreen(
                        projectId = (route as Route.Storyboard).projectId,
                        snackbar = snackbar,
                        onBack = { route = Route.Home },
                    )
                    is Route.Generation -> {
                        val id = (route as Route.Generation).projectId
                        GenerationScreen(
                            projectId = id,
                            onDone = { route = Route.Result(id) },
                            onEditStoryboard = { route = Route.Storyboard(id) },
                            onOpenKeySettings = { route = Route.Settings },
                            onBack = { route = Route.Home },
                        )
                    }
                    is Route.Result -> ResultScreen(
                        projectId = (route as Route.Result).projectId,
                        snackbar = snackbar,
                        onBack = { route = Route.Home },
                    )
                    Route.Settings -> SettingsScreen(
                        snackbar = snackbar,
                        online = online,
                        onBack = { route = Route.Home },
                        onReopenOnboarding = { route = Route.Onboarding },
                    )
                    Route.About -> AboutScreen(onBack = { route = Route.Home })
                    Route.Ideas -> id.kenang.app.ui.ideas.IdeasScreen(
                        snackbar = snackbar,
                        onBack = { route = Route.Home },
                    )
                    Route.Upscale -> id.kenang.app.ui.upscale.UpscaleScreen(
                        snackbar = snackbar,
                        onBack = { route = Route.Home },
                    )
                }
            }
        }
        }
    }
}
