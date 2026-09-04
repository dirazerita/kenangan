import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.net.URI
import java.security.MessageDigest
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:data"))
    implementation(project(":core:db"))
    implementation(project(":core:providers"))

    implementation(compose.desktop.currentOs)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)

    implementation(libs.koin.core)
    implementation(libs.koin.compose)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.ktor.client.cio)

    testImplementation(libs.kotlin.test)
}

tasks.test {
    useJUnitPlatform()
}

// ---------------------------------------------------------------------------
// FFmpeg bundling (MASTER_PROMPT_02 §FFmpeg): download a PINNED release zip,
// verify sha256, stage ffmpeg.exe into app resources. First run copies it to
// %APPDATA%/Kenang/tools/ffmpeg (see FfmpegLocator). GPL attribution in About.
// Skip for quick dev builds with -PskipFfmpeg=true (app then runs without
// assembly support and FfmpegLocator reports unavailable).
// ---------------------------------------------------------------------------
val ffmpegUrl = "https://github.com/BtbN/FFmpeg-Builds/releases/download/autobuild-2026-08-24-13-10/ffmpeg-n8.1.2-44-g7c533d0f86-win64-gpl-8.1.zip"
val ffmpegSha256 = "5efb8182e0770c7af639ce46c229e5a5ea585884f17d2e94983051d8da90bdab"
val ffmpegDist = layout.projectDirectory.dir("ffmpeg-dist")
val skipFfmpeg = providers.gradleProperty("skipFfmpeg").getOrElse("false") == "true"
// Optional local cache so CI/dev doesn't re-download 160 MB (points at an
// already-downloaded copy of the SAME pinned zip; still sha256-verified).
val ffmpegLocalZip = providers.gradleProperty("ffmpegLocalZip").orNull

