package id.kenang.core.providers.story

import id.kenang.core.common.AppResult
import id.kenang.core.data.AppDirs
import id.kenang.core.data.PhotoRepository
import id.kenang.core.data.SettingsRepository
import id.kenang.core.data.story.FaceCrops
import id.kenang.core.data.story.PhotoAnalysis
import id.kenang.core.db.Photo
import id.kenang.core.providers.fal.FalStorage
import io.github.aakira.napier.Napier
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * "Kunci wajah" (owner 2026-09-12): customers sent videos back because the
 * faces were not quite the person. Identity leaks at two points - the image
 * model reimagines a face it saw at a few hundred pixels, and the video
 * model drifts while animating it. This hands both models the real face:
 * a full-resolution crop per person, cut from the original photo around the
 * box the analysis found, uploaded once and attached to every keyframe
 * request (extra reference images) and every video request (Kling
 * elements).
 *
 * Old projects have no boxes stored; [AnalysisService.backfillFaceBoxes]
 * fills them in with one small vision call the first time they are needed.
 */
class FaceLock(
    private val photoRepository: PhotoRepository,
    private val analysisService: AnalysisService,
    private val storage: FalStorage,
    private val settings: SettingsRepository,
) {
    /** One person's reference: where the crop is and what the model should call them. */
    data class Ref(
        val photoId: String,
        val subjectId: String,
        val description: String,
        val file: File,
        val url: String,
        /** The uploaded whole photo, for models that take a second reference. */
        val photoUrl: String?,
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Upload URLs by crop path - fal storage keeps files, so one upload per run is enough. */
    private val uploads = ConcurrentHashMap<String, String>()

    val enabled: Boolean get() = settings.faceLock

    /**
     * Face references for [photoIds] of [projectId], best faces first, at most
     * [MAX_FACES]. Empty when the lock is off, the photos have no analysis, or
     * nothing usable could be cut - callers then behave exactly as before.
     */
    suspend fun refs(projectId: String, photoIds: List<String>, allowBackfill: Boolean = true): List<Ref> {
        if (!enabled || photoIds.isEmpty()) return emptyList()
        val photos = photoRepository.photos(projectId).associateBy { it.id }
        val candidates = mutableListOf<Pair<Double, Ref>>()

        for (photoId in photoIds) {
            val photo = photos[photoId] ?: continue
            var analysis = parse(photo) ?: continue
            if (analysis.subjects.isNotEmpty() && analysis.subjects.none { FaceCrops.isPlausible(it.faceBox) }) {
                if (!allowBackfill) continue
                analysis = when (val r = analysisService.backfillFaceBoxes(projectId, photo, analysis)) {
                    is AppResult.Ok -> r.value
                    is AppResult.Err -> {
                        Napier.w("face lock: box backfill failed for $photoId (${r.error})")
                        continue
                    }
                }
            }
            val source = File(photo.local_path)
            val dir = File(AppDirs.projectDir(projectId), "faces")
            for (subject in analysis.subjects) {
                val box = subject.faceBox?.takeIf { FaceCrops.isPlausible(it) } ?: continue
                val crop = FaceCrops.crop(source, box, File(dir, "${photo.id}_${subject.id}.jpg")) ?: continue
                val url = upload(crop) ?: continue
                candidates += subject.faceQuality to Ref(photo.id, subject.id, subject.desc, crop, url, photo.upload_id)
            }
        }
        return candidates.sortedByDescending { it.first }.take(MAX_FACES).map { it.second }
    }

    /** Stored boxes for [photoId], for the storyboard's lock indicator (no uploads, no calls). */
    suspend fun hasBoxes(projectId: String, photoId: String): Boolean {
        val photo = photoRepository.photos(projectId).firstOrNull { it.id == photoId } ?: return false
        val analysis = parse(photo) ?: return false
        return analysis.subjects.any { FaceCrops.isPlausible(it.faceBox) }
    }

    /** Photo ids of a scene's `source_photos_json` (tolerant of bad rows). */
    fun photoIdsOf(sourcePhotosJson: String): List<String> = runCatching {
        json.decodeFromString(ListSerializer(String.serializer()), sourcePhotosJson)
    }.getOrDefault(emptyList())

    private fun parse(photo: Photo): PhotoAnalysis? = photo.analysis_json?.let { raw ->
        runCatching { json.decodeFromString(PhotoAnalysis.serializer(), raw) }.getOrNull()
    }

    private suspend fun upload(crop: File): String? {
        uploads[crop.absolutePath]?.let { return it }
        return when (val up = storage.uploadFile(crop)) {
            is AppResult.Ok -> up.value.also { uploads[crop.absolutePath] = it }
            is AppResult.Err -> {
                Napier.w("face lock: upload failed for ${crop.name} (${up.error})")
                null
            }
        }
    }

    companion object {
        /** Kling accepts a handful of elements; more faces than this would drown the prompt anyway. */
        const val MAX_FACES = 4
    }
}
