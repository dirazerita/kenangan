package id.kenang.app.ui.motion

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import id.kenang.app.ui.components.rememberFileBitmap
import id.kenang.app.ui.platform.AndroidActions
import id.kenang.app.ui.theme.SkeuoButton
import id.kenang.app.ui.theme.SkeuoCard
import id.kenang.app.ui.theme.SkeuoOutlinedButton
import id.kenang.core.common.AppResult
import id.kenang.core.common.ErrorTranslator
import id.kenang.core.common.i18n.Strings
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var modelKey by remember { mutableStateOf(service.selected().selectionKey()) }
    var photo by remember { mutableStateOf<File?>(null) }
    var video by remember { mutableStateOf<File?>(null) }
    var videoDurationS by remember { mutableStateOf<Double?>(null) }
    var orientation by remember { mutableStateOf("video") }
    var prompt by remember { mutableStateOf("") }
    var keepSound by remember { mutableStateOf(true) }
    var running by remember { mutableStateOf(false) }
    var elapsed by remember { mutableStateOf(0) }
    var resultFile by remember { mutableStateOf<File?>(null) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                photo = withContext(Dispatchers.IO) {
                    runCatching {
                        val f = File(context.cacheDir, "motion_photo.jpg")
                        context.contentResolver.openInputStream(uri)!!.use { input ->
                            f.outputStream().use { input.copyTo(it) }
                        }
                        f
                    }.getOrNull()
                }
            }
        }
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val copied = withContext(Dispatchers.IO) {
                    runCatching {
                        val f = File(context.cacheDir, "motion_ref.mp4")
                        context.contentResolver.openInputStream(uri)!!.use { input ->
                            f.outputStream().use { input.copyTo(it) }
                        }
                        f
                    }.getOrNull()
                }
                video = copied
                videoDurationS = copied?.let {
                    withContext(Dispatchers.IO) {
                        runCatching {
                            val mmr = android.media.MediaMetadataRetriever()
                            mmr.setDataSource(it.absolutePath)
                            val ms = mmr.extractMetadata(
                                android.media.MediaMetadataRetriever.METADATA_KEY_DURATION,
                            )?.toDoubleOrNull()
                            mmr.release()
                            ms?.div(1000.0)
                        }.getOrNull()
                    }
                }
            }
        }
    }

    LaunchedEffect(running) {
        elapsed = 0
        while (running) {
            kotlinx.coroutines.delay(1000)
            elapsed++
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(Strings.MOTION_TITLE, style = MaterialTheme.typography.headlineSmall)
                Text(
                    Strings.MOTION_SUBTITLE,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            Spacer(Modifier.width(12.dp))
            SkeuoOutlinedButton(onClick = onBack) { Text(Strings.BACK) }
        }
        Spacer(Modifier.height(16.dp))

        SkeuoCard(Modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().clickable(enabled = !running) { photoPicker.launch("image/*") }
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.fillMaxWidth().height(150.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    val bmp by rememberFileBitmap(photo?.absolutePath)
                    bmp?.let { Image(it, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
                        ?: Text("🖼", style = MaterialTheme.typography.headlineLarge)
                }
                Spacer(Modifier.height(6.dp))
                Text("📷  " + (photo?.name ?: Strings.MOTION_PICK_PHOTO), style = MaterialTheme.typography.titleSmall)
                Text(
                    Strings.MOTION_PHOTO_NOTE,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        SkeuoCard(Modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().clickable(enabled = !running) { videoPicker.launch("video/*") }
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(if (video == null) "🎬" else "🎬 ✓", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(6.dp))
                Text("🎬  " + (video?.name ?: Strings.MOTION_PICK_VIDEO), style = MaterialTheme.typography.titleSmall)
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
        Spacer(Modifier.height(16.dp))

        // ---------- Model picker (owner 2026-09-02: user decides the model) ----------
        Text(Strings.MOTION_MODEL_LABEL, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            service.options().forEach { option ->
                val perS = service.pricePerSecondOf(option)
                FilterChip(
                    selected = modelKey == option.selectionKey(),
                    onClick = {
                        service.select(option.selectionKey())
                        modelKey = option.selectionKey()
                    },
                    label = {
                        Text(
                            (if (option.tested) "" else "◦ ") + option.labelId +
                                "  ·  $" + "%.3f".format(perS) + "/dtk",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                    enabled = !running,
                )
            }
        }
        Text(
            Strings.MOTION_MODEL_NOTE,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )
        Spacer(Modifier.height(12.dp))

        if (service.selectedIsKling()) {
            Text(Strings.MOTION_ORIENTATION, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
        }

        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it.take(300) },
            label = { Text(Strings.MOTION_PROMPT_LABEL) },
            placeholder = { Text(Strings.MOTION_PROMPT_HINT) },
            singleLine = true,
            enabled = !running,
            modifier = Modifier.fillMaxWidth(),
        )
        if (service.selectedIsKling()) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Switch(checked = keepSound, onCheckedChange = { keepSound = it }, enabled = !running)
                Text(Strings.MOTION_KEEP_SOUND, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(16.dp))

        val est = service.estimateUsd(videoDurationS, orientation)
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
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(Strings.MOTION_START + " — " + Strings.ESTIMATE_LABEL + " ±$" + "%.2f".format(est))
        }
        if (running) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.width(18.dp).height(18.dp))
                Text(Strings.MOTION_RUNNING + "  (${elapsed}s)", style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { (elapsed / 360f).coerceAtMost(0.95f) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        resultFile?.let { file ->
            Spacer(Modifier.height(16.dp))
            SkeuoCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(Strings.MOTION_DONE_PREFIX + file.name, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SkeuoButton(onClick = { AndroidActions.playVideo(context, file) }) {
                            Text("▶  " + Strings.MOTION_PLAY)
                        }
                        SkeuoOutlinedButton(onClick = { AndroidActions.shareVideo(context, file) }) {
                            Text(Strings.RESULT_SHARE)
                        }
                    }
                }
            }
        }
    }
}
