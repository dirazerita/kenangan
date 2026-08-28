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
import io.github.aakira.napier.Napier
import org.koin.core.context.startKoin

fun main() {
    Logging.init(AppDirs.logs)
    Napier.i("Kenang starting — data dir: ${AppDirs.root.absolutePath}")

    startKoin {
        modules(appModule)
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
