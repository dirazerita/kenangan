package id.kenang.core.data.story

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** MEMORY §6 PhotoAnalysis contract — produced per photo by the VLM. */
@Serializable
data class PhotoAnalysis(
    @SerialName("photo_id") val photoId: String,
    val subjects: List<Subject> = emptyList(),
    val setting: String = "",
    @SerialName("era_style") val eraStyle: String = "",
    val mood: String = "",
    @SerialName("quality_score") val qualityScore: Double = 0.0,
    val issues: List<String> = emptyList(),
) {
    @Serializable
    data class Subject(
        val id: String,
        val desc: String,
        @SerialName("face_quality") val faceQuality: Double = 0.0,
    )
}

/**
 * What the story-plan LLM returns per scene. Motion is CONSTRAINED to
 * template keys — MotionTemplateValidator resolves/repairs them in app code
 * before anything is persisted.
 */
@Serializable
data class ScenePlanItem(
    @SerialName("scene_id") val sceneId: String = "",
    @SerialName("source_photos") val sourcePhotos: List<String>,
    val type: String = "single", // single | fusion
    @SerialName("motion_category") val motionCategory: String,
    val camera: String = "slow_push_in",
    val adjectives: String = "",
    /** Short EN scene description used inside the keyframe prompt. */
    @SerialName("keyframe_hint") val keyframeHint: String = "",
    /** EN subject phrase for the motion prompt, e.g. "the elderly woman". */
    @SerialName("subject_en") val subjectEn: String = "the person",
    /** ID subject phrase for the card summary, e.g. "Beliau". */
    @SerialName("subject_id") val subjectId: String = "Beliau",
    /** 2-3 EN sentences: the gentle motion arc across the whole clip. */
    @SerialName("motion_detail_en") val motionDetailEn: String = "",
    /** 1-2 ID sentences: the same arc, shown to the user on the scene card. */
    @SerialName("motion_detail_id") val motionDetailId: String = "",
)

/**
 * One scene idea read FROM a reference photo (owner 2026-09-08): the
 * suggestion in the reference-scene dialog used to come from a fixed list
 * that never looked at the photo, so it proposed blooming plants for a
 * photo with no plants in it.
 */
@Serializable
data class SceneIdeaSuggestion(
    /** English activity clause for the keyframe prompt. */
    val activity_en: String = "",
    /** Indonesian description shown to the user — two sentences, not a label. */
    val description_id: String = "",
    /** 1-3 lowercase English words naming the distinctive element. */
    val keyword: String = "",
    /** MotionCategory key. */
    val category: String = "",
    /** CameraMove key. */
    val camera: String = "",
)

/** Moderation pre-check result per photo (client-side, before paid calls). */
@Serializable
data class ModerationResult(
    val category: String = "none", // none | nsfw | violence | public_figure
    val reason: String = "",
) {
    val blocked: Boolean get() = category != "none"
}
