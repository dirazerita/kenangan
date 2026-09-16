package id.kenang.core.providers.story

import id.kenang.core.common.AppError
import id.kenang.core.common.AppResult
import id.kenang.core.common.Provider
import id.kenang.core.common.err
import id.kenang.core.common.ok
import id.kenang.core.data.AppDirs
import id.kenang.core.data.config.ConfigRepository
import id.kenang.core.providers.CostTracker
import id.kenang.core.providers.fal.FalQueueClient
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.File
import java.security.MessageDigest

/**
 * Wizard Step-2 voice preview: short MiniMax id-ID sample via fal (default
 * locked voice from config), cached locally so replays are free.
 * UI shows "biaya ±$0.001".
 */
class TtsPreviewService(
    private val falClient: FalQueueClient,
    private val http: HttpClient,
    private val configRepository: ConfigRepository,
    private val costTracker: CostTracker,
) {
    private val cacheDir: File get() = File(AppDirs.root, "cache/tts").apply { mkdirs() }

    /** Fetches (or reuses) the preview MP3 for [text] with [voiceId]. */
    suspend fun preview(text: String, voiceId: String? = null): AppResult<File> {
        val tts = configRepository.current().tts
        val voice = voiceId ?: tts.voice
        val sample = text.take(120).ifBlank { "Kenangan indah ini akan selalu hidup di hati kami." }

        val hash = MessageDigest.getInstance("SHA-1")
            .digest("$voice|$sample".toByteArray()).joinToString("") { "%02x".format(it) }
        val cached = File(cacheDir, "$hash.mp3")
        if (cached.isFile && cached.length() > 0) return cached.ok()

        val body = buildJsonObject {
            put("text", sample)
            putJsonObject("voice_setting") {
                put("voice_id", voice)
                put("speed", 0.95)
            }
            put("output_format", "url")
            put("language_boost", tts.languageBoost)
        }
        val submitted = when (val s = falClient.submit(tts.slug, body)) {
            is AppResult.Ok -> s.value
            is AppResult.Err -> return s
        }
        val payload = when (val r = falClient.awaitResult(submitted, timeoutMillis = 90_000)) {
            is AppResult.Ok -> r.value.payload
            is AppResult.Err -> return r
        }
        val audioUrl = runCatching {
            payload["audio"]!!.jsonObject["url"]!!.jsonPrimitive.content
        }.getOrNull() ?: return AppError.ProviderFailed(Provider.FAL, "no audio in TTS result").err()

        return runCatching {
            cached.writeBytes(http.get(audioUrl).readRawBytes())
            costTracker.record(
                projectId = "preview", jobId = submitted.requestId, model = tts.slug,
                keyLabel = submitted.keyLabel, qty = sample.length.toDouble(),
                unit = "per_1k_chars", estUsd = sample.length / 1000.0 * 0.10,
            )
            cached
        }.fold(
            onSuccess = { it.ok() },
            onFailure = { AppError.Unknown("preview save failed", it).err() },
        )
    }

    /** Plays an MP3 file through the media stack; returns a stop handle. */

    /**
     * The standard sample of [voiceId] for the voice list (owner 2026-09-16:
     * a button under every voice name that plays that voice). The preset
     * voices ship inside the app (`/voices/<id>.mp3`, generated once by the
     * voiceSamples task) so a click is instant and free; a cloned or unknown
     * voice is synthesised once with the config preview text and cached.
     */
    suspend fun sample(voiceId: String): AppResult<File> {
        val name = sampleName(voiceId)
        val cached = File(cacheDir, "sample_$name.mp3")
        if (cached.isFile && cached.length() > 0) return cached.ok()
        javaClass.getResourceAsStream("/voices/$name.mp3")?.use { input ->
            runCatching { cached.outputStream().use { input.copyTo(it) } }
                .onFailure { Napier.w("bundled voice sample copy failed: ${it.message}") }
            if (cached.isFile && cached.length() > 0) return cached.ok()
        }
        return when (val r = preview(configRepository.current().tts.previewText, voiceId)) {
            is AppResult.Ok -> runCatching { r.value.copyTo(cached, overwrite = true) }.getOrDefault(r.value).ok()
            is AppResult.Err -> r
        }
    }

    /** Plays an MP3 file; returns a stop handle. [onFinished] runs once playback ends by itself. */
    suspend fun play(file: File, onFinished: () -> Unit = {}): AutoCloseable = withContext(Dispatchers.IO) {
        val player = android.media.MediaPlayer()
        runCatching {
            player.setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            player.setDataSource(file.absolutePath)
            player.setOnCompletionListener {
                runCatching { it.release() }
                runCatching { onFinished() }
            }
            player.prepare()
            player.start()
        }.onFailure {
            Napier.w("tts preview playback failed: ${it.message}")
            runCatching { player.release() }
        }
        AutoCloseable {
            runCatching {
                if (player.isPlaying) player.stop()
                player.release()
            }
        }
    }

    companion object {
        /**
         * File name of a voice's sample. MiniMax ids differ only by case
         * ("Lovely_Girl" is a young woman, "lovely_girl" a little girl), and
         * a Windows file system cannot keep both apart - a short fingerprint
         * of the exact id makes the names distinct everywhere.
         */
        fun sampleName(voiceId: String): String {
            val safe = voiceId.replace(Regex("[^A-Za-z0-9_.-]"), "_")
            val digest = java.security.MessageDigest.getInstance("SHA-1").digest(voiceId.toByteArray())
            return safe + "-" + digest.take(3).joinToString("") { "%02x".format(it) }
        }
    }
}
