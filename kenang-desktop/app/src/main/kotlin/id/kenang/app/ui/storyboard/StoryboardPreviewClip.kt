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
     * REAL-motion preview (owner 2026-09-07: the slideshow "tidak ada
     * gerakan"): takes the first seconds of actually rendered scene clips and
     * concatenates them into a ~10s sample. The source clips are the same
     * files the final video reuses, so the sample's cost is never wasted.
     */
    suspend fun renderMotion(
        clips: List<File>,
        ratioLabel: String,
        outFile: File,
        runner: FfmpegRunner,
    ): AppResult<Unit>? {
        val sources = clips.filter(File::isFile)
        if (sources.isEmpty()) return null
        val perSegmentS = 10.0 / sources.size
        val portrait = ratioLabel.trim() == "9:16"
        val w = if (portrait) 1080 else 1920
        val h = if (portrait) 1920 else 1080

        val tempDir = java.nio.file.Files.createTempDirectory("kenang_motion_prev").toFile()
        return try {
            // 1) Uniform segments (each clip's opening seconds, re-encoded so
            //    the concat step is codec-safe regardless of source model).
            val segments = mutableListOf<File>()
            sources.forEachIndexed { i, clip ->
                val seg = File(tempDir, "seg_$i.mp4")
                val r = runner.run(
                    listOf(
                        "-y", "-i", clip.absolutePath,
                        "-t", "%.3f".format(perSegmentS), "-an",
                        "-vf",
                        "scale=$w:$h:force_original_aspect_ratio=decrease," +
                            "pad=$w:$h:(ow-iw)/2:(oh-ih)/2:color=0x0B1B2B,fps=30,format=yuv420p",
                        "-c:v", "libx264", "-preset", "veryfast", "-crf", "20",
                        seg.absolutePath,
                    ),
                    expectedDurationMs = (perSegmentS * 1000).toLong(),
                )
                if (r is AppResult.Err) return r
                segments += seg
            }
            // 2) Concat (streams already uniform) + faststart, pinned to 10s.
            val listFile = File(tempDir, "list.txt")
            listFile.writeText(
                segments.joinToString("\n") {
                    "file '" + it.absolutePath.replace('\\', '/').replace("'", "'\\''") + "'"
                },
            )
            runner.run(
                listOf(
                    "-y", "-f", "concat", "-safe", "0", "-i", listFile.absolutePath,
                    "-c", "copy", "-movflags", "+faststart",
                    "-t", "%.3f".format(perSegmentS * segments.size),
                    outFile.absolutePath,
                ),
                expectedDurationMs = (perSegmentS * segments.size * 1000).toLong(),
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }

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
