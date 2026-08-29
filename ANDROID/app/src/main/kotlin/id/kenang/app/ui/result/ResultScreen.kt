@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package id.kenang.app.ui.result
import id.kenang.app.ui.theme.SkeuoButton
import id.kenang.app.ui.theme.SkeuoCard
import id.kenang.app.ui.theme.SkeuoOutlinedButton

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import id.kenang.app.ui.components.rememberFileBitmap
import id.kenang.app.ui.platform.AndroidActions
import id.kenang.core.common.i18n.Strings
import id.kenang.core.data.OutputRepository
import id.kenang.core.data.ProjectRepository
import id.kenang.core.data.media.MediaProbe
import id.kenang.core.db.Output
import id.kenang.core.db.Project
import id.kenang.core.providers.PriceBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import java.io.File
import java.util.Locale

/**
 * §4.4 result screen. Unlike desktop (thumbnail + system player), Android
 * plays the finished video inline with ExoPlayer, and the file is already in
 * the gallery — so "Simpan Sebagai…"/"Salin Lokasi" become "Bagikan" and
 * "Buka Galeri".
 */
@Composable
fun ResultScreen(
    projectId: String,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
) {
    val outputs = koinInject<OutputRepository>()
    val projects = koinInject<ProjectRepository>()
    val priceBook = koinInject<PriceBook>()
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    var output by remember { mutableStateOf<Output?>(null) }
    var project by remember { mutableStateOf<Project?>(null) }
    var durationText by remember { mutableStateOf("–") }

    LaunchedEffect(projectId) {
        project = projects.get(projectId)
        output = outputs.latest(projectId)
        output?.let { out ->
            val file = File(out.path)
            if (file.isFile) {
                MediaProbe.durationMs(file)?.let { ms ->
                    durationText = "%d:%02d".format(ms / 60000, (ms / 1000) % 60)
                }
            }
        }
    }

    val out = output
    val file = out?.let { File(it.path) }

    Column(
        Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(Strings.RESULT_TITLE, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            Strings.RESULT_AI_NOTE,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        )
        Spacer(Modifier.height(16.dp))

        val thumbPath = file?.let { f ->
            val perOutput = File(f.parentFile, f.nameWithoutExtension + "_thumb.jpg")
            (if (perOutput.isFile) perOutput else File(f.parentFile, "thumbnail.jpg"))
                .takeIf { it.isFile }?.absolutePath
        }
        val bmp by rememberFileBitmap(thumbPath)
        Box(
            Modifier.widthIn(max = 420.dp).fillMaxWidth().heightIn(min = 240.dp, max = 420.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            // Inline playback with controls; falls back to the poster frame
            // until the file exists.
            if (file?.isFile == true) {
                VideoPlayer(file, Modifier.fillMaxSize())
            } else {
                bmp?.let { Image(it, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
            }
        }
        Spacer(Modifier.height(12.dp))

        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SkeuoButton(
                onClick = { file?.let { AndroidActions.playVideo(context, it) } },
                enabled = file?.isFile == true,
            ) { Text("▶  " + Strings.RESULT_PLAY) }
            SkeuoOutlinedButton(
                onClick = { file?.let { AndroidActions.shareVideo(context, it) } },
                enabled = file?.isFile == true,
            ) { Text(Strings.RESULT_SHARE) }
            SkeuoOutlinedButton(
                onClick = { AndroidActions.openGallery(context) },
                enabled = file != null,
            ) { Text(Strings.RESULT_OPEN_GALLERY) }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            Strings.RESULT_GALLERY_NOTE,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        )

        Spacer(Modifier.height(20.dp))
        SkeuoCard(Modifier.widthIn(max = 480.dp).fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StatRow(Strings.RESULT_DURATION, durationText)
                StatRow(
                    Strings.RESULT_SIZE,
                    file?.takeIf { it.isFile }?.let { "%.1f MB".format(Locale.US, it.length() / 1e6) } ?: "–",
                )
                StatRow(Strings.RESULT_TIER, out?.tier?.replaceFirstChar { it.uppercase() } ?: "–")
                StatRow(
                    Strings.RESULT_COST + " (${Strings.ESTIMATE_LABEL})",
                    out?.let {
                        "$%.2f ≈ Rp%,.0f".format(Locale.US, it.est_cost_usd, it.est_cost_usd * priceBook.fxIdr())
                    } ?: "–",
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        // PRD F6.5 activated: re-assemble the SAME clips in the other ratio —
        // pure local FFmpeg, no API spend.
        run {
            val assembly = koinInject<id.kenang.core.providers.gen.AssemblyService>()
            var exporting by remember { mutableStateOf(false) }
            var exportProgress by remember { mutableStateOf(0) }
            val otherRatio = if ((out?.ratio ?: "9:16") == "9:16") "16:9" else "9:16"
            SkeuoOutlinedButton(
                onClick = {
                    scope.launch {
                        exporting = true
                        exportProgress = 0
                        val prep = assembly.prepareAudio(projectId)
                        val (narr, tempo) = when (prep) {
                            is id.kenang.core.providers.gen.AssemblyService.AudioPrep.Ready ->
                                prep.narration to null
                            is id.kenang.core.providers.gen.AssemblyService.AudioPrep.DurationMismatch ->
                                prep.narration to 1.1
                            is id.kenang.core.providers.gen.AssemblyService.AudioPrep.Failed -> null to null
                        }
                        val r = assembly.assemble(
                            projectId, narr, includeSubtitles = true,
                            narrationTempo = tempo, ratioOverride = otherRatio,
                        ) { p -> exportProgress = p }
                        exporting = false
                        when (r) {
                            is id.kenang.core.common.AppResult.Ok -> {
                                output = outputs.latest(projectId)
                                output?.path?.let(::File)?.takeIf { it.isFile }?.let { f ->
                                    MediaProbe.durationMs(f)?.let { ms ->
                                        durationText = "%d:%02d".format(ms / 60000, (ms / 1000) % 60)
                                    }
                                }
                                snackbar.showSnackbar(
                                    Strings.RESULT_OTHER_RATIO_DONE.replace("%1", otherRatio),
                                )
                            }
                            is id.kenang.core.common.AppResult.Err -> snackbar.showSnackbar(
                                id.kenang.core.common.ErrorTranslator.translate(r.error).message,
                            )
                        }
                    }
                },
                enabled = file?.isFile == true && !exporting,
            ) {
                Text(
                    if (exporting) {
                        Strings.RESULT_OTHER_RATIO_RUNNING
                            .replace("%1", otherRatio).replace("%2", exportProgress.toString())
                    } else {
                        Strings.RESULT_OTHER_RATIO.replace("%1", otherRatio)
                    },
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                Strings.RESULT_OTHER_RATIO_NOTE,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onBack) { Text(Strings.BACK) }
    }
}

/** ExoPlayer surface with standard controls, released with the composition. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun VideoPlayer(file: File, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val player = remember(file.absolutePath) {
        androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
            setMediaItem(androidx.media3.common.MediaItem.fromUri(android.net.Uri.fromFile(file)))
            prepare()
        }
    }
    androidx.compose.runtime.DisposableEffect(player) {
        onDispose { player.release() }
    }
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { ctx ->
            androidx.media3.ui.PlayerView(ctx).apply {
                this.player = player
                useController = true
                setShowNextButton(false)
                setShowPreviousButton(false)
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.width(240.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
