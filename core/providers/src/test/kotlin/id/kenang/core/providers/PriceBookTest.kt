package id.kenang.core.providers

import id.kenang.core.data.config.ConfigRepository
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PriceBookTest {

    private val configRepository = ConfigRepository(userConfigFile = File("does-not-exist.json"))
    private val priceBook = PriceBook(configRepository)

    @Test
    fun `kling standard 10s estimates from config rates`() {
        val est = assertNotNull(priceBook.estimate("fal-ai/kling-video/v3/standard/image-to-video", 10.0))
        assertEquals(0.84, est.usd, 1e-9)
        assertEquals(0.84 * configRepository.current().fxIdr, est.idr, 1e-6)
    }

    @Test
    fun `tts is priced per 1k chars`() {
        val est = assertNotNull(priceBook.estimate("fal-ai/minimax/speech-02-hd", 500.0))
        assertEquals(0.05, est.usd, 1e-9)
    }

    @Test
    fun `unknown slug returns null, never zero`() {
        assertNull(priceBook.estimate("some/unknown/model", 5.0))
    }
}
