package id.kenang.android

import android.app.Application
import id.kenang.app.di.appModule
import id.kenang.core.common.Logging
import id.kenang.core.data.AppDirs
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class KenangApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Storage root must exist before any repository resolves.
        AppDirs.init(filesDir)
        id.kenang.app.ui.platform.AppContextHolder.init(this)
        Logging.init(AppDirs.logs)
        startKoin {
            androidContext(this@KenangApp)
            modules(appModule)
        }
    }
}
