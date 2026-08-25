package id.kenang.app.ui.onboarding

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import id.kenang.app.ui.components.openInBrowser
import id.kenang.app.ui.settings.FAL_KEYS_URL
import id.kenang.app.ui.settings.KeyManagerState
import id.kenang.app.ui.settings.TestResultView
import id.kenang.app.ui.settings.TestState
import id.kenang.core.common.i18n.Strings
import id.kenang.core.providers.KeyTester
import id.kenang.core.providers.fal.FalKeyPool
import id.kenang.core.providers.vault.FalKey
import id.kenang.core.providers.vault.KeyVault
import org.koin.compose.koinInject

/**
 * First-run wizard: 3 guided steps (MASTER_PROMPT_02 §API Key Manager).
 * Skippable; reopenable from Settings. Shows the BYOK responsibility notice
 * and the anti-credit-farming notice (MEMORY §7). NO license UI (D-002).
 */
@Composable
fun OnboardingScreen(
    snackbar: SnackbarHostState,
    onFinished: () -> Unit,
) {
    val vault = koinInject<KeyVault>()
    val pool = koinInject<FalKeyPool>()
    val tester = koinInject<KeyTester>()
    val scope = rememberCoroutineScope()
    val keyState = remember { KeyManagerState(vault, pool, tester, scope) }

    var step by remember { mutableStateOf(1) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(Modifier.widthIn(max = 640.dp).padding(24.dp)) {
            Column(Modifier.padding(32.dp).verticalScroll(rememberScrollState())) {
                Text(Strings.ONBOARD_TITLE, style = MaterialTheme.typography.headlineMedium)
                Text(
                    Strings.ONBOARD_SUBTITLE,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(8.dp))
                StepIndicator(step)
                Spacer(Modifier.height(16.dp))

                when (step) {
                    1 -> Step1()
                    2 -> Step2(keyState)
                    3 -> Step3()
                }

                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (step > 1) {
                        OutlinedButton(onClick = { step-- }) { Text(Strings.BACK) }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onFinished) { Text(Strings.SKIP) }
                    if (step < 3) {
                        Button(onClick = { step++ }) { Text(Strings.NEXT) }
                    } else {
                        Button(onClick = onFinished) { Text(Strings.DONE) }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepIndicator(step: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (1..3).forEach { i ->
            Box(
                Modifier.width(48.dp).height(6.dp)
                    .background(
                        if (i <= step) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(3.dp),
                    ),
            )
        }
    }
}

/** Placeholder illustration block — real screenshots land with docs polish. */
@Composable
private fun IllustrationPlaceholder(emoji: String) {
    Box(
        Modifier.fillMaxWidth().height(120.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, style = MaterialTheme.typography.displayMedium)
    }
}

@Composable
private fun Step1() {
    Column {
        Text(Strings.ONBOARD_STEP1_TITLE, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        IllustrationPlaceholder("🔑")
        Spacer(Modifier.height(8.dp))
        Text(Strings.ONBOARD_STEP1_BODY, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        Button(onClick = { openInBrowser(FAL_KEYS_URL) }) { Text(Strings.KEYS_OPEN_FAL) }
        Spacer(Modifier.height(16.dp))
        Text(
            Strings.ONBOARD_BYOK_NOTICE,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun Step2(keyState: KeyManagerState) {
    var pasted by remember { mutableStateOf("") }
    Column {
        Text(Strings.ONBOARD_STEP2_TITLE, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        IllustrationPlaceholder("📋")
        Spacer(Modifier.height(8.dp))
        Text(Strings.ONBOARD_STEP2_BODY, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))

        val existing = keyState.falKeys.firstOrNull()
        if (existing != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${existing.label}: ${existing.masked}")
                TestResultView(keyState.testResults[existing.label] ?: TestState.Idle)
                TextButton(onClick = { keyState.testFalKey(existing) }) { Text(Strings.KEYS_TEST) }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = pasted, onValueChange = { pasted = it },
                    label = { Text(Strings.KEYS_KEY_HINT) },
                    modifier = Modifier.weight(1f), singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                Button(
                    onClick = {
                        if (keyState.addFalKey("Utama", pasted)) {
                            keyState.testFalKey(FalKey("Utama", pasted.trim()))
                            pasted = ""
                        }
                    },
                    enabled = pasted.isNotBlank(),
                ) { Text(Strings.SAVE) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            Strings.KEYS_TEST_COST_NOTE,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            Strings.ONBOARD_ANTI_FARM_NOTICE,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
        )
    }
}

@Composable
private fun Step3() {
    Column {
        Text(Strings.ONBOARD_STEP3_TITLE, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        IllustrationPlaceholder("🎞")
        Spacer(Modifier.height(8.dp))
        Text(Strings.ONBOARD_STEP3_BODY, style = MaterialTheme.typography.bodyMedium)
    }
}
