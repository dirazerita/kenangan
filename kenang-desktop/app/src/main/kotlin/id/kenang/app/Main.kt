package id.kenang.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.LocalWindowExceptionHandlerFactory
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowExceptionHandler
import androidx.compose.ui.window.WindowExceptionHandlerFactory
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import id.kenang.app.di.appModule
import id.kenang.app.ui.App
import id.kenang.app.ui.CrashGuard
import id.kenang.core.common.Logging
import id.kenang.core.common.i18n.Strings
import id.kenang.core.data.AppDirs
import id.kenang.core.data.SettingsRepository
import java.io.File
import io.github.aakira.napier.Napier
import org.koin.core.context.startKoin

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
fun main() {
    Logging.init(AppDirs.logs)
    // Background threads: log, never die silently (the JVM does not exit
    // on a worker thread's exception, but the log must say what happened).
    Thread.setDefaultUncaughtExceptionHandler { thread, t ->
        Napier.e("uncaught exception on ${thread.name}: $t", t)
    }
    Napier.i("Kenang starting — data dir: ${AppDirs.root.absolutePath}")

    val koin = startKoin {
        modules(appModule)
    }.koin

    // The heavy folders may live on another drive (owner 2026-09-10). Applied
    // BEFORE any screen, so nothing ever resolves a path under the old root.
    val dataFolder = runCatching {
        koin.get<SettingsRepository>().dataFolder
    }.getOrNull()
    if (!dataFolder.isNullOrBlank()) {
        AppDirs.useMediaRoot(File(dataFolder))
        Napier.i("data folder override: ${AppDirs.mediaRoot.absolutePath}")
    }

    application {
        // An uncaught exception in the UI must not close the app (owner
        // 2026-09-15): Compose's default handler shows "Error" and disposes
        // the window, taking every in-flight render with it. The window that
        // threw cannot be kept either - its composition is dead - so it is
        // rebuilt: same app state, database and jobs, fresh screens, and the
        // reason shown in a dialog. Only a storm of exceptions ends the app.
        var uiGeneration by remember { mutableStateOf(0) }
        val windowState = rememberWindowState(width = 1200.dp, height = 800.dp)
        val guard = WindowExceptionHandlerFactory { _ ->
            WindowExceptionHandler { t ->
                Napier.e("uncaught UI exception: $t", t)
                if (CrashGuard.report(t)) {
                    Napier.w("rebuilding the window after an uncaught exception (generation ${uiGeneration + 1})")
                    uiGeneration++
                } else {
                    Napier.e("uncaught exceptions keep coming - closing the app")
                    exitApplication()
                }
            }
        }
        key(uiGeneration) {
            CompositionLocalProvider(LocalWindowExceptionHandlerFactory provides guard) {
                Window(
                    onCloseRequest = ::exitApplication,
                    title = Strings.APP_NAME,
                    // Title bar + taskbar icon (the .ico covers the packaged EXE).
                    icon = androidx.compose.ui.res.painterResource("icon/kenang_512.png"),
                    state = windowState,
                ) {
                    App()
                    CrashGuard.Dialog()
                }
            }
        }
    }
}
