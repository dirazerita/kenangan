package id.kenang.app.connectivity

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Lightweight online/offline probe: TCP connect to fal's edge every 10s.
 * Offline → UI shows the banner and AI features go read-only
 * (MASTER_PROMPT_02 §Global patterns).
 */
class ConnectivityMonitor(
    private val host: String = "queue.fal.run",
    private val port: Int = 443,
    private val intervalMillis: Long = 10_000,
) {
    private val _online = MutableStateFlow(true)
    val online: StateFlow<Boolean> = _online

    fun start(scope: CoroutineScope) {
        scope.launch {
            while (true) {
                _online.value = probe()
                delay(intervalMillis)
            }
        }
    }

    private suspend fun probe(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            Socket().use { it.connect(InetSocketAddress(host, port), 3000) }
            true
        }.getOrDefault(false)
    }
}
