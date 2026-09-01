package id.kenang.core.providers.upscale

import id.kenang.core.common.AppError
import id.kenang.core.common.AppResult
import id.kenang.core.common.Provider
import id.kenang.core.common.err
import id.kenang.core.common.ok
import id.kenang.core.data.AppDirs
import id.kenang.core.data.config.ConfigRepository
import id.kenang.core.data.config.ModelOption
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
import kotlinx.serialization.json.add
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.File

/**
 * Standalone photo tool (owner 2026-09-01): upscale and/or restore damaged
 * photos in batch, model chosen from config `model_catalog.upscale` (AD-10 —
 * slugs and prices live in config only).
 *
 * Two request shapes, selected by the option's input_mode:
 *  - "edit_prompt": Nano-Banana-style {prompt, image_urls} with the proven
 *    restoration prompt; result under images[0].url.
 *  - "image_url" (default): {image_url, ...params}; result under image.url
 *    (verified against fal docs for clarity-upscaler / aura-sr / codeformer).
 */
class UpscaleService(
    private val falClient: FalQueueClient,
    private val storage: FalStorage,
    private val http: HttpClient,
    private val configRepository: ConfigRepository,
    private val priceBook: PriceBook,
    private val costTracker: CostTracker,
    private val settings: id.kenang.core.data.SettingsRepository,
) {
    companion object {
        /** Same repair wording as the wizard's "Restorasi foto lama" (proven). */
        const val RESTORE_PROMPT =
            "Fully restore and upscale this old photograph to a highly detailed, high-resolution " +
                "image: repair scratches, tears, stains and creases, remove noise and grain, correct " +
                "color fading and color cast, recover natural skin tones and fine facial detail, and " +
                "sharpen softly. Keep every person's identity, pose, clothing and the original " +
                "composition exactly the same — do not add, remove or reimagine anything."

        /** Cost-tracker bucket; the tool is not tied to any project. */
        const val PROJECT_BUCKET = "upscale"
    }

    fun options(): List<ModelOption> = configRepository.current().modelCatalog.upscale

    /** Per-image cost estimate for the UI ("±$0.15 / foto"). */
    fun estimate(option: ModelOption): Double = priceBook.estimate(option.id, 1.0)?.usd ?: 0.0

    /**
     * Results folder: `<Folder Output>/Upscale/` when the setting is usable,
     * else the app-private fallback — mirroring AssemblyService's routing.
     */
    fun outputDir(): File {
        val custom = settings.outputFolder?.trim()?.takeIf { it.isNotBlank() }
            ?.let { File(it, "Upscale") }
            ?.takeIf { dir -> runCatching { dir.mkdirs(); dir.isDirectory }.getOrDefault(false) }
        return custom ?: File(AppDirs.root, "upscale").apply { mkdirs() }
    }

    /** Upscales/restores ONE photo; the screen fans this out in parallel. */
    suspend fun process(source: File, option: ModelOption): AppResult<File> {
        if (!source.isFile) return AppError.Unknown("file missing: ${source.name}").err()

        val uploaded = when (val up = storage.uploadBytes(
            UploadPrep.prepareJpeg(source),
            "upscale_${source.nameWithoutExtension}.jpg",
            "image/jpeg",
        )) {
            is AppResult.Ok -> up.value
            is AppResult.Err -> return up
        }

        var result = runJob(uploaded, option)
        // Troubled provider call → next key, one retry (owner requirement).
        val err = (result as? AppResult.Err)?.error
        if (err is AppError.ProviderFailed || err is AppError.Timeout || err is AppError.RateLimited) {
            falClient.rotateKey()
            result = runJob(uploaded, option)
        }
        val imageUrl = when (result) {
            is AppResult.Ok -> result.value
            is AppResult.Err -> return result
        }

        val target = uniqueTarget(source)
        return runCatching {
            target.writeBytes(http.get(imageUrl).readRawBytes())
            target
        }.fold(
            onSuccess = { it.ok() },
            onFailure = { AppError.Unknown("hasil gagal diunduh: ${it.message}", it).err() },
        )
    }

    /** Submits one job and returns the result image URL. */
    private suspend fun runJob(sourceUrl: String, option: ModelOption): AppResult<String> {
        val body = if (option.inputMode == "edit_prompt") {
            buildJsonObject {
                put("prompt", RESTORE_PROMPT)
                putJsonArray("image_urls") { add(sourceUrl) }
                put("num_images", 1)
                put("output_format", "png")
            }
        } else {
            buildJsonObject {
                put("image_url", sourceUrl)
                option.params?.forEach { (k, v) -> put(k, v) }
            }
        }

        val submitted = when (val s = falClient.submit(option.id, body)) {
            is AppResult.Ok -> s.value
            is AppResult.Err -> return s
        }
        val payload = when (val r = falClient.awaitResult(submitted, timeoutMillis = 5 * 60_000)) {
            is AppResult.Ok -> r.value.payload
            is AppResult.Err -> return r
        }

        // images[0].url (edit models) or image.url (upscalers) — accept either.
        val url = runCatching {
            payload["images"]?.jsonArray?.get(0)?.jsonObject?.get("url")?.jsonPrimitive?.content
        }.getOrNull() ?: runCatching {
            payload["image"]?.jsonObject?.get("url")?.jsonPrimitive?.content
        }.getOrNull()
        if (url == null) {
            Napier.w("upscale ${option.id}: no image in payload keys=${payload.keys}")
            return AppError.ProviderFailed(Provider.FAL, "no image in upscale result").err()
        }

        val billed = payload["usage"]?.jsonObject?.get("cost")?.jsonPrimitive?.content?.toDoubleOrNull()
        costTracker.record(
            PROJECT_BUCKET, submitted.requestId, option.id, submitted.keyLabel,
            1.0, "per_image", billed ?: estimateFor(option),
        )
        return url.ok()
    }

    private fun estimateFor(option: ModelOption): Double = priceBook.estimate(option.id, 1.0)?.usd ?: 0.0

    /** `<name>_HD.png`, suffixed `_2`, `_3`… so a re-run never overwrites. */
    private fun uniqueTarget(source: File): File {
        val dir = outputDir()
        val base = source.nameWithoutExtension
        var candidate = File(dir, "${base}_HD.png")
        var n = 2
        while (candidate.exists()) {
            candidate = File(dir, "${base}_HD_$n.png")
            n++
        }
        return candidate
    }
}
