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

    /**
     * Identity locked, composition freed — right for one or two people, whose
     * faces the model can hold while it re-stages them (verified 2026-09-12).
     */
    const val FREE_COMPOSITION_CLAUSE =
        " Preserve each person's face, age, and clothing exactly, but change the pose, body " +
            "position, expression, camera angle and framing naturally to fit the scene — " +
            "do NOT copy the original photo's composition."

    /**
     * From this many people on, a scene is an EDIT of the photo instead
     * (owner 2026-09-15, Rahayu RO 1: a family of six re-staged four times
     * came back as strangers, while the one scene the model treated as an
     * edit of the photo kept every face and changed only the background —
     * which is the result the owner wants).
     */
    const val GROUP_ANCHOR_MIN = 3

    /**
     * The group prompt, structured as an EDIT of the photo. Verified the same
     * day that a lock phrased as a mid-prompt clause is not enough: with
     * "sharing kue on a low wooden table" as the scene, the model still
     * re-staged six people around a table, dropped one and redrew the faces.
     * So the lock LEADS, the scene hint is presented as the surroundings to
     * change, and the count is restated as "nobody removed".
     */
    fun groupEditPrompt(people: Int, hint: String, setting: String = ""): String {
        val where = setting.trim().takeIf { it.isNotBlank() }?.let { ", set in $it" } ?: ""
        val scene = hint.trim().trimEnd('.')
        return "GROUP LOCK — EDIT this photograph (image 1) of exactly $people people. Keep all $people " +
            "people EXACTLY as they are in image 1: the same arrangement, positions, sizes and poses, " +
            "the same clothing, and every face pixel-faithful to image 1 — the same bone structure, " +
            "eyes, nose, mouth, skin, hair and age — so each person is instantly recognisable to their " +
            "family. Change ONLY the surroundings so the picture matches this description: " +
            "\"$scene\"$where. Surroundings means the place and background, furniture and props, the " +
            "light and time of day, and the camera distance and framing; the people themselves may " +
            "only change their expression slightly or move a hand. Do not re-pose, reseat, rearrange, " +
            "move or resize anyone, and never redraw a face from imagination. Nobody added, nobody " +
            "removed — count them before finalizing: exactly $people people, the same individuals as " +
            "image 1, each appearing once."
    }

    /** The lock as a clause, for a stored prompt whose shape cannot be rebuilt. */
    fun groupLockClause(people: Int): String =
        " GROUP LOCK: this is an EDIT of image 1, not a new picture. Keep all $people people EXACTLY " +
            "as they are in image 1 — the same arrangement, positions, sizes and poses — and keep " +
            "every face pixel-faithful to image 1: the same bone structure, eyes, nose, mouth, skin, " +
            "hair and age, so each person is instantly recognisable to their family. Change ONLY what " +
            "the scene needs: the surroundings and background, the light and time of day, the camera " +
            "distance and framing, small props, and gentle changes of expression or a hand gesture. " +
            "Do not re-pose, reseat, rearrange, move or resize anyone, and never redraw a face from " +
            "imagination."

    /**
     * The shape [build] has written for a single-source scene since
     * 2026-08-27, so a stored prompt can be taken apart and rebuilt.
     */
    private val storedShape = Regex(
        "^(?<restore>First fully restore the old photograph:.*?sharpen softly\\. )?" +
            "Create a new photorealistic scene of the exact same (?<n>\\d+) people" +
            "(?:, keeping the original photo's era and setting style| in (?<setting>.+?)): (?<hint>.+?)\\." +
            " The scene contains exactly \\d+ people" +
            "(?:.*?(?<focus> Include ONLY the people whose faces.*?doing it alone\\.))?.*$",
        RegexOption.DOT_MATCHES_ALL,
    )

    /**
     * Rebuilds a prompt stored with the freed composition as
     * [groupEditPrompt] when the scene shows [people] >= [GROUP_ANCHOR_MIN],
     * so "Buat ulang gambar" on an existing storyboard gets the edit
     * structure at once. A prompt whose shape cannot be parsed keeps its
     * text with only the composition clause swapped for the lock.
     */
    fun anchorGroupComposition(prompt: String, people: Int?): String {
        if (people == null || people < GROUP_ANCHOR_MIN || FREE_COMPOSITION_CLAUSE !in prompt) return prompt
        val match = storedShape.find(prompt)
            ?: return prompt.replace(FREE_COMPOSITION_CLAUSE, groupLockClause(people))
        val ratioPhrase = if ("9:16 portrait" in prompt) "9:16 portrait" else "16:9 landscape"
        val restore = match.groups["restore"]?.value ?: ""
        val setting = match.groups["setting"]?.value ?: ""
        val focus = match.groups["focus"]?.value ?: ""
        return restore + groupEditPrompt(people, match.groups["hint"]!!.value, setting) + focus +
            NO_DUPLICATE_CLAUSE + " Photorealistic, warm natural light, $ratioPhrase."
    }

    /**
     * User "negative prompt" (owner 2026-09-06: unwanted new people/objects
     * keep appearing). Nano Banana has no negative_prompt API param, so the
     * exclusion rides the prompt as a strict ban list. Applied at SUBMIT time
     * (KeyframeService) so regens on existing scenes honor it immediately.
     */
    /**
     * User-edited scene description (owner 2026-09-06 rev 2: "Mereka bertiga"
     * → "Mereka berdua" must actually change the image). Appended at submit
     * as the AUTHORITATIVE description, overriding the stored hint where they
     * conflict — including the person count.
     */
    fun descriptionOverrideClause(description: String?): String {
        val text = description?.trim()?.takeIf { it.isNotBlank() } ?: return ""
        return " AUTHORITATIVE SCENE DESCRIPTION (user-edited, Indonesian — follow it EXACTLY and " +
            "let it OVERRIDE anything above it conflicts with, including how many people appear): " +
            "${text.take(300)}."
    }

    fun negativeClause(negative: String?): String {
        val text = negative?.trim()?.takeIf { it.isNotBlank() } ?: return ""
        return " STRICTLY FORBIDDEN — none of the following may appear anywhere in the image, " +
            "remove them if present: ${text.take(300)}."
    }

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
        // A group is an EDIT of the photo, not a re-staging (owner 2026-09-15).
        val groupPeople = exactSubjects?.takeIf {
            !isFusion && !isPet && it >= GROUP_ANCHOR_MIN && activity.isNotEmpty()
        }
        if (groupPeople != null) {
            return restoration + groupEditPrompt(groupPeople, activity, vibe.promptEn) +
                focusClause(focusMainOnly) + NO_DUPLICATE_CLAUSE +
                " Photorealistic, warm natural light, $ratioPhrase."
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
            FREE_COMPOSITION_CLAUSE
        }
        val focus = focusClause(focusMainOnly)
        return restoration + base + fusion + preservation + focus + NO_DUPLICATE_CLAUSE +
            " Photorealistic, warm natural light, $ratioPhrase."
    }

    private fun focusClause(focusMainOnly: Boolean): String =
        if (focusMainOnly) {
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

    /**
     * Face references (owner 2026-09-12, "kunci wajah"): the crops are sent
     * AFTER the source photo(s), so the clause names them by position and
     * ties each to the person it belongs to. Without this the model treats
     * extra images as loose inspiration instead of an identity contract.
     */
    fun faceReferenceClause(firstIndex: Int, descriptions: List<String>): String {
        if (descriptions.isEmpty()) return ""
        val lines = descriptions.mapIndexed { i, desc ->
            "image ${firstIndex + i} is the close-up face of ${desc.trim().trimEnd('.')}"
        }
        return " FACE LOCK: ${lines.joinToString("; ")} — these are the SAME people as in image 1. " +
            "Reproduce each face EXACTLY as in its close-up: the same bone structure, eye shape, " +
            "nose, mouth, skin tone, wrinkles, age and hairline, so a family member would " +
            "recognise them instantly. Never substitute a similar-looking person, never idealise " +
            "or rejuvenate the face."
    }
}
