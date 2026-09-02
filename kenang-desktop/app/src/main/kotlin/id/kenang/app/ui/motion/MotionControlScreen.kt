package id.kenang.app.ui.motion

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import id.kenang.app.ui.components.IMAGE_DROP_EXTENSIONS
import id.kenang.app.ui.components.filesDropTarget
import id.kenang.app.ui.components.rememberFileBitmap
import id.kenang.app.ui.theme.SkeuoButton
import id.kenang.app.ui.theme.SkeuoCard
import id.kenang.app.ui.theme.SkeuoOutlinedButton
import id.kenang.core.common.AppResult
import id.kenang.core.common.ErrorTranslator
import id.kenang.core.common.i18n.Strings
import id.kenang.core.data.ffmpeg.FfmpegLocator
import id.kenang.core.providers.motion.MotionControlService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import java.io.File

/**
 * Motion Control (owner 2026-09-02): photo + reference motion video → the
 * photo's character performs the reference movements (Kling 3.0 Pro).
 */
@Composable
fun MotionControlScreen(
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
) {
    val service = koinInject<MotionControlService>()
    val ffmpegLocator = koinInject<FfmpegLocator>()
    val scope = rememberCoroutineScope()

    var photo by remember { mutableStateOf<File?>(null) }
    var video by remember { mutableStateOf<File?>(null) }
    var videoDurationS by remember { mutableStateOf<Double?>(null) }
    var orientation by remember { mutableStateOf("video") }
    var prompt by remember { mutableStateOf("") }
    var keepSound by remember { mutableStateOf(true) }
    var running by remember { mutableStateOf(false) }
    var elapsed by remember { mutableStateOf(0) }
    var resultFile by remember { mutableStateOf<File?>(null) }
    var dragOver by remember { mutableStateOf(false) }

    fun setVideo(file: File) {
        video = file
        videoDurationS = null
        scope.launch {
            videoDurationS = withContext(Dispatchers.IO) { probeDurationSeconds(ffmpegLocator, file) }
        }
    }

    // Drop anywhere (owner 2026-09-02): images become the character photo,
    // videos become the motion reference — both in one drop if mixed.
    fun acceptDropped(files: List<File>) {
        files.firstOrNull { it.extension.lowercase() in IMAGE_DROP_EXTENSIONS }?.let { photo = it }
        files.firstOrNull { it.extension.lowercase() in MotionControlService.VIDEO_EXTENSIONS }
            ?.let { setVideo(it) }
    }

    LaunchedEffect(running) {
        elapsed = 0
        while (running) {
            kotlinx.coroutines.delay(1000)
            elapsed++
        }
    }

    Column(
        Modifier.fillMaxSize()
            .filesDropTarget(enabled = !running, onHover = { dragOver = it }) { acceptDropped(it) }
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(Strings.MOTION_TITLE, style = MaterialTheme.typography.headlineSmall)
                Text(
                    Strings.MOTION_SUBTITLE,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            Spacer(Modifier.width(16.dp))
            SkeuoOutlinedButton(onClick = onBack) { Text(Strings.BACK) }
        }
        Text(
            "🖱  " + Strings.MOTION_DROP_HINT,
            style = MaterialTheme.typography.labelMedium,
            color = if (dragOver) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )
        Spacer(Modifier.height(12.dp))

        // ---------- Inputs: photo + reference video ----------
        val dropBorder = if (dragOver) {
            Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(24.dp))
        } else {
            Modifier
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SkeuoCard(Modifier.weight(1f).then(dropBorder)) {
                Column(
                    Modifier.fillMaxWidth().clickable(enabled = !running) {
                        pickFile(Strings.MOTION_PICK_PHOTO, "Foto (JPG, PNG, WebP)",
                            "jpg", "jpeg", "png", "webp")?.let { photo = it }
                    }.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.fillMaxWidth().height(160.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        val bmp by rememberFileBitmap(photo?.absolutePath)
                        bmp?.let { Image(it, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
                            ?: Text("🖼", style = MaterialTheme.typography.headlineLarge)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("📷  " + (photo?.name ?: Strings.MOTION_PICK_PHOTO),
                        style = MaterialTheme.typography.titleSmall)
                    Text(
                        Strings.MOTION_PHOTO_NOTE,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }
            }
            SkeuoCard(Modifier.weight(1f).then(dropBorder)) {
                Column(
                    Modifier.fillMaxWidth().clickable(enabled = !running) {
                        pickFile(
                            Strings.MOTION_PICK_VIDEO,
                            "Video (${MotionControlService.VIDEO_EXTENSIONS.joinToString(", ") { it.uppercase() }})",
                            *MotionControlService.VIDEO_EXTENSIONS.toTypedArray(),
                        )?.let { setVideo(it) }
                    }.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.fillMaxWidth().height(160.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(if (video == null) "🎬" else "🎬 ✓", style = MaterialTheme.typography.headlineLarge)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("🎬  " + (video?.name ?: Strings.MOTION_PICK_VIDEO),
                        style = MaterialTheme.typography.titleSmall)
                    val cap = service.capSeconds(orientation)
                    val durText = when {
                        video == null -> Strings.MOTION_VIDEO_NOTE
                        videoDurationS == null -> Strings.MOTION_DURATION_UNKNOWN
                        else -> {
                            val d = videoDurationS!!
                            Strings.MOTION_DURATION_PREFIX + "%.1f dtk".format(d) +
                                if (d > cap) Strings.MOTION_DURATION_CAPPED.replace("%1", cap.toString()) else ""
                        }
                    }
                    Text(
                        durText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        // ---------- Orientation ----------
        Text(Strings.MOTION_ORIENTATION, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = orientation == "video",
                onClick = { orientation = "video" },
                label = { Text(Strings.MOTION_ORIENT_VIDEO.replace("%1", service.config().maxSVideo.toString())) },
                enabled = !running,
            )
            FilterChip(
                selected = orientation == "image",
                onClick = { orientation = "image" },
                label = { Text(Strings.MOTION_ORIENT_IMAGE.replace("%1", service.config().maxSImage.toString())) },
                enabled = !running,
            )
        }
        Text(
            if (orientation == "video") Strings.MOTION_ORIENT_VIDEO_DESC else Strings.MOTION_ORIENT_IMAGE_DESC,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it.take(300) },
            label = { Text(Strings.MOTION_PROMPT_LABEL) },
            placeholder = { Text(Strings.MOTION_PROMPT_HINT) },
            singleLine = true,
            enabled = !running,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Switch(checked = keepSound, onCheckedChange = { keepSound = it }, enabled = !running)
            Text(Strings.MOTION_KEEP_SOUND, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(16.dp))

        // ---------- Start ----------
        val est = service.estimateUsd(videoDurationS, orientation)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SkeuoButton(
                onClick = {
                    val p = photo
                    val v = video
                    if (p == null || v == null) {
                        scope.launch { snackbar.showSnackbar(Strings.MOTION_NEED_BOTH) }
                    } else {
                        scope.launch {
                            running = true
                            resultFile = null
                            when (val r = service.run(p, v, orientation, prompt, keepSound, videoDurationS)) {
                                is AppResult.Ok -> resultFile = r.value.file
                                is AppResult.Err ->
                                    snackbar.showSnackbar(ErrorTranslator.translate(r.error).message)
                            }
                            running = false
                        }
                    }
                },
                enabled = !running && photo != null && video != null,
            ) {
                Text(Strings.MOTION_START + " — " + Strings.ESTIMATE_LABEL + " ±$" + "%.2f".format(est))
            }
            if (running) {
                CircularProgressIndicator(Modifier.width(20.dp).height(20.dp))
                Text(
                    Strings.MOTION_RUNNING + "  (${elapsed}s)",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        if (running) {
            Spacer(Modifier.height(8.dp))
            // No percent from fal — track elapsed against a ~6-minute typical render.
            LinearProgressIndicator(
                progress = { (elapsed / 360f).coerceAtMost(0.95f) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            Strings.MOTION_RESULT_NOTE.replace("%1", service.outputDir().absolutePath),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )

        // ---------- Result ----------
        resultFile?.let { file ->
            Spacer(Modifier.height(16.dp))
            SkeuoCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(Strings.MOTION_DONE_PREFIX + file.absolutePath,
                        style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SkeuoButton(onClick = {
                            runCatching { java.awt.Desktop.getDesktop().open(file) }
                        }) { Text("▶  " + Strings.MOTION_PLAY) }
                        SkeuoOutlinedButton(onClick = {
                            runCatching { java.awt.Desktop.getDesktop().open(file.parentFile) }
                        }) { Text(Strings.MOTION_OPEN_FOLDER) }
                    }
                }
            }
        }
    }
}

/** Single-file chooser (same JFileChooser pattern as KI-013). */
private fun pickFile(title: String, filterLabel: String, vararg extensions: String): File? {
    val chooser = javax.swing.JFileChooser().apply {
        dialogTitle = title
        isMultiSelectionEnabled = false
        fileSelectionMode = javax.swing.JFileChooser.FILES_ONLY
        fileFilter = javax.swing.filechooser.FileNameExtensionFilter(filterLabel, *extensions)
    }
    return if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile
    } else {
        null
    }
}

/**
 * Reference-video duration via the bundled ffmpeg (`-i` prints
 * "Duration: HH:MM:SS.cc" on stderr; no ffprobe is shipped). Null = unknown,
 * the estimate then uses the orientation's cap.
 */
private fun probeDurationSeconds(locator: FfmpegLocator, video: File): Double? = runCatching {
    val exe = locator.locate() ?: return null
    val proc = ProcessBuilder(exe.absolutePath, "-i", video.absolutePath)
        .redirectErrorStream(true).start()
    val out = proc.inputStream.bufferedReader().readText()
    proc.waitFor(15, java.util.concurrent.TimeUnit.SECONDS)
    val m = Regex("Duration: (\\d+):(\\d+):(\\d+)\\.(\\d+)").find(out) ?: return null
    val (h, min, s, cs) = m.destructured
    h.toDouble() * 3600 + min.toDouble() * 60 + s.toDouble() + "0.$cs".toDouble()
}.getOrNull()
