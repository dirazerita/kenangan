package id.kenang.app.devtools

import id.kenang.app.di.appModule
import id.kenang.core.common.AppResult
import id.kenang.core.common.Logging
import id.kenang.core.data.AppDirs
import id.kenang.core.data.PhotoRepository
import id.kenang.core.data.ProjectRepository
import id.kenang.core.data.SceneRepository
import id.kenang.core.data.SettingsRepository
import id.kenang.core.data.story.PhotoAnalysis
import id.kenang.core.providers.gen.GenerationOrchestrator
import id.kenang.core.providers.story.FaceLock
import id.kenang.core.providers.story.KeyframeService
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.koin.core.context.startKoin
import java.awt.BasicStroke
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.system.exitProcess

/**
 * Diagnostic for the face lock (owner 2026-09-12). For every photo of the
 * project it resolves face references (backfilling boxes on old analyses -
 * one small vision call per photo), writes the crops, and draws the boxes on
 * a preview image so a human can judge them.
 *
 *   -Ddoctor.project=<id>    required
 *   -Ddoctor.keyframe=<sceneId>  also regenerate that keyframe WITH face refs (paid)
 *   -Ddoctor.video=<sceneId>     also render that scene's clip WITH elements (paid)
 */
fun main(): Unit = runBlocking {
    Logging.init(AppDirs.logs)
    val koin = startKoin { modules(appModule) }.koin
    koin.get<SettingsRepository>().dataFolder?.takeIf { it.isNotBlank() }?.let { AppDirs.useMediaRoot(File(it)) }

    val projectId = System.getProperty("doctor.project")
        ?: run { println("pass -PdoctorProject=<projectId>"); exitProcess(1) }
    val projects = koin.get<ProjectRepository>()
    val photos = koin.get<PhotoRepository>()
    val faceLock = koin.get<FaceLock>()
    val project = projects.get(projectId) ?: run { println("project not found"); exitProcess(1) }
    println("== faceLockDoctor: ${project.name} (tier ${project.tier}) enabled=${faceLock.enabled}")

    val json = Json { ignoreUnknownKeys = true; isLenient = true }
    for (photo in photos.photos(projectId)) {
        val refs = faceLock.refs(projectId, listOf(photo.id))
        println("photo ${photo.id}: ${refs.size} face ref(s)")
        refs.forEach { println("   - ${it.subjectId}: ${it.file.name} (${it.file.length() / 1024} KB) <- ${it.description.take(70)}") }

        // Preview with the boxes drawn, from the (possibly backfilled) analysis.
        val fresh = photos.photos(projectId).firstOrNull { it.id == photo.id } ?: continue
        val analysis = fresh.analysis_json?.let { runCatching { json.decodeFromString(PhotoAnalysis.serializer(), it) }.getOrNull() }
            ?: continue
        val src = runCatching { ImageIO.read(File(fresh.local_path)) }.getOrNull() ?: continue
        val scale = minOf(1.0, 1200.0 / maxOf(src.width, src.height))
        val w = (src.width * scale).toInt()
        val h = (src.height * scale).toInt()
        val preview = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val g = preview.createGraphics()
        g.drawImage(src, 0, 0, w, h, null)
        g.stroke = BasicStroke(4f)
        analysis.subjects.forEach { sub ->
            val b = sub.faceBox ?: return@forEach
            if (b.size != 4) return@forEach
            g.color = Color.GREEN
            g.drawRect((b[0] * w).toInt(), (b[1] * h).toInt(), ((b[2] - b[0]) * w).toInt(), ((b[3] - b[1]) * h).toInt())
            g.drawString(sub.id, (b[0] * w).toInt(), (b[1] * h).toInt() - 6)
        }
        g.dispose()
        val out = File(File(AppDirs.projectDir(projectId), "faces"), "preview_${photo.id}.jpg")
        ImageIO.write(preview, "jpg", out)
        println("   preview: ${out.absolutePath}")
    }

    System.getProperty("doctor.keyframe")?.let { sceneId ->
        println("-- regenerating keyframe $sceneId with face refs (paid)")
        val t0 = System.currentTimeMillis()
        when (val r = koin.get<KeyframeService>().generate(sceneId, project.tier, isRegen = true)) {
            is AppResult.Ok -> println("OK in ${(System.currentTimeMillis() - t0) / 1000}s -> ${r.value.local_keyframe_path}")
            is AppResult.Err -> println("FAILED: ${r.error}")
        }
    }

    System.getProperty("doctor.video")?.let { sceneId ->
        println("-- rendering clip for $sceneId with elements (paid)")
        val t0 = System.currentTimeMillis()
        when (val r = koin.get<GenerationOrchestrator>().generateOne(projectId, project.tier, sceneId)) {
            is AppResult.Ok -> {
                val clip = koin.get<SceneRepository>().scene(sceneId)?.local_clip_path
                println("OK in ${(System.currentTimeMillis() - t0) / 1000}s -> $clip")
            }
            is AppResult.Err -> println("FAILED: ${r.error}")
        }
    }
    exitProcess(0)
}
