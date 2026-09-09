package id.kenang.core.data.ffmpeg

import id.kenang.core.common.AppResult
import id.kenang.core.common.DispatcherProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.RandomAccessFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression for the owner's "selalu gagal di langkah ini" (2026-09-09): the
 * previous export was still open in a video player, so replacing it threw
 * AccessDenied and a finished 60 MB render — nearly an hour of encoding — was
 * deleted behind a message that blamed disk space.
 */
class AssemblyMoveTest {

    private val dispatchers = object : DispatcherProvider {
        override val main get() = Dispatchers.Default
        override val io get() = Dispatchers.IO
        override val default get() = Dispatchers.Default
    }

    // moveIntoPlace never touches the locator — the real one is fine here.
    private fun assembler() = VideoAssembler(FfmpegLocator(), dispatchers)

    private fun tempDir() = File(System.getProperty("java.io.tmpdir"), "kenang-move-${System.nanoTime()}")
        .apply { mkdirs() }

    @Test
    fun `replaces the previous export when nothing holds it`() = runBlocking {
        val dir = tempDir()
        try {
            val target = File(dir, "video.mp4").apply { writeBytes(ByteArray(10) { 1 }) }
            val temp = File(dir, ".video.mp4.tmp.mp4").apply { writeBytes(ByteArray(20) { 2 }) }

            val result = assembler().moveIntoPlace(temp, target)

            assertTrue(result is AppResult.Ok)
            assertEquals(target.absolutePath, (result as AppResult.Ok).value.absolutePath)
            assertEquals(20, target.length())
            assertTrue(!temp.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    /**
     * A player holding the old file open must never cost the user the render:
     * the video is kept beside it under a free name.
     */
    @Test
    fun `keeps the render under a free name when the target is locked`() = runBlocking {
        val dir = tempDir()
        val target = File(dir, "video.mp4").apply { writeBytes(ByteArray(10) { 1 }) }
        val lock = RandomAccessFile(target, "rw")
        try {
            val temp = File(dir, ".video.mp4.tmp.mp4").apply { writeBytes(ByteArray(20) { 2 }) }

            val result = assembler().moveIntoPlace(temp, target)

            assertTrue(result is AppResult.Ok, "a finished render was thrown away")
            val saved = (result as AppResult.Ok).value
            assertEquals("video_2.mp4", saved.name)
            assertEquals(20, saved.length())
            assertTrue(!temp.exists(), "temp left behind")
            assertEquals(10, target.length(), "the locked file was corrupted")
        } finally {
            lock.close()
            dir.deleteRecursively()
        }
    }
}
