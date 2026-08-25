package id.kenang.app.ui.home

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import id.kenang.app.ui.components.ConfirmDialog
import id.kenang.app.ui.components.StatusChip
import id.kenang.core.common.i18n.Strings
import id.kenang.core.common.license.LicenseGate
import id.kenang.core.data.ProjectCard
import id.kenang.core.data.ProjectRepository
import id.kenang.core.providers.CostTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image as SkiaImage
import org.koin.compose.koinInject
import java.io.File

@Composable
fun HomeScreen(
    snackbar: SnackbarHostState,
    online: Boolean,
    onNewProject: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
) {
    val projects = koinInject<ProjectRepository>()
    val costTracker = koinInject<CostTracker>()
    val licenseGate = koinInject<LicenseGate>()
    val scope = rememberCoroutineScope()

    var cards by remember { mutableStateOf<List<ProjectCard>>(emptyList()) }
    var monthlySpendUsd by remember { mutableStateOf(0.0) }
    var deleteTarget by remember { mutableStateOf<ProjectCard?>(null) }

    suspend fun refresh() {
        cards = projects.listCards()
        monthlySpendUsd = costTracker.thisMonthTotalUsd()
    }

    LaunchedEffect(Unit) { refresh() }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(Strings.HOME_TITLE, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.width(12.dp))
            // License tag comes ONLY from LicenseGate (D-002).
            StatusChip(licenseGate.state().displayTag)
            Spacer(Modifier.weight(1f))
            Text(
                Strings.HOME_MONTHLY_SPEND_PREFIX + "$" + "%.2f".format(monthlySpendUsd),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            Spacer(Modifier.width(16.dp))
            IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = Strings.SETTINGS_TITLE) }
            IconButton(onClick = onAbout) { Icon(Icons.Default.Info, contentDescription = Strings.ABOUT_TITLE) }
        }

        Spacer(Modifier.height(16.dp))

        Button(onClick = onNewProject) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(Strings.HOME_NEW_PROJECT)
        }

        Spacer(Modifier.height(16.dp))

        if (cards.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    Strings.HOME_EMPTY,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 220.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(cards, key = { it.project.id }) { card ->
                    ProjectCardView(
                        card = card,
                        onDelete = { deleteTarget = card },
                    )
                }
            }
        }
    }

    deleteTarget?.let { target ->
        ConfirmDialog(
            title = Strings.HOME_DELETE_TITLE,
            message = Strings.HOME_DELETE_MESSAGE,
            confirmLabel = Strings.HOME_DELETE_CONFIRM,
            onConfirm = {
                deleteTarget = null
                scope.launch {
                    projects.delete(target.project.id)
                    refresh()
                }
            },
            onDismiss = { deleteTarget = null },
        )
    }
}

@Composable
private fun ProjectCardView(card: ProjectCard, onDelete: () -> Unit) {
    Card {
        Column {
            Box(
                Modifier.fillMaxWidth().height(140.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                val bitmap by produceThumbnail(card.thumbnailPath)
                bitmap?.let {
                    Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } ?: Text("🖼", style = MaterialTheme.typography.headlineLarge)
            }
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(card.project.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    Spacer(Modifier.height(4.dp))
                    StatusChip(statusLabel(card.project.status))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = Strings.HOME_DELETE_CONFIRM,
                        tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

private fun statusLabel(status: String): String = when (status) {
    "draft" -> Strings.STATUS_DRAFT
    "generating", "storyboard" -> Strings.STATUS_PROCESSING
    "done" -> Strings.STATUS_DONE
    "failed" -> Strings.STATUS_FAILED
    else -> status
}

@Composable
private fun produceThumbnail(path: String?) =
    androidx.compose.runtime.produceState<ImageBitmap?>(initialValue = null, path) {
        if (path == null) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = File(path).takeIf { it.isFile }?.readBytes() ?: return@runCatching null
                SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
            }.getOrNull()
        }
    }
