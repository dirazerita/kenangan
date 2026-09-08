package id.kenang.app.devtools

import id.kenang.app.di.appModule
import id.kenang.core.common.AppResult
import id.kenang.core.common.Logging
import id.kenang.core.data.AppDirs
import id.kenang.core.data.GenJobRepository
import id.kenang.core.data.GenJobStatus
import id.kenang.core.data.SceneRepository
import id.kenang.core.db.KenangDb
import id.kenang.core.providers.gen.ClipDownloader
import kotlinx.coroutines.runBlocking
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.system.exitProcess

/**
 * Repair tool for D-045: before the ClipDownloader overwrite fix, a scene that
 * was re-rendered after an edit kept its OLD clip file — the fal result was
 * paid for and thrown away. The result URL survives on the job row, so the
 * clip is recoverable: any scene whose newest DONE job is NEWER than its clip
 * file gets that job's video downloaded over the stale file.
 *
 * Run: gradlew :app:clipDoctor [-PdoctorProject=<id>] [-PdoctorDry=true]
 */
fun main(): Unit = runBlocking {
    Logging.init(AppDirs.logs)
    startKoin { modules(appModule) }
    val koin = GlobalContext.get()
    val db = koin.get<KenangDb>()
    val scenes = koin.get<SceneRepository>()
    val jobs = koin.get<GenJobRepository>()
    val downloader = koin.get<ClipDownloader>()

    val explicit = System.getProperty("doctor.project")
    val dryRun = System.getProperty("doctor.dry") == "true"
    val all = db.kenangQueries.selectAllProjects().executeAsList()
    val project = if (explicit != null) all.firstOrNull { it.id == explicit }
    else all.maxByOrNull { it.updated_at }
    if (project == null) { println("no project found"); exitProcess(1) }

    val stamp = SimpleDateFormat("dd MMM HH:mm")
    println("== clipDoctor: '${project.name}'${if (dryRun) "  (DRY RUN)" else ""}")

    var repaired = 0
    var recoveredUsd = 0.0
    for (scene in scenes.scenes(project.id).sortedBy { it.order_index }) {
        val label = "Adegan ${scene.order_index + 1} (${scene.scene_id})"
        val job = jobs.latestForScene(scene.scene_id)
        if (job == null || job.status != GenJobStatus.DONE || job.output_url == null) {
            println("$label: no finished render on record — skipped")
            continue
        }
        val clip = File(AppDirs.projectClips(project.id), "${scene.scene_id}.mp4")
        val clipTime = if (clip.isFile) clip.lastModified() else 0L
        // A healthy clip is written DURING its job, so its mtime falls between
        // the job's start and its completion stamp. Only a clip older than the
        // job even started is stale — the render it should hold was discarded.
        if (clipTime >= job.created_at) {
            println("$label: clip current (${stamp.format(Date(clipTime))})")
            continue
        }

        val paidUsd = job.est_cost_usd ?: 0.0
        println(
            "$label: STALE — clip ${if (clipTime == 0L) "missing" else stamp.format(Date(clipTime))}" +
                ", paid render ${stamp.format(Date(job.created_at))} (\$$paidUsd)",
        )
        if (dryRun) { repaired++; recoveredUsd += paidUsd; continue }

        when (val dl = downloader.download(job.output_url!!, clip, overwrite = true)) {
            is AppResult.Ok -> {
                scenes.setClipPath(scene.scene_id, clip.absolutePath)
                repaired++
                recoveredUsd += paidUsd
                println("    recovered ${clip.length() / 1024} KB -> ${clip.absolutePath}")
            }
            is AppResult.Err -> println("    FAILED to recover: ${dl.error} (fal link may have expired)")
        }
    }

    println("-- ${if (dryRun) "would repair" else "repaired"} $repaired scene(s), " +
        "\$${"%.2f".format(recoveredUsd)} of paid renders recovered")
    exitProcess(0)
}
