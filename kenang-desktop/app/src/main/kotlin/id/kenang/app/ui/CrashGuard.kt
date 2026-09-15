package id.kenang.app.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import id.kenang.core.common.i18n.Strings

/**
 * What happens after an uncaught UI exception (owner 2026-09-15: Compose's
 * default handler put up "Error" and disposed the window, and the app was
 * gone with five renders still to run).
 *
 * The window whose composition threw is DEAD - its recomposer has shut
 * down, so nothing drawn in it will ever update again (verified: a dialog
 * set from the handler never appeared). So Main rebuilds the window: a new
 * composition over the same app state, database and in-flight jobs, which
 * opens on Home and shows this dialog. A storm of exceptions (a bug that
 * throws on every rebuild) ends the app the old way rather than looping.
 */
object CrashGuard {
    /** Exceptions within [STORM_WINDOW_MS] after which the app gives up. */
    const val STORM_LIMIT = 3
    const val STORM_WINDOW_MS = 15_000L

    private val message = mutableStateOf<String?>(null)
    private val recent = ArrayDeque<Long>()

    /** Dev hook (-Dkenang.devThrow=true) fires once, not on every rebuilt window. */
    @Volatile
    var devThrown = false

    /** Records [t] for the dialog; true when the app should keep going (rebuild), false when it is a storm. */
    @Synchronized
    fun report(t: Throwable, now: Long = System.currentTimeMillis()): Boolean {
        message.value = (t.message ?: t::class.simpleName ?: "kesalahan").take(300)
        recent.addLast(now)
        while (recent.isNotEmpty() && now - recent.first() > STORM_WINDOW_MS) recent.removeFirst()
        return recent.size < STORM_LIMIT
    }

    @Composable
    fun Dialog() {
        val text = message.value ?: return
        AlertDialog(
            onDismissRequest = { message.value = null },
            title = { Text(Strings.CRASH_TITLE) },
            text = { Text(Strings.CRASH_BODY + "\n\n" + text) },
            confirmButton = { TextButton(onClick = { message.value = null }) { Text(Strings.CRASH_OK) } },
        )
    }
}
