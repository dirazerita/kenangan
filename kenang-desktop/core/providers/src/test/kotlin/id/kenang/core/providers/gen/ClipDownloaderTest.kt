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
            File(dir, "scene.mp4.part").writeBytes(payload.copyOfRange(0, 400))

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
            File(dir, "scene.mp4.part").writeBytes(ByteArray(400) { 1 }) // stale garbage

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
}
