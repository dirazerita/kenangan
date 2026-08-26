package id.kenang.core.data

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SceneStatusTest {

    @Test
    fun `happy path transitions are allowed`() {
        assertTrue(SceneStatus.canTransition(SceneStatus.DRAFT, SceneStatus.KEYFRAME_PENDING))
        assertTrue(SceneStatus.canTransition(SceneStatus.KEYFRAME_PENDING, SceneStatus.KEYFRAME_READY))
        assertTrue(SceneStatus.canTransition(SceneStatus.KEYFRAME_READY, SceneStatus.CONFIRMED))
    }

    @Test
    fun `regen and retry loops are allowed`() {
        assertTrue(SceneStatus.canTransition(SceneStatus.KEYFRAME_PENDING, SceneStatus.KEYFRAME_FAILED))
        assertTrue(SceneStatus.canTransition(SceneStatus.KEYFRAME_FAILED, SceneStatus.KEYFRAME_PENDING))
        assertTrue(SceneStatus.canTransition(SceneStatus.KEYFRAME_READY, SceneStatus.KEYFRAME_PENDING))
    }

    @Test
    fun `illegal jumps are refused`() {
        assertFalse(SceneStatus.canTransition(SceneStatus.DRAFT, SceneStatus.CONFIRMED))
        assertFalse(SceneStatus.canTransition(SceneStatus.DRAFT, SceneStatus.KEYFRAME_READY))
        assertFalse(SceneStatus.canTransition(SceneStatus.KEYFRAME_FAILED, SceneStatus.CONFIRMED))
        assertFalse(SceneStatus.canTransition(SceneStatus.DONE, SceneStatus.DRAFT))
    }
}
