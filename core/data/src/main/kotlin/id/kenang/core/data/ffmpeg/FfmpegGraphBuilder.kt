package id.kenang.core.data.ffmpeg

import java.io.File

/**
 * Typed builder for the single FFmpeg assembly command (MASTER_PROMPT_04 §4.3).
 * Pure string output — unit-tested without running FFmpeg.
 *
 * Video: per-clip scale+crop to the exact target, settb/fps normalize, xfade
 * chain between scenes, global fade in/out. Audio: looped+trimmed music ducked
 * under narration (sidechaincompress) → amix → loudnorm. Subtitles and the
 * LicenseGate-controlled watermark are optional stages.
 */
object FfmpegGraphBuilder {

    enum class Ratio(val w: Int, val h: Int, val label: String) {
        V9X16(1080, 1920, "9:16"),
        H16X9(1920, 1080, "16:9");

        companion object {
            fun fromLabel(label: String): Ratio =
                entries.firstOrNull { it.label == label } ?: V9X16
        }
    }

    data class ClipSpec(val file: File, val durationS: Double)

    data class AssemblySpec(
        val clips: List<ClipSpec>,
        val ratio: Ratio,
        val output: File,
        val musicFile: File? = null,
        val narrationFile: File? = null,
        val subtitleFile: File? = null,
        /** Non-null = watermark ON (decided by LicenseGate, never here — D-002). */
        val watermarkFile: File? = null,
        /** e.g. 1.1 for the F5.3 "tempo +10%" narration fix; null = unchanged. */
        val narrationTempo: Double? = null,
        val fps: Int = 30,
        val crossfadeS: Double = 0.6,
        val fadeS: Double = 0.5,
        val musicVolume: Double = 0.9,
    ) {
        init {
            require(clips.isNotEmpty()) { "assembly needs at least one clip" }
        }
    }

    /** Final video length: clip durations minus one crossfade overlap per join. */
    fun totalDurationS(spec: AssemblySpec): Double =
        spec.clips.sumOf { it.durationS } - spec.crossfadeS * (spec.clips.size - 1)

    /** Full argument list after the ffmpeg executable. */
    fun build(spec: AssemblySpec): List<String> {
        val args = mutableListOf("-y")

        // ---- inputs ----
        spec.clips.forEach { args += listOf("-i", it.file.absolutePath) }
        val musicIndex = if (spec.musicFile != null) {
            args += listOf("-stream_loop", "-1", "-i", spec.musicFile.absolutePath)
            spec.clips.size
        } else null
        val narrationIndex = if (spec.narrationFile != null) {
            args += listOf("-i", spec.narrationFile.absolutePath)
            spec.clips.size + (if (musicIndex != null) 1 else 0)
        } else null
        val watermarkIndex = if (spec.watermarkFile != null) {
            args += listOf("-i", spec.watermarkFile.absolutePath)
            spec.clips.size + (if (musicIndex != null) 1 else 0) + (if (narrationIndex != null) 1 else 0)
        } else null

        val total = totalDurationS(spec)
        val graph = StringBuilder()

        // ---- video: normalize every clip to the exact target ----
        spec.clips.forEachIndexed { i, _ ->
            graph.append(
                "[$i:v]scale=${spec.ratio.w}:${spec.ratio.h}:force_original_aspect_ratio=increase," +
                    "crop=${spec.ratio.w}:${spec.ratio.h},settb=AVTB,fps=${spec.fps},format=yuv420p[v$i];",
            )
        }
        // xfade chain: offset(k) = sum(d0..dk) - crossfade*(k+1)
        var chained = "v0"
        var offset = 0.0
        for (i in 1 until spec.clips.size) {
            offset += spec.clips[i - 1].durationS - spec.crossfadeS
            val out = "x$i"
            graph.append(
                "[$chained][v$i]xfade=transition=fade:duration=${fmt(spec.crossfadeS)}:offset=${fmt(offset)}[$out];",
            )
            chained = out
        }
        // Global fade in/out.
        var vLabel = "vf"
        graph.append(
            "[$chained]fade=t=in:st=0:d=${fmt(spec.fadeS)}," +
                "fade=t=out:st=${fmt(total - spec.fadeS)}:d=${fmt(spec.fadeS)}[$vLabel];",
        )
        if (spec.subtitleFile != null) {
            graph.append("[$vLabel]subtitles=${escapeFilterPath(spec.subtitleFile)}[vs];")
            vLabel = "vs"
        }
        if (watermarkIndex != null) {
            graph.append("[$vLabel][$watermarkIndex:v]overlay=W-w-24:H-h-24[vw];")
            vLabel = "vw"
        }

        // ---- audio ----
        var aLabel: String? = null
        when {
            musicIndex != null && narrationIndex != null -> {
                val tempo = spec.narrationTempo?.let { "atempo=${fmt(it)}," } ?: ""
                graph.append("[$narrationIndex:a]${tempo}asplit=2[nduck][nmix];")
                graph.append(
                    "[$musicIndex:a]atrim=0:${fmt(total)},asetpts=PTS-STARTPTS," +
                        "volume=${fmt(spec.musicVolume)}[mus];",
                )
                graph.append("[mus][nduck]sidechaincompress=threshold=0.05:ratio=8:attack=5:release=300[duck];")
                graph.append("[duck][nmix]amix=inputs=2:duration=first:dropout_transition=3[mixed];")
                graph.append("[mixed]loudnorm=I=-16:TP=-1.5:LRA=11[aout];")
                aLabel = "aout"
            }
            musicIndex != null -> {
                graph.append(
                    "[$musicIndex:a]atrim=0:${fmt(total)},asetpts=PTS-STARTPTS," +
                        "volume=${fmt(spec.musicVolume)},loudnorm=I=-16:TP=-1.5:LRA=11[aout];",
                )
                aLabel = "aout"
            }
            narrationIndex != null -> {
                val tempo = spec.narrationTempo?.let { "atempo=${fmt(it)}," } ?: ""
                graph.append("[$narrationIndex:a]${tempo}loudnorm=I=-16:TP=-1.5:LRA=11[aout];")
                aLabel = "aout"
            }
        }

        args += listOf("-filter_complex", graph.toString().trimEnd(';'))
        args += listOf("-map", "[$vLabel]")
        if (aLabel != null) args += listOf("-map", "[$aLabel]") else args += "-an"

        // ---- output encoding (spec §4.3) ----
        args += listOf("-c:v", "libx264", "-crf", "20", "-preset", "medium", "-pix_fmt", "yuv420p")
        // -ar 48000: loudnorm internally resamples to 192k and would leave 96kHz
        // AAC in the file — some players/WhatsApp choke on that.
        if (aLabel != null) args += listOf("-c:a", "aac", "-b:a", "192k", "-ar", "48000")
        args += listOf("-t", fmt(total))
        args += listOf("-movflags", "+faststart")
        args += listOf("-metadata", "comment=AI-generated (Kenang)")
        args += spec.output.absolutePath
        return args
    }

    /**
     * Escapes a Windows path for use inside filter_complex (the subtitles
     * filter): forward slashes, escaped drive colon, single-quoted.
     */
    internal fun escapeFilterPath(file: File): String {
        val p = file.absolutePath.replace('\\', '/').replace(":", "\\:").replace("'", "\\'")
        return "'$p'"
    }

    // Locale.US: a comma decimal separator (id-ID default locale) breaks filter syntax.
    private fun fmt(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString()
        else "%.4f".format(java.util.Locale.US, v).trimEnd('0').trimEnd('.')
}
