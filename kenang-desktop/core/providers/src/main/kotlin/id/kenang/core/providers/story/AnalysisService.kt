package id.kenang.core.providers.story

import id.kenang.core.common.AppError
import id.kenang.core.common.AppResult
import id.kenang.core.common.Provider
import id.kenang.core.common.story.MotionTemplateValidator
import id.kenang.core.common.story.MotionTemplates
import id.kenang.core.data.PhotoRepository
import id.kenang.core.data.SceneRepository
import id.kenang.core.data.SceneStatus
import id.kenang.core.data.config.ConfigRepository
import id.kenang.core.data.story.ModerationResult
import id.kenang.core.data.story.PhotoAnalysis
import id.kenang.core.data.story.ScenePlanItem
import id.kenang.core.data.story.UploadPrep
import id.kenang.core.db.Photo
import id.kenang.core.db.Scene
import id.kenang.core.providers.CostTracker
import id.kenang.core.providers.fal.FalQueueClient
import id.kenang.core.providers.fal.FalStorage
import id.kenang.core.providers.optional.GeminiClient
import id.kenang.core.providers.vault.KeyVault
import io.github.aakira.napier.Napier
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.doubleOrNull
import java.io.File

/** Progress stages for the full-screen analysis UI. */
sealed class AnalysisStage {
    data class Uploading(val done: Int, val total: Int) : AnalysisStage()
    data class Moderating(val done: Int, val total: Int) : AnalysisStage()
    data class Analyzing(val done: Int, val total: Int) : AnalysisStage()
    data object Planning : AnalysisStage()
    data object Saving : AnalysisStage()
}

sealed class AnalysisOutcome {
    data class Ok(val sceneCount: Int) : AnalysisOutcome()
    /** Moderation pre-check refused a photo — UI shows which one + edit options. */
    data class Blocked(val photoId: String, val category: String) : AnalysisOutcome()
    data class Failed(val error: AppError) : AnalysisOutcome()
}

/**
 * F2 — client-side analysis flow (BYOK: photos go straight to the user's
 * provider, never to any Kenang server): moderation pre-check → PhotoAnalysis
 * per photo → story plan, with the motion-template validator enforced in app
 * code. Everything persisted; resume-safe.
 */
