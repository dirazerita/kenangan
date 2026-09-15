package id.kenang.app.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A rebuilt window after an uncaught exception, but never an endless loop of them. */
class CrashGuardTest {

    @Test
    fun `isolated exceptions rebuild, a storm gives up`() {
        val guard = CrashGuard
        assertTrue(guard.report(IllegalStateException("one"), now = 0L))
        assertTrue(guard.report(IllegalStateException("two"), now = 5_000L))
        assertFalse(guard.report(IllegalStateException("three"), now = 9_000L), "three in fifteen seconds is a storm")
        // A quiet spell forgets the storm.
        assertTrue(guard.report(IllegalStateException("later"), now = 60_000L))
    }
}
