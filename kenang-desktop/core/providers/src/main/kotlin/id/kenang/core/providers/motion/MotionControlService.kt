package id.kenang.core.providers.motion

import id.kenang.core.common.AppError
import id.kenang.core.common.AppResult
import id.kenang.core.common.Provider
import id.kenang.core.common.err
import id.kenang.core.common.ok
import id.kenang.core.data.AppDirs
import id.kenang.core.data.SettingsRepository
import id.kenang.core.data.config.ConfigRepository
import id.kenang.core.data.story.UploadPrep
import id.kenang.core.providers.CostTracker
import id.kenang.core.providers.PriceBook
import id.kenang.core.providers.fal.FalQueueClient
import id.kenang.core.providers.fal.FalStorage
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File

/**
 * Motion Control (owner 2026-09-02): the character from a PHOTO performs the
 * movements of a REFERENCE VIDEO — Kling 3.0 Pro Motion Control, fal's
 * best/most expensive motion-transfer stack (verified live on the model page:
 * $0.168/s, image_url + video_url + character_orientation).
 *
 * character_orientation: "video" follows complex body motion (ref ≤ 30s),
 * "image" favors the photo's framing/camera (ref ≤ 10s).
 */
class MotionControlService(
    private val falClient: FalQueueClient,
    private val storage: FalStorage,
    private val http: HttpClient,
    private val configRepository: ConfigRepository,
    private val priceBook: PriceBook,
    private val costTracker: CostTracker,
    private val settings: SettingsRepository,
) {
    companion object {
        /** Pseudo project id for gen_cost rows (tool runs belong to no project). */
        private const val COST_PROJECT = "motion-control"
        const val MAX_VIDEO_BYTES = 200L * 1024 * 1024
        private val VIDEO_MIME = mapOf(
            "mp4" to "video/mp4", "mov" to "video/quicktime", "webm" to "video/webm",
            "mkv" to "video/x-matroska", "avi" to "video/x-msvideo", "3gp" to "video/3gpp",
        )
        val VIDEO_EXTENSIONS = VIDEO_MIME.keys.toList()
    }

    data class MotionResult(val file: File, val estUsd: Double)

    fun config() = configRepository.current().motionControl

    /**
     * User-selectable models (owner 2026-09-02): all fal motion-control
     * models from the config catalog; falls back to the legacy single slug
     * when the catalog is empty (older user-override configs).
     */
    fun options(): List<id.kenang.core.data.config.ModelOption> =
        configRepository.current().modelCatalog.motion.ifEmpty {
            listOf(
                id.kenang.core.data.config.ModelOption(
                    id = config().slug,
                    labelId = "Kling 3.0 Pro (bawaan)",
                    inputMode = "kling",
                ),
            )
        }

    /** The active model — the Settings choice, else the catalog's first entry. */
    fun selected(): id.kenang.core.data.config.ModelOption {
        val opts = options()
        val key = settings.modelMotion
        return opts.firstOrNull { it.selectionKey() == key } ?: opts.first()
    }

    fun select(key: String?) {
        settings.modelMotion = key
    }

    /** True when the active model takes the Kling body (orientation + sound). */
    fun selectedIsKling(): Boolean = selected().inputMode != "plain"

    fun pricePerSecond(): Double = pricePerSecondOf(selected())

    fun pricePerSecondOf(option: id.kenang.core.data.config.ModelOption): Double =
        priceBook.estimate(option.id, 1.0)?.usd ?: 0.0

    /** Estimated cost for a reference of [durationS] seconds (capped per orientation). */
    fun estimateUsd(durationS: Double?, orientation: String): Double {
        val cap = capSeconds(orientation)
        val s = (durationS ?: cap.toDouble()).coerceAtMost(cap.toDouble())
        return pricePerSecond() * s
    }

    fun capSeconds(orientation: String): Int = when {
        !selectedIsKling() -> config().maxSVideo
        orientation == "image" -> config().maxSImage
        else -> config().maxSVideo
    }

    /**
     * Results folder: `<Folder Output>/MotionControl/` when usable, else the
     * app-private fallback — same routing as the upscale tool.
     */
    fun outputDir(): File {
        val custom = settings.outputFolder?.trim()?.takeIf { it.isNotBlank() }
            ?.let { File(it, "MotionControl") }
            ?.takeIf { dir -> runCatching { dir.mkdirs(); dir.isDirectory }.getOrDefault(false) }
        return custom ?: File(AppDirs.root, "motion").apply { mkdirs() }
    }

    suspend fun run(
        photo: File,
        video: File,
        orientation: String,        // "video" | "image"
        prompt: String?,
        keepSound: Boolean,
        /** Reference duration probed by the UI (null = unknown). */
        durationS: Double?,
    ): AppResult<MotionResult> {
        if (video.length() > MAX_VIDEO_BYTES) {
            return AppError.Unknown("Video referensi terlalu besar (maks 200MB).").err()
        }
        val videoMime = VIDEO_MIME[video.extension.lowercase()]
            ?: return AppError.Unknown("Format video tidak didukung: .${video.extension}").err()

        val imageUrl = when (val up = storage.uploadBytes(
            UploadPrep.prepareJpeg(photo), "${photo.nameWithoutExtension}.jpg", "image/jpeg",
        )) {
            is AppResult.Ok -> up.value
            is AppResult.Err -> return up
        }
        val videoUrl = when (val up = storage.uploadBytes(video.readBytes(), video.name, videoMime)) {
            is AppResult.Ok -> up.value
            is AppResult.Err -> return up
        }

        val option = selected()
        val body = buildJsonObject {
            put("image_url", imageUrl)
            put("video_url", videoUrl)
            if (option.inputMode != "plain") {
                put("character_orientation", if (orientation == "image") "image" else "video")
                put("keep_original_sound", keepSound)
            }
            prompt?.trim()?.takeIf { it.isNotBlank() }?.let { put("prompt", it.take(500)) }
            // Catalog params (e.g. Wan resolution) merged last, like i2v params.
            option.params?.forEach { (k, v) -> put(k, v) }
        }

        var submitted = when (val s = falClient.submit(option.id, body)) {
            is AppResult.Ok -> s.value
            is AppResult.Err -> return s
        }
        // Motion transfer renders take minutes; rotate + one retry on trouble
        // (same owner requirement as the rest of the pipeline).
        var awaited = falClient.awaitResult(submitted, timeoutMillis = 20 * 60_000)
        val err = (awaited as? AppResult.Err)?.error
        if (err is AppError.ProviderFailed || err is AppError.Timeout || err is AppError.RateLimited) {
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
        val resultUrl = runCatching {
            payload["video"]!!.jsonObject["url"]!!.jsonPrimitive.content
        }.getOrNull() ?: return AppError.ProviderFailed(Provider.FAL, "no video in motion-control result").err()

        val outFile = File(outputDir(), "motion_${System.currentTimeMillis()}.mp4")
        return runCatching {
            outFile.writeBytes(http.get(resultUrl).readRawBytes())
            val est = estimateUsd(durationS, orientation)
            costTracker.record(
                COST_PROJECT, submitted.requestId, option.id, submitted.keyLabel,
                (durationS ?: capSeconds(orientation).toDouble()), "per_second", est,
            )
            Napier.i("motion-control done -> ${outFile.absolutePath}")
            MotionResult(outFile, est)
        }.fold(
            onSuccess = { it.ok() },
            onFailure = { AppError.Unknown("gagal menyimpan video hasil", it).err() },
        )
    }
}
