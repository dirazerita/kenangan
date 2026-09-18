package id.kenang.core.data.media

import java.io.File

/**
 * What "Foto berbicara dari MP3" needs to know about a recording (owner
 * 2026-09-18): how long it is, and a copy the provider can take - MP3, no
 * longer than the model's ceiling. Platform-implemented: ffmpeg on desktop
 * (probe, transcode, trim), MediaMetadataRetriever on Android (probe only;
 * a recording that is too long is refused there instead of trimmed).
 */
interface AudioProbe {
    /** Duration in milliseconds, or null when the file cannot be read as audio. */
    suspend fun durationMs(file: File): Long?

    /**
     * A copy of [file] the provider can take, written to [out] (or next to it
     * with the right extension), at most [maxSeconds] long. Null when the
     * recording cannot be prepared on this platform.
     */
    suspend fun prepare(file: File, out: File, maxSeconds: Double): File?
}
