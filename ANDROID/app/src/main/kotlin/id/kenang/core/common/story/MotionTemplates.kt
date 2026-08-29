package id.kenang.core.common.story

/**
 * Motion prompts come ONLY from these template categories (MEMORY §7) plus a
 * camera move. The LLM fills templates; this file is the app-code enforcement:
 * never free-form actions. Validator + auto-repair below.
 */
enum class MotionCategory(
    val key: String,
    /** English clause with {s} = subject placeholder. */
    val phraseEn: String,
    /** Indonesian summary clause with {s} = subject placeholder. */
    val phraseId: String,
) {
    SMILE("smile", "{s} smiles warmly and naturally", "{s} tersenyum hangat"),
    BLINK("blink", "{s} blinks gently, eyes coming alive", "matanya berkedip lembut, mulai hidup"),
    SLIGHT_HEAD_TURN("slight_head_turn", "{s} turns the head slightly", "{s} menoleh pelan"),
    WAVE("wave", "{s} waves a hand softly", "{s} melambaikan tangan pelan"),
    HUG("hug", "they share a gentle warm hug", "mereka berpelukan hangat"),
    HOLD_HANDS("hold_hands", "they hold hands tenderly", "mereka bergandengan tangan"),
    WALK_SLOWLY("walk_slowly", "{s} walks slowly and calmly", "{s} berjalan perlahan"),
    LOOK_AT_CAMERA("look_at_camera", "{s} looks toward the camera with soft eyes", "{s} menatap ke arah kamera"),
    LAUGH_SOFTLY("laugh_softly", "{s} laughs softly", "{s} tertawa kecil"),
    PET_ANIMAL("pet_animal", "{s} gently pets the animal", "{s} mengelus hewannya dengan lembut");

    companion object {
        fun fromKey(key: String): MotionCategory? =
            entries.firstOrNull { it.key.equals(key.trim(), ignoreCase = true) }
    }
}

enum class CameraMove(
    val key: String,
    val phraseEn: String,
    val phraseId: String,
) {
    SLOW_PUSH_IN("slow_push_in", "gentle slow push-in", "kamera mendekat perlahan"),
    GENTLE_PAN("gentle_pan", "gentle pan", "kamera bergeser lembut"),
    STATIC("static", "static camera", "kamera diam");

    companion object {
        fun fromKey(key: String): CameraMove? =
            entries.firstOrNull { it.key.equals(key.trim(), ignoreCase = true) }
    }
}

/** A validated motion choice. [adjectives] is a free descriptor field, max 8 words. */
data class MotionSpec(
    val category: MotionCategory,
    val camera: CameraMove,
    val adjectives: String = "",
    /** English subject phrase, e.g. "the elderly woman"; defaults kept neutral. */
    val subjectEn: String = "the person",
    /** Indonesian subject phrase, e.g. "Beliau". */
    val subjectId: String = "Beliau",
    /**
     * Planner-written continuation sentences (EN) describing the gentle motion
     * arc across the whole clip. Sanitized by [MotionTemplateValidator.sanitizeDetail]
     * before it ever gets here — the template phrase above stays the anchor.
     */
    val detailEn: String = "",
    /** Indonesian version of [detailEn] for the scene card. */
    val detailId: String = "",
) {
    init {
        require(adjectives.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.size <= MotionTemplates.MAX_ADJECTIVE_WORDS) {
            "adjectives limited to ${MotionTemplates.MAX_ADJECTIVE_WORDS} words"
        }
    }
}

object MotionTemplates {
    const val MAX_ADJECTIVE_WORDS = 8

    /** Builds the canonical English motion prompt sent to I2V models. */
    fun buildPromptEn(spec: MotionSpec): String {
        val base = spec.category.phraseEn.replace("{s}", spec.subjectEn)
        val adj = spec.adjectives.trim()
        val motion = if (adj.isEmpty()) base else "$base, $adj"
        val head = "${motion.replaceFirstChar { it.uppercase() }}; ${spec.camera.phraseEn}."
        // Detail sentences carry the rest of the clip's motion arc — without
        // them 10s clips collapse into one short gesture (dogfood 2026-08-28).
        val detail = spec.detailEn.trim()
        return if (detail.isEmpty()) head else "$head $detail"
    }

    /** Builds the Indonesian summary shown on scene cards — pure local mapping. */
    fun buildSummaryId(spec: MotionSpec): String {
        val base = spec.category.phraseId.replace("{s}", spec.subjectId)
        val head = "${base.replaceFirstChar { it.uppercase() }}; ${spec.camera.phraseId}."
        val detail = spec.detailId.trim()
        return if (detail.isEmpty()) head else "$head $detail"
    }
}

