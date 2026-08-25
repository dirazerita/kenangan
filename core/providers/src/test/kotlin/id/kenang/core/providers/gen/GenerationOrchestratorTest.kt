package id.kenang.core.providers.gen

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** Pure request-shaping rules proven against Phase 00 T4 payloads. */
class GenerationOrchestratorTest {

    private val json = Json

    @Test
    fun `kling body uses start_image_url and keeps generate_audio false`() {
        val extra = json.parseToJsonElement("""{"generate_audio": false}""") as JsonObject
        val body = GenerationOrchestrator.buildI2vBody(
            "fal-ai/kling-video/v3/standard/image-to-video",
            "https://cdn/img.jpg", "She smiles warmly; camera static.", 5, "9:16", extra,
        )
        assertEquals("https://cdn/img.jpg", body["start_image_url"]!!.jsonPrimitive.content)
        assertEquals("5", body["duration"]!!.jsonPrimitive.content)
        // Kling upstream default is ON (+50% cost) — must stay false (MEMORY §3).
        assertFalse(body["generate_audio"]!!.jsonPrimitive.boolean)
        assertNull(body["image_url"])
    }

    @Test
    fun `wan body uses image_url with 720p and no variant leak`() {
        val extra = json.parseToJsonElement("""{"variant": "flash"}""") as JsonObject
        val body = GenerationOrchestrator.buildI2vBody(
            "wan/v2.6/image-to-video/flash", "https://cdn/img.jpg", "prompt", 5, "9:16", extra,
        )
        assertEquals("https://cdn/img.jpg", body["image_url"]!!.jsonPrimitive.content)
        assertEquals("720p", body["resolution"]!!.jsonPrimitive.content)
        assertNull(body["variant"], "variant routes the slug, never the body")
    }

    @Test
    fun `seedance body uses image_urls array with reference prefix`() {
        val body = GenerationOrchestrator.buildI2vBody(
            "bytedance/seedance-2.0/mini/reference-to-video",
            "https://cdn/img.jpg", "the family smiles", 5, "16:9", null,
        )
        assertEquals("https://cdn/img.jpg", body["image_urls"]!!.jsonArray[0].jsonPrimitive.content)
        assertEquals("@Image1 the family smiles", body["prompt"]!!.jsonPrimitive.content)
        assertEquals("16:9", body["aspect_ratio"]!!.jsonPrimitive.content)
    }

    @Test
    fun `variant param appends a slug sub-path (Wan flash, D-006)`() {
        val extra = json.parseToJsonElement("""{"variant": "flash"}""") as JsonObject
        assertEquals(
            "wan/v2.6/image-to-video/flash",
            GenerationOrchestrator.submitSlug("wan/v2.6/image-to-video", extra),
        )
        assertEquals(
            "fal-ai/kling-video/v3/standard/image-to-video",
            GenerationOrchestrator.submitSlug(
                "fal-ai/kling-video/v3/standard/image-to-video",
                json.parseToJsonElement("""{"generate_audio": false}""") as JsonObject,
            ),
        )
        assertEquals("x/y", GenerationOrchestrator.submitSlug("x/y", null))
    }
}
