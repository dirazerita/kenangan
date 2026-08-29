package id.kenang.core.providers.story

import id.kenang.core.common.AppError
import id.kenang.core.common.AppResult
import id.kenang.core.common.Provider
import id.kenang.core.common.err
import id.kenang.core.common.ok
import id.kenang.core.data.AppDirs
import id.kenang.core.data.PhotoRepository
import id.kenang.core.data.SceneRepository
import id.kenang.core.data.SceneStatus
import id.kenang.core.data.config.ConfigRepository
import id.kenang.core.data.story.UploadPrep
import id.kenang.core.db.Scene
import id.kenang.core.providers.CostTracker
import id.kenang.core.providers.PriceBook
import id.kenang.core.providers.fal.FalQueueClient
import id.kenang.core.providers.fal.FalStorage
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.File

/**
 * Generates one scene's keyframe via Nano Banana (tier-routed slug from
 * config), downloads it locally, tracks cost, and drives the scene state
 * machine: * → keyframe_pending → keyframe_ready|keyframe_failed.
 */
class KeyframeService(
    private val falClient: FalQueueClient,
    private val storage: FalStorage,
    private val http: HttpClient,
    private val configRepository: ConfigRepository,
    private val priceBook: PriceBook,
    private val costTracker: CostTracker,
    private val sceneRepository: SceneRepository,
    private val photoRepository: PhotoRepository,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Estimated cost of one (re)generation for the UI chip ("±$0.04"). */
    fun regenEstimate(tier: String): Double {
        val model = configRepository.current().tierRouting.resolve(tier).keyframe
        return priceBook.estimate(model, 1.0)?.usd ?: 0.0
    }

    /**
     * Runs the keyframe job for [sceneId]. [isRegen] counts toward regen_count
     * (regens feed the cost estimator).
     */
    suspend fun generate(sceneId: String, tier: String, isRegen: Boolean): AppResult<Scene> {
        val scene = sceneRepository.scene(sceneId)
            ?: return AppError.Unknown("scene $sceneId missing").err()
        if (scene.status !in setOf(SceneStatus.DRAFT, SceneStatus.KEYFRAME_FAILED, SceneStatus.KEYFRAME_READY)) {
            return AppError.Unknown("scene ${scene.status} not eligible for keyframe").err()
        }
        sceneRepository.transition(sceneId, SceneStatus.KEYFRAME_PENDING)

        var result = runJob(scene, tier)
        // Troubled provider call → rotate to the next key and retry once
        // (owner requirement: the process must not stop on one bad call).
        val err = result.errorOrNull()
        if (err is AppError.ProviderFailed || err is AppError.Timeout || err is AppError.RateLimited) {
            falClient.rotateKey()
            result = runJob(scene, tier)
        }
        return when (result) {
            is AppResult.Ok -> {
                sceneRepository.setKeyframeResult(sceneId, result.value.first, result.value.second, isRegen)
                sceneRepository.scene(sceneId)!!.ok()
            }
            is AppResult.Err -> {
                sceneRepository.setKeyframeResult(sceneId, null, null, false)
                result
            }
        }
    }

    /** Returns (remoteUrl, localPath) on success. */
    private suspend fun runJob(scene: Scene, tier: String): AppResult<Pair<String, String>> {
        val config = configRepository.current()
        val model = config.tierRouting.resolve(tier).keyframe

        // Resolve source photo upload URLs (upload lazily if the cache is cold).
        val sourceIds: List<String> = runCatching {
            json.decodeFromString<List<String>>(scene.source_photos_json)
        }.getOrDefault(emptyList())
        val photos = photoRepository.photos(scene.project_id).associateBy { it.id }
        val urls = mutableListOf<String>()
        for (id in sourceIds) {
            val photo = photos[id] ?: continue
            val cached = photo.upload_id
            if (cached != null) urls += cached
            else when (val up = storage.uploadBytes(
                UploadPrep.prepareJpeg(File(photo.local_path)),
                "${photo.id}.jpg", "image/jpeg",
            )) {
                is AppResult.Ok -> {
                    photoRepository.setUploadUrl(id, up.value)
                    urls += up.value
                }
                is AppResult.Err -> return up
            }
        }
        if (urls.isEmpty()) return AppError.Unknown("no source photos for scene ${scene.scene_id}").err()

        val body = buildJsonObject {
            put("prompt", scene.keyframe_prompt_en ?: "")
            putJsonArray("image_urls") { urls.forEach { add(it) } }
            put("num_images", 1)
            put("output_format", "jpeg")
        }

        val submitted = when (val s = falClient.submit(model, body)) {
            is AppResult.Ok -> s.value
            is AppResult.Err -> return s
        }
        val payload = when (val r = falClient.awaitResult(submitted, timeoutMillis = 4 * 60_000)) {
            is AppResult.Ok -> r.value.payload
            is AppResult.Err -> return r
        }
        val imageUrl = runCatching {
            payload["images"]!!.jsonArray[0].jsonObject["url"]!!.jsonPrimitive.content
        }.getOrNull() ?: return AppError.ProviderFailed(Provider.FAL, "no image in keyframe result").err()

        val outFile = File(
            AppDirs.projectKeyframes(scene.project_id),
            "${scene.scene_id}_r${scene.regen_count}.jpg",
        )
        runCatching {
            val bytes = http.get(imageUrl).readRawBytes()
            outFile.writeBytes(bytes)
        }.onFailure {
            Napier.w("keyframe download failed, keeping remote URL only: ${it.message}")
        }

        val est = priceBook.estimate(model, 1.0)?.usd ?: 0.0
        costTracker.record(
            scene.project_id, submitted.requestId, model, submitted.keyLabel,
            1.0, "per_image", est,
        )
        return (imageUrl to outFile.absolutePath).ok()
    }
}
