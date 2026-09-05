package id.kenang.app.ui.analysis
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import id.kenang.app.ui.components.rememberFileBitmap
import id.kenang.core.common.ErrorTranslator
import id.kenang.core.common.i18n.Strings
import id.kenang.core.data.PhotoRepository
import id.kenang.core.data.ProjectRepository
import id.kenang.core.providers.story.AnalysisOutcome
import id.kenang.core.providers.story.AnalysisService
import id.kenang.core.providers.story.AnalysisStage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Full-screen analysis progress (F2). BYOK note: photos go directly to the
 * user's AI provider; nothing is uploaded to any Kenang server.
 */
@Composable
fun AnalysisScreen(
    projectId: String,
    onDone: () -> Unit,
    onBackToWizard: () -> Unit,
) {
    val analysis = koinInject<AnalysisService>()
    val projects = koinInject<ProjectRepository>()
    val photosRepo = koinInject<PhotoRepository>()

    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var stageText by remember { mutableStateOf(Strings.ANALYSIS_UPLOADING) }
    var elapsed by remember { mutableStateOf(0L) }
    var outcome by remember { mutableStateOf<AnalysisOutcome?>(null) }
    var runKey by remember { mutableStateOf(0) }

    LaunchedEffect(runKey) {
        outcome = null
        elapsed = 0
        val ticker = launch { while (true) { delay(1000); elapsed++ } }
        val project = projects.get(projectId)
        val result = if (project == null) {
            AnalysisOutcome.Failed(id.kenang.core.common.AppError.Unknown("project missing"))
        } else {
            analysis.run(
                projectId = projectId,
                vibeId = project.vibe,
                ratio = project.ratio,
                sceneDurationS = project.scene_duration_s,
                narration = project.narration,
                targetScenes = project.target_scenes,
                restorePhotos = project.restore_photos == 1L,
                sceneGuidance = project.scene_guidance,
                customVibe = project.custom_vibe,
                useOriginalPhotos = project.use_original_photos == 1L,
            ) { stage ->
                stageText = when (stage) {
                    is AnalysisStage.Uploading -> Strings.ANALYSIS_UPLOADING + " (${stage.done + 1}/${stage.total})"
                    is AnalysisStage.Moderating -> Strings.ANALYSIS_MODERATING + " (${stage.done + 1}/${stage.total})"
                    is AnalysisStage.Analyzing -> Strings.ANALYSIS_READING + " (${stage.done + 1}/${stage.total})"
                    AnalysisStage.Planning -> Strings.ANALYSIS_PLANNING
                    AnalysisStage.Saving -> Strings.ANALYSIS_SAVING
                }
            }
        }
        ticker.cancel()
        if (result is AnalysisOutcome.Ok) {
            projects.updateStatus(projectId, "storyboard")
            onDone()
        } else {
            outcome = result
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (val o = outcome) {
            null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(24.dp))
                Text(Strings.ANALYSIS_TITLE, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text(stageText, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    Strings.ANALYSIS_ELAPSED_PREFIX + "${elapsed}s",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }

            is AnalysisOutcome.Blocked -> BlockedView(
                projectId = projectId,
                photoId = o.photoId,
                onRemoveAndRetry = {
                    // Remove the refused photo, then rerun the whole analysis.
                    val id = o.photoId
                    scope.launch {
                        photosRepo.photos(projectId).firstOrNull { it.id == id }
                            ?.let { photosRepo.removePhoto(it) }
                        runKey++
                    }
                },
                onBack = onBackToWizard,
            )

            is AnalysisOutcome.Failed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Make the cause visible (dogfood: repeated generic failures
                // were undiagnosable) — log it and show the technical detail.
                LaunchedEffect(o) {
                    io.github.aakira.napier.Napier.e("analysis failed for $projectId: ${o.error}")
                }
                val ui = ErrorTranslator.translate(o.error)
                Text(ui.title, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text(ui.message, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    Strings.ERROR_DETAIL_PREFIX + o.error.toString().take(220),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    modifier = Modifier.width(560.dp),
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SkeuoOutlinedButton(onClick = onBackToWizard) { Text(Strings.BACK) }
                    SkeuoButton(onClick = { runKey++ }) { Text(Strings.RETRY) }
                    androidx.compose.material3.TextButton(onClick = {
                        runCatching {
                            java.awt.Desktop.getDesktop().open(id.kenang.core.data.AppDirs.logs)
                        }
                    }) { Text(Strings.ERROR_OPEN_LOGS) }
                }
            }

            is AnalysisOutcome.Ok -> Unit // navigated away
        }
    }
}

@Composable
private fun BlockedView(
    projectId: String,
    photoId: String,
    onRemoveAndRetry: () -> Unit,
    onBack: () -> Unit,
) {
    val photosRepo = koinInject<PhotoRepository>()
    var photoPath by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(photoId) {
        photoPath = photosRepo.photos(projectId).firstOrNull { it.id == photoId }?.local_path
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(Strings.ANALYSIS_BLOCKED_TITLE, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        val bmp by rememberFileBitmap(photoPath)
        Box(
            Modifier.size(160.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            bmp?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            Strings.ANALYSIS_BLOCKED_BODY,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.width(480.dp),
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SkeuoOutlinedButton(onClick = onBack) { Text(Strings.ANALYSIS_BLOCKED_BACK) }
            SkeuoButton(onClick = onRemoveAndRetry) { Text(Strings.ANALYSIS_BLOCKED_REMOVE) }
        }
    }
}
