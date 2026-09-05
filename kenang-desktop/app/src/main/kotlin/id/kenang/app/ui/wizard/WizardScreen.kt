@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
)

package id.kenang.app.ui.wizard
import id.kenang.app.ui.theme.SkeuoButton
import id.kenang.app.ui.theme.SkeuoCard
import id.kenang.app.ui.theme.SkeuoOutlinedButton

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import id.kenang.app.ui.components.StatusChip
import id.kenang.app.ui.components.rememberFileBitmap
import id.kenang.core.common.i18n.Strings
import id.kenang.core.data.PhotoRepository
import id.kenang.core.data.ProjectRepository
import id.kenang.core.data.SettingsRepository
import id.kenang.core.data.config.ConfigRepository
import id.kenang.core.data.story.QualityBadge
import id.kenang.core.providers.story.TtsPreviewService
import org.koin.compose.koinInject
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

@Composable
fun WizardScreen(
    existingProjectId: String?,
    onBack: () -> Unit,
    onGoAnalysis: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val projects = koinInject<ProjectRepository>()
    val photosRepo = koinInject<PhotoRepository>()
    val settings = koinInject<SettingsRepository>()
    val configRepo = koinInject<ConfigRepository>()
    val ttsPreview = koinInject<TtsPreviewService>()
    val voiceClone = koinInject<id.kenang.core.providers.voice.VoiceCloneService>()
    val state = remember {
        WizardState(projects, photosRepo, settings, configRepo, ttsPreview, voiceClone, scope, existingProjectId)
    }
    LaunchedEffect(Unit) { state.start() }

    Column(Modifier.fillMaxSize().padding(32.dp).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(Strings.WIZARD_TITLE, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.weight(1f))
            SkeuoOutlinedButton(onClick = { state.persistMeta(); onBack() }) { Text(Strings.BACK) }
        }
        Spacer(Modifier.height(8.dp))
        StepHeader(state.step)
        Spacer(Modifier.height(20.dp))

        when (state.step) {
            1 -> StepPhotos(state)
            2 -> StepStory(state)
            3 -> StepMusic(state)
            4 -> StepFormat(state)
        }

        Spacer(Modifier.height(24.dp))
        Row {
            if (state.step > 1) {
                SkeuoOutlinedButton(onClick = { state.persistMeta(); state.step-- }) { Text(Strings.BACK) }
            }
            Spacer(Modifier.weight(1f))
            if (state.step < 4) {
                SkeuoButton(
                    onClick = { state.persistMeta(); state.step++ },
                    enabled = state.step != 1 || state.canProceedFromStep1(),
                ) { Text(Strings.NEXT) }
            } else {
                SkeuoButton(
                    onClick = { state.requestFinish(onGoAnalysis) },
                    enabled = state.canProceedFromStep1() && !state.finished,
                ) { Text(Strings.WIZARD_FINISH) }
            }
        }
    }

    if (state.showConsent) {
        // Consent gate — first project only (MEMORY §7); acceptance timestamp persisted.
        AlertDialog(
            onDismissRequest = { state.showConsent = false },
            title = { Text(Strings.CONSENT_TITLE) },
            text = { Text(Strings.CONSENT_BODY) },
            confirmButton = {
                SkeuoButton(onClick = { state.acceptConsent(onGoAnalysis) }) { Text(Strings.CONSENT_ACCEPT) }
            },
            dismissButton = {
                TextButton(onClick = { state.showConsent = false }) { Text(Strings.CANCEL) }
            },
        )
    }
}

@Composable
private fun StepHeader(current: Int) {
    val labels = listOf(Strings.WIZARD_STEP1, Strings.WIZARD_STEP2, Strings.WIZARD_STEP3, Strings.WIZARD_STEP4)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { i, label ->
            FilterChip(
                selected = current == i + 1,
                onClick = {},
                label = { Text("${i + 1} · $label") },
            )
        }
    }
}

// ---------------------------------------------------------------- Step 1

