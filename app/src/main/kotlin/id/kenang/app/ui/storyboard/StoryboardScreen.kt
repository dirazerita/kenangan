package id.kenang.app.ui.storyboard

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import id.kenang.app.ui.components.StatusChip
import id.kenang.app.ui.components.rememberFileBitmap
import id.kenang.core.common.events.GenerationEvents
import id.kenang.core.common.i18n.Strings
import id.kenang.core.common.story.CameraMove
import id.kenang.core.common.story.MotionCategory
import id.kenang.core.common.story.MotionSpec
import id.kenang.core.common.story.MotionTemplateValidator
import id.kenang.core.common.story.MotionTemplates
import id.kenang.core.data.ProjectRepository
import id.kenang.core.data.SceneRepository
import id.kenang.core.data.SceneStatus
import id.kenang.core.data.config.ConfigRepository
import id.kenang.core.db.Scene
import id.kenang.core.providers.story.CostEstimator
import id.kenang.core.providers.story.KeyframeService
import org.koin.compose.koinInject

@Composable
fun StoryboardScreen(
    projectId: String,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val projects = koinInject<ProjectRepository>()
    val sceneRepo = koinInject<SceneRepository>()
    val keyframes = koinInject<KeyframeService>()
    val estimator = koinInject<CostEstimator>()
    val configRepo = koinInject<ConfigRepository>()
    val events = koinInject<GenerationEvents>()

    val state = remember {
        StoryboardState(projectId, projects, sceneRepo, keyframes, estimator, configRepo, events, scope)
    }
    LaunchedEffect(Unit) { state.start() }
    LaunchedEffect(state.snackMessage) {
        state.snackMessage?.let { snackbar.showSnackbar(it); state.snackMessage = null }
    }

    var editing by remember { mutableStateOf<Scene?>(null) }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        // ---------- Header ----------
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(state.project?.name ?: Strings.SB_TITLE_FALLBACK, style = MaterialTheme.typography.headlineSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusChip(state.project?.ratio ?: "")
                    val vibeLabel = state.config.vibes.firstOrNull { it.id == state.project?.vibe }?.labelId
                    StatusChip(vibeLabel ?: state.project?.vibe ?: "")
                }
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                state.estimate?.let { est ->
                    val estText = if (est.complete)
                        "$" + "%.2f".format(est.usd) + "  ≈ Rp" + "%,.0f".format(est.idr)
                    else Strings.SB_ESTIMATE_UNKNOWN
                    Text(estText, style = MaterialTheme.typography.titleMedium)
                    Text(
                        Strings.SB_ESTIMATE_LABEL,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            OutlinedButton(onClick = onBack) { Text(Strings.BACK) }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { state.showConfirm = true }, enabled = state.allReady() && !state.confirmed) {
                Text(Strings.SB_CREATE_VIDEO)
            }
        }

        Spacer(Modifier.height(16.dp))

        // ---------- Scene grid ----------
        val ordered = state.scenes.sortedBy { it.order_index }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 260.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(ordered, key = { it.scene_id }) { scene ->
                SceneCard(
                    scene = scene,
                    index = ordered.indexOf(scene),
                    lastIndex = ordered.lastIndex,
                    regenCost = state.regenCostUsd(),
                    onEdit = { editing = scene },
                    onRegen = { state.regenerateKeyframe(scene) },
                    onRetry = { state.retryKeyframe(scene) },
                    onDelete = { state.delete(scene) },
                    onMove = { delta -> state.move(scene, delta) },
                )
            }
        }
    }

    editing?.let { scene ->
        MotionEditorDialog(
            scene = scene,
            onSave = { spec -> state.saveMotion(scene, spec); editing = null },
            onDismiss = { editing = null },
        )
    }

    if (state.showConfirm) {
        ConfirmDialog(state)
    }
}

// ------------------------------------------------------------------ card

@Composable
private fun SceneCard(
    scene: Scene,
    index: Int,
    lastIndex: Int,
    regenCost: Double,
    onEdit: () -> Unit,
    onRegen: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    onMove: (Int) -> Unit,
) {
    Card {
        Column {
            Box(
                Modifier.fillMaxWidth().height(180.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                when (scene.status) {
                    SceneStatus.KEYFRAME_PENDING, SceneStatus.DRAFT -> ShimmerBox()
                    SceneStatus.KEYFRAME_FAILED -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(Strings.SB_KEYFRAME_FAILED, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = onRetry) { Text(Strings.SB_RETRY) }
                    }
                    else -> {
                        val bmp by rememberFileBitmap(scene.local_keyframe_path)
                        bmp?.let {
                            Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        } ?: ShimmerBox()
                    }
                }
            }
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Adegan ${index + 1}", style = MaterialTheme.typography.titleSmall)
                    StatusChip("${scene.duration_s}s")
                    if (scene.type == "fusion") StatusChip(Strings.SB_FUSION_BADGE, MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { onMove(-1) }, enabled = index > 0) {
                        Icon(Icons.Default.KeyboardArrowLeft, "maju")
                    }
                    IconButton(onClick = { onMove(1) }, enabled = index < lastIndex) {
                        Icon(Icons.Default.KeyboardArrowRight, "mundur")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, Strings.SB_DELETE_SCENE, tint = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    scene.motion_summary_id ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, null, Modifier.width(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(Strings.SB_EDIT_PROMPT)
                    }
                    TextButton(
                        onClick = onRegen,
                        enabled = scene.status == SceneStatus.KEYFRAME_READY,
                    ) {
                        Icon(Icons.Default.Refresh, null, Modifier.width(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(Strings.SB_REGEN_KEYFRAME + " ±$" + "%.3f".format(regenCost))
                    }
                }
            }
        }
    }
}

@Composable
private fun ShimmerBox() {
    val transition = rememberInfiniteTransition()
    val alpha by transition.animateFloat(
        initialValue = 0.25f, targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
    )
    Box(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha * 0.3f)),
    )
}

