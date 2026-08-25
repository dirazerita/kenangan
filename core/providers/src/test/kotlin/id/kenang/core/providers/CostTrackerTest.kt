package id.kenang.core.providers

import id.kenang.core.common.DispatcherProvider
import id.kenang.core.db.DatabaseFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private object TestDispatchers : DispatcherProvider {
    override val io: CoroutineDispatcher = Dispatchers.Unconfined
    override val default: CoroutineDispatcher = Dispatchers.Unconfined
    override val main: CoroutineDispatcher = Dispatchers.Unconfined
}

class CostTrackerTest {

    @Test
    fun `records and sums per project, month and key`() = runTest {
        val db = DatabaseFactory.createInMemory()
        val now = System.currentTimeMillis()
        val tracker = CostTracker(db, TestDispatchers, clock = { now })

        tracker.record("p1", "j1", "fal-ai/kling-video/v3/standard/image-to-video", "Utama", 10.0, "per_second", 0.84)
        tracker.record("p1", "j2", "fal-ai/nano-banana/edit", "Utama", 1.0, "per_image", 0.039)
        tracker.record("p2", "j3", "fal-ai/kling-video/v3/standard/image-to-video", "Klien B", 5.0, "per_second", 0.42)

        assertEquals(0.879, tracker.projectTotalUsd("p1"), 1e-9)
        assertEquals(1.299, tracker.thisMonthTotalUsd(), 1e-9)

        val perKey = tracker.thisMonthPerKey().associate { it.keyLabel to it.usd }
        assertEquals(0.879, perKey["Utama"]!!, 1e-9)
        assertEquals(0.42, perKey["Klien B"]!!, 1e-9)
    }
}