/**
 * Enforces the template guardrail on any motion text (LLM output or edited
 * prompt). Free-form action verbs are rejected; [repair] maps them onto the
 * nearest allowed category or falls back to a safe smile.
 */
object MotionTemplateValidator {

    /** Free-form/action verbs that must never reach an I2V prompt. */
    private val FORBIDDEN = listOf(
        "run", "running", "jump", "jumping", "dance", "dancing", "fight", "fighting",
        "kiss", "kissing", "drive", "driving", "swim", "swimming", "fall", "falling",
        "scream", "screaming", "shout", "shouting", "punch", "grab", "throw",
        "spin", "spinning", "fly", "flying", "climb", "climbing", "undress", "strip",
    )

    /** Repair mapping: forbidden intent → closest allowed category. */
    private val REPAIR_MAP = mapOf(
        "run" to MotionCategory.WALK_SLOWLY, "running" to MotionCategory.WALK_SLOWLY,
        "jump" to MotionCategory.WAVE, "jumping" to MotionCategory.WAVE,
        "dance" to MotionCategory.WALK_SLOWLY, "dancing" to MotionCategory.WALK_SLOWLY,
        "kiss" to MotionCategory.HUG, "kissing" to MotionCategory.HUG,
        "scream" to MotionCategory.LAUGH_SOFTLY, "shout" to MotionCategory.LAUGH_SOFTLY,
        "spin" to MotionCategory.SLIGHT_HEAD_TURN, "spinning" to MotionCategory.SLIGHT_HEAD_TURN,
    )

    sealed class Verdict {
        data object Valid : Verdict()
        data class Invalid(val forbiddenWord: String) : Verdict()
    }

    /** A prompt is valid iff it contains an allowed category phrase and no forbidden verb. */
    fun validatePromptEn(prompt: String): Verdict {
        val lower = prompt.lowercase()
        val words = lower.split(Regex("[^a-z-]+"))
        val forbidden = words.firstOrNull { it in FORBIDDEN }
        if (forbidden != null) return Verdict.Invalid(forbidden)
        val hasTemplate = MotionCategory.entries.any { cat ->
            // match on the distinctive stem of the category phrase (without subject)
            lower.contains(cat.phraseEn.replace("{s} ", "").substringBefore(","))
        }
        return if (hasTemplate) Verdict.Valid else Verdict.Invalid("no-template-phrase")
    }

    /** Validates an LLM-proposed (categoryKey, cameraKey); repairs unknown values. */
    fun resolveOrRepair(categoryKey: String, cameraKey: String, adjectives: String = ""): MotionSpec {
        val category = MotionCategory.fromKey(categoryKey)
            ?: REPAIR_MAP.entries.firstOrNull { categoryKey.lowercase().contains(it.key) }?.value
            ?: MotionCategory.SMILE
        val camera = CameraMove.fromKey(cameraKey) ?: CameraMove.SLOW_PUSH_IN
        val cleanAdjectives = sanitizeAdjectives(adjectives)
        return MotionSpec(category, camera, cleanAdjectives)
    }

    /**
     * Sanitizes planner-written motion detail: whole sentences containing a
     * forbidden verb are dropped (never word-patched — a patched sentence can
     * flip meaning), the rest is capped at [maxChars] on a sentence boundary.
     */
    fun sanitizeDetail(raw: String, maxChars: Int = 420): String {
        val sentences = raw.trim().split(Regex("(?<=[.!?])\\s+"))
        val kept = StringBuilder()
        for (s in sentences) {
            val clean = s.trim()
            if (clean.isEmpty()) continue
            val words = clean.lowercase().split(Regex("[^a-z-]+"))
            if (words.any { it in FORBIDDEN }) continue
            if (kept.length + clean.length + 1 > maxChars) break
            if (kept.isNotEmpty()) kept.append(' ')
            kept.append(clean)
        }
        val out = kept.toString()
        return if (out.isEmpty() || out.endsWith(".") || out.endsWith("!") || out.endsWith("?")) out else "$out."
    }

    /** Drops forbidden words from the free adjective field and caps it at 8 words. */
    fun sanitizeAdjectives(raw: String): String =
        raw.trim().split(Regex("\\s+"))
            .filter { it.isNotBlank() && it.lowercase().trim(',', '.') !in FORBIDDEN }
            .take(MotionTemplates.MAX_ADJECTIVE_WORDS)
            .joinToString(" ")
}
