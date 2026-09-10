package id.kenang.app

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import id.kenang.app.di.appModule
import id.kenang.app.ui.App
import id.kenang.core.common.Logging
import id.kenang.core.common.i18n.Strings
import id.kenang.core.data.AppDirs
import id.kenang.core.data.SettingsRepository
import java.io.File
import io.github.aakira.napier.Napier
import org.koin.core.context.startKoin

fun main() {
    Logging.init(AppDirs.logs)
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
        Window(
            onCloseRequest = ::exitApplication,
            title = Strings.APP_NAME,
            // Title bar + taskbar icon (the .ico covers the packaged EXE).
            icon = androidx.compose.ui.res.painterResource("icon/kenang_512.png"),
            state = rememberWindowState(width = 1200.dp, height = 800.dp),
        ) {
            App()
        }
    }
}
