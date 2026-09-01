package id.kenang.core.providers.story

import id.kenang.core.common.story.CameraMove
import id.kenang.core.common.story.MotionCategory
import kotlin.random.Random

/**
 * Curated activity ideas for "Adegan baru dengan AI" (owner 2026-09-02):
 * one click appends a NEW scene that continues the storyboard with a fresh
 * activity — no LLM round-trip needed (photo analyses are not persisted, so
 * a plan re-run would mean re-analyzing every photo). The pool is written to
 * the same standard as the planner's variety examples: concrete activity,
 * distinct spot, motion category that matches the action.
 */
data class SceneIdea(
    /** English activity clause for the keyframe prompt (subject-neutral). */
    val activityEn: String,
    /** Indonesian description shown on the scene card. */
    val descriptionId: String,
    /** Lowercase keyword used to skip ideas already present in the storyboard. */
    val keyword: String,
    val category: MotionCategory,
    val camera: CameraMove,
)

object SceneIdeas {

    val ALL: List<SceneIdea> = listOf(
        SceneIdea(
            "They sit together around a small table sharing drinks and light snacks, laughing at a story",
            "Duduk bersama di meja kecil sambil menikmati minuman dan camilan, tertawa mendengar cerita.",
            "snacks", MotionCategory.LAUGH_SOFTLY, CameraMove.STATIC,
        ),
        SceneIdea(
            "They stroll side by side along a shaded path, relaxed and unhurried",
            "Berjalan santai beriringan di jalur yang teduh.",
            "shaded path", MotionCategory.WALK_SLOWLY, CameraMove.GENTLE_PAN,
        ),
        SceneIdea(
            "They gather into a gentle warm group hug, eyes closed with contentment",
            "Berkumpul dalam pelukan hangat penuh kebahagiaan.",
            "group hug", MotionCategory.HUG, CameraMove.SLOW_PUSH_IN,
        ),
        SceneIdea(
            "They wave cheerfully toward the camera from a doorway, welcoming and warm",
            "Melambaikan tangan dengan ceria ke arah kamera dari depan pintu.",
            "doorway", MotionCategory.WAVE, CameraMove.SLOW_PUSH_IN,
        ),
        SceneIdea(
            "They sit together on wide steps, chatting warmly and smiling",
            "Duduk bersama di anak tangga sambil mengobrol hangat.",
            "steps", MotionCategory.SMILE, CameraMove.STATIC,
        ),
        SceneIdea(
            "One of them points at something interesting in the distance while the others look and smile",
            "Salah satu menunjuk sesuatu di kejauhan, yang lain menoleh sambil tersenyum.",
            "points at", MotionCategory.SLIGHT_HEAD_TURN, CameraMove.GENTLE_PAN,
        ),
        SceneIdea(
            "They look through an old photo album together, heads leaning close",
            "Membuka album foto lama bersama, saling mendekatkan kepala.",
            "photo album", MotionCategory.SMILE, CameraMove.SLOW_PUSH_IN,
        ),
        SceneIdea(
            "They hold hands in a relaxed line, smiling proudly at the camera",
            "Bergandengan tangan berjajar santai, tersenyum bangga ke arah kamera.",
            "hold hands in", MotionCategory.HOLD_HANDS, CameraMove.STATIC,
        ),
        SceneIdea(
            "They stand under a big shady tree enjoying the breeze, hair and clothes moving softly",
            "Berdiri di bawah pohon rindang menikmati semilir angin.",
            "shady tree", MotionCategory.SLIGHT_HEAD_TURN, CameraMove.GENTLE_PAN,
        ),
        SceneIdea(
            "They gather close for a warm group portrait in soft golden light",
            "Merapat untuk potret bersama dalam cahaya keemasan yang lembut.",
            "group portrait", MotionCategory.LOOK_AT_CAMERA, CameraMove.SLOW_PUSH_IN,
        ),
        SceneIdea(
            "They walk toward the camera together, relaxed and happy",
            "Berjalan bersama ke arah kamera dengan santai dan bahagia.",
            "toward the camera", MotionCategory.WALK_SLOWLY, CameraMove.STATIC,
        ),
        SceneIdea(
            "They sit on a long bench sharing quiet stories, one gesturing gently",
            "Duduk di bangku panjang berbagi cerita, salah satu menggerakkan tangan pelan.",
            "long bench", MotionCategory.SMILE, CameraMove.GENTLE_PAN,
        ),
        SceneIdea(
            "A candid moment of genuine laughter at a joke, some covering their mouths",
            "Momen candid tertawa lepas mendengar candaan.",
            "candid", MotionCategory.LAUGH_SOFTLY, CameraMove.STATIC,
        ),
        SceneIdea(
            "They admire blooming plants nearby, one leaning in to look closely",
            "Mengagumi tanaman yang sedang berbunga, salah satu mencondongkan badan mengamati.",
            "blooming plants", MotionCategory.SLIGHT_HEAD_TURN, CameraMove.GENTLE_PAN,
        ),
        SceneIdea(
            "They wave a warm goodbye in soft late-afternoon light",
            "Melambaikan salam perpisahan hangat dalam cahaya sore yang lembut.",
            "goodbye", MotionCategory.WAVE, CameraMove.GENTLE_PAN,
        ),
        SceneIdea(
            "They share a toast with cups of tea, smiling at each other",
            "Mengangkat cangkir teh bersama sambil saling tersenyum.",
            "toast", MotionCategory.SMILE, CameraMove.SLOW_PUSH_IN,
        ),
    )

    /**
     * Picks an idea whose keyword does not already appear in the storyboard's
     * existing prompts ([usedLower] = all keyframe prompts + hints, lowercase).
     * Every idea used up → any random idea (better a repeat than a dead button).
     */
    fun pick(usedLower: String, random: Random = Random.Default): SceneIdea {
        val fresh = ALL.filter { it.keyword !in usedLower }
        return (fresh.ifEmpty { ALL }).random(random)
    }
}
