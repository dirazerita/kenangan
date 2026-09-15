package id.kenang.core.data.story

import id.kenang.core.data.story.FaceCheck.Verdict
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The crop check decides which face crops may be sent to a paid model as
 * somebody's face (owner 2026-09-15). The Rahayu case: four crops labelled
 * as faces showed feet, a shirt, the boy and the grandmother swapped.
 */
class FaceCheckTest {

    @Test
    fun `a tile passes when it shows a face and names nobody else`() {
        val verified = FaceCheck.verified(
            listOf("s1", "s2", "s3"),
            listOf(Verdict(1, true, "s1"), Verdict(2, true, null), Verdict(3, true, "s1")),
        )
        // Tile 3 is s1 again — a neighbour's face cut for s3 — and is rejected.
        assertEquals(setOf("s1", "s2"), verified)
    }

    @Test
    fun `Rahayu's feet-and-shirt crops come out mostly wrong`() {
        val expected = listOf("s6", "s2", "s3", "s5")
        val verified = FaceCheck.verified(
            expected,
            listOf(Verdict(1, false, null), Verdict(2, true, "s3"), Verdict(3, true, "s2"), Verdict(4, false, null)),
        )
        assertTrue(verified.isEmpty(), "nothing should pass: $verified")
        assertTrue(FaceCheck.mostlyWrong(expected, verified))
    }

    @Test
    fun `one stray face does not flip the whole photo`() {
        assertTrue(!FaceCheck.mostlyWrong(listOf("a", "b", "c"), setOf("a", "b")))
        assertTrue(FaceCheck.mostlyWrong(listOf("a", "b", "c"), setOf("a")))
        assertTrue(!FaceCheck.mostlyWrong(emptyList(), emptySet()))
    }

    @Test
    fun `a missing verdict or an unknown id is handled`() {
        assertTrue(FaceCheck.verified(listOf("a"), emptyList()).isEmpty())
        // An id we never listed is the model guessing; the face itself still counts.
        assertEquals(setOf("a"), FaceCheck.verified(listOf("a"), listOf(Verdict(1, true, "zzz"))))
        assertTrue(FaceCheck.verified(listOf("a"), listOf(Verdict(1, false, "a"))).isEmpty())
    }
}
