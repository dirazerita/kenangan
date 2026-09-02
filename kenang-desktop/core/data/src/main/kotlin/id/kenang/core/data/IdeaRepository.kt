package id.kenang.core.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import id.kenang.core.common.DispatcherProvider
import id.kenang.core.db.Idea
import id.kenang.core.db.KenangDb
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Product-idea inbox (owner 2026-09-02): suggestions from advertisers/
 * resellers, logged by the owner. Category/priority/status values are plain
 * strings validated at the UI layer (chips), defaults in the schema.
 */
class IdeaRepository(
    private val db: KenangDb,
    private val dispatchers: DispatcherProvider,
) {
    fun observeIdeas(): Flow<List<Idea>> =
        db.kenangQueries.selectAllIdeas().asFlow().mapToList(dispatchers.io)

    suspend fun add(
        title: String,
        description: String?,
        sourceName: String?,
        contact: String?,
        category: String,
        priority: String,
    ): String = withContext(dispatchers.io) {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        db.kenangQueries.transaction {
            db.kenangQueries.insertIdea(
                id, title.trim(), description?.trim()?.ifBlank { null },
                sourceName?.trim()?.ifBlank { null }, contact?.trim()?.ifBlank { null },
                category, priority, "baru", now, now,
            )
        }
        id
    }

    suspend fun update(
        id: String,
        title: String,
        description: String?,
        sourceName: String?,
        contact: String?,
        category: String,
        priority: String,
    ) = withContext(dispatchers.io) {
        db.kenangQueries.updateIdea(
            title.trim(), description?.trim()?.ifBlank { null },
            sourceName?.trim()?.ifBlank { null }, contact?.trim()?.ifBlank { null },
            category, priority, System.currentTimeMillis(), id,
        )
    }

    suspend fun setStatus(id: String, status: String) = withContext(dispatchers.io) {
        db.kenangQueries.updateIdeaStatus(status, System.currentTimeMillis(), id)
    }

    suspend fun delete(id: String) = withContext(dispatchers.io) {
        db.kenangQueries.deleteIdea(id)
    }
}
