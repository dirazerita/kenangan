package id.kenang.app.ui.storyboard

import id.kenang.core.common.AppResult
import id.kenang.core.data.ffmpeg.FfmpegRunner
import id.kenang.core.db.Scene
import java.io.File

/**
 * ~10-second slideshow MP4 of the storyboard keyframes (owner 2026-09-04:
 * the approval package is the PNG sheet PLUS a short video). Pure local
 * ffmpeg concat of stills — zero AI cost, works offline.
 *
 * Per-image duration = 10s / scene count, clamped to [0.8s, 3s], so 6 scenes
 * ≈ 10s, 12 scenes ≈ 10s, and a single scene stays a watchable 3s.
 */
object StoryboardPreviewClip {

    /**
     * Renders the clip from scenes that already have a keyframe image.
     * Returns null when none has one (sheet-only projects); otherwise the
     * ffmpeg result carrying [outFile].
     */
    suspend fun render(
        scenes: List<Scene>,
        ratioLabel: String,
        outFile: File,
        runner: FfmpegRunner,
    ): AppResult<Unit>? {
        val images = scenes.sortedBy { it.order_index }
            .mapNotNull { it.local_keyframe_path?.let(::File)?.takeIf(File::isFile) }
        if (images.isEmpty()) return null

        val perS = (10.0 / images.size).coerceIn(0.8, 3.0)
        val portrait = ratioLabel.trim() == "9:16"
        val w = if (portrait) 1080 else 1920
        val h = if (portrait) 1920 else 1080

        // concat demuxer list: forward slashes + quoted; the last file is
        // repeated without a duration per the demuxer's contract.
        val listFile = File.createTempFile("kenang_sheet_clip", ".txt")
        fun q(f: File) = "file '" + f.absolutePath.replace('\\', '/').replace("'", "'\\''") + "'"
        listFile.writeText(
            buildString {
                images.forEach { img ->
                    appendLine(q(img))
                    appendLine("duration $perS")
                }
                appendLine(q(images.last()))
            },
        )

        return try {
            runner.run(
                listOf(
                    "-y", "-f", "concat", "-safe", "0", "-i", listFile.absolutePath,
                    "-vf",
                    "scale=$w:$h:force_original_aspect_ratio=decrease," +
                        "pad=$w:$h:(ow-iw)/2:(oh-ih)/2:color=0x0B1B2B,fps=30,format=yuv420p",
                    "-c:v", "libx264", "-preset", "veryfast", "-crf", "20",
                    "-movflags", "+faststart",
                    // The concat demuxer's repeated-last-entry trick pads the
                    // tail; -t pins the output to the intended total exactly.
                    "-t", "%.3f".format(perS * images.size),
                    outFile.absolutePath,
                ),
                expectedDurationMs = (perS * images.size * 1000).toLong(),
            )
        } finally {
            listFile.delete()
        }
    }
}
