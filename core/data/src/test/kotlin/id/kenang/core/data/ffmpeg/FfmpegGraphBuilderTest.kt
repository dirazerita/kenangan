package id.kenang.core.data.ffmpeg

import id.kenang.core.data.ffmpeg.FfmpegGraphBuilder.AssemblySpec
import id.kenang.core.data.ffmpeg.FfmpegGraphBuilder.ClipSpec
import id.kenang.core.data.ffmpeg.FfmpegGraphBuilder.Ratio
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** MASTER_PROMPT_04 §TESTS: 2/3/6 scenes, both ratios, watermark & subs on/off. */
class FfmpegGraphBuilderTest {

    private fun clips(n: Int, durationS: Double = 5.0) =
        (0 until n).map { ClipSpec(File("C:/clips/clip$it.mp4"), durationS) }

    private fun spec(
        n: Int,
        ratio: Ratio = Ratio.V9X16,
        subs: File? = null,
        watermark: File? = null,
        music: File? = null,
        narration: File? = null,
        tempo: Double? = null,
    ) = AssemblySpec(
        clips = clips(n), ratio = ratio, output = File("C:/out/final.mp4"),
        musicFile = music, narrationFile = narration, subtitleFile = subs,
        watermarkFile = watermark, narrationTempo = tempo,
    )

    private fun graph(args: List<String>): String = args[args.indexOf("-filter_complex") + 1]

    @Test
    fun `three scenes chain two xfades with cumulative offsets`() {
        val g = graph(FfmpegGraphBuilder.build(spec(3)))
        // offsets: 5-0.6=4.4 then 4.4+5-0.6=8.8
        assertContains(g, "xfade=transition=fade:duration=0.6:offset=4.4")
        assertContains(g, "xfade=transition=fade:duration=0.6:offset=8.8")
        assertContains(g, "scale=1080:1920:force_original_aspect_ratio=increase,crop=1080:1920")
        assertContains(g, "settb=AVTB,fps=30,format=yuv420p")
    }

    @Test
    fun `two scenes single xfade, 16x9 target`() {
        val g = graph(FfmpegGraphBuilder.build(spec(2, ratio = Ratio.H16X9)))
        assertContains(g, "scale=1920:1080")
        assertEquals(1, Regex("xfade").findAll(g).count())
    }

    @Test
    fun `six scenes produce five xfades and correct total duration`() {
        val s = spec(6)
        val args = FfmpegGraphBuilder.build(s)
        assertEquals(5, Regex("xfade").findAll(graph(args)).count())
        // total = 30 - 5*0.6 = 27
        assertEquals(27.0, FfmpegGraphBuilder.totalDurationS(s))
        assertContains(args, "-t")
        assertEquals("27", args[args.indexOf("-t") + 1])
    }

    @Test
    fun `global fade in and out bracket the video`() {
        val g = graph(FfmpegGraphBuilder.build(spec(3)))
        assertContains(g, "fade=t=in:st=0:d=0.5")
        assertContains(g, "fade=t=out:st=13.3:d=0.5") // total 13.8 - 0.5
    }

    @Test
    fun `subtitles filter escapes the windows path`() {
        val g = graph(FfmpegGraphBuilder.build(spec(2, subs = File("C:\\proj dir\\subtitles.ass"))))
        assertContains(g, "subtitles='C\\:/proj dir/subtitles.ass'")
    }

    @Test
    fun `watermark on maps overlay bottom-right, off has no overlay`() {
        val on = graph(FfmpegGraphBuilder.build(spec(2, watermark = File("C:/wm.png"))))
        assertContains(on, "overlay=W-w-24:H-h-24")
        val off = graph(FfmpegGraphBuilder.build(spec(2)))
        assertFalse(off.contains("overlay"))
    }

    @Test
    fun `music plus narration builds ducking chain with loudnorm`() {
        val g = graph(
            FfmpegGraphBuilder.build(
                spec(2, music = File("C:/m.mp3"), narration = File("C:/n.mp3")),
            ),
        )
        assertContains(g, "sidechaincompress=threshold=0.05:ratio=8:attack=5:release=300")
        assertContains(g, "amix=inputs=2:duration=first:dropout_transition=3")
        assertContains(g, "loudnorm=I=-16:TP=-1.5:LRA=11")
    }

    @Test
    fun `narration tempo fix inserts atempo`() {
        val g = graph(
            FfmpegGraphBuilder.build(
                spec(2, music = File("C:/m.mp3"), narration = File("C:/n.mp3"), tempo = 1.1),
            ),
        )
        assertContains(g, "atempo=1.1")
    }

    @Test
    fun `no audio inputs emits -an`() {
        val args = FfmpegGraphBuilder.build(spec(2))
        assertContains(args, "-an")
    }

    @Test
    fun `output encoding matches spec`() {
        val args = FfmpegGraphBuilder.build(spec(2, music = File("C:/m.mp3")))
        for (expected in listOf("libx264", "-crf", "20", "-preset", "medium", "yuv420p", "aac", "192k", "+faststart")) {
            assertTrue(args.any { it == expected }, "missing $expected in $args")
        }
        assertContains(args, "comment=AI-generated (Kenang)")
    }

    @Test
    fun `single clip has no xfade but still fades`() {
        val g = graph(FfmpegGraphBuilder.build(spec(1)))
        assertFalse(g.contains("xfade"))
        assertContains(g, "fade=t=in")
    }
}
