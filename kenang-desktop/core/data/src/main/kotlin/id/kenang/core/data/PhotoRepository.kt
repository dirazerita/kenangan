package id.kenang.core.data

import id.kenang.core.common.DispatcherProvider
import id.kenang.core.db.KenangDb
import id.kenang.core.db.Photo
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class PhotoRepository(
    private val db: KenangDb,
    private val dispatchers: DispatcherProvider,
) {
    suspend fun photos(projectId: String): List<Photo> = withContext(dispatchers.io) {
        db.kenangQueries.selectPhotosByProject(projectId).executeAsList()
    }

    /** Copies the source file into the project's photos folder and records it. */
    suspend fun addPhoto(projectId: String, source: File): Photo = withContext(dispatchers.io) {
        val id = "p_" + UUID.randomUUID().toString().take(8)
        val target = File(AppDirs.projectPhotos(projectId), "$id.${source.extension.lowercase()}")
        source.copyTo(target, overwrite = true)
        db.kenangQueries.transaction {
            db.kenangQueries.insertPhoto(id, projectId, target.absolutePath, null, null)
        }
        Photo(id, projectId, target.absolutePath, null, null)
    }

    suspend fun removePhoto(photo: Photo) = withContext(dispatchers.io) {
        db.kenangQueries.transaction { db.kenangQueries.deletePhoto(photo.id) }
        runCatching { File(photo.local_path).delete() }
    }

    suspend fun setUploadUrl(photoId: String, url: String) = withContext(dispatchers.io) {
        db.kenangQueries.updatePhotoUpload(url, photoId)
    }

    suspend fun setAnalysisJson(photoId: String, json: String) = withContext(dispatchers.io) {
        db.kenangQueries.updatePhotoAnalysis(json, photoId)
    }
}
