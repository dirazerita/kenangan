package id.kenang.core.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import id.kenang.core.common.DispatcherProvider
import id.kenang.core.db.KenangDb
import id.kenang.core.db.Scene
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Persisted per-scene state machine (MASTER_PROMPT_03):
 * draft → keyframe_pending → keyframe_ready → confirmed (+ keyframe_failed w/ retry).
 * Any app restart restores exact screen state from these rows.
 */
object SceneStatus {
    const val DRAFT = "draft"
    const val KEYFRAME_PENDING = "keyframe_pending"
    const val KEYFRAME_READY = "keyframe_ready"
    const val KEYFRAME_FAILED = "keyframe_failed"
    const val CONFIRMED = "confirmed"
    const val GENERATING = "generating" // Phase 04
    const val DONE = "done"             // Phase 04
    const val FAILED = "failed"         // Phase 04

    private val allowed: Map<String, Set<String>> = mapOf(
        DRAFT to setOf(KEYFRAME_PENDING),
        KEYFRAME_PENDING to setOf(KEYFRAME_READY, KEYFRAME_FAILED),
        KEYFRAME_FAILED to setOf(KEYFRAME_PENDING),
        KEYFRAME_READY to setOf(KEYFRAME_PENDING, CONFIRMED), // re-gen or confirm
        CONFIRMED to setOf(GENERATING, KEYFRAME_PENDING),     // Phase 04 start, or back to edit
        GENERATING to setOf(DONE, FAILED),
        FAILED to setOf(GENERATING, KEYFRAME_PENDING),
    )

    fun canTransition(from: String, to: String): Boolean = to in (allowed[from] ?: emptySet())
}

class SceneRepository(
    private val db: KenangDb,
    private val dispatchers: DispatcherProvider,
) {
    fun observeScenes(projectId: String): Flow<List<Scene>> =
        db.kenangQueries.selectScenesByProject(projectId).asFlow().mapToList(dispatchers.io)

    suspend fun scenes(projectId: String): List<Scene> = withContext(dispatchers.io) {
        db.kenangQueries.selectScenesByProject(projectId).executeAsList()
    }

    suspend fun scene(sceneId: String): Scene? = withContext(dispatchers.io) {
        db.kenangQueries.selectSceneById(sceneId).executeAsOneOrNull()
    }

    suspend fun upsert(scene: Scene) = withContext(dispatchers.io) {
        db.kenangQueries.transaction {
            db.kenangQueries.upsertScene(
                scene.scene_id, scene.project_id, scene.source_photos_json, scene.type,
                scene.vibe, scene.keyframe_prompt_en, scene.keyframe_url,
                scene.motion_prompt_en, scene.motion_summary_id, scene.duration_s,
                scene.regen_count, scene.status, scene.order_index,
                scene.local_keyframe_path, scene.local_clip_path,
            )
        }
    }

    /** Guarded status transition; illegal moves throw (bug guard, not user error). */
    suspend fun transition(sceneId: String, to: String) = withContext(dispatchers.io) {
        db.kenangQueries.transaction {
            val current = db.kenangQueries.selectSceneById(sceneId).executeAsOneOrNull()
                ?: error("scene $sceneId missing")
            check(SceneStatus.canTransition(current.status, to)) {
                "illegal scene transition ${current.status} -> $to"
            }
            db.kenangQueries.updateSceneStatus(to, sceneId)
        }
    }

    suspend fun setKeyframeResult(sceneId: String, url: String?, localPath: String?, countRegen: Boolean) =
        withContext(dispatchers.io) {
            db.kenangQueries.transaction {
                db.kenangQueries.updateSceneKeyframe(
                    if (url != null) SceneStatus.KEYFRAME_READY else SceneStatus.KEYFRAME_FAILED,
                    url, localPath, if (countRegen) 1L else 0L, sceneId,
                )
            }
        }

    suspend fun updateMotion(sceneId: String, promptEn: String, summaryId: String) =
        withContext(dispatchers.io) {
            db.kenangQueries.updateSceneMotion(promptEn, summaryId, sceneId)
        }

    suspend fun updateDuration(sceneId: String, durationS: Long) = withContext(dispatchers.io) {
        db.kenangQueries.updateSceneDuration(durationS, sceneId)
    }

    /** Persists a full ordering (list of scene ids in new order). */
    suspend fun reorder(sceneIdsInOrder: List<String>) = withContext(dispatchers.io) {
        db.kenangQueries.transaction {
            sceneIdsInOrder.forEachIndexed { index, id ->
                db.kenangQueries.updateSceneOrder(index.toLong(), id)
            }
        }
    }

    /** Deletes a scene; refuses to delete the last one (min 1 must remain). */
    suspend fun delete(sceneId: String, projectId: String): Boolean = withContext(dispatchers.io) {
        var deleted = false
        db.kenangQueries.transaction {
            val count = db.kenangQueries.countScenes(projectId).executeAsOne()
            if (count > 1) {
                db.kenangQueries.deleteScene(sceneId)
                deleted = true
            }
        }
        deleted
    }

    suspend fun deleteAll(projectId: String) = withContext(dispatchers.io) {
        db.kenangQueries.transaction { db.kenangQueries.deleteScenesByProject(projectId) }
    }

    suspend fun confirmAll(projectId: String) = withContext(dispatchers.io) {
        db.kenangQueries.transaction {
            db.kenangQueries.selectScenesByProject(projectId).executeAsList().forEach { s ->
                check(SceneStatus.canTransition(s.status, SceneStatus.CONFIRMED)) {
                    "scene ${s.scene_id} not confirmable from ${s.status}"
                }
                db.kenangQueries.updateSceneStatus(SceneStatus.CONFIRMED, s.scene_id)
            }
        }
    }
}
