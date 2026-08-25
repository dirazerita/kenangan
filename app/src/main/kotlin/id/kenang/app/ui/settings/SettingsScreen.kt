package id.kenang.app.ui.settings
import id.kenang.app.ui.theme.SkeuoButton
import id.kenang.app.ui.theme.SkeuoCard
import id.kenang.app.ui.theme.SkeuoOutlinedButton

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import id.kenang.app.ui.components.StatusChip
import id.kenang.app.ui.components.openInBrowser
import id.kenang.core.common.i18n.Strings
import id.kenang.core.data.SettingsRepository
import id.kenang.core.data.config.ConfigRepository
import id.kenang.core.providers.CostTracker
import id.kenang.core.providers.KeySpend
import id.kenang.core.providers.KeyTester
import id.kenang.core.providers.fal.FalKeyPool
import id.kenang.core.providers.fal.FalKeyStatus
import id.kenang.core.providers.vault.KeyVault
import org.koin.compose.koinInject

const val FAL_KEYS_URL = "https://fal.ai/dashboard/keys"
const val GEMINI_KEYS_URL = "https://aistudio.google.com/apikey"
const val ELEVENLABS_KEYS_URL = "https://elevenlabs.io/app/settings/api-keys"

@Composable
fun SettingsScreen(
    snackbar: SnackbarHostState,
    online: Boolean,
    onBack: () -> Unit,
    onReopenOnboarding: () -> Unit,
) {
    val vault = koinInject<KeyVault>()
    val pool = koinInject<FalKeyPool>()
    val tester = koinInject<KeyTester>()
    val settings = koinInject<SettingsRepository>()
    val configRepo = koinInject<ConfigRepository>()
    val costTracker = koinInject<CostTracker>()
    val scope = rememberCoroutineScope()

    val keyState = remember { KeyManagerState(vault, pool, tester, scope) }
    var perKeySpend by remember { mutableStateOf<List<KeySpend>>(emptyList()) }
    var outputFolder by remember { mutableStateOf(settings.outputFolder ?: "") }

    LaunchedEffect(Unit) { perKeySpend = costTracker.thisMonthPerKey() }

    Column(Modifier.fillMaxSize().padding(32.dp).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(Strings.SETTINGS_TITLE, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.weight(1f))
            SkeuoOutlinedButton(onClick = onBack) { Text(Strings.BACK) }
        }
        Spacer(Modifier.height(24.dp))

        // ------------------- API keys -------------------
        Text(Strings.SETTINGS_API_KEYS, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        FalKeysSection(keyState, online)
        Spacer(Modifier.height(16.dp))
        OptionalKeySection(
            title = Strings.KEYS_GEMINI_TITLE,
            description = Strings.KEYS_GEMINI_DESC,
            value = keyState.geminiKey,
            onValueChange = { keyState.geminiKey = it },
            testState = keyState.testResults["gemini"] ?: TestState.Idle,
            onSave = { keyState.saveGemini() },
            onTest = { keyState.testGemini() },
            openUrlLabel = Strings.KEYS_OPEN_GEMINI,
            openUrl = GEMINI_KEYS_URL,
            online = online,
        )
        Spacer(Modifier.height(16.dp))
        OptionalKeySection(
            title = Strings.KEYS_EL_TITLE,
            description = Strings.KEYS_EL_DESC,
            value = keyState.elevenLabsKey,
            onValueChange = { keyState.elevenLabsKey = it },
            testState = keyState.testResults["elevenlabs"] ?: TestState.Idle,
            onSave = { keyState.saveElevenLabs() },
            onTest = { keyState.testElevenLabs() },
            openUrlLabel = Strings.KEYS_OPEN_EL,
            openUrl = ELEVENLABS_KEYS_URL,
            online = online,
        )

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(24.dp))

        // ------------------- Per-key monthly spend (AD-14) -------------------
        Text(Strings.SETTINGS_SPEND_PER_KEY, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        if (perKeySpend.isEmpty()) {
            Text("—", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        } else {
            perKeySpend.forEach { spend ->
                Row(Modifier.padding(vertical = 2.dp)) {
                    Text(spend.keyLabel ?: "(tanpa label)", Modifier.width(240.dp))
                    Text("$" + "%.2f".format(spend.usd) + " (${Strings.ESTIMATE_LABEL})")
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(24.dp))

        // ------------------- General -------------------
        OutlinedTextField(
            value = outputFolder,
            onValueChange = {
                outputFolder = it
                settings.outputFolder = it
            },
            label = { Text(Strings.SETTINGS_OUTPUT_FOLDER) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(Strings.SETTINGS_LANGUAGE, Modifier.width(200.dp))
            Text(Strings.SETTINGS_LANGUAGE_ID) // ID only for now
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Telemetry placeholder — dormant until the opt-out backend exists (Phase 05).
            Checkbox(checked = false, onCheckedChange = null, enabled = false)
            Text(Strings.SETTINGS_TELEMETRY, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(Strings.SETTINGS_VERSION, Modifier.width(200.dp))
            val version = configRepo.current().latestVersion
            // Update check reads config min_version but stays dormant until the
            // remote config endpoint exists (Phase 01, AD-10).
            Text(version)
        }
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onReopenOnboarding) { Text(Strings.SETTINGS_REOPEN_ONBOARDING) }
    }
}

@Composable
fun FalKeysSection(state: KeyManagerState, online: Boolean) {
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(Strings.KEYS_FAL_TITLE, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                Strings.KEYS_FAL_DESC,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                Strings.ONBOARD_ANTI_FARM_NOTICE,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
            )
            Spacer(Modifier.height(12.dp))

            state.falKeys.forEachIndexed { index, key ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(Modifier.width(220.dp)) {
                        Text(key.label, style = MaterialTheme.typography.bodyMedium)
                        Text(key.masked, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                    val status = state.falStatuses[key.label] ?: FalKeyStatus.CADANGAN
                    StatusChip(
                        state.statusLabel(status),
                        color = when (status) {
                            FalKeyStatus.AKTIF -> Color(0xFF2E7D32)
                            FalKeyStatus.CADANGAN -> MaterialTheme.colorScheme.secondary
                            FalKeyStatus.SALDO_HABIS -> MaterialTheme.colorScheme.error
                        },
                    )
                    Spacer(Modifier.weight(1f))
                    TestResultView(state.testResults[key.label] ?: TestState.Idle)
                    TextButton(onClick = { state.testFalKey(key) }, enabled = online) {
                        Text(Strings.KEYS_TEST)
                    }
                    IconButton(onClick = { state.moveFalKey(key.label, -1) }, enabled = index > 0) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = Strings.KEYS_MOVE_UP)
                    }
                    IconButton(onClick = { state.moveFalKey(key.label, 1) }, enabled = index < state.falKeys.lastIndex) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = Strings.KEYS_MOVE_DOWN)
                    }
                    IconButton(onClick = { state.removeFalKey(key.label) }) {
                        Icon(Icons.Default.Delete, contentDescription = Strings.KEYS_REMOVE,
                            tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            AddFalKeyRow(state)
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { openInBrowser(FAL_KEYS_URL) }) { Text(Strings.KEYS_OPEN_FAL) }
            Text(
                Strings.KEYS_TEST_COST_NOTE,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun AddFalKeyRow(state: KeyManagerState) {
    var label by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = label, onValueChange = { label = it },
            label = { Text(Strings.KEYS_FAL_LABEL_HINT) },
            modifier = Modifier.width(240.dp), singleLine = true,
        )
        OutlinedTextField(
            value = key, onValueChange = { key = it },
            label = { Text(Strings.KEYS_KEY_HINT) },
            modifier = Modifier.weight(1f), singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )
        SkeuoButton(onClick = {
            if (state.addFalKey(label, key)) {
                label = ""; key = ""
            }
        }, enabled = key.isNotBlank()) {
            Text(Strings.KEYS_FAL_ADD)
        }
    }
}

@Composable
fun OptionalKeySection(
    title: String,
    description: String,
    value: String,
    onValueChange: (String) -> Unit,
    testState: TestState,
    onSave: () -> Unit,
    onTest: () -> Unit,
    openUrlLabel: String,
    openUrl: String,
    online: Boolean,
) {
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(description, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = value, onValueChange = onValueChange,
                    label = { Text(Strings.KEYS_KEY_HINT) },
                    modifier = Modifier.weight(1f), singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                SkeuoOutlinedButton(onClick = onSave) { Text(Strings.SAVE) }
                TestResultView(testState)
                TextButton(onClick = onTest, enabled = online && value.isNotBlank()) {
                    Text(Strings.KEYS_TEST)
                }
            }
            TextButton(onClick = { openInBrowser(openUrl) }) { Text(openUrlLabel) }
        }
    }
}

@Composable
fun TestResultView(state: TestState) {
    when (state) {
        TestState.Idle -> {}
        TestState.Testing -> CircularProgressIndicator(Modifier.width(20.dp).height(20.dp), strokeWidth = 2.dp)
        TestState.Ok -> StatusChip(Strings.KEYS_TEST_OK, color = Color(0xFF2E7D32))
        is TestState.Fail -> StatusChip(Strings.KEYS_TEST_FAIL, color = MaterialTheme.colorScheme.error)
    }
}
