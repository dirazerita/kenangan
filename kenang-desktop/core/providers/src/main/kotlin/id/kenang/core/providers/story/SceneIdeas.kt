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
            "Menikmati teh hangat dan camilan kecil di meja, tangan melingkari cangkir. Senyum tipis muncul saat uapnya naik pelan.",
            "snacks", MotionCategory.SMILE, CameraMove.STATIC,
        ),
        SceneIdea(
            "strolling calmly along a shaded garden path",
            "Berjalan santai menyusuri jalur taman yang teduh, langkahnya pelan dan tenang. Cahaya menembus dedaunan dan jatuh lembut di wajahnya.",
            "garden path", MotionCategory.WALK_SLOWLY, CameraMove.GENTLE_PAN,
        ),
        SceneIdea(
            "waving cheerfully toward the camera from a doorway",
            "Berdiri di depan pintu lalu melambaikan tangan dengan ceria ke arah kamera. Wajahnya cerah seperti menyambut tamu yang lama dinanti.",
            "doorway", MotionCategory.WAVE, CameraMove.SLOW_PUSH_IN,
        ),
        SceneIdea(
            "sitting relaxed on wide steps in the warm light",
            "Duduk santai di anak tangga sambil bersandar nyaman dalam cahaya hangat. Pandangannya jauh, seolah sedang mengenang sesuatu yang menyenangkan.",
            "steps", MotionCategory.SMILE, CameraMove.STATIC,
        ),
        SceneIdea(
            "looking through an old photo album, smiling at the memories",
            "Membuka lembar album foto lama dengan hati-hati, jarinya menyusuri satu gambar. Senyumnya melebar saat sebuah kenangan kembali teringat.",
            "photo album", MotionCategory.SMILE, CameraMove.SLOW_PUSH_IN,
        ),
        SceneIdea(
            "standing under a big shady tree enjoying the breeze",
            "Berdiri di bawah pohon rindang menikmati semilir angin sore. Kepalanya menoleh pelan mengikuti daun yang bergoyang di atasnya.",
            "shady tree", MotionCategory.SLIGHT_HEAD_TURN, CameraMove.GENTLE_PAN,
        ),
        SceneIdea(
            "a warm portrait in soft golden light, looking gently at the camera",
            "Potret hangat dalam cahaya keemasan menjelang senja, bahunya sedikit menyamping. Matanya menatap lembut ke kamera dengan tenang dan damai.",
            "golden light", MotionCategory.LOOK_AT_CAMERA, CameraMove.SLOW_PUSH_IN,
        ),
        SceneIdea(
            "walking toward the camera, relaxed and happy",
            "Melangkah santai ke arah kamera dengan raut wajah bahagia dan rileks. Langkahnya ringan, seakan pulang ke rumah yang dirindukan.",
            "toward the camera", MotionCategory.WALK_SLOWLY, CameraMove.STATIC,
        ),
        SceneIdea(
            "sitting on a long bench enjoying the quiet afternoon",
            "Duduk di bangku panjang menikmati sore yang tenang, punggung bersandar nyaman. Napasnya pelan sementara suasana di sekitarnya terasa lapang.",
            "long bench", MotionCategory.SMILE, CameraMove.GENTLE_PAN,
        ),
        SceneIdea(
            "admiring blooming plants nearby, leaning in to look closely",
            "Mencondongkan badan mengamati tanaman yang sedang berbunga di dekatnya. Wajahnya berbinar kagum sambil memperhatikan kelopaknya lebih dekat.",
            "blooming plants", MotionCategory.SLIGHT_HEAD_TURN, CameraMove.GENTLE_PAN,
        ),
        SceneIdea(
            "waving a warm goodbye in soft late-afternoon light",
            "Melambaikan salam perpisahan yang hangat dalam cahaya sore yang lembut. Tangannya terangkat pelan, senyumnya menahan haru yang tenang.",
            "goodbye", MotionCategory.WAVE, CameraMove.GENTLE_PAN,
        ),
        SceneIdea(
            "raising a cup of tea with a gentle smile",
            "Mengangkat cangkir teh sedikit ke depan seperti mengajak bersulang kecil. Senyum lembutnya muncul sementara uap teh mengepul tipis.",
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
