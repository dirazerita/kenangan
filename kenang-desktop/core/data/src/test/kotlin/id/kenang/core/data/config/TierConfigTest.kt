package id.kenang.core.data.config

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Group routing (owner 2026-09-15): a scene of three or more people goes to
 * the tier's keyframe_group model, because the standard edit model re-stages
 * a family group and redraws the faces while the pro one keeps them.
 */
class TierConfigTest {

    @Test
    fun `groups of three or more route to the group model`() {
        val tier = TierConfig(keyframe = "std", keyframeGroup = "pro", i2v = "i2v")
        assertEquals("std", tier.keyframeFor(null))
        assertEquals("std", tier.keyframeFor(2))
        assertEquals("pro", tier.keyframeFor(3))
        assertEquals("pro", tier.keyframeFor(6))
    }

    @Test
    fun `a tier without a group model keeps its own`() {
        assertEquals("std", TierConfig(keyframe = "std", i2v = "i2v").keyframeFor(6))
        assertEquals("std", TierConfig(keyframe = "std", keyframeGroup = "", i2v = "i2v").keyframeFor(6))
        assertEquals("pro", TierConfig(keyframe = "std", keyframeGroup = "pro", keyframeGroupMin = 5, i2v = "i2v").keyframeFor(5))
        assertEquals("std", TierConfig(keyframe = "std", keyframeGroup = "pro", keyframeGroupMin = 5, i2v = "i2v").keyframeFor(4))
    }

    @Test
    fun `the bundled config routes standar groups to the pro edit model`() {
        val repo = ConfigRepository(userConfigFile = File("does-not-exist.json"))
        val standar = repo.current().tierRouting.resolve("standar")
        assertEquals("fal-ai/nano-banana-pro/edit", standar.keyframeFor(6))
        assertEquals("fal-ai/nano-banana/edit", standar.keyframeFor(2))
        assertEquals("fal-ai/nano-banana-pro/edit", repo.current().tierRouting.resolve("premium").keyframeFor(2))
    }
}
