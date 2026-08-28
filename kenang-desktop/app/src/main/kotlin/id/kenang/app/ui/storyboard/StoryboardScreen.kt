package id.kenang.app.ui.storyboard
import id.kenang.app.ui.theme.SkeuoButton
import id.kenang.app.ui.theme.SkeuoCard
import id.kenang.app.ui.theme.SkeuoOutlinedButton

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
    var showAddScene by remember { mutableStateOf(false) }

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
            SkeuoOutlinedButton(onClick = onBack) { Text(Strings.BACK) }
            Spacer(Modifier.width(8.dp))
            SkeuoButton(onClick = { state.showConfirm = true }, enabled = state.allReady() && !state.confirmed) {
                Text(Strings.SB_CREATE_VIDEO)
            }
        }

        Spacer(Modifier.height(12.dp))

        // ---------- Keyframe progress (owner request: show how far along) ----------
        run {
            val total = state.scenes.size
            val ready = state.scenes.count {
                it.status !in setOf(SceneStatus.DRAFT, SceneStatus.KEYFRAME_PENDING)
            }
            if (total > 0 && ready < total) {
                Text(
                    Strings.SB_PHOTOS_READY
                        .replace("%1", ready.toString())
                        .replace("%2", total.toString()),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(4.dp))
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { ready / total.toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
            }
        }

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
                    onReplace = {
                        pickImage()?.let { file -> state.replaceKeyframe(scene, file) }
                    },
                )
            }
            // Owner feature 2026-08-28: append your own photo as a new scene.
            item(key = "add-user-scene") {
                SkeuoCard(
                    Modifier.height(180.dp).fillMaxWidth().clickable { showAddScene = true },
                ) {
                    Column(
                        Modifier.fillMaxSize().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text("＋", style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(Strings.SB_ADD_SCENE, style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            Strings.SB_ADD_SCENE_NOTE,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }
        }
    }

    if (showAddScene) {
        AddSceneDialog(
            onAdd = { file, category, camera, description ->
                state.addUserScene(file, category, camera, description)
                showAddScene = false
            },
            onDismiss = { showAddScene = false },
        )
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

/** Single-image chooser for keyframe replacement (same filter as the wizard). */
private fun pickImage(): java.io.File? {
    val chooser = javax.swing.JFileChooser().apply {
        dialogTitle = Strings.SB_REPLACE_IMAGE
        isMultiSelectionEnabled = false
        fileSelectionMode = javax.swing.JFileChooser.FILES_ONLY
        fileFilter = javax.swing.filechooser.FileNameExtensionFilter(
            "Foto (JPG, PNG, WebP)", "jpg", "jpeg", "png", "webp",
        )
    }
    return if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile
    } else {
        null
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
    onReplace: () -> Unit,
) {
    Card {
        Column {
            Box(
                Modifier.fillMaxWidth().height(180.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                when (scene.status) {
                    SceneStatus.KEYFRAME_PENDING, SceneStatus.DRAFT -> KeyframeProgress(scene.scene_id)
                    SceneStatus.KEYFRAME_FAILED -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(Strings.SB_KEYFRAME_FAILED, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                        SkeuoOutlinedButton(onClick = onRetry) { Text(Strings.SB_RETRY) }
                    }
                    else -> {
                        val bmp by rememberFileBitmap(scene.local_keyframe_path)
                        var showPreview by remember(scene.scene_id) { mutableStateOf(false) }
                        bmp?.let { image ->
                            // Click (or the magnifier chip) → full-size preview
                            // before spending on video (owner request).
                            Image(
                                image, null, contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                                    .clickable { showPreview = true },
                            )
                            Box(Modifier.align(Alignment.TopEnd).padding(6.dp)) {
                                StatusChip(Strings.SB_PREVIEW, MaterialTheme.colorScheme.secondary)
                            }
                            if (showPreview) {
                                androidx.compose.ui.window.Dialog(
                                    onDismissRequest = { showPreview = false },
                                    properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
                                ) {
                                    Column(
                                        Modifier
                                            .fillMaxSize()
                                            .background(androidx.compose.ui.graphics.Color(0xE6050B14))
                                            .clickable { showPreview = false }
                                            .padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Image(
                                            image, null, contentScale = ContentScale.Fit,
                                            modifier = Modifier.weight(1f).fillMaxWidth(),
                                        )
                                        Spacer(Modifier.height(12.dp))
                                        SkeuoOutlinedButton(onClick = { showPreview = false }) {
                                            Text(Strings.CLOSE)
                                        }
                                    }
                                }
                            }
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
                        // User-added scenes have no source photo/prompt to regen from.
                        enabled = scene.status == SceneStatus.KEYFRAME_READY && scene.type != "custom",
                    ) {
                        Icon(Icons.Default.Refresh, null, Modifier.width(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(Strings.SB_REGEN_KEYFRAME + " ±$" + "%.3f".format(regenCost))
                    }
                }
                // Owner feature 2026-08-27: swap the generated image for the
                // user's own photo — free, uploaded at video-submit time.
                TextButton(
                    onClick = onReplace,
                    enabled = scene.status in setOf(SceneStatus.KEYFRAME_READY, SceneStatus.KEYFRAME_FAILED),
                ) {
                    Icon(Icons.Default.Edit, null, Modifier.width(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(Strings.SB_REPLACE_IMAGE)
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

/**
 * Per-image progress while a keyframe generates (owner request): fal image
 * jobs report no percent, so the bar tracks elapsed time against the typical
 * ~30s Nano Banana turnaround (capped at 92% until the real result lands).
 */
@Composable
private fun KeyframeProgress(sceneId: String) {
    var elapsed by remember(sceneId) { mutableStateOf(0) }
    LaunchedEffect(sceneId) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            elapsed++
        }
    }
    val estimateS = 30
    Box(Modifier.fillMaxSize()) {
        ShimmerBox()
        Column(
            Modifier.align(Alignment.Center).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            androidx.compose.material3.LinearProgressIndicator(
                progress = { (elapsed / estimateS.toFloat()).coerceAtMost(0.92f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (elapsed < estimateS) {
                    Strings.SB_KEYFRAME_ETA.replace("%1", (estimateS - elapsed).toString())
                } else {
                    Strings.SB_KEYFRAME_ALMOST
                },
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                "${elapsed}s",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
        }
    }
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
        confirmButton = { SkeuoButton(onClick = { onSave(spec) }) { Text(Strings.SAVE) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.CANCEL) } },
    )
}

/**
 * "Tambah adegan sendiri": the user's photo becomes the keyframe (free), their
 * Indonesian description rides behind the locked motion-template phrase, and
 * category/camera stay template-constrained like the motion editor.
 */
@Composable
private fun AddSceneDialog(
    onAdd: (java.io.File, MotionCategory, CameraMove, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var file by remember { mutableStateOf<java.io.File?>(null) }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(MotionCategory.SMILE) }
    var camera by remember { mutableStateOf(CameraMove.SLOW_PUSH_IN) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Strings.SB_ADD_SCENE_TITLE) },
        text = {
            Column {
                val bmp by rememberFileBitmap(file?.absolutePath)
                Box(
                    Modifier.fillMaxWidth().height(150.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { pickImage()?.let { file = it } },
                    contentAlignment = Alignment.Center,
                ) {
                    bmp?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                        ?: Text(Strings.SB_ADD_SCENE_NEED_PHOTO)
                }
                Spacer(Modifier.height(8.dp))
                SkeuoOutlinedButton(onClick = { pickImage()?.let { file = it } }, modifier = Modifier.fillMaxWidth()) {
                    Text(Strings.SB_ADD_SCENE_PICK)
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.take(260) },
                    label = { Text(Strings.SB_ADD_SCENE_DESC) },
                    placeholder = { Text(Strings.SB_ADD_SCENE_DESC_HINT) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                EnumDropdown(Strings.SB_MOTION_CATEGORY, MotionCategory.entries.map { it.key }, category.key) {
                    category = MotionCategory.fromKey(it) ?: MotionCategory.SMILE
                }
                Spacer(Modifier.height(8.dp))
                EnumDropdown(Strings.SB_MOTION_CAMERA, CameraMove.entries.map { it.key }, camera.key) {
                    camera = CameraMove.fromKey(it) ?: CameraMove.SLOW_PUSH_IN
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    Strings.SB_ADD_SCENE_NOTE,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        },
        confirmButton = {
            SkeuoButton(
                onClick = { file?.let { onAdd(it, category, camera, description) } },
                enabled = file != null,
            ) { Text(Strings.SAVE) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.CANCEL) } },
    )
}

@Composable
private fun EnumDropdown(label: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        SkeuoOutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
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
        confirmButton = { SkeuoButton(onClick = { state.confirm() }) { Text(Strings.CONFIRM_GO) } },
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
