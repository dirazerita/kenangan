package id.kenang.core.providers.story

import id.kenang.core.common.story.CameraMove
import id.kenang.core.common.story.MotionCategory
import kotlin.random.Random

/**
 * Curated activity ideas for "Adegan baru dengan AI" and the reference-photo
 * scene dialog (owner 2026-09-02): one click appends a NEW scene with a fresh
 * activity — no LLM round-trip needed (photo analyses are not persisted).
 *
 * [soloSafe] (owner 2026-09-03): activities phrased subject-neutrally, valid
 * for ANY person count. Group-only ideas ("They gather into a hug") fed to a
 * single-person reference photo made the model INVENT companions — reference
 * scenes therefore draw only from the soloSafe subset.
 */
data class SceneIdea(
    /** English activity clause for the keyframe prompt. */
    val activityEn: String,
    /** Indonesian description shown on the scene card. */
    val descriptionId: String,
    /** Lowercase keyword used to skip ideas already present in the storyboard. */
    val keyword: String,
    val category: MotionCategory,
    val camera: CameraMove,
    /** True when the wording adds no people (safe for 1..N subjects). */
    val soloSafe: Boolean = true,
)

object SceneIdeas {

    val ALL: List<SceneIdea> = listOf(
        SceneIdea(
            "enjoying warm tea and light snacks at a small table, smiling contentedly",
            "Menikmati teh hangat dan camilan di meja kecil sambil tersenyum.",
            "snacks", MotionCategory.SMILE, CameraMove.STATIC,
        ),
        SceneIdea(
            "strolling calmly along a shaded garden path",
            "Berjalan santai menyusuri jalur taman yang teduh.",
            "garden path", MotionCategory.WALK_SLOWLY, CameraMove.GENTLE_PAN,
        ),
        SceneIdea(
            "waving cheerfully toward the camera from a doorway",
            "Melambaikan tangan dengan ceria ke arah kamera dari depan pintu.",
            "doorway", MotionCategory.WAVE, CameraMove.SLOW_PUSH_IN,
        ),
        SceneIdea(
            "sitting relaxed on wide steps in the warm light",
            "Duduk santai di anak tangga dalam cahaya hangat.",
            "steps", MotionCategory.SMILE, CameraMove.STATIC,
        ),
        SceneIdea(
            "looking through an old photo album, smiling at the memories",
            "Membuka album foto lama sambil tersenyum mengenang.",
            "photo album", MotionCategory.SMILE, CameraMove.SLOW_PUSH_IN,
        ),
        SceneIdea(
            "standing under a big shady tree enjoying the breeze",
            "Berdiri di bawah pohon rindang menikmati semilir angin.",
            "shady tree", MotionCategory.SLIGHT_HEAD_TURN, CameraMove.GENTLE_PAN,
        ),
        SceneIdea(
            "a warm portrait in soft golden light, looking gently at the camera",
            "Potret hangat dalam cahaya keemasan, menatap lembut ke kamera.",
            "golden light", MotionCategory.LOOK_AT_CAMERA, CameraMove.SLOW_PUSH_IN,
        ),
        SceneIdea(
            "walking toward the camera, relaxed and happy",
            "Berjalan ke arah kamera dengan santai dan bahagia.",
            "toward the camera", MotionCategory.WALK_SLOWLY, CameraMove.STATIC,
        ),
        SceneIdea(
            "sitting on a long bench enjoying the quiet afternoon",
            "Duduk di bangku panjang menikmati sore yang tenang.",
            "long bench", MotionCategory.SMILE, CameraMove.GENTLE_PAN,
        ),
        SceneIdea(
            "admiring blooming plants nearby, leaning in to look closely",
            "Mengagumi tanaman yang sedang berbunga, mencondongkan badan mengamati.",
            "blooming plants", MotionCategory.SLIGHT_HEAD_TURN, CameraMove.GENTLE_PAN,
        ),
        SceneIdea(
            "waving a warm goodbye in soft late-afternoon light",
            "Melambaikan salam perpisahan hangat dalam cahaya sore yang lembut.",
            "goodbye", MotionCategory.WAVE, CameraMove.GENTLE_PAN,
        ),
        SceneIdea(
            "raising a cup of tea with a gentle smile",
            "Mengangkat cangkir teh dengan senyum lembut.",
            "cup of tea", MotionCategory.SMILE, CameraMove.SLOW_PUSH_IN,
        ),
        // ---- group-only wording: NEVER for reference photos (soloSafe=false) ----
        SceneIdea(
            "They gather into a gentle warm group hug, eyes closed with contentment",
            "Berkumpul dalam pelukan hangat penuh kebahagiaan.",
            "group hug", MotionCategory.HUG, CameraMove.SLOW_PUSH_IN,
            soloSafe = false,
        ),
        SceneIdea(
            "They hold hands in a relaxed line, smiling proudly at the camera",
            "Bergandengan tangan berjajar santai, tersenyum bangga ke arah kamera.",
            "hold hands in", MotionCategory.HOLD_HANDS, CameraMove.STATIC,
            soloSafe = false,
        ),
        SceneIdea(
            "One of them points at something in the distance while the others look and smile",
            "Salah satu menunjuk sesuatu di kejauhan, yang lain menoleh sambil tersenyum.",
            "points at", MotionCategory.SLIGHT_HEAD_TURN, CameraMove.GENTLE_PAN,
            soloSafe = false,
        ),
        SceneIdea(
            "A candid moment of genuine laughter at a shared joke",
            "Momen candid tertawa lepas mendengar candaan bersama.",
            "candid", MotionCategory.LAUGH_SOFTLY, CameraMove.STATIC,
            soloSafe = false,
        ),
    )

    /**
     * Picks an idea whose keyword does not already appear in the storyboard's
     * existing prompts ([usedLower], lowercase). [soloOnly] restricts to
     * subject-neutral ideas (reference-photo scenes). Everything used up →
     * any idea from the eligible pool (better a repeat than a dead button).
     */
    fun pick(usedLower: String, random: Random = Random.Default, soloOnly: Boolean = false): SceneIdea {
        val pool = if (soloOnly) ALL.filter { it.soloSafe } else ALL
        val fresh = pool.filter { it.keyword !in usedLower }
        return (fresh.ifEmpty { pool }).random(random)
    }
}
