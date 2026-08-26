package id.kenang.core.providers.story

import id.kenang.core.data.config.Vibe

/**
 * Assembles keyframe prompts in APP CODE — the preservation clauses and the
 * fusion "exactly N people" clause (D-003: std hallucinated an extra subject
 * without it) are guardrails the LLM cannot omit.
 * Wording follows the PoC-proven patterns (POC/01_vibe.py, 02_fusion.py).
 */
object KeyframePrompts {

    fun build(
        vibe: Vibe,
        ratio: String,               // "9:16" | "16:9"
        isFusion: Boolean,
        subjectCount: Int,
        keyframeHint: String = "",
        isPet: Boolean = false,
    ): String {
        val ratioPhrase = if (ratio == "16:9") "16:9 landscape" else "9:16 portrait"
        val who = if (isPet) "pet" else "people"
        val base = if (vibe.promptEn.isBlank()) {
            "Restore and enhance this photo subtly while keeping the original setting and composition."
        } else {
            // vibe.promptEn carries its own article ("a lush tropical garden …").
            "Place the exact same $who in ${vibe.promptEn} setting."
        }
        val fusion = if (isFusion) {
            " Combine the $who from the source photos into one natural scene together. " +
                "Exactly $subjectCount $who, no additional people."
        } else ""
        val hint = keyframeHint.trim().let { if (it.isNotEmpty()) " $it." else "" }
        return base + fusion +
            " Preserve faces, age, body, and clothing exactly." +
            " Photorealistic, warm natural light, $ratioPhrase." + hint
    }
}
