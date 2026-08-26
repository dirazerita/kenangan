package id.kenang.core.providers

import id.kenang.core.common.DispatcherProvider
import id.kenang.core.db.KenangDb
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

/** Per-key monthly total (AD-14 — per-client billing for jasa/studio users). */
data class KeySpend(val keyLabel: String?, val usd: Double)

/**
 * Writes every estimated provider charge to `gen_cost` (incl. which fal key
 * served the call) and exposes the sums the UI shows. Estimates only.
 */
class CostTracker(
    private val db: KenangDb,
    private val dispatchers: DispatcherProvider,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun record(
        projectId: String,
        jobId: String?,
        model: String,
        keyLabel: String?,
        qty: Double,
        unit: String,
        estUsd: Double,
    ) = withContext(dispatchers.io) {
        db.kenangQueries.transaction {
            db.kenangQueries.insertGenCost(projectId, jobId, model, keyLabel, qty, unit, estUsd, clock())
        }
    }

    suspend fun projectTotalUsd(projectId: String): Double = withContext(dispatchers.io) {
        db.kenangQueries.sumCostByProject(projectId).executeAsOne()
    }

    suspend fun thisMonthTotalUsd(): Double = withContext(dispatchers.io) {
        db.kenangQueries.sumCostSince(startOfMonthMillis()).executeAsOne()
    }

    suspend fun thisMonthPerKey(): List<KeySpend> = withContext(dispatchers.io) {
        db.kenangQueries.sumCostPerKeySince(startOfMonthMillis()).executeAsList()
            .map { KeySpend(it.key_label, it.total_usd) }
    }

    private fun startOfMonthMillis(): Long =
        LocalDate.now(ZoneId.systemDefault()).withDayOfMonth(1)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
