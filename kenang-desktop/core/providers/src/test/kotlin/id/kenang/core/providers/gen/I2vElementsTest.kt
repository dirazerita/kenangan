package id.kenang.core.providers.gen

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Face lock at the video stage (owner 2026-09-12): Kling binds "@ElementN"
 * in the prompt to the elements array, so the body must carry both halves -
 * a mapping sentence naming each person AND the frontal/reference URLs -
 * exactly in the shape the fal schema documents.
 */
class I2vElementsTest {

    private val refs = listOf(
        GenerationOrchestrator.ElementRef("the elderly woman in batik", "https://f/woman.jpg", listOf("https://f/photo.jpg")),
        GenerationOrchestrator.ElementRef("the man in the white shirt.", "https://f/man.jpg", listOf("https://f/photo.jpg")),
    )

    @Test
    fun `kling gets elements and a prompt that names them`() {
        val body = GenerationOrchestrator.buildI2vBody(
            "fal-ai/kling-video/v3/pro/image-to-video", "https://f/start.jpg",
            "they walk slowly", 5L, "9:16", null, refs,
        )
        val prompt = body["prompt"]!!.jsonPrimitive.content
        assertTrue(prompt.startsWith("@Element1 is the elderly woman in batik; @Element2 is the man in the white shirt."), prompt)
        assertTrue(prompt.endsWith("they walk slowly"), "the motion prompt must survive intact")

        val elements = body["elements"]!!.jsonArray
        assertEquals(2, elements.size)
        val first = elements[0].jsonObject
        assertEquals("https://f/woman.jpg", first["frontal_image_url"]!!.jsonPrimitive.content)
        assertEquals("https://f/photo.jpg", first["reference_image_urls"]!!.jsonArray[0].jsonPrimitive.content)
        assertEquals("https://f/start.jpg", body["start_image_url"]!!.jsonPrimitive.content)
    }

    @Test
    fun `no references leaves the kling body exactly as before`() {
        val body = GenerationOrchestrator.buildI2vBody(
            "fal-ai/kling-video/v3/standard/image-to-video", "https://f/start.jpg",
            "she smiles", 5L, "9:16", null,
        )
        assertNull(body["elements"], "no elements key without face references")
        assertEquals("she smiles", body["prompt"]!!.jsonPrimitive.content)
    }

    @Test
    fun `models without an elements parameter never receive one`() {
        val body = GenerationOrchestrator.buildI2vBody(
            "wan/v2.6/image-to-video", "https://f/start.jpg", "he waves", 5L, "9:16", null, refs,
        )
        assertNull(body["elements"])
        assertEquals("he waves", body["prompt"]!!.jsonPrimitive.content, "no @Element prefix for a model that cannot bind it")
    }
}
