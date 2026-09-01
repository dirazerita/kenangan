package id.kenang.app.devtools

import id.kenang.app.di.appModule
import id.kenang.core.common.AppResult
import id.kenang.core.common.Logging
import id.kenang.core.data.AppDirs
import id.kenang.core.providers.upscale.UpscaleService
import kotlinx.coroutines.runBlocking
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import java.io.File
import javax.imageio.ImageIO
import kotlin.system.exitProcess

/**
 * Diagnostic: runs ONE real upscale job so the tool is verified end-to-end.
 * -Ddoctor.model=<selectionKey> picks the model (default: cheapest, aura-sr);
 * -Ddoctor.image=<path> picks the source (default: first project photo found).
 * Run: gradlew :app:upscaleDoctor  (cost ≈ $0.002 with the default model)
 */
fun main(): Unit = runBlocking {
    Logging.init(AppDirs.logs)
    startKoin { modules(appModule) }
    val service = GlobalContext.get().get<UpscaleService>()

    val modelKey = System.getProperty("doctor.model") ?: "fal-ai/aura-sr"
    val option = service.options().firstOrNull { it.selectionKey() == modelKey }
        ?: run { println("model '$modelKey' not in catalog"); exitProcess(1) }

    val image = System.getProperty("doctor.image")?.let(::File)
        ?: File(AppDirs.projects, "").walkTopDown().maxDepth(3)
            .firstOrNull { it.isFile && it.parentFile?.name == "photos" && it.extension.lowercase() in setOf("jpg", "jpeg", "png") }
        ?: run { println("no test image found — pass -PdoctorImage=<path>"); exitProcess(1) }

    val src = ImageIO.read(image)
    println("== upscaleDoctor: ${option.labelId} (${option.id})")
    println("   source: ${image.name} ${src?.width}x${src?.height} (${image.length() / 1024} KB)")

    val t0 = System.currentTimeMillis()
    when (val r = service.process(image, option)) {
        is AppResult.Ok -> {
            val out = ImageIO.read(r.value)
            println("OK in ${(System.currentTimeMillis() - t0) / 1000}s")
            println("   result: ${r.value.absolutePath}")
            println("   size:   ${out?.width}x${out?.height} (${r.value.length() / 1024} KB)")
        }
        is AppResult.Err -> println("FAILED after ${(System.currentTimeMillis() - t0) / 1000}s: ${r.error}")
    }
    exitProcess(0)
}
