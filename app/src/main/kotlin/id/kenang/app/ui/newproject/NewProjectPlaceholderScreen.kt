package id.kenang.app.ui.newproject

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import id.kenang.core.common.i18n.Strings
import id.kenang.core.data.ProjectRepository
import id.kenang.core.data.config.ConfigRepository
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Placeholder route — Phase 03 owns the real input wizard. The sample-project
 * button exists so the Phase-02 DoD flow (create → kill → relaunch → delete)
 * can be exercised.
 */
@Composable
fun NewProjectPlaceholderScreen(onBack: () -> Unit) {
    val projects = koinInject<ProjectRepository>()
    val config = koinInject<ConfigRepository>()
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(Strings.NEW_PROJECT_PLACEHOLDER, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(24.dp))
        Button(onClick = {
            scope.launch {
                val cfg = config.current()
                projects.createPlaceholder(
                    name = "Proyek " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM HH:mm")),
                    ratio = "9:16",
                    vibe = cfg.vibes.firstOrNull()?.id ?: "asli",
                    tier = cfg.tierRouting.defaultTier,
                )
                onBack()
            }
        }) {
            Text("Buat proyek contoh")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onBack) { Text(Strings.BACK) }
    }
}
