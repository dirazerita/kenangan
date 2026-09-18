package id.kenang.core.data.media

/**
 * The decisions behind [AudioProbe.prepare], kept pure so they are tested
 * without ffmpeg: which recordings go to the provider as they are, which
 * are re-encoded, which are cut, and what the user is told.
 */
object AudioIntake {
    /** Formats every talking model on fal takes as they are. */
    val PASS_THROUGH = setOf("mp3", "wav")

    enum class Action { COPY, TRANSCODE, TRIM, REJECT }

    /** What to do with a recording of [ext] and [durationMs] against a [maxSeconds] ceiling. */
    fun plan(ext: String, durationMs: Long?, maxSeconds: Double, canTranscode: Boolean): Action {
        if (durationMs == null || durationMs <= 0) return Action.REJECT
        val tooLong = durationMs / 1000.0 > maxSeconds + 0.05
        return when {
            tooLong && canTranscode -> Action.TRIM
            tooLong -> Action.REJECT
            ext.lowercase() in PASS_THROUGH -> Action.COPY
            canTranscode -> Action.TRANSCODE
            else -> Action.REJECT
        }
    }

    /** Seconds the provider will bill for: the recording, or the ceiling when it was cut. */
    fun billableSeconds(durationMs: Long, maxSeconds: Double): Double =
        minOf(durationMs / 1000.0, maxSeconds).coerceAtLeast(1.0)
}