class AnalysisService(
    private val falClient: FalQueueClient,
    private val storage: FalStorage,
    private val configRepository: ConfigRepository,
    private val costTracker: CostTracker,
    private val geminiClient: GeminiClient,
    private val keyVault: KeyVault,
    private val photoRepository: PhotoRepository,
    private val sceneRepository: SceneRepository,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun run(
        projectId: String,
        vibeId: String,
        ratio: String,
        sceneDurationS: Long,
        narration: String?,
        /** Single-photo picker: exact scene count the user asked for (null = planner decides). */
        targetScenes: Long? = null,
        onStage: suspend (AnalysisStage) -> Unit,
    ): AnalysisOutcome {
        val config = configRepository.current()
        val photos = photoRepository.photos(projectId)
        if (photos.isEmpty()) return AnalysisOutcome.Failed(AppError.Unknown("no photos"))

        // 1. Upload (free) — cache file_url on the photo row.
        val urls = mutableMapOf<String, String>()
        photos.forEachIndexed { i, photo ->
            onStage(AnalysisStage.Uploading(i, photos.size))
            val existing = photo.upload_id
            if (existing != null) {
                urls[photo.id] = existing
            } else {
                val prepared = UploadPrep.prepareJpeg(File(photo.local_path))
                when (val up = storage.uploadBytes(prepared, "${photo.id}.jpg", "image/jpeg")) {
                    is AppResult.Ok -> {
                        urls[photo.id] = up.value
                        photoRepository.setUploadUrl(photo.id, up.value)
                    }
                    is AppResult.Err -> return AnalysisOutcome.Failed(up.error)
                }
            }
        }

        // 2. Moderation pre-check (cheap, before any expensive spend — MEMORY §7).
        photos.forEachIndexed { i, photo ->
            onStage(AnalysisStage.Moderating(i, photos.size))
            when (val res = moderatePhoto(projectId, urls.getValue(photo.id))) {
                is AppResult.Ok -> if (res.value.blocked) {
                    return AnalysisOutcome.Blocked(photo.id, res.value.category)
                }
                is AppResult.Err -> return AnalysisOutcome.Failed(res.error)
            }
        }

        // 3. PhotoAnalysis per photo (validate JSON + retry — no native JSON mode via router).
        val analyses = mutableListOf<PhotoAnalysis>()
        photos.forEachIndexed { i, photo ->
            onStage(AnalysisStage.Analyzing(i, photos.size))
            when (val res = analyzePhoto(projectId, photo, urls.getValue(photo.id))) {
                is AppResult.Ok -> {
                    analyses += res.value
                    photoRepository.setAnalysisJson(photo.id, json.encodeToString(PhotoAnalysis.serializer(), res.value))
                }
                is AppResult.Err -> return AnalysisOutcome.Failed(res.error)
            }
        }

        // 4. Story plan (template-constrained; validated/repaired in app code).
        onStage(AnalysisStage.Planning)
        val plan = when (val res = storyPlan(projectId, analyses, vibeId, narration, config.limits.maxScenes, targetScenes)) {
            is AppResult.Ok -> res.value
            is AppResult.Err -> return AnalysisOutcome.Failed(res.error)
        }

        // 5. Persist Scene rows (status=draft).
        onStage(AnalysisStage.Saving)
        val vibe = config.vibes.firstOrNull { it.id == vibeId } ?: config.vibes.first()
        val photoIds = photos.map { it.id }.toSet()
        val validItems = plan.filter { item -> item.sourcePhotos.isNotEmpty() && item.sourcePhotos.all { it in photoIds } }
            .take(config.limits.maxScenes)
            .ifEmpty { return AnalysisOutcome.Failed(AppError.ProviderFailed(Provider.FAL, "story plan empty/invalid")) }

        sceneRepository.deleteAll(projectId)
        validItems.forEachIndexed { index, item ->
            val subjectCount = analyses.filter { it.photoId in item.sourcePhotos }
                .sumOf { it.subjects.size }
                .coerceIn(1, config.limits.maxSubjectsFusion)
            val isFusion = item.type == "fusion" && item.sourcePhotos.size >= 2
            val spec = MotionTemplateValidator.resolveOrRepair(item.motionCategory, item.camera, item.adjectives)
                .copy(subjectEn = item.subjectEn.ifBlank { "the person" }, subjectId = item.subjectId.ifBlank { "Beliau" })
            val scene = Scene(
                scene_id = "sc_${projectId.take(8)}_$index",
                project_id = projectId,
                source_photos_json = json.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.serializer<String>()),
                    item.sourcePhotos,
                ),
                type = if (isFusion) "fusion" else "single",
                vibe = vibe.id,
                keyframe_prompt_en = KeyframePrompts.build(vibe, ratio, isFusion, subjectCount, item.keyframeHint),
                keyframe_url = null,
                motion_prompt_en = MotionTemplates.buildPromptEn(spec),
                motion_summary_id = MotionTemplates.buildSummaryId(spec),
                duration_s = sceneDurationS,
                regen_count = 0,
                status = SceneStatus.DRAFT,
                order_index = index.toLong(),
                local_keyframe_path = null,
                local_clip_path = null,
            )
            sceneRepository.upsert(scene)
        }
        return AnalysisOutcome.Ok(validItems.size)
    }

    // ------------------------------------------------------------------

    private suspend fun moderatePhoto(projectId: String, imageUrl: String): AppResult<ModerationResult> {
        val prompt = """Safety pre-check for a family memorial video app. Look at this photo and classify it.
Return ONLY valid JSON: {"category":"none|nsfw|violence|public_figure","reason":"<short>"}
Use "none" for normal family photos (including old, damaged, black-and-white photos, children with family, deceased loved ones, and pets). "public_figure" only for clearly recognizable celebrities/politicians."""
        return visionJson(projectId, prompt, listOf(imageUrl), maxTokens = 100) { raw ->
            json.decodeFromString(ModerationResult.serializer(), raw)
        }
    }

    private suspend fun analyzePhoto(projectId: String, photo: Photo, imageUrl: String): AppResult<PhotoAnalysis> {
        // Schema prompt proven in Phase 00 (POC/04_tts.py — 3/3 valid).
        val prompt = """Analyze this old family photo. Return ONLY valid JSON exactly matching:
{"photo_id": "${photo.id}",
 "subjects": [{"id":"s1","desc":"<person desc: age, clothing>","face_quality":0.0}],
 "setting": "<scene description>",
 "era_style": "<photo era/style e.g. 'faded color print', 'BW 1960s'>",
 "mood": "<mood>",
 "quality_score": 0.0,
 "issues": ["<defects: blur, fading, damage>"]}
face_quality and quality_score are 0-1 floats. No markdown, no extra text."""
        return visionJson(projectId, prompt, listOf(imageUrl), maxTokens = 800, imageFile = File(photo.local_path)) { raw ->
            json.decodeFromString(PhotoAnalysis.serializer(), raw).copy(photoId = photo.id)
        }
    }

    private suspend fun storyPlan(
        projectId: String,
        analyses: List<PhotoAnalysis>,
        vibeId: String,
        narration: String?,
        maxScenes: Int,
        targetScenes: Long? = null,
    ): AppResult<List<ScenePlanItem>> {
        val categories = id.kenang.core.common.story.MotionCategory.entries.joinToString("|") { it.key }
        val cameras = id.kenang.core.common.story.CameraMove.entries.joinToString("|") { it.key }
        val analysesJson = analyses.joinToString(",\n") { json.encodeToString(PhotoAnalysis.serializer(), it) }
        val sceneTarget = targetScenes?.toInt()?.coerceIn(1, maxScenes)
            ?: minOf(maxScenes, maxOf(2, analyses.size))
        // Single-photo storyboard: every scene derives from the one photo, and
        // the plan must vary the MOMENT, not the person — Nano Banana keeps the
        // character consistent because each keyframe is edited from that photo.
        val singlePhotoRules = if (analyses.size == 1 && sceneTarget > 1) """
All $sceneTarget scenes MUST use the single available photo as their only source_photos entry.
Each scene must show a DIFFERENT moment of the SAME person(s): vary the pose, expression,
camera framing and small details of the setting through keyframe_hint, but keep the person's
identity, age and clothing exactly consistent across all scenes. Never invent additional people.""" else ""
        val prompt = """You plan scenes for a gentle memorial video from old family photos.
PHOTO ANALYSES:
[$analysesJson]
${if (!narration.isNullOrBlank()) "NARRATION (Indonesian): $narration" else ""}
Ambience preset: $vibeId.
$singlePhotoRules

Create $sceneTarget scenes. Return ONLY a valid JSON array, each element exactly:
{"scene_id":"sc1","source_photos":["<photo_id>"],"type":"single|fusion",
 "motion_category":"$categories",
 "camera":"$cameras",
 "adjectives":"<max 8 gentle descriptive words, optional>",
 "keyframe_hint":"<one short English sentence describing the scene composition>",
 "subject_en":"<English subject phrase e.g. 'the elderly woman'>",
 "subject_id":"<Indonesian subject phrase e.g. 'Beliau' or 'Mereka'>"}
Rules: motion_category and camera MUST be from the given lists. Use "fusion" with 2+ source_photos
for at most one scene, only when subjects from different photos belong together.
Order scenes as a calm narrative arc. No markdown, no extra text."""
        return visionJson(projectId, prompt, emptyList(), maxTokens = 1600, textOnly = true) { raw ->
            json.decodeFromString<List<ScenePlanItem>>(raw)
        }
    }

    /**
     * One VLM/LLM JSON call with fence-stripping, validation, and up to 2
     * retries (router has no native JSON mode). Gemini path when the user
     * added a Google key; fal-hosted default otherwise.
     */
    private suspend fun <T> visionJson(
        projectId: String,
        prompt: String,
        imageUrls: List<String>,
        maxTokens: Int,
        imageFile: File? = null,
        textOnly: Boolean = false,
        parse: (String) -> T,
    ): AppResult<T> {
        val config = configRepository.current()
        val geminiKey = keyVault.geminiKey()
        var lastError: AppError = AppError.ProviderFailed(Provider.FAL, "no attempt")

        repeat(3) { attempt ->
            val rawResult: AppResult<String> = if (geminiKey != null && (imageFile != null || textOnly)) {
                val bareModel = config.analysis.resolvedGeminiModel()
                val gemini = geminiClient.generateVisionJson(
                    geminiKey, bareModel, prompt,
                    imageFile?.readBytes(),
                )
                when (gemini) {
                    is AppResult.Ok -> {
                        costTracker.record(projectId, null, "gemini/$bareModel", "gemini", 1.0, "per_image", 0.002)
                        gemini
                    }
                    is AppResult.Err -> {
                        // Gemini is the OPTIONAL quality path (AD-03) — its
                        // failure must never sink analysis while fal works
                        // (dogfood 2026-08-26: Google 404'd the model id).
                        Napier.w("gemini analysis failed (${gemini.error}) — falling back to fal VLM")
                        falVision(projectId, prompt, imageUrls, maxTokens)
                    }
                }
            } else {
                falVision(projectId, prompt, imageUrls, maxTokens)
            }

            when (rawResult) {
                is AppResult.Err -> lastError = rawResult.error
                is AppResult.Ok -> {
                    val cleaned = rawResult.value.trim()
                        .removePrefix("```json").removePrefix("```")
                        .removeSuffix("```").trim('`', ' ', '\n', '\r')
                    runCatching { parse(cleaned) }.fold(
                        onSuccess = { return AppResult.Ok(it) },
                        onFailure = {
                            Napier.w("visionJson attempt $attempt: invalid JSON (${it.message?.take(80)}) — retrying")
                            lastError = AppError.ProviderFailed(Provider.FAL, "invalid JSON after retries")
                        },
                    )
                }
            }
        }
        return AppResult.Err(lastError)
    }

    private suspend fun falVision(
        projectId: String,
        prompt: String,
        imageUrls: List<String>,
        maxTokens: Int,
    ): AppResult<String> {
        val config = configRepository.current()
        val body = buildJsonObject {
            put("prompt", prompt)
            if (imageUrls.isNotEmpty()) putJsonArray("image_urls") { imageUrls.forEach { add(it) } }
            put("model", config.analysis.model)
            put("temperature", 0.2)
            put("max_tokens", maxTokens)
        }
        val slug = config.analysis.slug
        return when (val submitted = falClient.submit(slug, body)) {
            is AppResult.Err -> submitted
            is AppResult.Ok -> when (val result = falClient.awaitResult(submitted.value, timeoutMillis = 120_000)) {
                is AppResult.Err -> result
                is AppResult.Ok -> {
                    val payload = result.value.payload
                    val output = payload["output"]?.jsonPrimitive?.content
                        ?: return AppResult.Err(AppError.ProviderFailed(Provider.FAL, "no output field"))
                    val billed = payload["usage"]?.jsonObject?.get("cost")?.jsonPrimitive?.doubleOrNull ?: 0.002
                    costTracker.record(
                        projectId, null, "${slug}:${config.analysis.model}",
                        submitted.value.keyLabel, 1.0, "per_image", billed,
                    )
                    AppResult.Ok(output)
                }
            }
        }
    }
}
