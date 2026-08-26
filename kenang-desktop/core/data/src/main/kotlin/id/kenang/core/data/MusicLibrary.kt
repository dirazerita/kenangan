package id.kenang.core.data

import id.kenang.core.data.config.BundledMusic
import id.kenang.core.data.config.ConfigRepository
import io.github.aakira.napier.Napier
import java.io.File

/**
 * Bundled royalty-free music (config-driven list, files in app resources
 * `/music/`). Tracks are staged to %APPDATA%/Kenang/music on first use;
 * attribution ([BundledMusic.credit]) must stay visible in About.
 */
class MusicLibrary(private val configRepository: ConfigRepository) {

    data class Track(val meta: BundledMusic, val file: File)

    fun tracks(): List<Track> =
        configRepository.current().bundledMusic.mapNotNull { meta ->
            stage(meta)?.let { Track(meta, it) }
        }

    private fun stage(meta: BundledMusic): File? = runCatching {
        val target = File(File(AppDirs.root, "music").apply { mkdirs() }, meta.file)
        if (!target.isFile || target.length() == 0L) {
            val res = MusicLibrary::class.java.getResourceAsStream("/music/${meta.file}")
            if (res == null) {
                Napier.w("bundled music missing from resources: ${meta.file}")
                return null
            }
            res.use { input -> target.outputStream().use { input.copyTo(it) } }
        }
        target
    }.onFailure { Napier.w("music staging failed for ${meta.file}: ${it.message}") }.getOrNull()
}
