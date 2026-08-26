package id.kenang.core.data.ffmpeg

import id.kenang.core.common.AppResult
import id.kenang.core.common.DefaultDispatcherProvider
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration: assembles the 3 tiny committed sample clips + sample mp3s from
 * `testdata/` with the REAL bundled ffmpeg.exe. Skips (passes vacuously) when
 * no ffmpeg is present (CI without the pinned zip).
 */
class AssemblyIntegrationTest {

    private val exe: File? = sequenceOf(
        File("../../app/ffmpeg-dist/ffmpeg/ffmpeg.exe"),
        File(System.getenv("APPDATA") ?: ".", "Kenang/tools/ffmpeg/ffmpeg.exe"),
    ).firstOrNull { it.isFile }

    private fun res(name: String, dir: File): File {
        val target = File(dir, name)
        javaClass.getResourceAsStream("/testdata/$name")!!.use { input ->
            target.outputStream().use { input.copyTo(it) }
        }
        return target
    }

    @Test
    fun `assembles three clips with music, narration and subtitles`() = runBlocking {
        val ff = exe ?: run { println("SKIP: ffmpeg.exe not found"); return@runBlocking }
        val work = File(System.getProperty("java.io.tmpdir"), "kenang-asm-test-${System.nanoTime()}")
        work.mkdirs()
        try {
            val clips = listOf("clip1_9x16.mp4", "clip2_9x16.mp4", "clip3_16x9.mp4").map { res(it, work) }
            val music = res("music_sample.mp3", work)
            val narration = res("narration_sample.mp3", work)
            val subs = File(work, "subtitles.ass").apply {
                writeText(AssBuilder.build("Kenangan indah ini akan selalu hidup di hati kami.", 3000, "9:16"))
            }
            val out = File(work, "final_9x16.mp4")

            val runner = FfmpegRunner(ff, DefaultDispatcherProvider())
            val durations = clips.map { runner.probeDurationMs(it)!! / 1000.0 }
            val spec = FfmpegGraphBuilder.AssemblySpec(
                clips = clips.zip(durations).map { (f, d) -> FfmpegGraphBuilder.ClipSpec(f, d) },
                ratio = FfmpegGraphBuilder.Ratio.V9X16,
                output = out,
                musicFile = music,
                narrationFile = narration,
                subtitleFile = subs,
            )
            var lastProgress = -1
            val result = runner.run(
                FfmpegGraphBuilder.build(spec),
                (FfmpegGraphBuilder.totalDurationS(spec) * 1000).toLong(),
            ) { p -> lastProgress = p }

            assertTrue(result is AppResult.Ok, "ffmpeg failed: ${(result as? AppResult.Err)?.error}")
            assertTrue(out.isFile && out.length() > 10_000, "output missing/too small")
            assertTrue(lastProgress == 100, "progress never reached 100 (got $lastProgress)")

            val outMs = assertNotNull(runner.probeDurationMs(out))
            val expectedMs = (FfmpegGraphBuilder.totalDurationS(spec) * 1000).toLong()
            assertTrue(
                Math.abs(outMs - expectedMs) < 500,
                "duration $outMs vs expected $expectedMs",
            )
        } finally {
            work.deleteRecursively()
        }
    }
}
