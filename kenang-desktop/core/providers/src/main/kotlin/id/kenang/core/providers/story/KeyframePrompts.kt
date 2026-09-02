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
        /**
         * Reference-photo scenes (owner 2026-09-02): keep only the clearly
         * visible people and DROP anyone whose face is cut off by the frame —
         * otherwise the model invents a stranger's face for them.
         */
        focusMainOnly: Boolean = false,
    ): String {
        val ratioPhrase = if (ratio == "16:9") "16:9 landscape" else "9:16 portrait"
        // Owner 2026-09-02 (5 people came out as 6): the person count now
        // leads the prompt inside the subject phrase itself, not only in a
        // trailing clause — leading positions bind harder on edit models.
        val who = when {
            isPet -> "pet"
            exactSubjects == 1 -> "one person"
            exactSubjects != null && exactSubjects > 0 && !isFusion -> "$exactSubjects people"
            else -> "people"
        }
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
            // D-003 extended (owner 2026-09-01, tightened 2026-09-02): the
            // exact-count clause guards EVERY scene and demands a recount —
            // without it a 5-person family came out as 6.
            val unit = if (isPet) "pet" else if (exactSubjects == 1) "person" else "people"
            " The scene contains exactly $exactSubjects $unit — count them before finalizing: " +
                "exactly $exactSubjects, the same individuals as the source photo, nobody added, " +
                "nobody repeated, no extra similar-looking person in the background."
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
        val focus = if (focusMainOnly) {
            " Include ONLY the people whose faces are clearly and completely visible in the source " +
                "photo. Any person who is partially cut off by the photo edge, whose face is not " +
                "visible, or who is unrecognizable must be OMITTED from the scene entirely — never " +
                "include them and never invent, reconstruct or guess a face for them." +
                // Owner 2026-09-03: a group-worded activity made the model
                // invent two companions for a single-person reference. The
                // photo's people are the WHOLE cast, no matter the activity.
                " The people from the source photo are the ONLY people in the scene — the same " +
                "count, the same individuals. Do NOT add any companion, family member, friend or " +
                "bystander who is not in the source photo, even if the activity wording suggests " +
                "company; if it does, depict the source photo's person(s) doing it alone."
        } else ""
        return restoration + base + fusion + preservation + focus + NO_DUPLICATE_CLAUSE +
            " Photorealistic, warm natural light, $ratioPhrase."
    }
}
