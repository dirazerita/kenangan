@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package id.kenang.app.ui.upscale

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import id.kenang.app.ui.theme.SkeuoButton
import id.kenang.app.ui.theme.SkeuoCard
import id.kenang.app.ui.theme.SkeuoOutlinedButton
import id.kenang.core.common.AppResult
import id.kenang.core.common.ErrorTranslator
import id.kenang.core.common.i18n.Strings
import id.kenang.core.providers.upscale.UpscaleService
import id.kenang.core.providers.vault.KeyVault
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.koin.compose.koinInject
import java.io.File
import java.util.Locale

private enum class ItemStatus { QUEUED, RUNNING, DONE, FAILED }

private class UpscaleItem(val source: File) {
    var status by mutableStateOf(ItemStatus.QUEUED)
    var result by mutableStateOf<File?>(null)
    var errorMessage by mutableStateOf<String?>(null)
}

/**
 * Standalone batch tool (owner 2026-09-01): pick MANY photos, pick a model,
 * one click — every photo is upscaled/restored in parallel (bounded), results
 * land in <Folder Output>/Upscale/.
 */
@Composable
fun UpscaleScreen(
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
) {
    val service = koinInject<UpscaleService>()
    val keyVault = koinInject<KeyVault>()
    val scope = rememberCoroutineScope()

    val items = remember { mutableStateListOf<UpscaleItem>() }
    val options = remember { service.options() }
    var selectedModel by remember { mutableStateOf(options.firstOrNull()?.selectionKey() ?: "") }
    var running by remember { mutableStateOf(false) }
    var compare by remember { mutableStateOf<UpscaleItem?>(null) }

    // Android photo picker (multi-select, no storage permission).
    val context = androidx.compose.ui.platform.LocalContext.current
    val pickPhotos = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia(15),
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                id.kenang.app.ui.platform.AndroidActions.importPicked(
                    context, uris, File(context.cacheDir, "picked"),
                ).forEach { file ->
                    if (items.none { it.source.absolutePath == file.absolutePath }) {
                        items.add(UpscaleItem(file))
                    }
                }
            }
        }
    }

    val option = options.firstOrNull { it.selectionKey() == selectedModel } ?: options.firstOrNull()
    val perPhoto = option?.let { service.estimate(it) } ?: 0.0
    val pending = items.count { it.status == ItemStatus.QUEUED || it.status == ItemStatus.FAILED }

    fun startBatch(retryOnly: Boolean) {
        val chosen = option ?: return
        if (!keyVault.hasFalKey()) {
            scope.launch { snackbar.showSnackbar(Strings.UPSCALE_NEED_KEY) }
            return
        }
        val targets = items.filter {
            if (retryOnly) it.status == ItemStatus.FAILED
            else it.status == ItemStatus.QUEUED || it.status == ItemStatus.FAILED
        }
        if (targets.isEmpty()) return
        running = true
        scope.launch {
            // Max 3 concurrent jobs: fast overall without hammering the queue.
            val limiter = Semaphore(3)
            val jobs = targets.map { item ->
                launch {
                    limiter.withPermit {
                        item.status = ItemStatus.RUNNING
                        item.errorMessage = null
                        when (val r = service.process(item.source, chosen)) {
                            is AppResult.Ok -> {
                                item.result = r.value
                                item.status = ItemStatus.DONE
                            }
                            is AppResult.Err -> {
                                item.errorMessage = ErrorTranslator.translate(r.error).message
                                item.status = ItemStatus.FAILED
                            }
                        }
                    }
                }
            }
            jobs.forEach { it.join() }
            running = false
            val ok = items.count { it.status == ItemStatus.DONE }
            val failed = items.count { it.status == ItemStatus.FAILED }
            snackbar.showSnackbar(
                Strings.UPSCALE_DONE_TOAST
                    .replace("%1", ok.toString())
                    .replace("%2", failed.toString()),
            )
        }
    }

    Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(Strings.UPSCALE_TITLE, style = MaterialTheme.typography.headlineSmall)
                Text(
                    Strings.UPSCALE_INTRO,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
            SkeuoOutlinedButton(onClick = onBack) { Text(Strings.BACK) }
        }

        Spacer(Modifier.height(16.dp))

        // ---------- Model choice (config-driven, AD-10) ----------
        Text(Strings.UPSCALE_MODEL_LABEL, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { opt ->
                FilterChip(
                    selected = selectedModel == opt.selectionKey(),
                    onClick = { selectedModel = opt.selectionKey() },
                    enabled = !running,
                    label = {
                        val est = service.estimate(opt)
                        val cost = if (est > 0) "  ·  " + Strings.UPSCALE_COST_PER_PHOTO
                            .replace("%1", "%.3f".format(Locale.US, est)) else ""
                        Text(opt.labelId + (if (!opt.tested) " ◦" else "") + cost)
                    },
                )
            }
        }
        option?.descId?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(4.dp))
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }

        Spacer(Modifier.height(16.dp))

        // ---------- Actions ----------
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeuoOutlinedButton(onClick = {
                pickPhotos.launch(
                    androidx.activity.result.PickVisualMediaRequest(
                        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly,
                    ),
                )
            }, enabled = !running) {
                Text((if (items.isEmpty()) Strings.UPSCALE_PICK else Strings.UPSCALE_ADD_MORE))
            }
            SkeuoButton(
                onClick = { startBatch(retryOnly = false) },
                enabled = !running && pending > 0 && option != null,
            ) {
                if (running) {
                    val done = items.count { it.status == ItemStatus.DONE || it.status == ItemStatus.FAILED }
                    Text(Strings.UPSCALE_RUNNING.replace("%1", done.toString()).replace("%2", items.size.toString()))
                } else {
                    Text(
                        Strings.UPSCALE_START
                            .replace("%1", pending.toString())
                            .replace("%2", "%.2f".format(Locale.US, perPhoto * pending)),
                    )
                }
            }
            if (!running && items.any { it.status == ItemStatus.FAILED }) {
                SkeuoOutlinedButton(onClick = { startBatch(retryOnly = true) }) {
                    Text(Strings.UPSCALE_RETRY_FAILED)
                }
            }
            if (items.any { it.status == ItemStatus.DONE }) {
                SkeuoOutlinedButton(onClick = {
                    id.kenang.app.ui.platform.AndroidActions.openGallery(context)
                }) { Text(Strings.UPSCALE_OPEN_OUTPUT) }
            }
            if (!running && items.isNotEmpty()) {
                TextButton(onClick = { items.clear() }) { Text(Strings.UPSCALE_CLEAR) }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            Strings.UPSCALE_RESULT_NOTE.replace("%1", "Galeri › Pictures/Kenang/Upscale"),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )

        Spacer(Modifier.height(16.dp))

        // ---------- Photo grid ----------
        if (items.isEmpty()) {
            SkeuoCard(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    Text(
                        Strings.UPSCALE_EMPTY,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            }
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items.forEach { item ->
                    ItemCard(
                        item = item,
                        busy = running,
                        onOpen = { if (item.status == ItemStatus.DONE) compare = item },
                        onRemove = { items.remove(item) },
                    )
                }
            }
        }
    }

    compare?.let { item -> CompareDialog(item) { compare = null } }
}

