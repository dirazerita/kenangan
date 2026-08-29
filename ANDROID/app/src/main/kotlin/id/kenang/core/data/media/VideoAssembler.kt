@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package id.kenang.core.data.media

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import id.kenang.core.common.AppError
import id.kenang.core.common.AppResult
import id.kenang.core.common.err
import id.kenang.core.common.ok
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/**
 * Android video assembly. The desktop build shells out to a bundled FFmpeg;
 * phones have no such binary, so the final MP4 is produced by Media3
 * Transformer instead:
 *
 *  - the scene clips are concatenated into one video sequence, each presented
 *    at the target ratio (scale + crop) so the output is uniform;
 *  - narration plays as its own audio sequence, optionally sped up by the
 *    F5.3 tempo fix (SonicAudioProcessor) and clipped to the video length;
 *  - background music loops underneath at a fixed low gain.
 *
 * Two desktop effects have no Transformer equivalent and are deliberately
 * absent here (documented in ANDROID/README.md): the 0.6s xfade between
 * scenes (Android hard-cuts) and burned-in subtitles.
 */
class VideoAssembler(private val context: Context) {

    enum class Ratio(val w: Int, val h: Int, val label: String) {
        V9X16(1080, 1920, "9:16"),
        H16X9(1920, 1080, "16:9");

        companion object {
            fun fromLabel(label: String): Ratio =
                entries.firstOrNull { it.label == label } ?: V9X16
        }
    }

    data class ClipSpec(val file: File, val durationS: Double)

    data class AssemblySpec(
        val clips: List<ClipSpec>,
        val ratio: Ratio,
        val output: File,
        val musicFile: File? = null,
        val narrationFile: File? = null,
        /** F5.3 tempo fix, e.g. 1.1 to speed narration up by 10%. */
        val narrationTempo: Double? = null,
    )

    /** Music sits well under the narration; the desktop graph ducks dynamically. */
    private val musicGain = 0.18f

    suspend fun assemble(spec: AssemblySpec, onProgress: (Int) -> Unit = {}): AppResult<File> {
        if (spec.clips.isEmpty()) return AppError.AssemblyFailed("no clips to assemble").err()
        val missing = spec.clips.firstOrNull { !it.file.isFile }
        if (missing != null) return AppError.AssemblyFailed("clip file gone: ${missing.file.name}").err()

        spec.output.parentFile?.mkdirs()
        if (spec.output.exists()) spec.output.delete()

        // Composition length is driven by the video sequence; audio is clipped
        // to it so a long narration can never stretch the output.
        val videoDurationMs = spec.clips.sumOf { (it.durationS * 1000).toLong() }

        // Transformer needs a Looper thread: build, start and poll on Main.
        return withContext(Dispatchers.Main) {
            val composition = buildComposition(spec, videoDurationMs)
            suspendCancellableCoroutine { cont ->
                val transformer = Transformer.Builder(context)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, result: ExportResult) {
                            onProgress(100)
                            if (cont.isActive) cont.resume(spec.output.ok())
                        }

                        override fun onError(
                            composition: Composition,
                            result: ExportResult,
                            exception: ExportException,
                        ) {
                            Napier.e("Transformer failed: ${exception.errorCode} ${exception.message}")
                            if (cont.isActive) {
                                cont.resume(
                                    AppError.AssemblyFailed(
                                        "Transformer error ${exception.errorCode}: ${exception.message}",
                                    ).err(),
                                )
                            }
                        }
                    })
                    .build()

                val progressJob = kotlinx.coroutines.MainScope().launch {
                    val holder = ProgressHolder()
                    while (isActive) {
                        val state = transformer.getProgress(holder)
                        if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                            onProgress(holder.progress.coerceIn(0, 99))
                        }
                        delay(400)
                    }
                }

                cont.invokeOnCancellation {
                    progressJob.cancel()
                    runCatching { transformer.cancel() }
                }

                runCatching { transformer.start(composition, spec.output.absolutePath) }
                    .onFailure { e ->
                        progressJob.cancel()
                        if (cont.isActive) {
                            cont.resume(AppError.AssemblyFailed("start failed: ${e.message}").err())
                        }
                    }
            }
        }
    }

    private fun buildComposition(spec: AssemblySpec, videoDurationMs: Long): Composition {
        val presentation = Presentation.createForWidthAndHeight(
            spec.ratio.w,
            spec.ratio.h,
            Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP,
        )
        val videoItems = spec.clips.map { clip ->
            EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(clip.file)))
                // Kling/Wan clips are generated silent, but a stray audio track
                // would fight the narration mix — drop it either way.
                .setRemoveAudio(true)
                .setEffects(Effects(emptyList(), listOf(presentation)))
                .build()
        }
        val sequences = mutableListOf(EditedMediaItemSequence.Builder(videoItems).build())

        spec.narrationFile?.takeIf { it.isFile }?.let { file ->
            val processors = spec.narrationTempo
                ?.takeIf { it > 1.0 }
                ?.let { tempo -> listOf(SonicAudioProcessor().apply { setSpeed(tempo.toFloat()) }) }
                ?: emptyList()
            val item = MediaItem.Builder()
                .setUri(Uri.fromFile(file))
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setEndPositionMs(videoDurationMs)
                        .build(),
                )
                .build()
            sequences += EditedMediaItemSequence.Builder(
                EditedMediaItem.Builder(item)
                    .setRemoveVideo(true)
                    .setEffects(Effects(processors, emptyList()))
                    .build(),
            ).build()
        }

        spec.musicFile?.takeIf { it.isFile }?.let { file ->
            val quieter = ChannelMixingAudioProcessor().apply {
                putChannelMixingMatrix(ChannelMixingMatrix.create(1, 1).scaleBy(musicGain))
                putChannelMixingMatrix(ChannelMixingMatrix.create(2, 2).scaleBy(musicGain))
            }
            sequences += EditedMediaItemSequence.Builder(
                EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(file)))
                    .setRemoveVideo(true)
                    .setEffects(Effects(listOf(quieter), emptyList()))
                    .build(),
            ).setIsLooping(true).build()
        }

        return Composition.Builder(sequences).build()
    }
}
