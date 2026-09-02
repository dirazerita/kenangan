@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

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
import androidx.compose.material3.FilterChip
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
import kotlinx.coroutines.launch
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
    val voiceCloneService = koinInject<id.kenang.core.providers.voice.VoiceCloneService>()
    val settings = koinInject<SettingsRepository>()
    val configRepo = koinInject<ConfigRepository>()
    val costTracker = koinInject<CostTracker>()
    val scope = rememberCoroutineScope()

    val billing = koinInject<id.kenang.core.providers.fal.FalBilling>()
    val keyState = remember { KeyManagerState(vault, pool, tester, billing, scope) }
    var perKeySpend by remember { mutableStateOf<List<KeySpend>>(emptyList()) }
    var outputFolder by remember { mutableStateOf(settings.outputFolder ?: "") }

    LaunchedEffect(Unit) { perKeySpend = costTracker.thisMonthPerKey() }

    // 32dp side padding eats a phone's width — 16dp here.
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
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

        // ------------------- Model AI (owner feature 2026-08-28) -------------------
        Text(Strings.SETTINGS_MODELS_TITLE, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            Strings.SETTINGS_MODELS_DESC,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(12.dp))
        run {
            val catalog = configRepo.current().modelCatalog
            var i2vSel by remember { mutableStateOf(settings.modelI2v) }
            var analysisSel by remember { mutableStateOf(settings.modelAnalysis) }
            var ttsSel by remember { mutableStateOf(settings.modelTts) }
            var voiceSel by remember { mutableStateOf(settings.defaultVoice) }

            ModelPicker(
                title = Strings.SETTINGS_MODEL_VIDEO,
                note = Strings.SETTINGS_MODEL_VIDEO_NOTE,
                options = catalog.i2v.map { Triple(it.selectionKey(), it.labelId, it.tested) },
                selected = i2vSel,
                onSelect = { i2vSel = it; settings.modelI2v = it },
            )
            ModelPicker(
                title = Strings.SETTINGS_MODEL_ANALYSIS,
                note = null,
                options = catalog.analysis.map { Triple(it.selectionKey(), it.labelId, it.tested) },
                selected = analysisSel,
                onSelect = { analysisSel = it; settings.modelAnalysis = it },
            )
            ModelPicker(
                title = Strings.SETTINGS_MODEL_TTS,
                note = null,
                options = catalog.tts.map { Triple(it.selectionKey(), it.labelId, it.tested) },
                selected = ttsSel,
                onSelect = { ttsSel = it; settings.modelTts = it },
            )
            ModelPicker(
                title = Strings.SETTINGS_MODEL_VOICE,
                note = null,
                // Cloned voices lead the list (owner 2026-09-02).
                options = voiceCloneService.cloned().map { Triple(it.voiceId, "🎤 " + it.label, true) } +
                    configRepo.current().tts.voices.map {
                        Triple(it.id, it.labelId + if (it.gender.isNotBlank()) " (${it.gender})" else "", true)
                    },
                selected = voiceSel,
                onSelect = { voiceSel = it; settings.defaultVoice = it },
            )
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(24.dp))

        // ------------------- Kloning Suara (owner 2026-09-02) -------------------
        Text(Strings.SETTINGS_CLONE_TITLE, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            Strings.SETTINGS_CLONE_DESC,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(12.dp))
        run {
            val context = androidx.compose.ui.platform.LocalContext.current
            val cloneScope = rememberCoroutineScope()
            var cloneLabel by remember { mutableStateOf("") }
            var cloning by remember { mutableStateOf(false) }
            var clonedList by remember { mutableStateOf(voiceCloneService.cloned()) }

            val audioPicker = androidx.activity.compose.rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.GetContent(),
            ) { uri ->
                if (uri != null) {
                    cloneScope.launch {
                        cloning = true
                        val copied = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            runCatching {
                                val mime = context.contentResolver.getType(uri) ?: "audio/mpeg"
                                val ext = when {
                                    mime.contains("wav") -> "wav"
                                    mime.contains("mp4") || mime.contains("m4a") -> "m4a"
                                    mime.contains("aac") -> "aac"
                                    mime.contains("ogg") -> "ogg"
                                    mime.contains("flac") -> "flac"
                                    else -> "mp3"
                                }
                                val f = java.io.File(context.cacheDir, "voice_sample.$ext")
                                context.contentResolver.openInputStream(uri)!!.use { input ->
                                    f.outputStream().use { input.copyTo(it) }
                                }
                                f
                            }.getOrNull()
                        }
                        if (copied == null) {
                            snackbar.showSnackbar("Gagal membaca file audio.")
                        } else {
                            when (val r = voiceCloneService.clone(copied, cloneLabel)) {
                                is id.kenang.core.common.AppResult.Ok -> {
                                    clonedList = voiceCloneService.cloned()
                                    cloneLabel = ""
                                    snackbar.showSnackbar(Strings.SETTINGS_CLONE_DONE + r.value.label)
                                }
                                is id.kenang.core.common.AppResult.Err ->
                                    snackbar.showSnackbar(
                                        id.kenang.core.common.ErrorTranslator.translate(r.error).message,
                                    )
                            }
                        }
                        cloning = false
                    }
                }
            }

            SkeuoCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = cloneLabel,
                        onValueChange = { cloneLabel = it.take(40) },
                        label = { Text(Strings.SETTINGS_CLONE_LABEL) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SkeuoButton(
                            onClick = { audioPicker.launch("audio/*") },
                            enabled = !cloning,
                        ) { Text(Strings.SETTINGS_CLONE_BUTTON + "  ±$" + "%.2f".format(voiceCloneService.estimateUsd())) }
                        if (cloning) {
                            CircularProgressIndicator(Modifier.width(20.dp).height(20.dp))
                            Text(Strings.SETTINGS_CLONE_RUNNING, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(Strings.SETTINGS_CLONE_LIST_TITLE, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    if (clonedList.isEmpty()) {
                        Text(
                            Strings.SETTINGS_CLONE_EMPTY,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                    } else {
                        clonedList.forEach { v ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🎤 " + v.label, style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.width(8.dp))
                                v.keyLabel?.let { StatusChip(it) }
                                Spacer(Modifier.weight(1f))
                                IconButton(onClick = {
                                    voiceCloneService.remove(v.voiceId)
                                    clonedList = voiceCloneService.cloned()
                                }) {
                                    Icon(Icons.Default.Delete, Strings.SB_DELETE_SCENE,
                                        tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                        Text(
                            Strings.SETTINGS_CLONE_KEY_NOTE,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        Strings.SETTINGS_CLONE_APPEARS,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(24.dp))

        // ------------------- Per-key monthly spend (AD-14) -------------------
        // Behind a button (owner 2026-08-28): the list got long, so it only
        // renders on demand.
        run {
            var showSpend by remember { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(Strings.SETTINGS_SPEND_PER_KEY, style = MaterialTheme.typography.titleMedium)
                SkeuoOutlinedButton(onClick = { showSpend = !showSpend }) {
                    Text(if (showSpend) Strings.SETTINGS_HIDE_SPEND else Strings.SETTINGS_SHOW_SPEND)
                }
            }
            if (showSpend) {
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
            }
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(24.dp))

        // ------------------- General -------------------
        // Android has no user-chosen output folder: finished videos go to the
        // gallery (Movies/Kenang/<project>) via MediaStore.
        Text(Strings.SETTINGS_OUTPUT_FOLDER, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            Strings.SET_OUTPUT_ANDROID,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
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

            // Phone layout: the desktop puts one key on a single wide row.
            // Here each key is a two-line block so nothing gets squeezed.
            state.falKeys.forEachIndexed { index, key ->
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Direct priority picker: "#1" = used first; failover walks down (AD-14).
                        var priorityMenu by remember(key.label) { mutableStateOf(false) }
                        androidx.compose.foundation.layout.Box {
                            TextButton(onClick = { priorityMenu = true }) {
                                Text("#${index + 1} ▾", style = MaterialTheme.typography.labelLarge)
                            }
                            androidx.compose.material3.DropdownMenu(
                                expanded = priorityMenu,
                                onDismissRequest = { priorityMenu = false },
                            ) {
                                state.falKeys.indices.forEach { pos ->
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text("${Strings.KEYS_PRIORITY} ${pos + 1}") },
                                        onClick = {
                                            priorityMenu = false
                                            state.moveFalKeyTo(key.label, pos)
                                        },
                                    )
                                }
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(key.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                            Text(key.masked, style = MaterialTheme.typography.bodySmall, maxLines = 1,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            BalanceLine(state.balances[key.label])
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
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TestResultView(state.testResults[key.label] ?: TestState.Idle)
                        TextButton(onClick = { state.testFalKey(key) }, enabled = online) {
                            Text(Strings.KEYS_TEST)
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { state.moveFalKey(key.label, -1) }, enabled = index > 0) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = Strings.KEYS_MOVE_UP)
                        }
                        IconButton(
                            onClick = { state.moveFalKey(key.label, 1) },
                            enabled = index < state.falKeys.lastIndex,
                        ) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = Strings.KEYS_MOVE_DOWN)
                        }
                        IconButton(onClick = { state.removeFalKey(key.label) }) {
                            Icon(Icons.Default.Delete, contentDescription = Strings.KEYS_REMOVE,
                                tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }
            }

            Spacer(Modifier.height(8.dp))
            AddFalKeyRow(state)
            Spacer(Modifier.height(12.dp))
            // "Cek Saldo": probe fal billing for every key and sum the results.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SkeuoButton(onClick = { state.checkAllBalances() }, enabled = online && !state.checkingBalances) {
                    Text(
                        if (state.checkingBalances) {
                            Strings.KEYS_CHECKING_ALL.replace("%1", state.falKeys.size.toString())
                        } else {
                            Strings.KEYS_CHECK_ALL_BALANCES
                        },
                    )
                }
                state.balanceSummary?.let { s ->
                    Text(
                        Strings.KEYS_BALANCE_TOTAL
                            .replace("%1", "%.2f %s".format(java.util.Locale.US, s.totalUsd, s.currency))
                            .replace("%2", s.readable.toString())
                            .replace("%3", s.unreadable.toString()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (s.readable > 0) Color(0xFF4CD97B)
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = { openInBrowser(FAL_KEYS_URL) }) { Text(Strings.KEYS_OPEN_FAL) }
            id.kenang.app.ui.components.CopyableUrl(FAL_KEYS_URL)
            Text(
                Strings.KEYS_BALANCE_ADMIN_HINT,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
            Text(
                Strings.KEYS_TEST_COST_NOTE,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
        }
    }
}

/**
 * One Model-AI picker row: a "Bawaan" chip (clears the override) plus one chip
 * per catalog option, wrapping to the window width. [options] = (key, label,
 * tested); untested options get a ◦ marker.
 */
@Composable
private fun ModelPicker(
    title: String,
    note: String?,
    options: List<Triple<String, String, Boolean>>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    Text(title, style = MaterialTheme.typography.titleSmall)
    note?.let {
        Text(it, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
    }
    Spacer(Modifier.height(6.dp))
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text(Strings.SETTINGS_MODEL_DEFAULT) },
        )
        options.forEach { (key, label, tested) ->
            FilterChip(
                selected = selected == key,
                onClick = { onSelect(key) },
                label = { Text(label + if (!tested) " ◦" else "") },
            )
        }
    }
    Spacer(Modifier.height(12.dp))
}

/** Remaining fal credit under a key's masked id, after "Tes koneksi". */
@Composable
private fun BalanceLine(balance: BalanceState?) {
    if (balance == null) return
    val (text, color) = when (balance) {
        BalanceState.Loading -> Strings.KEYS_BALANCE_CHECKING to
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        is BalanceState.Value -> (
            Strings.KEYS_BALANCE_PREFIX + "%.2f %s".format(java.util.Locale.US, balance.amount, balance.currency)
            ) to if (balance.amount <= 1.0) MaterialTheme.colorScheme.error else Color(0xFF4CD97B)
        BalanceState.NeedsAdminKey -> Strings.KEYS_BALANCE_NEEDS_ADMIN to
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
        BalanceState.Unavailable -> Strings.KEYS_BALANCE_UNAVAILABLE to
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
    }
    Text(text, style = MaterialTheme.typography.labelSmall, color = color)
}

@Composable
private fun AddFalKeyRow(state: KeyManagerState) {
    var label by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    // Phone layout: three side-by-side fields squeeze each other until the
    // labels render one letter per line — stack them instead.
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = label, onValueChange = { label = it },
            label = { Text(Strings.KEYS_FAL_LABEL_HINT) },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
        )
        OutlinedTextField(
            value = key, onValueChange = { key = it },
            label = { Text(Strings.KEYS_KEY_HINT) },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )
        SkeuoButton(
            onClick = {
                if (state.addFalKey(label, key)) {
                    label = ""; key = ""
                }
            },
            enabled = key.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
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
            OutlinedTextField(
                value = value, onValueChange = onValueChange,
                label = { Text(Strings.KEYS_KEY_HINT) },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SkeuoOutlinedButton(onClick = onSave) { Text(Strings.SAVE) }
                TestResultView(testState)
                TextButton(onClick = onTest, enabled = online && value.isNotBlank()) {
                    Text(Strings.KEYS_TEST)
                }
            }
            TextButton(onClick = { openInBrowser(openUrl) }) { Text(openUrlLabel) }
            id.kenang.app.ui.components.CopyableUrl(openUrl)
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
