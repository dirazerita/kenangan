package id.kenang.core.providers.gen

import id.kenang.core.data.SettingsRepository
import io.github.aakira.napier.Napier
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * How long a clip of a given model and length has taken on this machine,
 * so the generation screen can show a progress bar that means something
 * (owner 2026-09-16: "agar bisa kelihatan sejauh mana prosesnya"). fal
 * reports no percentage for a render, only queued / in progress / done -
 * the bar is therefore time against the running average of earlier clips,
 * seeded with 40 s per clip second (a 5 s Kling Pro clip measured 206 s
 * from submit to file, queue included).
 */
class RenderTimeStats(private val settings: SettingsRepository) {

    @Serializable
    data class Stat(val avgSeconds: Double, val samples: Int)

    private val json = Json { ignoreUnknownKeys = true }

    /** Expected seconds from submit to finished file for [slug] at [durationS]. */
    fun estimateSeconds(slug: String, durationS: Long): Int {
        val stat = load()[key(slug, durationS)]
        val seconds = stat?.avgSeconds ?: (durationS * SECONDS_PER_CLIP_SECOND)
        return seconds.toInt().coerceIn(MIN_ESTIMATE_S, MAX_ESTIMATE_S)
    }

    /** Folds one finished clip into the running average (the last [WINDOW] weigh most). */
    fun record(slug: String, durationS: Long, seconds: Long) {
        if (seconds <= 0) return
        val all = load().toMutableMap()
        val k = key(slug, durationS)
        val old = all[k]
        val n = ((old?.samples ?: 0) + 1).coerceAtMost(WINDOW)
        val avg = if (old == null) seconds.toDouble() else old.avgSeconds + (seconds - old.avgSeconds) / n
        all[k] = Stat(avg, n)
        runCatching { settings.set(KEY, json.encodeToString(MapSerializer(String.serializer(), Stat.serializer()), all)) }
            .onFailure { Napier.w("render stats not saved: ${it.message}") }
    }

    private fun load(): Map<String, Stat> = runCatching {
        settings.get(KEY)?.takeIf { it.isNotBlank() }
            ?.let { json.decodeFromString(MapSerializer(String.serializer(), Stat.serializer()), it) }
    }.getOrNull() ?: emptyMap()

    private fun key(slug: String, durationS: Long) = "$slug|$durationS"

    companion object {
        const val KEY = "render_stats"
        const val SECONDS_PER_CLIP_SECOND = 40.0
        const val MIN_ESTIMATE_S = 60
        const val MAX_ESTIMATE_S = 30 * 60
        const val WINDOW = 8
    }
}
