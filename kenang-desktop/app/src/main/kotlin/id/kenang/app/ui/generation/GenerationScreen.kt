package id.kenang.app.ui.generation
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import id.kenang.core.common.ErrorTranslator
import id.kenang.core.common.i18n.Strings
import id.kenang.core.data.GenJobRepository
import id.kenang.core.data.ProjectRepository
import id.kenang.core.data.SceneRepository
import id.kenang.core.data.SceneStatus
import id.kenang.core.db.Scene
import id.kenang.core.providers.gen.AssemblyService
import id.kenang.core.providers.gen.GenerationOrchestrator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Phase 04 screen: per-scene generation states (Antre → Diproses → Selesai /
 * Gagal per §4.1), then narration+subtitles, then the local FFmpeg assembly
 * with a percent bar. All state is DB-derived so a restart resumes cleanly.
 */
@Composable
fun GenerationScreen(
    projectId: String,
    onDone: () -> Unit,
    onEditStoryboard: () -> Unit,
    onOpenKeySettings: () -> Unit,
    onBack: () -> Unit,
) {
    val orchestrator = koinInject<GenerationOrchestrator>()
    val assembly = koinInject<AssemblyService>()
    val sceneRepo = koinInject<SceneRepository>()
    val jobRepo = koinInject<GenJobRepository>()
    val projects = koinInject<ProjectRepository>()
    val scope = rememberCoroutineScope()

    var scenes by remember { mutableStateOf<List<Scene>>(emptyList()) }
    var errorCodes by remember { mutableStateOf<Map<String, String?>>(emptyMap()) }
    val sceneProgress by orchestrator.progress.collectAsState()
    var phase by remember { mutableStateOf("scenes") } // scenes|audio|assembly
    var elapsed by remember { mutableStateOf(0L) }
    var assemblyProgress by remember { mutableStateOf(0) }
    // Second local pass when the watermarked twin is requested (owner 2026-09-11).
    var watermarkPass by remember { mutableStateOf(false) }
    var fatal by remember { mutableStateOf<ErrorTranslator.UiError?>(null) }
    var fatalIsKey by remember { mutableStateOf(false) }
    var showPartialDialog by remember { mutableStateOf(false) }
    var allFailed by remember { mutableStateOf(false) }
    var durationPrompt by remember { mutableStateOf<AssemblyService.AudioPrep.DurationMismatch?>(null) }
    var trimText by remember { mutableStateOf<String?>(null) }
    var includeSubtitles by remember { mutableStateOf(true) }
    var genError by remember { mutableStateOf<ErrorTranslator.UiError?>(null) }
    var runKey by remember { mutableStateOf(0) }

    fun assembleNow(narration: AssemblyService.Narration?, tempo: Double?) {
        scope.launch {
            phase = "assembly"
            genError = null
            val r = assembly.assemble(
                projectId, narration, includeSubtitles, tempo,
                onProgress = { p -> assemblyProgress = p },
                onStage = { stage -> watermarkPass = stage == AssemblyService.Stage.WATERMARK },
            )
            watermarkPass = false
            when (r) {
                is id.kenang.core.common.AppResult.Ok -> onDone()
                is id.kenang.core.common.AppResult.Err -> genError = ErrorTranslator.translate(r.error)
            }
        }
    }

    fun proceedToAudio(force: Boolean = false) {
        // Guard against double entry (outcome path + status watcher).
        if (!force && phase != "scenes") return
        phase = "audio"
        scope.launch {
            genError = null
            when (val prep = assembly.prepareAudio(projectId)) {
                is AssemblyService.AudioPrep.Ready -> assembleNow(prep.narration, null)
                is AssemblyService.AudioPrep.DurationMismatch -> durationPrompt = prep
                is AssemblyService.AudioPrep.Failed -> genError = ErrorTranslator.translate(prep.error)
            }
        }
    }

    LaunchedEffect(Unit) {
        launch { sceneRepo.observeScenes(projectId).collect { scenes = it } }
    }
    // Refresh failed scenes' error codes whenever statuses shift, and continue
    // to audio/assembly automatically once per-scene retries finish the set.
    LaunchedEffect(scenes.map { it.status }) {
        errorCodes = scenes.filter { it.status == SceneStatus.FAILED }
            .associate { it.scene_id to jobRepo.latestForScene(it.scene_id)?.error_code }
        if (scenes.isNotEmpty() && scenes.all { it.status == SceneStatus.DONE } &&
            !showPartialDialog && fatal == null
        ) {
            proceedToAudio()
        }
    }
    LaunchedEffect(runKey) {
        fatal = null
        genError = null
        allFailed = false
        val ticker = launch { while (true) { delay(1000); elapsed++ } }
        val project = projects.get(projectId)
        if (project == null) {
            genError = ErrorTranslator.translate(id.kenang.core.common.AppError.Unknown("project missing"))
            ticker.cancel(); return@LaunchedEffect
        }
        val outcome = try {
            orchestrator.run(projectId, project.tier)
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            // The screen must outlive a bug in the run (owner 2026-09-15: an
            // exception here closed the whole app).
            io.github.aakira.napier.Napier.e("generation run crashed: $t", t)
            ticker.cancel()
            genError = ErrorTranslator.translate(id.kenang.core.common.AppError.Unknown(t.message))
            return@LaunchedEffect
        }
        ticker.cancel()
        when {
            outcome.fatal != null -> {
                fatalIsKey = outcome.fatal is id.kenang.core.common.AppError.InvalidKey
                fatal = ErrorTranslator.translate(outcome.fatal!!)
            }
            outcome.failedScenes > 0 && outcome.doneScenes > 0 -> showPartialDialog = true
            outcome.doneScenes == 0 -> allFailed = true
            else -> proceedToAudio()
        }
    }

    Column(
        Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(Strings.GEN_TITLE, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            Strings.GEN_SUBTITLE + "  ·  " + Strings.GEN_ELAPSED_PREFIX + "${elapsed}s",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(12.dp))

        // Overall progress: at a glance, how many scenes are truly finished.
        if (scenes.isNotEmpty()) {
            val doneCount = scenes.count { it.status == SceneStatus.DONE }
            // Owner 2026-09-16: the bar moves while scenes render, not only
            // when one finishes - each scene contributes its own fraction.
            val nowMs = System.currentTimeMillis() + elapsed * 0L
            val overall = scenes.sumOf { s ->
                when (s.status) {
                    SceneStatus.DONE -> 1.0
                    else -> (sceneProgress[s.scene_id]?.at(nowMs) ?: 0f).toDouble()
                }
            } / scenes.size
            Text(
                Strings.GEN_PROGRESS
                    .replace("%1", doneCount.toString())
                    .replace("%2", scenes.size.toString()) +
                    "  ·  ${(overall * 100).toInt()}%",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { overall.toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
        }

        scenes.sortedBy { it.order_index }.forEachIndexed { sceneIndex, scene ->
            SceneRow(
                index = sceneIndex,
                scene = scene,
                errorCode = errorCodes[scene.scene_id],
                errorDetail = orchestrator.errorDetail(scene.scene_id),
                progress = sceneProgress[scene.scene_id],
                nowMs = System.currentTimeMillis() + elapsed * 0L,
                onRetry = {
                    scope.launch {
                        val project = projects.get(projectId) ?: return@launch
                        orchestrator.retryScene(projectId, scene.scene_id, project.tier)
                    }
                },
                onEditStoryboard = onEditStoryboard,
                onOpenKeySettings = onOpenKeySettings,
            )
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(16.dp))
        when (phase) {
            "audio" -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp))
                Spacer(Modifier.width(12.dp))
                Text(Strings.GEN_TTS_RUNNING + " " + Strings.GEN_SUBS_BUILDING)
            }
            "assembly" -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (watermarkPass) Strings.GEN_ASSEMBLY_WATERMARK else Strings.GEN_ASSEMBLING)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { assemblyProgress / 100f },
                    modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "$assemblyProgress% — ${Strings.GEN_ASSEMBLING_NOTE}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }

        fatal?.let { ui ->
            Spacer(Modifier.height(16.dp))
            ErrorCard(
                title = if (fatalIsKey) ui.title else Strings.GEN_PAUSED_BALANCE_TITLE,
                message = if (fatalIsKey) ui.message else Strings.GEN_PAUSED_BALANCE_BODY,
                ctaLabel = ui.ctaLabel,
                onCta = {
                    if (fatalIsKey) onOpenKeySettings()
                    else ui.ctaUrl?.let { runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(it)) } }
                },
                onRetry = { runKey++ },
                retryLabel = Strings.GEN_RESUME,
            )
        }
        genError?.let { ui ->
            Spacer(Modifier.height(16.dp))
            ErrorCard(ui.title, ui.message, null, {}, onRetry = { proceedToAudio(force = true) }, retryLabel = Strings.RETRY)
        }
        if (allFailed) {
            Spacer(Modifier.height(16.dp))
            ErrorCard(
                Strings.GEN_PARTIAL_TITLE, Strings.GEN_BILLING_NOTE, null, {},
                onRetry = { runKey++ }, retryLabel = Strings.GEN_PARTIAL_RETRY,
            )
        }

        Spacer(Modifier.height(16.dp))
        Text(
            Strings.GEN_BILLING_NOTE,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onBack) { Text(Strings.BACK) }
    }

    if (showPartialDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text(Strings.GEN_PARTIAL_TITLE) },
            text = { Text(Strings.GEN_PARTIAL_BODY) },
            confirmButton = {
                SkeuoButton(onClick = { showPartialDialog = false; proceedToAudio() }) {
                    Text(Strings.GEN_PARTIAL_CONTINUE)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPartialDialog = false; runKey++ }) {
                    Text(Strings.GEN_PARTIAL_RETRY)
                }
            },
        )
    }

    durationPrompt?.let { prompt ->
        DurationDialog(
            prompt = prompt,
            includeSubtitles = includeSubtitles,
            onToggleSubtitles = { includeSubtitles = it },
            onTempo = { durationPrompt = null; assembleNow(prompt.narration, 1.1) },
            onTrim = { trimText = prompt.narration.text; durationPrompt = null },
            onKeep = { durationPrompt = null; assembleNow(prompt.narration, null) },
        )
    }

    trimText?.let { current ->
        TrimDialog(
            initial = current,
            onSave = { newText ->
                trimText = null
                scope.launch {
                    val p = projects.get(projectId) ?: return@launch
                    projects.updateMeta(
                        projectId, p.name, p.ratio, p.vibe, p.tier,
                        newText.ifBlank { null }, p.music_path, p.scene_duration_s,
                    )
                    // Cached narration no longer matches — force re-synthesis.
                    java.io.File(
                        java.io.File(id.kenang.core.data.AppDirs.projectDir(projectId), "audio"),
                        "narration.mp3",
                    ).delete()
                    proceedToAudio()
                }
            },
            onCancel = { trimText = null },
        )
    }
}

