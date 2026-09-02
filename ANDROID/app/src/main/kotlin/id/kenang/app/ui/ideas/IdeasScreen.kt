@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package id.kenang.app.ui.ideas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import id.kenang.app.ui.components.StatusChip
import id.kenang.app.ui.theme.SkeuoButton
import id.kenang.app.ui.theme.SkeuoCard
import id.kenang.app.ui.theme.SkeuoOutlinedButton
import id.kenang.core.common.i18n.Strings
import id.kenang.core.data.IdeaRepository
import id.kenang.core.db.Idea
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "Ide Produk" (owner 2026-09-02): inbox for product ideas coming in from
 * advertisers/resellers. Pure local capture-and-triage — no API calls.
 */
@Composable
fun IdeasScreen(
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
) {
    val repo = koinInject<IdeaRepository>()
    val scope = rememberCoroutineScope()

    var ideas by remember { mutableStateOf<List<Idea>>(emptyList()) }
    var statusFilter by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Idea?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Idea?>(null) }

    LaunchedEffect(Unit) { repo.observeIdeas().collect { ideas = it } }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(Strings.IDEAS_TITLE, style = MaterialTheme.typography.headlineSmall)
                Text(
                    Strings.IDEAS_SUBTITLE,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            Spacer(Modifier.weight(1f))
            SkeuoOutlinedButton(onClick = onBack) { Text(Strings.BACK) }
            Spacer(Modifier.width(8.dp))
            SkeuoButton(onClick = { editing = null; showForm = true }) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(6.dp))
                Text(Strings.IDEAS_ADD)
            }
        }
        Spacer(Modifier.height(12.dp))

        // Status filter chips: Semua + each status, with counts.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = statusFilter == null,
                onClick = { statusFilter = null },
                label = { Text(Strings.IDEAS_FILTER_ALL + " (${ideas.size})") },
            )
            Strings.IDEA_STATUSES.forEach { (key, label) ->
                val count = ideas.count { it.status == key }
                FilterChip(
                    selected = statusFilter == key,
                    onClick = { statusFilter = if (statusFilter == key) null else key },
                    label = { Text("$label ($count)") },
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        val visible = ideas.filter { statusFilter == null || it.status == statusFilter }
        if (visible.isEmpty()) {
            SkeuoCard(Modifier.fillMaxWidth()) {
                Text(
                    Strings.IDEAS_EMPTY,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(24.dp),
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(visible, key = { it.id }) { idea ->
                    IdeaCard(
                        idea = idea,
                        onSetStatus = { s -> scope.launch { repo.setStatus(idea.id, s) } },
                        onEdit = { editing = idea; showForm = true },
                        onDelete = { deleting = idea },
                    )
                }
            }
        }
    }

    if (showForm) {
        IdeaFormDialog(
            existing = editing,
            onSave = { title, desc, source, contact, category, priority ->
                scope.launch {
                    val target = editing
                    if (target == null) {
                        repo.add(title, desc, source, contact, category, priority)
                    } else {
                        repo.update(target.id, title, desc, source, contact, category, priority)
                    }
                    snackbar.showSnackbar(Strings.IDEAS_SAVED)
                }
                showForm = false
            },
            onDismiss = { showForm = false },
        )
    }

    deleting?.let { idea ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(Strings.IDEAS_DELETE_CONFIRM) },
            text = { Text(idea.title) },
            confirmButton = {
                SkeuoButton(onClick = {
                    scope.launch { repo.delete(idea.id) }
                    deleting = null
                }) { Text(Strings.HOME_DELETE_CONFIRM) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(Strings.CANCEL) } },
        )
    }
}

@Composable
private fun IdeaCard(
    idea: Idea,
    onSetStatus: (String) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    SkeuoCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(idea.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, Strings.IDEAS_FORM_TITLE_EDIT) }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, Strings.HOME_DELETE_CONFIRM, tint = MaterialTheme.colorScheme.error)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip(labelOf(Strings.IDEA_CATEGORIES, idea.category))
                StatusChip(
                    labelOf(Strings.IDEA_PRIORITIES, idea.priority),
                    when (idea.priority) {
                        "tinggi" -> MaterialTheme.colorScheme.error
                        "rendah" -> MaterialTheme.colorScheme.outline
                        else -> MaterialTheme.colorScheme.secondary
                    },
                )
                Text(
                    SimpleDateFormat("d MMM yyyy", Locale("id")).format(Date(idea.created_at)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }
            idea.source_name?.let { source ->
                Spacer(Modifier.height(4.dp))
                Text(
                    Strings.IDEAS_FROM_PREFIX + source + (idea.contact?.let { "  ·  $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
            idea.description?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(10.dp))
            // One-click status triage.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Strings.IDEA_STATUSES.forEach { (key, label) ->
                    FilterChip(
                        selected = idea.status == key,
                        onClick = { onSetStatus(key) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }
        }
    }
}

@Composable
private fun IdeaFormDialog(
    existing: Idea?,
    onSave: (title: String, desc: String, source: String, contact: String, category: String, priority: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var desc by remember { mutableStateOf(existing?.description ?: "") }
    var source by remember { mutableStateOf(existing?.source_name ?: "") }
    var contact by remember { mutableStateOf(existing?.contact ?: "") }
    var category by remember { mutableStateOf(existing?.category ?: Strings.IDEA_CATEGORIES.first().first) }
    var priority by remember { mutableStateOf(existing?.priority ?: "sedang") }
    var titleError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) Strings.IDEAS_FORM_TITLE_NEW else Strings.IDEAS_FORM_TITLE_EDIT) },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(120); titleError = false },
                    label = { Text(Strings.IDEAS_FIELD_TITLE) },
                    isError = titleError,
                    supportingText = { if (titleError) Text(Strings.IDEAS_NEED_TITLE) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = source,
                        onValueChange = { source = it.take(60) },
                        label = { Text(Strings.IDEAS_FIELD_SOURCE) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = contact,
                        onValueChange = { contact = it.take(60) },
                        label = { Text(Strings.IDEAS_FIELD_CONTACT) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(Strings.IDEAS_FIELD_CATEGORY, style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Strings.IDEA_CATEGORIES.forEach { (key, label) ->
                        FilterChip(selected = category == key, onClick = { category = key }, label = { Text(label) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(Strings.IDEAS_FIELD_PRIORITY, style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Strings.IDEA_PRIORITIES.forEach { (key, label) ->
                        FilterChip(selected = priority == key, onClick = { priority = key }, label = { Text(label) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it.take(1000) },
                    label = { Text(Strings.IDEAS_FIELD_DESC) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            SkeuoButton(onClick = {
                if (title.isBlank()) {
                    titleError = true
                } else {
                    onSave(title, desc, source, contact, category, priority)
                }
            }) { Text(Strings.SAVE) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.CANCEL) } },
    )
}

private fun labelOf(options: List<Pair<String, String>>, key: String): String =
    options.firstOrNull { it.first == key }?.second ?: key