@Composable
private fun StepPhotos(state: WizardState) {
    // Drag-and-drop photo import (owner 2026-08-28): dropping image files
    // anywhere on step 1 adds them like the picker would (same limit and
    // quality checks in addPhotos).
    var dragOver by remember { mutableStateOf(false) }
    val photoExtensions = setOf("jpg", "jpeg", "png", "webp")
    val dropTarget = remember(state) {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) { dragOver = true }
            override fun onExited(event: DragAndDropEvent) { dragOver = false }
            override fun onEnded(event: DragAndDropEvent) { dragOver = false }
            override fun onDrop(event: DragAndDropEvent): Boolean {
                dragOver = false
                val transferable = event.awtTransferable
                if (!transferable.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.javaFileListFlavor)) return false
                val files = runCatching {
                    (transferable.getTransferData(java.awt.datatransfer.DataFlavor.javaFileListFlavor) as? List<*>)
                        .orEmpty()
                        .filterIsInstance<File>()
                        .filter { it.isFile && it.extension.lowercase() in photoExtensions }
                }.getOrDefault(emptyList())
                if (files.isEmpty()) return false
                state.addPhotos(files)
                return true
            }
        }
    }
    Column(
        Modifier.dragAndDropTarget(shouldStartDragAndDrop = { true }, target = dropTarget),
    ) {
        // Project name up front (owner request): first thing after "Proyek
        // Baru", so projects are easy to group on Home. Prefilled with a
        // dated default; autosaved with every step like the rest.
        OutlinedTextField(
            value = state.name,
            onValueChange = { state.name = it },
            label = { Text(Strings.WIZARD_NAME_LABEL) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        SkeuoButton(onClick = {
            val files = pickFiles("Pilih foto", "Foto (JPG, PNG, WebP)", "jpg", "jpeg", "png", "webp")
            if (files.isNotEmpty()) state.addPhotos(files)
        }) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(Strings.WIZARD_ADD_PHOTOS.replace("%1", state.config.limits.maxPhotos.toString()))
        }
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier.fillMaxWidth().height(96.dp)
                .background(
                    if (dragOver) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f),
                    RoundedCornerShape(19.dp),
                )
                .border(
                    2.dp,
                    if (dragOver) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                    RoundedCornerShape(19.dp),
                )
                .clickable {
                    val files = pickFiles("Pilih foto", "Foto (JPG, PNG, WebP)", "jpg", "jpeg", "png", "webp")
                    if (files.isNotEmpty()) state.addPhotos(files)
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (dragOver) Strings.WIZARD_DROP_ACTIVE else "🖼  " + Strings.WIZARD_DROP_HINT,
                style = MaterialTheme.typography.bodyLarge,
                color = if (dragOver) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            )
        }
        state.rejectionMessage?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(16.dp))
        // Photo cards wrap to the window width — no clipping at the right edge.
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.photos.forEachIndexed { index, ui ->
                Card {
                    Column(Modifier.width(160.dp)) {
                        val bmp by rememberFileBitmap(ui.photo.local_path)
                        Box(
                            Modifier.fillMaxWidth().height(120.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            bmp?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                                ?: CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                        Column(Modifier.padding(8.dp)) {
                            QualityBadgeChip(ui.check.badge)
                            ui.check.tipId?.let {
                                Text(it, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                            Row {
                                IconButton(onClick = { state.movePhoto(index, -1) }, enabled = index > 0) {
                                    Icon(Icons.Default.KeyboardArrowLeft, "geser kiri")
                                }
                                IconButton(onClick = { state.movePhoto(index, 1) }, enabled = index < state.photos.lastIndex) {
                                    Icon(Icons.Default.KeyboardArrowRight, "geser kanan")
                                }
                                Spacer(Modifier.weight(1f))
                                IconButton(onClick = { state.removePhoto(ui) }) {
                                    Icon(Icons.Default.Delete, Strings.KEYS_REMOVE, tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Scene-count picker (owner features 2026-08-26/27): works for ANY
        // photo count. When scenes > photos, the planner derives extra scenes
        // from the chosen photos with distinct activities; every keyframe is
        // edited FROM its source photo (Nano Banana), so characters stay
        // consistent. Generated photos appear in the storyboard first.
        if (state.photos.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            SkeuoCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    val single = state.photos.size == 1
                    Text(
                        if (single) Strings.WIZARD_SINGLE_PHOTO_TITLE else Strings.WIZARD_SCENES_TITLE,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.height(4.dp))
                    val priceBook = koinInject<id.kenang.core.providers.PriceBook>()
                    val keyframeSlug = state.config.tierRouting
                        .resolve(state.config.tierRouting.defaultTier).keyframe
                    val perPhoto = priceBook.estimate(keyframeSlug, 1.0)
                        ?.let { "$%.3f".format(java.util.Locale.US, it.usd) } ?: "?"
                    Text(
                        if (single) {
                            Strings.WIZARD_SINGLE_PHOTO_DESC.replace("%1", perPhoto)
                        } else {
                            Strings.WIZARD_SCENES_DESC
                                .replace("%1", state.photos.size.toString())
                                .replace("%2", perPhoto)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = state.targetScenes == null,
                            onClick = { state.targetScenes = null; state.persistMeta() },
                            label = { Text(Strings.WIZARD_SINGLE_PHOTO_AUTO) },
                        )
                        listOf(2L, 3L, 4L, 5L, 6L, 8L, 10L, 12L)
                            .filter { it <= state.config.limits.maxScenes }
                            .forEach { n ->
                                FilterChip(
                                    selected = state.targetScenes == n,
                                    onClick = { state.targetScenes = n; state.persistMeta() },
                                    label = { Text("$n" + Strings.WIZARD_SINGLE_PHOTO_SCENES_SUFFIX) },
                                )
                            }
                    }
                    Spacer(Modifier.height(12.dp))
                    // Tuntunan adegan (owner 2026-08-28): free-text direction
                    // the story planner must follow, so scenes go where the
                    // user wants instead of generic variations.
                    OutlinedTextField(
                        value = state.sceneGuidance,
                        onValueChange = { state.sceneGuidance = it.take(300) },
                        label = { Text(Strings.WIZARD_GUIDANCE_LABEL) },
                        placeholder = { Text(Strings.WIZARD_GUIDANCE_HINT) },
                        supportingText = { Text("${state.sceneGuidance.length}/300") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    // Gunakan foto asli (owner 2026-09-05): originals become
                    // their scenes' keyframes directly — free, no AI image.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Switch(
                            checked = state.useOriginalPhotos,
                            onCheckedChange = { state.useOriginalPhotos = it; state.persistMeta() },
                        )
                        Text(Strings.WIZARD_USE_ORIGINALS, style = MaterialTheme.typography.titleSmall)
                    }
                    if (state.useOriginalPhotos) {
                        Text(
                            Strings.WIZARD_USE_ORIGINALS_DESC,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QualityBadgeChip(badge: QualityBadge) {
    when (badge) {
        QualityBadge.BAGUS -> StatusChip(Strings.WIZARD_BADGE_BAGUS, Color(0xFF2E7D32))
        QualityBadge.CUKUP -> StatusChip(Strings.WIZARD_BADGE_CUKUP, Color(0xFF9A6A00))
        QualityBadge.KURANG -> StatusChip(Strings.WIZARD_BADGE_KURANG, MaterialTheme.colorScheme.error)
    }
}

// ---------------------------------------------------------------- Step 2

@Composable
private fun StepStory(state: WizardState) {
    // Arriving at this step with an empty box always prefills the template —
    // covers projects created before the prefill existed and any race on
    // project creation. Users edit instead of writing from scratch.
    LaunchedEffect(state.noNarration) {
        if (state.narration.isBlank() && !state.noNarration) {
            state.shuffleNarration()
        }
    }
    Column {
        // Explicit "Tanpa narasi" choice (owner request): narration is
        // optional, and this makes that obvious instead of implied.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Switch(checked = state.noNarration, onCheckedChange = { state.chooseNoNarration(it) })
            Text(Strings.WIZARD_NO_NARRATION, style = MaterialTheme.typography.titleSmall)
        }
        Spacer(Modifier.height(12.dp))
        if (state.noNarration) {
            Text(
                Strings.WIZARD_NO_NARRATION_HINT,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            return@Column
        }
        val max = state.config.limits.maxNarrationChars
        OutlinedTextField(
            value = state.narration,
            onValueChange = { if (it.length <= max) state.narration = it },
            label = { Text(Strings.WIZARD_NARRATION_LABEL) },
            placeholder = { Text(Strings.WIZARD_NARRATION_HINT) },
            supportingText = { Text("${state.narration.length}/$max") },
            minLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
        // The prefill is only a starting point, and a new one is one click
        // away (owner 2026-09-01: every project opened with identical text).
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeuoOutlinedButton(onClick = { state.shuffleNarration() }) {
                Text("🎲  " + Strings.WIZARD_NARRATION_SHUFFLE)
            }
            Text(
                Strings.WIZARD_NARRATION_TEMPLATE_NOTE,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(Strings.WIZARD_VOICE_LABEL, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        // 15 config-driven voices: chips WRAP to the window width (owner
        // report: horizontal rows clipped at the right edge).
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.voiceOptions.forEach { voice ->
                FilterChip(
                    selected = state.voiceId == voice.id,
                    onClick = { state.selectVoice(voice.id) },
                    label = {
                        Text(voice.labelId + if (voice.gender.isNotBlank()) " (${voice.gender})" else "")
                    },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            SkeuoOutlinedButton(onClick = { state.playPreview() }, enabled = !state.previewPlaying) {
                if (state.previewPlaying) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text(Strings.WIZARD_VOICE_PREVIEW)
            }
            Text(Strings.WIZARD_VOICE_COST, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }
        state.previewError?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

// ---------------------------------------------------------------- Step 3

@Composable
private fun StepMusic(state: WizardState) {
    var showCopyright by remember { mutableStateOf(false) }
    var acked by remember { mutableStateOf(false) }
    var pendingFile by remember { mutableStateOf<File?>(null) }

    val musicLibrary = koinInject<id.kenang.core.data.MusicLibrary>()
    val bundled = remember { musicLibrary.tracks() }

    Column {
        Text(Strings.WIZARD_MUSIC_BUNDLED, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        if (bundled.isEmpty()) {
            Text(Strings.WIZARD_MUSIC_BUNDLED_EMPTY, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        } else {
            // Large config-driven collection: chips wrap to the window width.
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                bundled.forEach { track ->
                    FilterChip(
                        selected = state.musicPath?.endsWith(track.meta.file) == true,
                        onClick = { state.setMusic(track.file) },
                        label = { Text(track.meta.labelId) },
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                bundled.map { it.meta.credit }.distinct().joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SkeuoOutlinedButton(onClick = {
                val files = pickFiles("Pilih musik", "Musik (MP3, WAV)", "mp3", "wav")
                if (files.isNotEmpty()) {
                    pendingFile = files.first()
                    acked = false
                    showCopyright = true
                }
            }) { Text(Strings.WIZARD_MUSIC_UPLOAD) }
            TextButton(onClick = { state.setMusic(null) }) { Text(Strings.WIZARD_MUSIC_NONE) }
        }
        state.musicPath?.let {
            Spacer(Modifier.height(8.dp))
            Text("♪ " + File(it).name, style = MaterialTheme.typography.bodyMedium)
        }
    }

    if (showCopyright) {
        AlertDialog(
            onDismissRequest = { showCopyright = false },
            title = { Text(Strings.WIZARD_MUSIC_COPYRIGHT_TITLE) },
            text = {
                Column {
                    Text(Strings.WIZARD_MUSIC_COPYRIGHT_BODY)
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = acked, onCheckedChange = { acked = it })
                        Text(Strings.WIZARD_MUSIC_COPYRIGHT_ACK, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                SkeuoButton(
                    onClick = {
                        state.setMusic(pendingFile)
                        showCopyright = false
                    },
                    enabled = acked,
                ) { Text(Strings.SAVE) }
            },
            dismissButton = { TextButton(onClick = { showCopyright = false }) { Text(Strings.CANCEL) } },
        )
    }
}

// ---------------------------------------------------------------- Step 4

@Composable
private fun StepFormat(state: WizardState) {
    Column {
        Text(Strings.WIZARD_RATIO_LABEL, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RatioFrame("9:16", state.ratio == "9:16") { state.ratio = "9:16" }
            RatioFrame("16:9", state.ratio == "16:9") { state.ratio = "16:9" }
        }
        Spacer(Modifier.height(20.dp))
        Text(Strings.WIZARD_VIBE_LABEL, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        // 50 config-driven vibes: cards WRAP to the window width (the wizard
        // column scrolls vertically), so nothing clips off-screen.
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Suasana kustom (owner 2026-08-28): the user writes their own
            // ambience; the card leads the preset list.
            SkeuoCard(
                modifier = Modifier.width(150.dp).clickable { state.vibeId = "custom" }
                    .then(
                        if (state.vibeId == "custom")
                            Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(24.dp))
                        else Modifier
                    ),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("✏ " + Strings.WIZARD_VIBE_CUSTOM, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    Text(Strings.WIZARD_VIBE_CUSTOM_DESC, style = MaterialTheme.typography.labelSmall, maxLines = 2,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            }
            state.config.vibes.forEach { vibe ->
                SkeuoCard(
                    modifier = Modifier.width(150.dp).clickable { state.vibeId = vibe.id }
                        .then(
                            if (state.vibeId == vibe.id)
                                Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(24.dp))
                            else Modifier
                        ),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(vibe.labelId, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                        Text(vibe.descId, style = MaterialTheme.typography.labelSmall, maxLines = 2,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                }
            }
        }
        if (state.vibeId == "custom") {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.customVibe,
                onValueChange = { state.customVibe = it.take(200) },
                label = { Text(Strings.WIZARD_VIBE_CUSTOM_LABEL) },
                placeholder = { Text(Strings.WIZARD_VIBE_CUSTOM_HINT) },
                supportingText = { Text("${state.customVibe.length}/200") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(Strings.WIZARD_DURATION_LABEL, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(5L, 10L).forEach { d ->
                FilterChip(
                    selected = state.durationS == d,
                    onClick = { state.durationS = d },
                    label = { Text("${d}s") },
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val restoreEnabled = state.config.flags["restore_enabled"] == true
            Switch(
                checked = state.restorePhotos,
                onCheckedChange = { state.restorePhotos = it; state.persistMeta() },
                enabled = restoreEnabled,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                Strings.WIZARD_RESTORE_LABEL,
                color = MaterialTheme.colorScheme.onSurface.copy(
                    alpha = if (restoreEnabled) 0.9f else 0.5f,
                ),
            )
        }
    }
}

@Composable
private fun RatioFrame(label: String, selected: Boolean, onClick: () -> Unit) {
    val (w, h) = if (label == "9:16") 54.dp to 96.dp else 96.dp to 54.dp
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(width = w, height = h)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    else MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(6.dp),
                )
                .border(
                    2.dp,
                    if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    RoundedCornerShape(6.dp),
                )
                .clickable(onClick = onClick),
        )
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

/** Remembers the last-browsed folder across picks within a session. */
private var lastPickDir: File? = null

/**
 * Swing file picker (multi-select) with a REAL extension filter. The previous
 * AWT FileDialog set "*.jpg;*.jpeg;…" as the filename pattern, which on some
 * Windows setups filtered out every file ("No items match your search" —
 * dogfood bug 2026-08-26). FileNameExtensionFilter matches case-insensitively
 * and the user can still switch to "All Files".
 */
private fun pickFiles(title: String, description: String, vararg extensions: String): List<File> {
    val chooser = javax.swing.JFileChooser().apply {
        dialogTitle = title
        isMultiSelectionEnabled = true
        fileSelectionMode = javax.swing.JFileChooser.FILES_ONLY
        fileFilter = javax.swing.filechooser.FileNameExtensionFilter(description, *extensions)
        lastPickDir?.let { currentDirectory = it }
    }
    return if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) {
        lastPickDir = chooser.currentDirectory
        chooser.selectedFiles.toList()
    } else {
        emptyList()
    }
}
