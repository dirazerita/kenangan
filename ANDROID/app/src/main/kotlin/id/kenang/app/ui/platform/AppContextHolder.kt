package id.kenang.app.ui.platform

import android.content.Context

/**
 * Application context for the handful of shared helpers that are plain
 * functions on desktop (openInBrowser). Only ever holds the *application*
 * context, so it cannot leak an Activity.
 */
object AppContextHolder {
    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun get(): Context? = appContext
}
