package id.kenang.core.providers.story

import id.kenang.core.data.config.Vibe

/**
 * Assembles keyframe prompts in APP CODE — the preservation clauses and the
 * fusion "exactly N people" clause (D-003: std hallucinated an extra subject
 * without it) are guardrails the LLM cannot omit.
 * Wording follows the PoC-proven patterns (POC/01_vibe.py, 02_fusion.py).
 */
object KeyframePrompts {

    /**
     * Anti-twin guardrail (owner 2026-09-01: composition-freed prompts made
     * Nano Banana clone a single subject into twins). Appended to EVERY
     * keyframe prompt; [ensureNoDuplicateGuard] retrofits it onto prompts
     * stored before the fix so regens on old projects benefit too.
     */
    const val NO_DUPLICATE_CLAUSE =
        " Never duplicate or clone any person: each individual from the source photo appears " +
            "exactly ONCE — no twins, no mirrored copies, no lookalikes — and no new people are added."

    fun ensureNoDuplicateGuard(prompt: String): String =
        if (prompt.isBlank() || prompt.contains("no twins")) prompt
        else prompt + NO_DUPLICATE_CLAUSE

    fun build(
        vibe: Vibe,
        ratio: String,               // "9:16" | "16:9"
        isFusion: Boolean,
        subjectCount: Int,
        keyframeHint: String = "",
        isPet: Boolean = false,
        /** "Restorasi foto lama": explicit damage/fade repair before styling. */
        restore: Boolean = false,
        /** Uncapped person count for non-fusion scenes; null = unknown. */
        exactSubjects: Int? = null,
    ): String {
        val ratioPhrase = if (ratio == "16:9") "16:9 landscape" else "9:16 portrait"
        val who = if (isPet) "pet" else "people"
        val restoration = if (restore) {
            "First fully restore the old photograph: repair scratches, tears, stains and creases, " +
                "remove noise and grain, correct color fading and color cast, recover natural skin " +
                "tones, and sharpen softly. "
        } else ""
        // The scene ACTIVITY leads the prompt (dogfood 2026-08-27: trailing
        // hints + "preserve body exactly" produced 12 near-identical images).
        val activity = keyframeHint.trim().trimEnd('.').let { if (it.isNotEmpty()) "$it." else "" }
        val base = when {
            vibe.promptEn.isBlank() && activity.isEmpty() ->
                "Restore and enhance this photo subtly while keeping the original setting and composition."
            vibe.promptEn.isBlank() ->
                "Create a new photorealistic scene of the exact same $who, keeping the original " +
                    "photo's era and setting style: $activity"
            activity.isEmpty() ->
                // vibe.promptEn carries its own article ("a lush tropical garden …").
                "Place the exact same $who in ${vibe.promptEn} setting."
            else ->
                "Create a new photorealistic scene of the exact same $who in ${vibe.promptEn}: $activity"
        }
        val fusion = if (isFusion) {
            " Combine the $who from the source photos into one natural scene together. " +
                "Exactly $subjectCount $who, no additional people."
        } else if (exactSubjects != null && exactSubjects > 0) {
            // D-003 extended (owner 2026-09-01): the exact-count clause now
            // guards EVERY scene, not just fusion — without it a single
            // subject came out duplicated as twins.
            val unit = if (isPet) "pet" else if (exactSubjects == 1) "person" else "people"
            " The scene contains exactly $exactSubjects $unit — no more, no fewer."
        } else ""
        // Identity locked, composition freed: this is what makes multi-scene
        // single-photo storyboards varied instead of 12 clones of the photo.
        val preservation = if (activity.isEmpty()) {
            " Preserve faces, age, body, and clothing exactly."
        } else {
            " Preserve each person's face, age, and clothing exactly, but change the pose, body " +
                "position, expression, camera angle and framing naturally to fit the scene — " +
                "do NOT copy the original photo's composition."
        }
        return restoration + base + fusion + preservation + NO_DUPLICATE_CLAUSE +
            " Photorealistic, warm natural light, $ratioPhrase."
    }
}
