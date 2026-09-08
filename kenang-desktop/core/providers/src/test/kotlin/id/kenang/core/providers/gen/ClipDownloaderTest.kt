package id.kenang.core.providers.gen

import id.kenang.core.common.AppResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClipDownloaderTest {

    private val payload = ByteArray(1000) { (it % 251).toByte() }

    private fun tempDir() = File(System.getProperty("java.io.tmpdir"), "kenang-dl-${System.nanoTime()}")
        .apply { mkdirs() }

    @Test
    fun `resumes a partial download with a Range request`() = runBlocking {
        var sawRange: String? = null
        val engine = MockEngine { request ->
            sawRange = request.headers[HttpHeaders.Range]
            if (sawRange == "bytes=400-") {
                respond(
                    payload.copyOfRange(400, payload.size), HttpStatusCode.PartialContent,
                    headersOf(HttpHeaders.ContentRange, "bytes 400-999/1000"),
                )
            } else {
                respond(payload, HttpStatusCode.OK)
            }
        }
        val dir = tempDir()
        try {
            val target = File(dir, "scene.mp4")
            ClipDownloader.partFile(target, "https://cdn.example/clip.mp4")
                .writeBytes(payload.copyOfRange(0, 400))

            val result = ClipDownloader(HttpClient(engine)).download("https://cdn.example/clip.mp4", target)

            assertTrue(result is AppResult.Ok)
            assertEquals("bytes=400-", sawRange)
            assertTrue(payload.contentEquals(target.readBytes()), "reassembled bytes differ")
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `restarts cleanly when the server ignores Range`() = runBlocking {
        val engine = MockEngine { respond(payload, HttpStatusCode.OK) }
        val dir = tempDir()
        try {
            val target = File(dir, "scene.mp4")
            ClipDownloader.partFile(target, "https://cdn.example/clip.mp4")
                .writeBytes(ByteArray(400) { 1 }) // stale garbage

            val result = ClipDownloader(HttpClient(engine)).download("https://cdn.example/clip.mp4", target)

            assertTrue(result is AppResult.Ok)
            assertTrue(payload.contentEquals(target.readBytes()))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `sha mismatch is rejected`() = runBlocking {
        val engine = MockEngine { respond(payload, HttpStatusCode.OK) }
        val dir = tempDir()
        try {
            val target = File(dir, "scene.mp4")
            val result = ClipDownloader(HttpClient(engine))
                .download("https://cdn.example/clip.mp4", target, expectedSha256 = "deadbeef")
            assertTrue(result is AppResult.Err)
            assertTrue(!target.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `existing complete file is reused without a request`() = runBlocking {
        var hits = 0
        val engine = MockEngine { hits++; respond(payload, HttpStatusCode.OK) }
        val dir = tempDir()
        try {
            val target = File(dir, "scene.mp4").apply { writeBytes(payload) }
            val result = ClipDownloader(HttpClient(engine)).download("https://cdn.example/clip.mp4", target)
            assertTrue(result is AppResult.Ok)
            assertEquals(0, hits)
        } finally {
            dir.deleteRecursively()
        }
    }

    /**
     * Regression (owner 2026-09-08): a regenerated scene downloads onto the
     * path of its previous clip. Without overwrite the paid render was
     * discarded and the stale clip kept.
     */
    @Test
    fun `overwrite replaces an existing clip`() = runBlocking {
        var hits = 0
        val engine = MockEngine { hits++; respond(payload, HttpStatusCode.OK) }
        val dir = tempDir()
        try {
            val stale = ByteArray(600) { 7 }
            val target = File(dir, "scene.mp4").apply { writeBytes(stale) }

            val result = ClipDownloader(HttpClient(engine))
                .download("https://cdn.example/clip.mp4", target, overwrite = true)

            assertTrue(result is AppResult.Ok)
            assertEquals(1, hits)
            assertTrue(payload.contentEquals(target.readBytes()), "stale clip was kept")
        } finally {
            dir.deleteRecursively()
        }
    }

    /** A partial from the PREVIOUS video must never be spliced into a new one. */
    @Test
    fun `a partial from another video is never resumed`() = runBlocking {
        var sawRange: String? = null
        val engine = MockEngine { request ->
            sawRange = request.headers[HttpHeaders.Range]
            respond(payload, HttpStatusCode.OK)
        }
        val dir = tempDir()
        try {
            val target = File(dir, "scene.mp4")
            // Half of the scene's PREVIOUS render, left behind by a crash.
            ClipDownloader.partFile(target, "https://cdn.example/old.mp4")
                .writeBytes(ByteArray(400) { 9 })

            val result = ClipDownloader(HttpClient(engine))
                .download("https://cdn.example/new.mp4", target, overwrite = true)

            assertTrue(result is AppResult.Ok)
            assertEquals(null, sawRange, "resumed a partial belonging to a different video")
            assertTrue(payload.contentEquals(target.readBytes()))
            assertTrue(
                dir.listFiles()?.none { it.name.endsWith(".part") } == true,
                "stale partial of the previous render was left behind",
            )
        } finally {
            dir.deleteRecursively()
        }
    }
}
