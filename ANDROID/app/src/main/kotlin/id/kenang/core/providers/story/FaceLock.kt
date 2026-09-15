package id.kenang.core.providers.story

import id.kenang.core.common.AppResult
import id.kenang.core.data.AppDirs
import id.kenang.core.data.PhotoRepository
import id.kenang.core.data.SettingsRepository
import id.kenang.core.data.story.FaceBoxes
import id.kenang.core.data.story.FaceCheck
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
     * [limit]. Empty when the lock is off, the photos have no analysis, or
     * nothing usable could be cut - callers then behave exactly as before.
     *
     * The first time a photo's boxes are used they are SETTLED (owner
     * 2026-09-15): put into x-first order when the model answered y-first,
     * and checked as crops by a cheap vision call, so that no crop of a wall
     * or a pair of feet is ever sent to a paid model as somebody's face.
     */
    suspend fun refs(
        projectId: String,
        photoIds: List<String>,
        allowBackfill: Boolean = true,
        limit: Int = MAX_FACES,
    ): List<Ref> {
        if (!enabled || photoIds.isEmpty()) return emptyList()
        val photos = photoRepository.photos(projectId).associateBy { it.id }
        val candidates = mutableListOf<Pair<Double, Ref>>()

        for (photoId in photoIds) {
            val photo = photos[photoId] ?: continue
            var analysis = parse(photo) ?: continue
            // Only a never-checked analysis is backfilled: after the check has
            // rejected every crop the photo simply has no lock, rather than
            // paying for the same boxes again on every scene.
            val needsBoxes = !analysis.faceBoxesChecked && analysis.subjects.isNotEmpty() &&
                analysis.subjects.none { FaceCrops.isPlausible(it.faceBox) }
            if (needsBoxes) {
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
            if (!analysis.faceBoxesChecked) {
                if (!allowBackfill) continue
                analysis = settle(projectId, photo, analysis, source, dir)
            }
            for (subject in analysis.subjects) {
                val box = subject.faceBox?.takeIf { FaceCrops.isPlausible(it) } ?: continue
                val crop = FaceCrops.crop(source, box, cropFile(dir, photo.id, subject.id, box)) ?: continue
                val url = upload(crop) ?: continue
                candidates += subject.faceQuality to Ref(photo.id, subject.id, subject.desc, crop, url, photo.upload_id)
            }
        }
        return candidates.sortedByDescending { it.first }.take(limit).map { it.second }
    }

    /**
     * How many people the analyses of [photoIds] list, or null when none of
     * them has been analysed. The keyframe prompt anchors a group of three or
     * more to the photo's own composition (owner 2026-09-15).
     */
    suspend fun peopleCount(projectId: String, photoIds: List<String>): Int? {
        val photos = photoRepository.photos(projectId).associateBy { it.id }
        val counts = photoIds.mapNotNull { id -> photos[id]?.let { parse(it) }?.subjects?.size }
        return if (counts.isEmpty()) null else counts.sum()
    }

    /**
     * Orients the boxes by their geometry, then has the crops checked by a
     * vision call; when most crops fail, tries the other orientation and
     * keeps whichever produced more real faces. Subjects whose crop failed in
     * the kept orientation lose their box. The result is stored so this runs
     * once per photo; a check that could not be made leaves the analysis
     * unmarked, so it is tried again next time.
     */
    private suspend fun settle(
        projectId: String,
        photo: Photo,
        analysis: PhotoAnalysis,
        source: File,
        dir: File,
    ): PhotoAnalysis {
        val size = FaceCrops.imageSize(source)
        if (size == null) {
            Napier.w("face lock: cannot read the size of ${source.name}; boxes used as stored")
            return analysis
        }
        val ids = analysis.subjects.map { it.id }
        val geometric = FaceBoxes.orient(analysis.subjects.map { it.faceBox }, size.first, size.second, "face boxes of ${photo.id}")

        val first = check(projectId, photo, analysis, geometric, source, dir) ?: return analysis
        var boxes = geometric
        var verified = first
        if (FaceCheck.mostlyWrong(ids, first)) {
            val flipped = geometric.map { it?.let(FaceBoxes::swap) }
            val second = check(projectId, photo, analysis, flipped, source, dir)
            if (second != null && second.size > first.size) {
                Napier.w(
                    "face lock: crops of ${photo.id} only pass with the axes swapped " +
                        "(${second.size} vs ${first.size}) - using the swapped boxes",
                )
                boxes = flipped
                verified = second
            }
        }
        val settled = analysis.copy(
            subjects = analysis.subjects.mapIndexed { i, sub ->
                sub.copy(faceBox = boxes[i]?.takeIf { sub.id in verified })
            },
            faceBoxesChecked = true,
        )
        Napier.i("face lock: ${photo.id} settled - ${verified.size}/${ids.size} face crop(s) confirmed")
        photoRepository.setAnalysisJson(photo.id, json.encodeToString(PhotoAnalysis.serializer(), settled))
        return settled
    }

    /**
     * Cuts the crops for [boxes], lays them on a numbered sheet and asks the
     * model which tiles really show the listed person's face. Returns the
     * confirmed subject ids, or null when the check itself failed.
     */
    private suspend fun check(
        projectId: String,
        photo: Photo,
        analysis: PhotoAnalysis,
        boxes: List<List<Double>?>,
        source: File,
        dir: File,
    ): Set<String>? {
        val tiles = analysis.subjects.mapIndexedNotNull { i, sub ->
            val box = boxes[i]?.takeIf { FaceCrops.isPlausible(it) } ?: return@mapIndexedNotNull null
            FaceCrops.crop(source, box, cropFile(dir, photo.id, sub.id, box))?.let { sub to it }
        }
        if (tiles.isEmpty()) return emptySet()
        val sheet = FaceCrops.sheet(tiles.map { it.second }, File(dir, "check_${photo.id}_${boxes.hashCode()}.jpg"))
            ?: return null
        val people = tiles.map { it.first.id to it.first.desc }
        return when (val r = analysisService.checkFaceCrops(projectId, sheet, people)) {
            is AppResult.Ok -> FaceCheck.verified(people.map { it.first }, r.value)
            is AppResult.Err -> {
                Napier.w("face lock: crop check failed for ${photo.id} (${r.error})")
                null
            }
        }
    }

    /** One file per box, so a corrected box never reuses a crop cut with the wrong one. */
    private fun cropFile(dir: File, photoId: String, subjectId: String, box: List<Double>): File =
        File(dir, "${photoId}_${subjectId}_${box.joinToString("_") { (it * 1000).toInt().toString() }}.jpg")

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

        /**
         * The image model takes a list of references, and a family of six
         * needs all six (owner 2026-09-15: the two people without a crop
         * were re-imagined). Beyond this the references stop helping.
         */
        const val KEYFRAME_MAX_FACES = 8
    }
}
