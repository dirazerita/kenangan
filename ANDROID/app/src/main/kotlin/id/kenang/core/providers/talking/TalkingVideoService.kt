package id.kenang.core.providers.talking

import id.kenang.core.common.AppError
import id.kenang.core.common.AppResult
import id.kenang.core.common.Provider
import id.kenang.core.common.err
import id.kenang.core.common.ok
import id.kenang.core.data.AppDirs
import id.kenang.core.data.SettingsRepository
import id.kenang.core.data.config.ConfigRepository
import id.kenang.core.data.config.ModelOption
import id.kenang.core.data.media.GalleryExporter
import id.kenang.core.data.story.UploadPrep
import id.kenang.core.providers.CostTracker
import id.kenang.core.providers.PriceBook
import id.kenang.core.providers.fal.FalQueueClient
import id.kenang.core.providers.fal.FalStorage
import id.kenang.core.providers.gen.TtsService
import id.kenang.core.providers.voice.ClonedVoice
import id.kenang.core.providers.voice.VoiceCloneService
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File

/**
 * "Video Berbicara" (owner 2026-09-13): a photo speaks a script. Three
 * stages, each on the best stack fal offers: the script becomes speech with
 * MiniMax Speech-02 HD - in a voice cloned from the user's own recording
 * (MiniMax voice-clone) or one of the preset voices - and the photo is then
 * animated to that speech by ByteDance OmniHuman 1.5, whose expressions,
 * head and hand movement follow what is being said.
 *
 * Android twin: results are written app-private and ALSO published to the
 * gallery (Movies/Kenang/VideoBerbicara), like the other phone tools.
 */
