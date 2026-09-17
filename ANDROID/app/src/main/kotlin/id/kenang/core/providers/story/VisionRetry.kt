package id.kenang.core.providers.story

/**
 * What a vision/LLM JSON call does when its answer came back cut off
 * (owner 2026-09-17: a three-photo project failed three times in a row on
 * one group photo - fourteen people, each carrying a face box since D-055,
 * cut at ~2000 characters by an 800-token budget; every retry asked for
 * the same answer with the same budget and was cut at the same place).
 * Pure, so the rules are testable without a model.
 */
object VisionRetry {

    /** The router clamps output around this many tokens whatever is asked (D-034). */
    const val TOKEN_CEILING = 2000

    /** Parser messages that mean "the text stopped early", not "the model wrote nonsense". */
    fun looksTruncated(parseMessage: String?): Boolean {
        val m = parseMessage ?: return false
        return m.contains("EOF", ignoreCase = true) ||
            m.contains("Expected end of the", ignoreCase = true) ||
            m.contains("Unexpected end", ignoreCase = true) ||
            m.contains("unterminated", ignoreCase = true)
    }

    /** Twice the budget, never past the ceiling. */
    fun nextTokens(current: Int): Int = (current * 2).coerceAtMost(TOKEN_CEILING).coerceAtLeast(current)

    /** The same request, asked for again in the shortest form that keeps the schema. */
    fun compactRetryPrompt(prompt: String): String =
        prompt.trimEnd() + "\n\nYOUR PREVIOUS ANSWER WAS CUT OFF before the JSON ended. Answer again MORE " +
            "COMPACTLY with the SAME schema and the SAME number of entries: the shortest possible strings " +
            "(descriptions at most 6 words), numbers with at most 2 decimals, no spaces after ':' or ',', " +
            "no line breaks inside strings, nothing before or after the JSON."
}
