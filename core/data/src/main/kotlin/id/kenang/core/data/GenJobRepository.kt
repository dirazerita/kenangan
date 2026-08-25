package id.kenang.core.data

import id.kenang.core.common.DispatcherProvider
import id.kenang.core.db.Job
import id.kenang.core.db.KenangDb
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * GenJob rows (MEMORY §6): one row per fal I2V submission. `backend_job_id` +
 * `key_label` + `model` are persisted BEFORE polling starts so a force-kill can
 * resume polling the exact same fal request with the exact same key (AD-14)
 * instead of resubmitting (resubmitting = double provider spend).
 */
object GenJobStatus {
    const val QUEUED = "queued"
    const val RUNNING = "running"
    const val DONE = "done"
    const val FAILED_RETRYABLE = "failed_retryable"
    const val FAILED_PERMANENT = "failed_permanent"
}

class GenJobRepository(
    private val db: KenangDb,
    private val dispatchers: DispatcherProvider,
) {
    /** Creates a queued job row for a scene; returns the new job id ("g_…"). */
    suspend fun create(sceneId: String, model: String, estCostUsd: Double): String =
        withContext(dispatchers.io) {
            val id = "g_" + UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            db.kenangQueries.transaction {
                db.kenangQueries.upsertJob(
                    id, sceneId, null, model, null, GenJobStatus.QUEUED,
                    null, estCostUsd, null, now, now,
                )
            }
            id
        }

    /** Persists the fal request id + submitting key label; marks the job running. */
    suspend fun markSubmitted(jobId: String, backendJobId: String, keyLabel: String) =
        withContext(dispatchers.io) {
            db.kenangQueries.transaction {
                db.kenangQueries.updateJobSubmitted(
                    backendJobId, keyLabel, GenJobStatus.RUNNING, System.currentTimeMillis(), jobId,
                )
            }
        }

    suspend fun setStatus(jobId: String, status: String, errorCode: String? = null, outputUrl: String? = null) =
        withContext(dispatchers.io) {
            db.kenangQueries.transaction {
                db.kenangQueries.updateJobStatus(status, errorCode, outputUrl, System.currentTimeMillis(), jobId)
            }
        }

    suspend fun job(jobId: String): Job? = withContext(dispatchers.io) {
        db.kenangQueries.selectJobById(jobId).executeAsOneOrNull()
    }

    /** All queued/running jobs across projects (resume sweep on app start). */
    suspend fun openJobs(): List<Job> = withContext(dispatchers.io) {
        db.kenangQueries.selectOpenJobs().executeAsList()
    }

    suspend fun jobsByProject(projectId: String): List<Job> = withContext(dispatchers.io) {
        db.kenangQueries.selectJobsByProject(projectId).executeAsList()
    }

    suspend fun latestForScene(sceneId: String): Job? = withContext(dispatchers.io) {
        db.kenangQueries.selectLatestJobForScene(sceneId).executeAsOneOrNull()
    }
}