// --------------------------------------------------------------- components

@Composable
private fun SceneRow(
    index: Int,
    scene: Scene,
    errorCode: String?,
    errorDetail: String? = null,
    progress: GenerationOrchestrator.SceneProgress? = null,
    nowMs: Long = System.currentTimeMillis(),
    onRetry: () -> Unit,
    onEditStoryboard: () -> Unit,
    onOpenKeySettings: () -> Unit,
) {
    val doneGreen = androidx.compose.ui.graphics.Color(0xFF4CD97B)
    SkeuoCard(Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val bmp by rememberFileBitmap(scene.local_keyframe_path)
            Box(
                Modifier.size(64.dp).background(
                    MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp),
                ),
                contentAlignment = Alignment.Center,
            ) {
                bmp?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                // Big unmistakable check on finished scenes.
                if (scene.status == SceneStatus.DONE) {
                    Box(
                        Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0x66103322)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("✓", style = MaterialTheme.typography.headlineMedium, color = doneGreen)
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    Strings.GEN_SCENE_PREFIX + (index + 1) + " · " + (scene.motion_summary_id ?: scene.scene_id),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                )
                Spacer(Modifier.height(4.dp))
                when (scene.status) {
                    SceneStatus.CONFIRMED, SceneStatus.GENERATING -> Column {
                        // Owner 2026-09-16: a bar per scene. fal reports no
                        // percentage for a render, so this is time against the
                        // running average of earlier clips (RenderTimeStats).
                        val phase = progress?.phase
                        val fraction = progress?.at(nowMs) ?: 0f
                        val remaining = progress?.remainingS(nowMs)
                        val label = when (phase) {
                            GenerationOrchestrator.Phase.QUEUED ->
                                "⏳ " + Strings.GEN_QUEUE_AT_PROVIDER +
                                    (progress?.queuePosition?.let { " (" + Strings.GEN_QUEUE_POSITION.replace("%1", it.toString()) + ")" } ?: "")
                            GenerationOrchestrator.Phase.RENDERING ->
                                Strings.GEN_STATUS_RUNNING + "… " + (fraction * 100).toInt() + "%" +
                                    (remaining?.let { "  ·  " + Strings.GEN_ETA.replace("%1", formatSeconds(it)) } ?: "")
                            GenerationOrchestrator.Phase.DOWNLOADING -> Strings.GEN_DOWNLOADING + "…"
                            GenerationOrchestrator.Phase.WAITING -> "⏳ " + Strings.GEN_WAITING_SLOT
                            else -> if (scene.status == SceneStatus.GENERATING) Strings.GEN_STATUS_RUNNING + "…" else "⏳ " + Strings.GEN_STATUS_QUEUED
                        } + (progress?.attempt?.takeIf { it > 1 }?.let { "  ·  " + Strings.GEN_ATTEMPT.replace("%1", it.toString()) } ?: "")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (scene.status == SceneStatus.GENERATING) {
                                CircularProgressIndicator(Modifier.size(14.dp))
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                label,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (scene.status == SceneStatus.GENERATING) MaterialTheme.colorScheme.secondary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                        )
                    }
                    SceneStatus.DONE -> Text(
                        "✓ " + Strings.GEN_STATUS_DONE,
                        style = MaterialTheme.typography.labelLarge,
                        color = doneGreen,
                    )
                    SceneStatus.FAILED -> Column {
                        val reason = when (errorCode) {
                            GenerationOrchestrator.ErrorCodes.CONTENT_BLOCKED ->
                                ErrorTranslator.translate(id.kenang.core.common.AppError.ContentBlocked()).title
                            GenerationOrchestrator.ErrorCodes.INVALID_KEY ->
                                ErrorTranslator.translate(
                                    id.kenang.core.common.AppError.InvalidKey(id.kenang.core.common.Provider.FAL),
                                ).title
                            // Owner 2026-09-15: six scenes read "Gagal — Gagal" while
                            // the log held the reason; every code now names itself.
                            GenerationOrchestrator.ErrorCodes.BAD_REQUEST ->
                                ErrorTranslator.translate(
                                    id.kenang.core.common.AppError.BadRequest(id.kenang.core.common.Provider.FAL),
                                ).title
                            GenerationOrchestrator.ErrorCodes.PROVIDER_BALANCE ->
                                ErrorTranslator.translate(
                                    id.kenang.core.common.AppError.ProviderBalance(id.kenang.core.common.Provider.FAL),
                                ).title
                            GenerationOrchestrator.ErrorCodes.TIMEOUT ->
                                ErrorTranslator.translate(id.kenang.core.common.AppError.Timeout()).title
                            GenerationOrchestrator.ErrorCodes.PROVIDER_FAILED ->
                                ErrorTranslator.translate(
                                    id.kenang.core.common.AppError.ProviderFailed(id.kenang.core.common.Provider.FAL),
                                ).title
                            GenerationOrchestrator.ErrorCodes.INTERNAL ->
                                ErrorTranslator.translate(id.kenang.core.common.AppError.Unknown()).title
                            else -> Strings.GEN_STATUS_FAILED
                        }
                        Text(
                            Strings.GEN_STATUS_FAILED + " — " + reason,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        errorDetail?.takeIf { it.isNotBlank() }?.let { detail ->
                            Text(
                                Strings.GEN_FAIL_DETAIL + ": " + detail.take(160),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Row {
                            // Every failure is retryable (owner 2026-08-27) —
                            // retries pick the next working key via failover.
                            TextButton(onClick = onRetry) { Text(Strings.GEN_RETRY_SCENE) }
                            when (errorCode) {
                                GenerationOrchestrator.ErrorCodes.CONTENT_BLOCKED ->
                                    TextButton(onClick = onEditStoryboard) { Text(Strings.GEN_EDIT_MOTION_CTA) }
                                GenerationOrchestrator.ErrorCodes.INVALID_KEY ->
                                    TextButton(onClick = onOpenKeySettings) { Text(Strings.GEN_OPEN_KEYS_CTA) }
                                else -> Unit
                            }
                        }
                    }
                    else -> Text(scene.status, style = MaterialTheme.typography.labelMedium)
                }
            }
            Text("${scene.duration_s}s", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun ErrorCard(
    title: String,
    message: String,
    ctaLabel: String?,
    onCta: () -> Unit,
    onRetry: () -> Unit,
    retryLabel: String,
) {
    SkeuoCard(Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ctaLabel?.let { SkeuoOutlinedButton(onClick = onCta) { Text(it) } }
                SkeuoButton(onClick = onRetry) { Text(retryLabel) }
            }
        }
    }
}

@Composable
private fun DurationDialog(
    prompt: AssemblyService.AudioPrep.DurationMismatch,
    includeSubtitles: Boolean,
    onToggleSubtitles: (Boolean) -> Unit,
    onTempo: () -> Unit,
    onTrim: () -> Unit,
    onKeep: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { },
        title = { Text(Strings.DUR_TITLE) },
        text = {
            Column {
                Text(Strings.DUR_BODY)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Narasi ${prompt.narration.durationMs / 1000}s · video ${prompt.videoDurationMs / 1000}s",
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.height(12.dp))
                // F5.3 "extend scenes" is only possible BEFORE generation; here clips exist.
                Text(
                    Strings.DUR_OPT_EXTEND_DISABLED,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = includeSubtitles, onCheckedChange = onToggleSubtitles)
                    Text(Strings.GEN_SUBS_TOGGLE, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            SkeuoButton(onClick = onTempo) {
                Text(Strings.DUR_OPT_TEMPO + if (prompt.tempoFixes) Strings.DUR_RECOMMENDED_SUFFIX else "")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onTrim) { Text(Strings.DUR_OPT_TRIM) }
                TextButton(onClick = onKeep) { Text(Strings.SKIP) }
            }
        },
    )
}

@Composable
private fun TrimDialog(initial: String, onSave: (String) -> Unit, onCancel: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val maxChars = koinInject<id.kenang.core.data.config.ConfigRepository>()
        .current().limits.maxNarrationChars
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(Strings.DUR_OPT_TRIM) },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(maxChars) },
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { SkeuoButton(onClick = { onSave(text) }) { Text(Strings.SAVE) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(Strings.CANCEL) } },
    )
}


/** "1m 20s" / "45s" for the remaining-time label. */
private fun formatSeconds(s: Int): String = if (s >= 60) "${s / 60}m ${s % 60}s" else "${s}s"
