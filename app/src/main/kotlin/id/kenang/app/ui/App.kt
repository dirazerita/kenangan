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
import id.kenang.app.ui.components.OfflineBanner
import id.kenang.app.ui.home.HomeScreen
import id.kenang.app.ui.newproject.NewProjectPlaceholderScreen
import id.kenang.app.ui.onboarding.OnboardingScreen
import id.kenang.app.ui.settings.SettingsScreen
import id.kenang.app.ui.theme.KenangTheme
import id.kenang.core.common.i18n.Strings
import id.kenang.core.data.SettingsRepository
import id.kenang.core.providers.fal.FalKeyPool
import org.koin.compose.koinInject

/** Simple in-memory navigation — Phase 03 may replace with a real nav library. */
sealed class Route {
    data object Onboarding : Route()
    data object Home : Route()
    data object NewProject : Route()
    data object Settings : Route()
    data object About : Route()
}

@Composable
fun App() {
    val settings = koinInject<SettingsRepository>()
    val connectivity = koinInject<ConnectivityMonitor>()
    val keyPool = koinInject<FalKeyPool>()

    // First launch goes straight to onboarding → Home. NO license UI (D-002).
    // -Dkenang.devRoute=home|settings|about|onboarding forces a start screen (dev/screenshots only).
    var route by remember {
        val devRoute = when (System.getProperty("kenang.devRoute")) {
            "home" -> Route.Home
            "settings" -> Route.Settings
            "about" -> Route.About
            "onboarding" -> Route.Onboarding
            else -> null
        }
        mutableStateOf(devRoute ?: if (settings.onboardingDone) Route.Home else Route.Onboarding)
    }
    val snackbar = remember { SnackbarHostState() }
    val online by connectivity.online.collectAsState()

    LaunchedEffect(Unit) { connectivity.start(this) }

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
        Scaffold(
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
                        onNewProject = { route = Route.NewProject },
                        onSettings = { route = Route.Settings },
                        onAbout = { route = Route.About },
                    )
                    Route.NewProject -> NewProjectPlaceholderScreen(onBack = { route = Route.Home })
                    Route.Settings -> SettingsScreen(
                        snackbar = snackbar,
                        online = online,
                        onBack = { route = Route.Home },
                        onReopenOnboarding = { route = Route.Onboarding },
                    )
                    Route.About -> AboutScreen(onBack = { route = Route.Home })
                }
            }
        }
    }
}