// ------------------------------------------------------------------ motion editor

/**
 * Template-constrained prompt editor: category dropdown + camera dropdown +
 * free adjective field (≤ 8 words, forbidden words dropped) — never free-form
 * motion text (MEMORY §7).
 */
@Composable
private fun MotionEditorDialog(
    scene: Scene,
    onSave: (MotionSpec) -> Unit,
    onDismiss: () -> Unit,
) {
    val currentPrompt = scene.motion_prompt_en?.lowercase() ?: ""
    var category by remember(scene.scene_id) {
        mutableStateOf(
            MotionCategory.entries.firstOrNull {
                currentPrompt.contains(it.phraseEn.replace("{s} ", "").substringBefore(","))
            } ?: MotionCategory.SMILE,
        )
    }
    var camera by remember(scene.scene_id) {
        mutableStateOf(
            CameraMove.entries.firstOrNull { currentPrompt.contains(it.phraseEn) }
                ?: CameraMove.SLOW_PUSH_IN,
        )
    }
    var adjectives by remember(scene.scene_id) { mutableStateOf("") }

    val spec = MotionSpec(category, camera, MotionTemplateValidator.sanitizeAdjectives(adjectives))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Strings.SB_EDIT_PROMPT) },
        text = {
            Column {
                EnumDropdown(Strings.SB_MOTION_CATEGORY, MotionCategory.entries.map { it.key }, category.key) {
                    category = MotionCategory.fromKey(it) ?: MotionCategory.SMILE
                }
                Spacer(Modifier.height(8.dp))
                EnumDropdown(Strings.SB_MOTION_CAMERA, CameraMove.entries.map { it.key }, camera.key) {
                    camera = CameraMove.fromKey(it) ?: CameraMove.SLOW_PUSH_IN
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = adjectives,
                    onValueChange = { adjectives = it },
                    label = { Text(Strings.SB_MOTION_ADJECTIVES) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text(MotionTemplates.buildSummaryId(spec), style = MaterialTheme.typography.bodyMedium)
                Text(
                    MotionTemplates.buildPromptEn(spec),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }
        },
        confirmButton = { Button(onClick = { onSave(spec) }) { Text(Strings.SAVE) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.CANCEL) } },
    )
}

@Composable
private fun EnumDropdown(label: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label: $selected")
        }
        androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = { onSelect(option); expanded = false },
                )
            }
        }
    }
}

// ------------------------------------------------------------------ confirm dialog

@Composable
private fun ConfirmDialog(state: StoryboardState) {
    val est = state.estimateFor(state.confirmTier)
    AlertDialog(
        onDismissRequest = { state.showConfirm = false },
        title = { Text(Strings.CONFIRM_TITLE) },
        text = {
            Column {
                Text("${est.sceneCount} adegan · total ${est.totalDurationS}s")
                Spacer(Modifier.height(12.dp))
                Text(Strings.CONFIRM_TIER_LABEL, style = MaterialTheme.typography.titleSmall)
                TierOption(state, "hemat", Strings.CONFIRM_TIER_HEMAT,
                    enabled = state.config.tierRouting.tiers["hemat"]?.enabled == true)
                TierOption(state, "standar", Strings.CONFIRM_TIER_STANDAR, enabled = true)
                TierOption(state, "premium", Strings.CONFIRM_TIER_PREMIUM, enabled = true)
                Spacer(Modifier.height(12.dp))
                Text(
                    "$" + "%.2f".format(est.usd) + "  ≈ Rp" + "%,.0f".format(est.idr),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(Strings.SB_ESTIMATE_LABEL, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                Spacer(Modifier.height(8.dp))
                Text(Strings.CONFIRM_FIRST_TIME_NOTE, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(4.dp))
                Text(Strings.CONFIRM_DISCLAIMER, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            }
        },
        confirmButton = { Button(onClick = { state.confirm() }) { Text(Strings.CONFIRM_GO) } },
        dismissButton = { TextButton(onClick = { state.showConfirm = false }) { Text(Strings.CANCEL) } },
    )
}

@Composable
private fun TierOption(state: StoryboardState, key: String, label: String, enabled: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(
            selected = state.confirmTier == key,
            onClick = { state.confirmTier = key },
            enabled = enabled,
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
        )
    }
}
