package id.kenang.core.data

import id.kenang.core.common.DispatcherProvider
import id.kenang.core.db.KenangDb
import id.kenang.core.db.Output
import kotlinx.coroutines.withContext
import java.util.UUID

/** Final exported MP4s (result screen's source of truth). */
class OutputRepository(
    private val db: KenangDb,
    private val dispatchers: DispatcherProvider,
) {
    suspend fun record(projectId: String, path: String, ratio: String, tier: String, estCostUsd: Double): String =
        withContext(dispatchers.io) {
            val id = "o_" + UUID.randomUUID().toString()
            db.kenangQueries.transaction {
                db.kenangQueries.insertOutput(id, projectId, path, ratio, tier, estCostUsd, System.currentTimeMillis())
            }
            id
        }

    suspend fun byProject(projectId: String): List<Output> = withContext(dispatchers.io) {
        db.kenangQueries.selectOutputsByProject(projectId).executeAsList()
    }

    suspend fun latest(projectId: String): Output? = byProject(projectId).firstOrNull()
}
