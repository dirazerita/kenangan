@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package id.kenang.app.ui.talking

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import id.kenang.core.providers.talking.TalkingVideoService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import java.io.File

/**
 * Video Berbicara (owner 2026-09-13): photo + script (+ optional voice
 * recording) -> the photo speaks the script. Android twin of the desktop
 * screen: system pickers instead of drag & drop, results also land in the
 * gallery.
 */
@Composable
fun TalkingVideoScreen(
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
) {
    val service = koinInject<TalkingVideoService>()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var modelKey by remember { mutableStateOf(service.selected().selectionKey()) }
    var photo by remember { mutableStateOf<File?>(null) }
    var script by remember { mutableStateOf("") }
    var useVoiceFile by remember { mutableStateOf(false) }
    var voiceFile by remember { mutableStateOf<File?>(null) }
    var voiceId by remember { mutableStateOf(service.defaultVoiceId()) }
    var running by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf<TalkingVideoService.Phase?>(null) }
    var elapsed by remember { mutableStateOf(0) }
    var result by remember { mutableStateOf<TalkingVideoService.TalkingResult?>(null) }
    // Script: written by hand, or by the AI from a theme (owner 2026-09-13).
    var aiScript by remember { mutableStateOf(false) }
    var theme by remember { mutableStateOf("") }
    var scriptSeconds by remember { mutableStateOf(TalkingVideoService.SCRIPT_LENGTHS[1]) }
    var writing by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                photo = withContext(Dispatchers.IO) {
                    runCatching {
                        val f = File(context.cacheDir, "talking_photo.jpg")
                        context.contentResolver.openInputStream(uri)!!.use { input ->
                            f.outputStream().use { input.copyTo(it) }
                        }
                        f
                    }.getOrNull()
                }
            }
        }
    }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                voiceFile = withContext(Dispatchers.IO) {
                    runCatching {
                        val mime = context.contentResolver.getType(uri) ?: "audio/mpeg"
                        val ext = when {
                            mime.contains("wav") -> "wav"
                            mime.contains("mp4") || mime.contains("m4a") -> "m4a"
                            mime.contains("aac") -> "aac"
                            mime.contains("ogg") -> "ogg"
                            mime.contains("flac") -> "flac"
                            else -> "mp3"
                        }
                        // Named after the source so the clone cache recognises the same file.
                        val name = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.')
                            ?.replace(Regex("[^A-Za-z0-9_\\- ]"), "")?.take(40)?.ifBlank { null } ?: "suara"
                        val f = File(context.cacheDir, "$name.$ext")
                        context.contentResolver.openInputStream(uri)!!.use { input ->
                            f.outputStream().use { input.copyTo(it) }
                        }
                        f
                    }.getOrNull()
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

    val option = service.options().firstOrNull { it.selectionKey() == modelKey } ?: service.selected()
    val newClone = useVoiceFile && voiceFile != null && service.existingClone(voiceFile!!) == null
    val est = service.estimate(script.trim().length, option, withNewClone = newClone)

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(Strings.TALK_TITLE, style = MaterialTheme.typography.headlineSmall)
                Text(
                    Strings.TALK_SUBTITLE,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            Spacer(Modifier.width(12.dp))
            SkeuoOutlinedButton(onClick = onBack) { Text(Strings.BACK) }
        }
        Spacer(Modifier.height(12.dp))

        // ---------- Photo ----------
        SkeuoCard(Modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().clickable(enabled = !running) { photoPicker.launch("image/*") }.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.fillMaxWidth().height(200.dp).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    val bmp by rememberFileBitmap(photo?.absolutePath)
                    bmp?.let { Image(it, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
                        ?: Text("🖼", style = MaterialTheme.typography.headlineLarge)
                }
                Spacer(Modifier.height(8.dp))
                Text("📷  " + (photo?.name ?: Strings.TALK_PICK_PHOTO), style = MaterialTheme.typography.titleSmall)
                Text(
                    Strings.TALK_PHOTO_NOTE,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        // ---------- Voice ----------
        SkeuoCard(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                Text(Strings.TALK_VOICE_TITLE, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !useVoiceFile, onClick = { useVoiceFile = false }, label = { Text(Strings.TALK_VOICE_PRESET) }, enabled = !running)
                    FilterChip(selected = useVoiceFile, onClick = { useVoiceFile = true }, label = { Text(Strings.TALK_VOICE_FILE) }, enabled = !running)
                }
                Spacer(Modifier.height(8.dp))
                if (useVoiceFile) {
                    Box(
                        Modifier.fillMaxWidth().height(80.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable(enabled = !running) { audioPicker.launch("audio/*") },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (voiceFile == null) "🎤  " + Strings.TALK_VOICE_FILE_PICK
                            else "🎤  " + voiceFile!!.name + (if (!newClone) Strings.TALK_VOICE_CLONED_TAG else ""),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        Strings.TALK_VOICE_FILE_NOTE.replace("%1", "%.2f".format(service.estimate(0, option, true).cloneUsd)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    )
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        service.voices().forEach { v ->
                            FilterChip(
                                selected = voiceId == v.id,
                                onClick = { voiceId = v.id },
                                label = { Text((if (v.cloned) "🎤 " else "") + v.label, style = MaterialTheme.typography.labelMedium) },
                                enabled = !running,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // ---------- Script: manual or written by the AI ----------
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !aiScript,
                onClick = { aiScript = false },
                label = { Text(Strings.TALK_SCRIPT_TAB_MANUAL) },
                enabled = !running && !writing,
            )
            FilterChip(
                selected = aiScript,
                onClick = { aiScript = true },
                label = { Text(Strings.TALK_SCRIPT_TAB_AI) },
                enabled = !running && !writing,
            )
        }
        if (aiScript) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = theme,
                onValueChange = { theme = it.take(300) },
                label = { Text(Strings.TALK_THEME_LABEL) },
                placeholder = { Text(Strings.TALK_THEME_HINT) },
                minLines = 2,
                enabled = !running && !writing,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TalkingVideoService.SCRIPT_LENGTHS.forEach { secs ->
                    FilterChip(
                        selected = scriptSeconds == secs,
                        onClick = { scriptSeconds = secs },
                        label = { Text("±$secs dtk") },
                        enabled = !running && !writing,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            SkeuoOutlinedButton(
                onClick = {
                    if (theme.isBlank()) {
                        scope.launch { snackbar.showSnackbar(Strings.TALK_NEED_THEME) }
                    } else {
                        scope.launch {
                            writing = true
                            when (val r = service.writeScript(theme, photo, scriptSeconds)) {
                                is AppResult.Ok -> script = r.value
                                is AppResult.Err ->
                                    snackbar.showSnackbar(ErrorTranslator.translate(r.error).message)
                            }
                            writing = false
                        }
                    }
                },
                enabled = !running && !writing,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (writing) Strings.TALK_WRITING else if (script.isBlank()) Strings.TALK_WRITE else Strings.TALK_REWRITE) }
            Text(
                Strings.TALK_AI_NOTE,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = script,
            onValueChange = { script = it.take(TalkingVideoService.MAX_CHARS) },
            label = { Text(Strings.TALK_SCRIPT_LABEL) },
            placeholder = { Text(Strings.TALK_SCRIPT_HINT.replace("%1", TalkingVideoService.MAX_CHARS.toString())) },
            minLines = 4,
            enabled = !running && !writing,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "${script.trim().length}/${TalkingVideoService.MAX_CHARS}  ·  " +
                Strings.TALK_ESTIMATE_NOTE
                    .replace("%1", "%.0f".format(est.seconds))
                    .replace("%2", "%.2f".format(est.videoUsd))
                    .replace("%3", "%.2f".format(est.ttsUsd)) +
                (if (newClone) "  + kloning $" + "%.2f".format(est.cloneUsd) else ""),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        )
        Spacer(Modifier.height(12.dp))

        // ---------- Model ----------
        Text(Strings.TALK_MODEL_LABEL, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            service.options().forEach { o ->
                FilterChip(
                    selected = modelKey == o.selectionKey(),
                    onClick = { service.select(o.selectionKey()); modelKey = o.selectionKey() },
                    label = {
                        Text(
                            (if (o.tested) "" else "◦ ") + o.labelId + "  ·  $" + "%.3f".format(service.pricePerSecondOf(o)) + "/dtk",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    },
                    enabled = !running,
                )
            }
        }
        option.descId?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
        }
        Spacer(Modifier.height(16.dp))

        // ---------- Start ----------
        SkeuoButton(
            onClick = {
                val p = photo
                when {
                    p == null || script.isBlank() -> scope.launch { snackbar.showSnackbar(Strings.TALK_NEED_INPUT) }
                    useVoiceFile && voiceFile == null -> scope.launch { snackbar.showSnackbar(Strings.TALK_NEED_VOICE_FILE) }
                    else -> scope.launch {
                        running = true
                        result = null
                        val r = service.run(
                            p, script,
                            voiceId = if (useVoiceFile) null else voiceId,
                            voiceSample = if (useVoiceFile) voiceFile else null,
                            option = option,
                        ) { phase = it }
                        when (r) {
                            is AppResult.Ok -> result = r.value
                            is AppResult.Err -> snackbar.showSnackbar(ErrorTranslator.translate(r.error).message)
                        }
                        running = false
                        phase = null
                    }
                }
            },
            enabled = !running && photo != null && script.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(Strings.TALK_START + " — " + Strings.ESTIMATE_LABEL + " ±$" + "%.2f".format(est.totalUsd))
        }
        if (running) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.width(18.dp).height(18.dp))
                Text(
                    when (phase) {
                        TalkingVideoService.Phase.CLONING -> Strings.TALK_PHASE_CLONING
                        TalkingVideoService.Phase.SPEAKING -> Strings.TALK_PHASE_SPEAKING
                        TalkingVideoService.Phase.RENDERING -> Strings.TALK_PHASE_RENDERING
                        TalkingVideoService.Phase.SAVING -> Strings.TALK_PHASE_SAVING
                        null -> ""
                    } + "  (${elapsed}s)",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(progress = { (elapsed / 300f).coerceAtMost(0.95f) }, modifier = Modifier.fillMaxWidth())
        }

        // ---------- Result ----------
        result?.let { r ->
            Spacer(Modifier.height(16.dp))
            SkeuoCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(Strings.TALK_DONE_PREFIX + r.video.name, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "%.1f dtk · suara: %s · ±$%.2f".format(r.durationS, r.voiceLabel, r.usd),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SkeuoButton(onClick = { AndroidActions.playVideo(context, r.video) }) { Text("▶  " + Strings.MOTION_PLAY) }
                        SkeuoOutlinedButton(onClick = { AndroidActions.shareVideo(context, r.video) }) { Text(Strings.RESULT_SHARE) }
                    }
                }
            }
        }
    }
}