@Composable
private fun ItemCard(item: UpscaleItem, busy: Boolean, onOpen: () -> Unit, onRemove: () -> Unit) {
    Card {
        Column(Modifier.width(180.dp)) {
            // Show the RESULT once done — that's what the user paid for.
            val shownPath = item.result?.absolutePath ?: item.source.absolutePath
            val bmp by rememberFileBitmap(shownPath)
            Box(
                Modifier.fillMaxWidth().height(130.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(enabled = item.status == ItemStatus.DONE) { onOpen() },
                contentAlignment = Alignment.Center,
            ) {
                bmp?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                if (item.status == ItemStatus.RUNNING) {
                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                }
            }
            Column(Modifier.padding(8.dp)) {
                Text(item.source.name, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (item.status) {
                        ItemStatus.QUEUED -> StatusChip(Strings.UPSCALE_STATUS_QUEUED)
                        ItemStatus.RUNNING -> StatusChip(Strings.UPSCALE_STATUS_RUNNING, MaterialTheme.colorScheme.secondary)
                        ItemStatus.DONE -> StatusChip(Strings.UPSCALE_STATUS_DONE, androidx.compose.ui.graphics.Color(0xFF2E7D32))
                        ItemStatus.FAILED -> StatusChip(Strings.UPSCALE_STATUS_FAILED, MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.weight(1f))
                    if (!busy) {
                        TextButton(onClick = onRemove) { Text("✕") }
                    }
                }
                item.errorMessage?.let {
                    Text(
                        it, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error, maxLines = 2,
                    )
                }
            }
        }
    }
}

/** Side-by-side before/after so the user can judge what they got. */
@Composable
private fun CompareDialog(item: UpscaleItem, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier.fillMaxSize()
                .background(androidx.compose.ui.graphics.Color(0xE6050B14))
                .clickable { onDismiss() }
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(Strings.UPSCALE_COMPARE_BEFORE, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    val before by rememberFileBitmap(item.source.absolutePath)
                    before?.let { Image(it, null, contentScale = ContentScale.Fit, modifier = Modifier.weight(1f).fillMaxWidth()) }
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(Strings.UPSCALE_COMPARE_AFTER, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    val after by rememberFileBitmap(item.result?.absolutePath)
                    after?.let { Image(it, null, contentScale = ContentScale.Fit, modifier = Modifier.weight(1f).fillMaxWidth()) }
                }
            }
            Spacer(Modifier.height(12.dp))
            SkeuoOutlinedButton(onClick = onDismiss) { Text(Strings.CLOSE) }
        }
    }
}

// Photo picking goes through the Android photo picker (launcher above).
