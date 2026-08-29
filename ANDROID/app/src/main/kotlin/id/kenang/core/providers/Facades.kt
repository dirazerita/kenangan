package id.kenang.core.providers

import id.kenang.core.data.config.ConfigRepository
import id.kenang.core.providers.vault.KeyVault

/**
 * Routing facades (MASTER_PROMPT_02 §Provider layer): choose the fal-hosted
 * default vs a premium provider when the user has added the optional key.
 * Phase 03 (analysis) and Phase 04 (TTS) add the actual calls; Phase 02 owns
 * only the routing decision so the Settings UI can show what will be used.
 */
enum class AnalysisBackend { FAL_VLM, GEMINI }
enum class TtsBackend { FAL_MINIMAX, ELEVENLABS }

class AnalysisProvider(
    private val vault: KeyVault,
    private val configRepository: ConfigRepository,
) {
    /** Gemini key present → sharper analysis via the user's Google key. */
    fun activeBackend(): AnalysisBackend =
        if (vault.geminiKey() != null) AnalysisBackend.GEMINI else AnalysisBackend.FAL_VLM

    fun analysisModelSlug(): String = configRepository.current().analysis.slug
}

class TtsProvider(
    private val vault: KeyVault,
    private val configRepository: ConfigRepository,
) {
    /** ElevenLabs key present → premium voice path (paid EL plan required for id-ID). */
    fun activeBackend(): TtsBackend =
        if (vault.elevenLabsKey() != null) TtsBackend.ELEVENLABS else TtsBackend.FAL_MINIMAX

    fun defaultVoiceConfig() = configRepository.current().tts
}