val downloadFfmpeg = tasks.register("downloadFfmpeg") {
    outputs.dir(ffmpegDist)
    onlyIf { !skipFfmpeg }
    doLast {
        val exeTarget = ffmpegDist.file("ffmpeg/ffmpeg.exe").asFile
        if (exeTarget.isFile) return@doLast

        val zipFile = File(temporaryDir, "ffmpeg.zip")
        val cached = ffmpegLocalZip?.let(::File)
        if (cached != null && cached.isFile) {
            cached.copyTo(zipFile, overwrite = true)
        } else {
            logger.lifecycle("Downloading pinned FFmpeg: $ffmpegUrl")
            URI(ffmpegUrl).toURL().openStream().use { input ->
                zipFile.outputStream().use { input.copyTo(it) }
            }
        }

        val digest = MessageDigest.getInstance("SHA-256")
        zipFile.inputStream().use { ins ->
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = ins.read(buf); if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        check(actual == ffmpegSha256) {
            "FFmpeg zip sha256 mismatch: expected $ffmpegSha256, got $actual"
        }

        exeTarget.parentFile.mkdirs()
        ZipFile(zipFile).use { zip ->
            val entry = zip.entries().asSequence().firstOrNull { it.name.endsWith("bin/ffmpeg.exe") }
                ?: error("ffmpeg.exe not found in $ffmpegUrl")
            zip.getInputStream(entry).use { input ->
                exeTarget.outputStream().use { input.copyTo(it) }
            }
        }
        zipFile.delete()
        logger.lifecycle("FFmpeg staged: ${exeTarget.absolutePath} (${exeTarget.length() / 1_000_000} MB)")
    }
}

tasks.processResources {
    dependsOn(downloadFfmpeg)
    from(ffmpegDist)
}

// Dev helper: `gradlew :app:run -PdevRoute=settings` opens straight on a screen
// (used for docs screenshots; no effect on packaged builds).
tasks.withType<JavaExec>().configureEach {
    providers.gradleProperty("devRoute").orNull?.let { systemProperty("kenang.devRoute", it) }
}

// Scripted Phase-03 demo against real provider APIs (docs/demo-03.md).
tasks.register<JavaExec>("demoDriver") {
    group = "verification"
    description = "Runs the Phase-03 scripted demo (REAL small API spend)"
    mainClass.set("id.kenang.app.devtools.DemoDriverKt")
    classpath = sourceSets["main"].runtimeClasspath
    providers.gradleProperty("demoPhotos").orNull?.let { systemProperty("demo.photos", it) }
}

// Scripted Phase-04 E2E against real provider APIs (docs/demo-04.md).
// Stages: -PdemoStage=a (kill mid-gen) | b:<projectId> (resume) | full[:ratio]
tasks.register<JavaExec>("demoDriver04") {
    group = "verification"
    description = "Runs the Phase-04 scripted E2E (REAL small API spend)"
    mainClass.set("id.kenang.app.devtools.DemoDriver04Kt")
    classpath = sourceSets["main"].runtimeClasspath
    providers.gradleProperty("demoPhotos").orNull?.let { systemProperty("demo.photos", it) }
    providers.gradleProperty("demoStage").orNull?.let { systemProperty("demo.stage", it) }
}

// Regenerates the app icon (icons/kenang.ico + resources/icon/kenang_512.png).
tasks.register<JavaExec>("generateIcon") {
    group = "build"
    description = "Draws the Kenang app icon (Java2D) and writes the .ico + PNG"
    mainClass.set("id.kenang.app.devtools.IconGeneratorKt")
    classpath = sourceSets["main"].runtimeClasspath
    systemProperty("icon.appDir", layout.projectDirectory.asFile.absolutePath)
}

// Diagnostic: re-run analysis on the newest stuck project with verbose logging.
tasks.register<JavaExec>("analysisDoctor") {
    group = "verification"
    description = "Re-runs analysis on a stuck project (~USD 0.01)"
    mainClass.set("id.kenang.app.devtools.AnalysisDoctorKt")
    classpath = sourceSets["main"].runtimeClasspath
    providers.gradleProperty("doctorProject").orNull?.let { systemProperty("doctor.project", it) }
    providers.gradleProperty("doctorStatusFix").orNull?.let { systemProperty("doctor.statusfix", it) }
}

// Diagnostic: verify router model ids before offering them in the catalog.
tasks.register<JavaExec>("modelDoctor") {
    group = "verification"
    description = "Pings candidate router model ids (1 token each)"
    mainClass.set("id.kenang.app.devtools.ModelDoctorKt")
    classpath = sourceSets["main"].runtimeClasspath
}

// Diagnostic: one real upscale job (~$0.002 default model). gradlew :app:upscaleDoctor
tasks.register<JavaExec>("upscaleDoctor") {
    group = "verification"
    description = "Runs one real upscale/restore job to verify the tool"
    mainClass.set("id.kenang.app.devtools.UpscaleDoctorKt")
    classpath = sourceSets["main"].runtimeClasspath
    providers.gradleProperty("doctorModel").orNull?.let { systemProperty("doctor.model", it) }
    providers.gradleProperty("doctorImage").orNull?.let { systemProperty("doctor.image", it) }
}

// Diagnostic: render the storyboard contact sheet headlessly (free, offline).
tasks.register<JavaExec>("sheetDoctor") {
    group = "verification"
    description = "Renders the newest project's storyboard sheet (no UI, no cost)"
    mainClass.set("id.kenang.app.devtools.SheetDoctorKt")
    classpath = sourceSets["main"].runtimeClasspath
    providers.gradleProperty("doctorProject").orNull?.let { systemProperty("doctor.project", it) }
}

// Diagnostic: test every stored fal key (~$0.001 each). gradlew :app:keyDoctor
tasks.register<JavaExec>("keyDoctor") {
    group = "verification"
    description = "Tests every stored fal key via the app's Tes koneksi"
    mainClass.set("id.kenang.app.devtools.KeyDoctorKt")
    classpath = sourceSets["main"].runtimeClasspath
}

compose.desktop {
    application {
        mainClass = "id.kenang.app.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "Kenang"
            packageVersion = providers.gradleProperty("appVersion").getOrElse("0.1.0")
            description = "Kenang (Beta) — hidupkan kenangan dari foto lama"
            vendor = "Kenang"
            copyright = "© 2026 Kenang. Includes FFmpeg (GPL) — https://ffmpeg.org"

            modules("java.sql", "java.naming", "java.net.http", "jdk.unsupported", "jdk.crypto.ec")

            windows {
                menuGroup = "Kenang"
                shortcut = true
                // Lay-user install (owner 2026-08-29): per-user (no admin/UAC
                // prompt) and no folder question — double-click and it's in.
                perUserInstall = true
                dirChooser = false
                // Custom icon (regenerate with :app:generateIcon).
                iconFile.set(project.file("icons/kenang.ico"))
                // Stable upgrade UUID so MSI updates replace older installs — never change.
                upgradeUuid = "7c1f4b3a-58d2-4e96-b1aa-c3958d20ef11"
            }
        }
    }
}
