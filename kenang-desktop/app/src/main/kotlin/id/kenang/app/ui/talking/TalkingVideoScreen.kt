@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package id.kenang.app.ui.talking

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import id.kenang.app.ui.components.IMAGE_DROP_EXTENSIONS
import id.kenang.app.ui.components.PhotoImport
import id.kenang.app.ui.components.filesDropTarget
import id.kenang.app.ui.components.openVideoFile
import id.kenang.app.ui.components.rememberFileBitmap
import id.kenang.app.ui.theme.SkeuoButton
import id.kenang.app.ui.theme.SkeuoCard
import id.kenang.app.ui.theme.SkeuoOutlinedButton
import id.kenang.core.common.AppResult
import id.kenang.core.common.ErrorTranslator
import id.kenang.core.common.i18n.Strings
import id.kenang.core.providers.talking.TalkingVideoService
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.io.File

/**
 * Video Berbicara (owner 2026-09-13): photo + script (+ optional voice
 * recording) -> the photo speaks the script. Voice from a recording is
 * cloned once per file; otherwise a preset or previously cloned voice.
 */
@Composable
fun TalkingVideoScreen(
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
) {
    val service = koinInject<TalkingVideoService>()
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
    var dragOver by remember { mutableStateOf(false) }
    var outputDirText by remember { mutableStateOf(service.outputDir().absolutePath) }
    // Script: written by hand, or by the AI from a theme (owner 2026-09-13).
    var aiScript by remember { mutableStateOf(false) }
    var theme by remember { mutableStateOf("") }
    var scriptSeconds by remember { mutableStateOf(TalkingVideoService.SCRIPT_LENGTHS[1]) }
    var writing by remember { mutableStateOf(false) }
    // Who speaks when the photo holds more than one person (owner 2026-09-15).
    var speakers by remember { mutableStateOf<List<id.kenang.core.data.story.SpeakerCandidate>>(emptyList()) }
    var speaker by remember { mutableStateOf<id.kenang.core.data.story.SpeakerCandidate?>(null) }
    var checkingSpeakers by remember { mutableStateOf(false) }


    // Drop anywhere: images become the photo, audio becomes the voice sample.
    fun acceptDropped(files: List<File>) {
        files.firstOrNull { it.extension.lowercase() in IMAGE_DROP_EXTENSIONS }
            ?.let { photo = PhotoImport.normalize(it) }
        files.firstOrNull { it.extension.lowercase() in TalkingVideoService.AUDIO_EXTENSIONS }
            ?.let { voiceFile = it; useVoiceFile = true }
    }

    LaunchedEffect(running) {
        elapsed = 0
        while (running) {
            kotlinx.coroutines.delay(1000)
            elapsed++
        }
    }

    // One cheap vision call per chosen photo; the chooser only appears when
    // there is actually someone to choose between.
    LaunchedEffect(photo) {
        speakers = emptyList()
        speaker = null
        val p = photo ?: return@LaunchedEffect
        checkingSpeakers = true
        speakers = when (val found = service.speakers(p)) {
            is AppResult.Ok -> found.value
            is AppResult.Err -> emptyList()
        }
        checkingSpeakers = false
    }

    val option = service.options().firstOrNull { it.selectionKey() == modelKey } ?: service.selected()
    val newClone = useVoiceFile && voiceFile != null && service.existingClone(voiceFile!!) == null
    val est = service.estimate(script.trim().length, option, withNewClone = newClone)

    Column(
        Modifier.fillMaxSize()
            .filesDropTarget(enabled = !running, onHover = { dragOver = it }) { acceptDropped(it) }
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(Strings.TALK_TITLE, style = MaterialTheme.typography.headlineSmall)
                Text(
                    Strings.TALK_SUBTITLE,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            Spacer(Modifier.width(16.dp))
            SkeuoOutlinedButton(onClick = onBack) { Text(Strings.BACK) }
        }
        Text(
            "🖱  " + Strings.TALK_DROP_HINT,
            style = MaterialTheme.typography.labelMedium,
            color = if (dragOver) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )
        Spacer(Modifier.height(12.dp))

        val dropBorder = if (dragOver) {
            Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(24.dp))
        } else Modifier

        // ---------- Photo + voice ----------
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SkeuoCard(Modifier.weight(1f).then(dropBorder)) {
                Column(
                    Modifier.fillMaxWidth().clickable(enabled = !running) {
                        pickFile(Strings.TALK_PICK_PHOTO, "Foto (JPG, PNG, WebP, HEIC, AVIF)", *IMAGE_DROP_EXTENSIONS.toTypedArray())
                            ?.let { photo = PhotoImport.normalize(it) }
                    }.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.fillMaxWidth().height(180.dp).background(MaterialTheme.colorScheme.surfaceVariant),
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

            SkeuoCard(Modifier.weight(1f).then(dropBorder)) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(Strings.TALK_VOICE_TITLE, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !useVoiceFile,
                            onClick = { useVoiceFile = false },
                            label = { Text(Strings.TALK_VOICE_PRESET) },
                            enabled = !running,
                        )
                        FilterChip(
                            selected = useVoiceFile,
                            onClick = { useVoiceFile = true },
                            label = { Text(Strings.TALK_VOICE_FILE) },
                            enabled = !running,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    if (useVoiceFile) {
                        Box(
                            Modifier.fillMaxWidth().height(96.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable(enabled = !running) {
                                    pickFile(
                                        Strings.TALK_VOICE_FILE_PICK,
                                        "Audio (" + TalkingVideoService.AUDIO_EXTENSIONS.joinToString(", ") { it.uppercase() } + ")",
                                        *TalkingVideoService.AUDIO_EXTENSIONS.toTypedArray(),
                                    )?.let { voiceFile = it }
                                },
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
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            service.voices().forEach { v ->
                                FilterChip(
                                    selected = voiceId == v.id,
                                    onClick = { voiceId = v.id },
                                    label = {
                                        Text(
                                            (if (v.cloned) "🎤 " else "") + v.label,
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                    },
                                    enabled = !running,
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        // ---------- Who speaks (group photos) ----------
        if (checkingSpeakers || speakers.size > 1) {
            Spacer(Modifier.height(12.dp))
            Text(Strings.TALK_SPEAKER_LABEL, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            if (checkingSpeakers) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.width(16.dp).height(16.dp))
                    Text(Strings.TALK_SPEAKER_CHECKING, style = MaterialTheme.typography.labelMedium)
                }
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = speaker == null,
                        onClick = { speaker = null },
                        label = { Text(Strings.TALK_SPEAKER_AUTO) },
                        enabled = !running,
                    )
                    speakers.forEach { person ->
                        FilterChip(
                            selected = speaker?.id == person.id,
                            onClick = { speaker = person },
                            label = { Text(person.label) },
                            enabled = !running && service.supportsSpeakerChoice(option),
                        )
                    }
                }
                Text(
                    Strings.TALK_SPEAKER_NOTE,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                )
            }
        }

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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(Strings.TALK_THEME_LENGTH, style = MaterialTheme.typography.labelLarge)
                TalkingVideoService.SCRIPT_LENGTHS.forEach { secs ->
                    FilterChip(
                        selected = scriptSeconds == secs,
                        onClick = { scriptSeconds = secs },
                        label = { Text("±$secs dtk") },
                        enabled = !running && !writing,
                    )
                }
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
                ) { Text(if (script.isBlank()) Strings.TALK_WRITE else Strings.TALK_REWRITE) }
                if (writing) {
                    CircularProgressIndicator(Modifier.width(18.dp).height(18.dp))
                    Text(Strings.TALK_WRITING, style = MaterialTheme.typography.labelMedium)
                }
            }
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
        Spacer(Modifier.height(16.dp))

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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                                speaker = speaker,
                                others = speakers,
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
            ) {
                Text(Strings.TALK_START + " — " + Strings.ESTIMATE_LABEL + " ±$" + "%.2f".format(est.totalUsd))
            }
            if (running) {
                CircularProgressIndicator(Modifier.width(20.dp).height(20.dp))
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
        }
        if (running) {
            Spacer(Modifier.height(8.dp))
            // fal gives no percent; pace against a typical ~5-minute render.
            LinearProgressIndicator(progress = { (elapsed / 300f).coerceAtMost(0.95f) }, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(4.dp))
        // Results-folder picker (owner 2026-09-13): the tool remembers its own
        // destination, independent of the global Folder Output.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                Strings.TALK_RESULT_NOTE.replace("%1", outputDirText),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
            TextButton(
                onClick = {
                    pickFolder(outputDirText)?.let { dir ->
                        service.setOutputFolder(dir.absolutePath)
                        outputDirText = service.outputDir().absolutePath
                    }
                },
                enabled = !running,
            ) { Text(Strings.TALK_CHANGE_FOLDER) }
            TextButton(
                onClick = {
                    service.setOutputFolder(null)
                    outputDirText = service.outputDir().absolutePath
                },
                enabled = !running,
            ) { Text(Strings.TALK_FOLDER_RESET) }
        }

        // ---------- Result ----------
        result?.let { r ->
            Spacer(Modifier.height(16.dp))
            SkeuoCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(Strings.TALK_DONE_PREFIX + r.video.absolutePath, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "%.1f dtk · suara: %s · ±$%.2f  —  %s".format(r.durationS, r.voiceLabel, r.usd, Strings.TALK_AUDIO_SAVED),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SkeuoButton(onClick = { openVideoFile(r.video) }) { Text("▶  " + Strings.MOTION_PLAY) }
                        SkeuoOutlinedButton(onClick = {
                            runCatching { java.awt.Desktop.getDesktop().open(r.video.parentFile) }
                        }) { Text(Strings.MOTION_OPEN_FOLDER) }
                    }
                }
            }
        }
    }
}

/** Folder picker for the tool's results destination. */
private fun pickFolder(current: String): File? {
    val chooser = javax.swing.JFileChooser().apply {
        dialogTitle = Strings.TALK_CHANGE_FOLDER
        fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
        File(current).takeIf { it.isDirectory }?.let { currentDirectory = it }
    }
    return if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile
    } else {
        null
    }
}

/** Single-file chooser (same JFileChooser pattern as the other tools). */
private fun pickFile(title: String, filterLabel: String, vararg extensions: String): File? {
    val chooser = javax.swing.JFileChooser().apply {
        dialogTitle = title
        isMultiSelectionEnabled = false
        fileSelectionMode = javax.swing.JFileChooser.FILES_ONLY
        fileFilter = javax.swing.filechooser.FileNameExtensionFilter(filterLabel, *extensions)
    }
    return if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}
