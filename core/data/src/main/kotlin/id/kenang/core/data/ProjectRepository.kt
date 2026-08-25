package id.kenang.core.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import id.kenang.core.common.DispatcherProvider
import id.kenang.core.db.KenangDb
import id.kenang.core.db.Project
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID

/** Project card shown on Home: project row + thumbnail resolution. */
data class ProjectCard(
    val project: Project,
    /** First keyframe if any, else first photo, else null (placeholder icon). */
    val thumbnailPath: String?,
)

class ProjectRepository(
    private val db: KenangDb,
    private val dispatchers: DispatcherProvider,
) {
    fun observeProjects(): Flow<List<Project>> =
        db.kenangQueries.selectAllProjects().asFlow().mapToList(dispatchers.io)

    suspend fun listCards(): List<ProjectCard> = withContext(dispatchers.io) {
        db.kenangQueries.selectAllProjects().executeAsList().map { p ->
            val thumb = db.kenangQueries.selectFirstKeyframePath(p.id).executeAsOneOrNull()?.let { it }
                ?: db.kenangQueries.selectFirstPhotoPath(p.id).executeAsOneOrNull()
            ProjectCard(p, thumb)
        }
    }

    fun thumbnailFor(projectId: String): String? =
        db.kenangQueries.selectFirstKeyframePath(projectId).executeAsOneOrNull()?.let { it }
            ?: db.kenangQueries.selectFirstPhotoPath(projectId).executeAsOneOrNull()

    suspend fun create(name: String, ratio: String, vibe: String, tier: String): String =
        withContext(dispatchers.io) {
            val id = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            db.kenangQueries.transaction {
                db.kenangQueries.insertProject(
                    id = id, name = name, ratio = ratio, vibe = vibe, tier = tier,
                    narration = null, music_path = null, status = "draft",
                    scene_duration_s = 5L, created_at = now, updated_at = now,
                )
            }
            AppDirs.projectDir(id)
            id
        }

    suspend fun get(projectId: String) = withContext(dispatchers.io) {
        db.kenangQueries.selectProjectById(projectId).executeAsOneOrNull()
    }

    /** Wizard autosave — every step persists immediately (back-safe). */
    suspend fun updateMeta(
        projectId: String, name: String, ratio: String, vibe: String, tier: String,
        narration: String?, musicPath: String?, sceneDurationS: Long,
    ) = withContext(dispatchers.io) {
        db.kenangQueries.transaction {
            db.kenangQueries.updateProjectMeta(
                name, ratio, vibe, tier, narration, musicPath, sceneDurationS,
                System.currentTimeMillis(), projectId,
            )
        }
    }

    suspend fun updateStatus(projectId: String, status: String) = withContext(dispatchers.io) {
        db.kenangQueries.transaction {
            db.kenangQueries.updateProjectStatus(status, System.currentTimeMillis(), projectId)
        }
    }

    suspend fun updateTier(projectId: String, tier: String) = withContext(dispatchers.io) {
        db.kenangQueries.updateProjectTier(tier, System.currentTimeMillis(), projectId)
    }

    /** Deletes DB rows (cascade) AND wipes the project folder. */
    suspend fun delete(projectId: String) = withContext(dispatchers.io) {
        db.kenangQueries.transaction {
            db.kenangQueries.deleteProject(projectId)
        }
        AppDirs.wipeProjectDir(projectId)
    }
}
