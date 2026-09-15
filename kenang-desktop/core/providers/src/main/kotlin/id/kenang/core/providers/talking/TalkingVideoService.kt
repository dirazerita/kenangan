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
import id.kenang.core.data.story.SpeakerCandidate
import id.kenang.core.data.story.SpeakerMask
import id.kenang.core.data.story.UploadPrep
import id.kenang.core.providers.CostTracker
import id.kenang.core.providers.PriceBook
import id.kenang.core.providers.fal.FalQueueClient
import id.kenang.core.providers.fal.FalStorage
import id.kenang.core.providers.gen.TtsService
import id.kenang.core.providers.story.AnalysisService
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
 * Models, limits and prices live in config (AD-10): `model_catalog.talking`
 * lists the selectable avatar models; the audio ceilings below are the
 * OmniHuman 1.5 ones from fal's schema (1080p up to 30 s, 720p up to 60 s).
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
    private val analysis: AnalysisService,
) {
    companion object {
        /** Pseudo project id for gen_cost rows and the TTS scratch folder. */
        const val COST_PROJECT = "talking"
        const val DEFAULT_SLUG = "fal-ai/bytedance/omnihuman/v1.5"
        /** Audio ceilings (OmniHuman 1.5 schema): 1080p <= 30 s, 720p <= 60 s. */
        const val MAX_AUDIO_S = 60.0
        const val HD_AUDIO_S = 30.0
        /**
         * Indonesian speech at the app's TTS speed runs at roughly this many
         * characters per second - used only for the pre-run estimate; the real
         * duration comes back from the TTS call.
         */
        const val CHARS_PER_SECOND = 14.0
        /** Keeps the estimate inside the 60 s ceiling with headroom. */
        const val MAX_CHARS = 800
        /** Label prefix for clones this tool makes, so the same file is reused. */
        private const val CLONE_LABEL_PREFIX = "Video Berbicara: "
        /** Script lengths offered in the UI, in seconds of speech. */
        val SCRIPT_LENGTHS = listOf(15, 30, 45)
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

    /** A selectable voice: a MiniMax preset or a clone the user made. */
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

    /** Preset voices from config plus the user's clones (marked). */
    fun voices(): List<VoiceChoice> {
        val presets = configRepository.current().tts.voices.map { VoiceChoice(it.id, it.labelId, cloned = false) }
        val clones = voiceClone.cloned().map { VoiceChoice(it.voiceId, it.label, cloned = true) }
        return clones + presets
    }

    fun defaultVoiceId(): String = settings.defaultVoice ?: configRepository.current().tts.voice

    /** Pre-run estimate from the script length; [withNewClone] adds the one-off clone fee. */
    fun estimate(chars: Int, option: ModelOption = selected(), withNewClone: Boolean = false): Estimate {
        val seconds = (chars / CHARS_PER_SECOND).coerceIn(1.0, MAX_AUDIO_S)
        val ttsSlug = settings.modelTts ?: configRepository.current().tts.slug
        val ttsUsd = priceBook.estimate(ttsSlug, chars.toDouble())?.usd ?: 0.0
        val videoUsd = pricePerSecondOf(option) * seconds
        val cloneUsd = if (withNewClone) voiceClone.estimateUsd() else 0.0
        return Estimate(seconds, ttsUsd, videoUsd, cloneUsd)
    }

    /** True when [sample] was already cloned by this tool (no new fee). */
    fun existingClone(sample: File): ClonedVoice? =
        voiceClone.cloned().firstOrNull { it.label == cloneLabel(sample) }

    /**
     * Results folder, in order of preference (owner 2026-09-13: the tool has
     * its own folder picker, like Upscale): the tool's chosen folder →
     * `<Folder Output>/VideoBerbicara/` → app-private fallback. An unusable
     * path (unplugged drive, no write permission) silently falls through, so
     * a finished video is never lost to a stale setting.
     */
    fun outputDir(): File {
        fun usable(dir: File): File? =
            dir.takeIf { runCatching { it.mkdirs(); it.isDirectory }.getOrDefault(false) }

        val own = settings.talkingOutputFolder?.trim()?.takeIf { it.isNotBlank() }
            ?.let { usable(File(it)) }
        if (own != null) return own
        val fromOutput = settings.outputFolder?.trim()?.takeIf { it.isNotBlank() }
            ?.let { usable(File(it, "VideoBerbicara")) }
        return fromOutput ?: AppDirs.talking
    }

    /**
     * Writes the script from the user's theme (owner 2026-09-13: manual OR
     * automatic). [photo] is optional — with it the words fit the person who
     * will speak them. The user edits the result freely afterwards.
     */
    suspend fun writeScript(theme: String, photo: File?, targetSeconds: Int): AppResult<String> =
        analysis.writeTalkingScript(
            projectId = COST_PROJECT,
            theme = theme,
            photo = photo,
            targetSeconds = targetSeconds.coerceIn(5, MAX_AUDIO_S.toInt()),
            maxChars = MAX_CHARS,
        )

    /**
     * Lists the people in [photo] so the user can choose who speaks (owner
     * 2026-09-15: a couple in one photo, only the husband should talk). One
     * cheap vision call; an empty list means "let the model decide".
     */
    suspend fun speakers(photo: File): AppResult<List<SpeakerCandidate>> =
        analysis.detectSpeakers(COST_PROJECT, photo)

    /** True when [option] can be told WHICH person speaks (OmniHuman masks). */
    fun supportsSpeakerChoice(option: ModelOption): Boolean = option.id.contains("omnihuman")

    /** Remembers the tool's own results folder; null restores the default. */
    fun setOutputFolder(path: String?) {
        settings.talkingOutputFolder = path
    }

    /**
     * Runs the whole pipeline. Exactly one of [voiceId] / [voiceSample] is
     * used: a sample is cloned first (or its earlier clone reused), otherwise
     * the chosen preset/clone id speaks.
     */
    suspend fun run(
        photo: File,
        script: String,
        voiceId: String?,
        voiceSample: File?,
        option: ModelOption = selected(),
        /** Who should speak in a group photo; null = the model decides. */
        speaker: SpeakerCandidate? = null,
        /** The other people in that photo, cut out of the mask so they stay silent. */
        others: List<SpeakerCandidate> = emptyList(),
        onPhase: (Phase) -> Unit = {},
    ): AppResult<TalkingResult> {
        val text = script.trim()
        if (text.isBlank()) return AppError.Unknown("naskah kosong").err()
        if (text.length > MAX_CHARS) return AppError.Unknown("naskah melebihi $MAX_CHARS karakter").err()
        if (!photo.isFile) return AppError.Unknown("foto tidak ditemukan").err()

        // ---- 1. voice ----
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

        // ---- 2. speech ----
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

        // ---- 3. talking video ----
        onPhase(Phase.RENDERING)
        // The mask must line up with the image the provider actually receives,
        // so both are built from these exact bytes (UploadPrep may downscale).
        val preparedImage = UploadPrep.prepareJpeg(photo)
        val imageUrl = when (
            val up = storage.uploadBytes(preparedImage, "${photo.nameWithoutExtension}.jpg", "image/jpeg")
        ) {
            is AppResult.Ok -> up.value
            is AppResult.Err -> return up
        }
        val maskUrl = speaker?.takeIf { supportsSpeakerChoice(option) }
            ?.let { uploadSpeakerMask(preparedImage, it, others, stamp) }
        val audioUrl = when (val up = storage.uploadFile(narration.file)) {
            is AppResult.Ok -> up.value
            is AppResult.Err -> return up
        }
        val body = buildJsonObject {
            put("image_url", imageUrl)
            put("audio_url", audioUrl)
            // "Only the person in the white area of the mask will speak" (fal).
            maskUrl?.let { put("mask_url", it) }
            // OmniHuman 1.5: 1080p only for <= 30 s of audio (fal schema).
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
            // Owner requirement: a troubled call retries on the next key.
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

        // ---- 4. save ----
        onPhase(Phase.SAVING)
        val bytes = runCatching { http.get(videoUrl).readRawBytes() }.getOrElse {
            return AppError.Unknown("gagal mengunduh video hasil", it).err()
        }
        // The render is paid for and already in hand. If the chosen folder
        // refuses the write (read-only, drive unplugged during the ~10-minute
        // render), keep the video in the app folder instead of losing it —
        // the lesson of D-051.
        val videoFile = writeVideo(bytes, dir, "bicara_$stamp.mp4")
            ?: return AppError.Unknown("video tidak bisa disimpan di folder mana pun").err()
        return runCatching {
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

    /**
     * Writes [bytes] to [preferred], falling back to the app folder when that
     * write fails. Returns the file that actually holds the video, or null
     * when even the fallback refused.
     */
    private fun writeVideo(bytes: ByteArray, preferred: File, name: String): File? {
        for (dir in listOf(preferred, AppDirs.talking).distinctBy { it.absolutePath }) {
            val file = File(dir, name)
            val written = runCatching {
                dir.mkdirs()
                file.writeBytes(bytes)
                file.isFile && file.length() == bytes.size.toLong()
            }.getOrDefault(false)
            if (written) {
                if (dir.absolutePath != preferred.absolutePath) {
                    Napier.w("talking: '${preferred.absolutePath}' refused the write — saved to ${file.absolutePath}")
                }
                return file
            }
        }
        return null
    }

    /**
     * Renders and uploads the mask for [speaker]. Returns null when the boxes
     * are unusable or the upload fails — the run then proceeds unmasked
     * rather than failing, because a video of the wrong person speaking is
     * still better than no video at all after paying for the speech.
     */
    private suspend fun uploadSpeakerMask(
        preparedImage: ByteArray,
        speaker: SpeakerCandidate,
        others: List<SpeakerCandidate>,
        stamp: Long,
    ): String? {
        val image = runCatching {
            javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(preparedImage))
        }.getOrNull() ?: return null
        // The face decides who speaks, and one box is all this depends on.
        // The body box is only a fallback for a photo where no face was found.
        val box = SpeakerMask.speakerBox(speaker.faceBox, speaker.personBox) ?: return null

        // Whoever else is in the frame gets cut back out of the white area:
        // their body when it does not sit over the speaker's face, otherwise
        // just their face (owner 2026-09-15: a child on a lap spoke too).
        val cutOut = others.filter { it.id != speaker.id }.mapNotNull { other ->
            val body = other.personBox?.takeIf { !overlaps(it, speaker.faceBox) }
            body ?: other.faceBox
        }

        val maskFile = SpeakerMask.render(
            image.width, image.height, box, File(AppDirs.cache, "talking/mask_$stamp.png"),
            others = cutOut, speakerFace = speaker.faceBox,
        ) ?: return null
        return when (val up = storage.uploadFile(maskFile)) {
            is AppResult.Ok -> {
                Napier.i("talking: '${speaker.label}' will speak (mask ${image.width}x${image.height})")
                up.value
            }
            is AppResult.Err -> {
                Napier.w("talking: mask upload failed (${up.error}) - rendering without a mask")
                null
            }
        }
    }

    /** True when [a] covers any part of [b] — used to protect the speaker's face. */
    private fun overlaps(a: List<Double>, b: List<Double>?): Boolean {
        if (b == null || a.size != 4 || b.size != 4) return false
        return a[0] < b[2] && a[2] > b[0] && a[1] < b[3] && a[3] > b[1]
    }

    private fun cloneLabel(sample: File): String =
        CLONE_LABEL_PREFIX + sample.nameWithoutExtension.take(40) + " (" + sample.length() / 1024 + " KB)"
}