class TalkingVideoService(
    private val falClient: FalQueueClient,
    private val storage: FalStorage,
    private val http: HttpClient,
    private val configRepository: ConfigRepository,
    private val priceBook: PriceBook,
    private val costTracker: CostTracker,
    private val settings: SettingsRepository,
    private val tts: TtsService,
    private val voiceClone: VoiceCloneService,
    private val gallery: GalleryExporter,
) {
    companion object {
        const val COST_PROJECT = "talking"
        const val DEFAULT_SLUG = "fal-ai/bytedance/omnihuman/v1.5"
        /** Audio ceilings (OmniHuman 1.5 schema): 1080p <= 30 s, 720p <= 60 s. */
        const val MAX_AUDIO_S = 60.0
        const val HD_AUDIO_S = 30.0
        const val CHARS_PER_SECOND = 14.0
        const val MAX_CHARS = 800
        private const val CLONE_LABEL_PREFIX = "Video Berbicara: "
        val AUDIO_EXTENSIONS: List<String> get() = VoiceCloneService.AUDIO_EXTENSIONS
    }

    enum class Phase { CLONING, SPEAKING, RENDERING, SAVING }

    data class Estimate(val seconds: Double, val ttsUsd: Double, val videoUsd: Double, val cloneUsd: Double) {
        val totalUsd: Double get() = ttsUsd + videoUsd + cloneUsd
    }

    data class TalkingResult(
        val video: File,
        val audio: File,
        val durationS: Double,
        val usd: Double,
        val voiceLabel: String,
    )

    data class VoiceChoice(val id: String, val label: String, val cloned: Boolean)

    fun options(): List<ModelOption> =
        configRepository.current().modelCatalog.talking.ifEmpty {
            listOf(ModelOption(id = DEFAULT_SLUG, labelId = "OmniHuman 1.5"))
        }

    fun selected(): ModelOption =
        options().firstOrNull { it.selectionKey() == settings.modelTalking } ?: options().first()

    fun select(key: String?) {
        settings.modelTalking = key
    }

    fun pricePerSecondOf(option: ModelOption): Double = priceBook.estimate(option.id, 1.0)?.usd ?: 0.0

    fun voices(): List<VoiceChoice> {
        val presets = configRepository.current().tts.voices.map { VoiceChoice(it.id, it.labelId, cloned = false) }
        val clones = voiceClone.cloned().map { VoiceChoice(it.voiceId, it.label, cloned = true) }
        return clones + presets
    }

    fun defaultVoiceId(): String = settings.defaultVoice ?: configRepository.current().tts.voice

    fun estimate(chars: Int, option: ModelOption = selected(), withNewClone: Boolean = false): Estimate {
        val seconds = (chars / CHARS_PER_SECOND).coerceIn(1.0, MAX_AUDIO_S)
        val ttsSlug = settings.modelTts ?: configRepository.current().tts.slug
        val ttsUsd = priceBook.estimate(ttsSlug, chars.toDouble())?.usd ?: 0.0
        val videoUsd = pricePerSecondOf(option) * seconds
        val cloneUsd = if (withNewClone) voiceClone.estimateUsd() else 0.0
        return Estimate(seconds, ttsUsd, videoUsd, cloneUsd)
    }

    fun existingClone(sample: File): ClonedVoice? =
        voiceClone.cloned().firstOrNull { it.label == cloneLabel(sample) }

    /** App-private results folder; the gallery copy is what the user opens. */
    fun outputDir(): File = File(AppDirs.root, "talking").apply { mkdirs() }

    suspend fun run(
        photo: File,
        script: String,
        voiceId: String?,
        voiceSample: File?,
        option: ModelOption = selected(),
        onPhase: (Phase) -> Unit = {},
    ): AppResult<TalkingResult> {
        val text = script.trim()
        if (text.isBlank()) return AppError.Unknown("naskah kosong").err()
        if (text.length > MAX_CHARS) return AppError.Unknown("naskah melebihi $MAX_CHARS karakter").err()
        if (!photo.isFile) return AppError.Unknown("foto tidak ditemukan").err()

        var voiceLabel: String
        val voice: String = if (voiceSample != null) {
            val reused = existingClone(voiceSample)
            val cloned = if (reused != null) {
                Napier.i("talking: reusing clone for ${voiceSample.name}")
                reused
            } else {
                onPhase(Phase.CLONING)
                when (val c = voiceClone.clone(voiceSample, cloneLabel(voiceSample))) {
                    is AppResult.Ok -> c.value
                    is AppResult.Err -> return c
                }
            }
            voiceLabel = cloned.label
            cloned.voiceId
        } else {
            val chosen = voiceId ?: defaultVoiceId()
            voiceLabel = voices().firstOrNull { it.id == chosen }?.label ?: chosen
            chosen
        }

        onPhase(Phase.SPEAKING)
        val stamp = System.currentTimeMillis()
        // Resolved ONCE: the speech and the video must land together even if
        // the folder setting or the drive changes while the render runs.
        val dir = outputDir()
        val audioFile = File(dir, "bicara_$stamp.mp3")
        val narration = when (val n = tts.synthesize(COST_PROJECT, text, voiceId = voice, outFile = audioFile)) {
            is AppResult.Ok -> n.value
            is AppResult.Err -> return n
        }
        val seconds = narration.durationMs / 1000.0
        if (seconds > MAX_AUDIO_S) {
            return AppError.Unknown("terlalu panjang: ${"%.0f".format(seconds)} dtk").err()
        }

        onPhase(Phase.RENDERING)
        val imageUrl = when (
            val up = storage.uploadBytes(UploadPrep.prepareJpeg(photo), "${photo.nameWithoutExtension}.jpg", "image/jpeg")
        ) {
            is AppResult.Ok -> up.value
            is AppResult.Err -> return up
        }
        val audioUrl = when (val up = storage.uploadFile(narration.file)) {
            is AppResult.Ok -> up.value
            is AppResult.Err -> return up
        }
        val body = buildJsonObject {
            put("image_url", imageUrl)
            put("audio_url", audioUrl)
            if (option.id.endsWith("omnihuman/v1.5")) {
                put("resolution", if (seconds <= HD_AUDIO_S) "1080p" else "720p")
            }
            option.params?.forEach { (k, v) -> put(k, v) }
        }

        var submitted = when (val s = falClient.submit(option.id, body)) {
            is AppResult.Ok -> s.value
            is AppResult.Err -> return s
        }
        var awaited = falClient.awaitResult(submitted, timeoutMillis = 20 * 60_000)
        val trouble = (awaited as? AppResult.Err)?.error
        if (trouble is AppError.ProviderFailed || trouble is AppError.Timeout || trouble is AppError.RateLimited) {
            falClient.rotateKey()
            submitted = when (val s = falClient.submit(option.id, body)) {
                is AppResult.Ok -> s.value
                is AppResult.Err -> return s
            }
            awaited = falClient.awaitResult(submitted, timeoutMillis = 20 * 60_000)
        }
        val payload = when (val r = awaited) {
            is AppResult.Ok -> r.value.payload
            is AppResult.Err -> return r
        }
        val videoUrl = runCatching { payload["video"]!!.jsonObject["url"]!!.jsonPrimitive.content }.getOrNull()
            ?: return AppError.ProviderFailed(Provider.FAL, "no video in talking result").err()
        val billedSeconds = payload["duration"]?.jsonPrimitive?.doubleOrNull?.takeIf { it > 0 } ?: seconds

        onPhase(Phase.SAVING)
        val videoFile = File(dir, "bicara_$stamp.mp4")
        return runCatching {
            videoFile.writeBytes(http.get(videoUrl).readRawBytes())
            gallery.export(videoFile, "VideoBerbicara")
            val usd = pricePerSecondOf(option) * billedSeconds
            costTracker.record(
                COST_PROJECT, submitted.requestId, option.id, submitted.keyLabel,
                billedSeconds, "per_second", usd,
            )
            Napier.i("talking video done -> ${videoFile.absolutePath} (${"%.1f".format(billedSeconds)} s)")
            TalkingResult(videoFile, narration.file, billedSeconds, usd, voiceLabel)
        }.fold(
            onSuccess = { it.ok() },
            onFailure = { AppError.Unknown("gagal menyimpan video hasil", it).err() },
        )
    }

    private fun cloneLabel(sample: File): String =
        CLONE_LABEL_PREFIX + sample.nameWithoutExtension.take(40) + " (" + sample.length() / 1024 + " KB)"
}
