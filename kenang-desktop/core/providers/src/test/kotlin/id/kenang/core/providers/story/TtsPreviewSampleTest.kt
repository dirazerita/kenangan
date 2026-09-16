package id.kenang.core.providers.story

import id.kenang.core.common.AppResult
import id.kenang.core.common.DispatcherProvider
import id.kenang.core.data.AppDirs
import id.kenang.core.data.config.ConfigRepository
import id.kenang.core.db.DatabaseFactory
import id.kenang.core.providers.CostTracker
import id.kenang.core.providers.InMemoryStore
import id.kenang.core.providers.fal.FalKeyPool
import id.kenang.core.providers.fal.FalQueueClient
import id.kenang.core.providers.vault.FalKey
import id.kenang.core.providers.vault.KeyVault
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private object Unconfined : DispatcherProvider {
    override val io: CoroutineDispatcher = Dispatchers.Unconfined
    override val default: CoroutineDispatcher = Dispatchers.Unconfined
    override val main: CoroutineDispatcher = Dispatchers.Unconfined
}

/**
 * The voice list's "Dengar" button (owner 2026-09-16) must never cost a
 * call for a preset voice: the sample ships with the app, and the copy in
 * the cache serves every later click.
 */
class TtsPreviewSampleTest {

    private val media = File(System.getProperty("java.io.tmpdir"), "kenang-voices-${System.nanoTime()}").apply { mkdirs() }
    private var calls = 0

    private fun service(): TtsPreviewService {
        AppDirs.useMediaRoot(media)
        val http = HttpClient(MockEngine { calls++; respond("{}", HttpStatusCode.InternalServerError) })
        val vault = KeyVault(InMemoryStore()).apply { saveFalKeys(listOf(FalKey("A", "k1"))) }
        val config = ConfigRepository(userConfigFile = File(media, "does-not-exist.json"))
        return TtsPreviewService(FalQueueClient(http, FalKeyPool(vault)), http, config, CostTracker(DatabaseFactory.createInMemory(), Unconfined))
    }

    @AfterTest
    fun cleanUp() {
        media.deleteRecursively()
    }

    @Test
    fun `a bundled sample is served from the app, then from the cache, never from the network`() = runTest {
        val s = service()
        assertEquals("Test_Voice-a86b3a", TtsPreviewService.sampleName("Test_Voice"))
        assertTrue(TtsPreviewService.sampleName("Lovely_Girl") != TtsPreviewService.sampleName("lovely_girl"), "case-only ids get distinct files")
        val first = assertIs<AppResult.Ok<File>>(s.sample("Test_Voice")).value
        assertEquals("ID3-test-sample-bytes", first.readText())
        assertTrue(first.absolutePath.startsWith(media.absolutePath), "the sample is copied into the cache: $first")

        val second = assertIs<AppResult.Ok<File>>(s.sample("Test_Voice")).value
        assertEquals(first.absolutePath, second.absolutePath)
        assertEquals(0, calls, "a preset sample must not touch the network")
    }

    @Test
    fun `a voice without a bundled sample is synthesised (and its failure reported)`() = runTest {
        val s = service()
        val r = s.sample("Cloned_Voice_xyz")
        assertIs<AppResult.Err>(r)
        assertTrue(calls >= 1, "an unknown voice goes to the provider")
    }
}
