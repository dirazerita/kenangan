package id.kenang.app.di

import id.kenang.app.connectivity.ConnectivityMonitor
import id.kenang.core.common.DefaultDispatcherProvider
import id.kenang.core.common.DispatcherProvider
import id.kenang.core.common.events.GenerationEvents
import id.kenang.core.common.license.DevFullLicense
import id.kenang.core.common.license.LicenseGate
import id.kenang.core.data.AppDirs
import id.kenang.core.data.GenJobRepository
import id.kenang.core.data.MusicLibrary
import id.kenang.core.data.OutputRepository
import id.kenang.core.data.PhotoRepository
import id.kenang.core.data.ProjectRepository
import id.kenang.core.data.SceneRepository
import id.kenang.core.data.SettingsRepository
import id.kenang.core.data.config.ConfigRepository
import id.kenang.core.data.media.GalleryExporter
import id.kenang.core.data.story.AndroidRatioCropper
import id.kenang.core.data.story.RatioCropper
import id.kenang.core.data.media.VideoAssembler
import id.kenang.core.db.DatabaseFactory
import id.kenang.core.db.KenangDb
import id.kenang.core.providers.AnalysisProvider
import id.kenang.core.providers.CostTracker
import id.kenang.core.providers.KeyTester
import id.kenang.core.providers.PriceBook
import id.kenang.core.providers.TtsProvider
import id.kenang.core.providers.fal.FalBilling
import id.kenang.core.providers.fal.FalKeyPool
import id.kenang.core.providers.fal.FalQueueClient
import id.kenang.core.providers.fal.FalStorage
import id.kenang.core.providers.gen.AssemblyService
import id.kenang.core.providers.gen.ClipDownloader
import id.kenang.core.providers.gen.GenerationOrchestrator
import id.kenang.core.providers.gen.TtsService
import id.kenang.core.providers.motion.MotionControlService
import id.kenang.core.providers.optional.ElevenLabsClient
import id.kenang.core.providers.optional.GeminiClient
import id.kenang.core.providers.story.AnalysisService
import id.kenang.core.providers.story.CostEstimator
import id.kenang.core.providers.story.KeyframeService
import id.kenang.core.providers.story.TtsPreviewService
import id.kenang.core.providers.upscale.UpscaleService
import id.kenang.core.providers.voice.VoiceCloneService
import id.kenang.core.providers.vault.KeyVault
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import java.io.File

val appModule = module {
    single<DispatcherProvider> { DefaultDispatcherProvider() }

    // TODO(D-002): Phase 05 swaps DevFullLicense for the real Ed25519-backed gate.
    single<LicenseGate> { DevFullLicense() }

    single<KenangDb> { DatabaseFactory.create(androidContext()) }
    single { ConfigRepository() }
    single { SettingsRepository(get()) }
    single { ProjectRepository(get(), get()) }
    single { PhotoRepository(get(), get()) }
    single { SceneRepository(get(), get()) }
    single { GenerationEvents() }

    single {
        HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = 60_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 60_000
            }
        }
    }

    single { KeyVault.createDefault(File(AppDirs.config, "vault"), deviceSecret(androidContext())) }
    single { FalKeyPool(get()) }
    single { FalQueueClient(get(), get()) }
    single { FalBilling(get()) }
    single { GeminiClient(get()) }
    single { ElevenLabsClient(get()) }
    single { KeyTester(get(), get(), get(), get(), get()) }
    single { PriceBook(get()) }
    single { CostTracker(get(), get()) }
    single { AnalysisProvider(get(), get()) }
    single { TtsProvider(get(), get()) }
    single { ConnectivityMonitor() }

    // Phase 03 — storyboard engine services
    single<RatioCropper> { AndroidRatioCropper() }
    single { FalStorage(get(), get()) }
    single { AnalysisService(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    single { KeyframeService(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    single { TtsPreviewService(get(), get(), get(), get()) }
    single { VoiceCloneService(get(), get(), get(), get(), get(), get()) }
    single { MotionControlService(get(), get(), get(), get(), get(), get(), get()) }
    single { CostEstimator(get(), get(), get()) }

    // Phase 04 — video pipeline (generate → audio → assemble)
    single { GenJobRepository(get(), get()) }
    single { OutputRepository(get(), get()) }
    single { MusicLibrary(get()) }
    single { VideoAssembler(androidContext()) }
    single { GalleryExporter(androidContext()) }
    single { ClipDownloader(get()) }
    single { TtsService(get(), get(), get(), get(), get(), get()) }
    single { GenerationOrchestrator(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    single { AssemblyService(get(), get(), get(), get(), get(), get(), get(), get()) }

    // Standalone photo tool (owner 2026-09-01)
    single { UpscaleService(get(), get(), get(), get(), get(), get(), get()) }
}

/**
 * Device-bound identity for the encrypted key vault (AD-11). Not a user
 * identifier and never sent anywhere — it only derives the local AES key.
 */
@Suppress("HardwareIds")
private fun deviceSecret(context: android.content.Context): String {
    val androidId = runCatching {
        android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID,
        )
    }.getOrNull() ?: "kenang-device"
    return "kenang|$androidId|${context.packageName}"
}
