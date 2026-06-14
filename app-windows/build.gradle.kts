import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

// ── App version (single source of truth) ─────────────────────────────────────
// Drives both the MSI `packageVersion` AND the generated AppBuildConfig the
// in-app updater compares against GitHub Releases — so the two can never drift.
val appVersion = "1.1.0"

val generateAppBuildConfig by tasks.registering {
    val outDir = layout.buildDirectory.dir("generated/buildconfig/kotlin")
    outputs.dir(outDir)
    val versionValue = appVersion
    doLast {
        val pkgDir = outDir.get().dir("com/puretv/twitch/desktop").asFile
        pkgDir.mkdirs()
        File(pkgDir, "AppBuildConfig.kt").writeText(
            """
            |package com.puretv.twitch.desktop
            |
            |/** Generated from build.gradle.kts `appVersion` — do not edit by hand. */
            |object AppBuildConfig {
            |    const val VERSION = "$versionValue"
            |    const val GITHUB_OWNER = "dhawal-ss"
            |    const val GITHUB_REPO = "puretv"
            |}
            |
            """.trimMargin(),
        )
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
    sourceSets["main"].kotlin.srcDir(generateAppBuildConfig)
}

dependencies {
    // SECTION 12.4 — consumes `core`'s `jvm("desktop")` target. Gradle resolves
    // the matching JVM variant automatically; the target's name ("desktop")
    // doesn't have to match this module's name, only its KotlinPlatformType.
    implementation(project(":core"))

    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    // SECTION 08.4 — `Icons.Filled.Tv`/`Login`/`Send`/`VolumeUp` live in the
    // extended icon pack, not `material-icons-core` (which ships with material3).
    implementation(compose.materialIconsExtended)
    implementation(compose.foundation)
    implementation(compose.runtime)
    implementation(compose.ui)
    implementation(compose.components.resources)

    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    // Networking — same Ktor client engine `core` uses on its desktop target.
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.websockets)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.client.logging)

    // SECTION 08.3 [CRITICAL] — embedded Ktor server backing `LocalStreamProxy`
    // (port 7979, ad-blocked m3u8 delivery to VLCJ) and the OAuth callback
    // listener (port 3000, Section 03.2/10).
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)

    implementation(libs.koin.core)
    implementation(libs.koin.compose)

    // SECTION 08.2 [CRITICAL] — VLCJ wraps either the bundled VLC copy placed by
    // `bundleVlc` (packaged distributable) or the user's system VLC install (dev).
    implementation(libs.vlcj)

    implementation(libs.coil.compose)
    implementation(libs.coil.network)

    testImplementation(libs.kotlin.test)
}

// ── VLC bundling ─────────────────────────────────────────────────────────────
// Run `gradle :app-windows:bundleVlc` ONCE before packaging to copy the
// local VLC installation's essential DLLs into resources/windows/vlc/
// (gitignored).  Only the plugins needed for HLS/live streaming are included,
// keeping the bundle to ~95 MB instead of the full ~180 MB VLC install.
//
// VlcPlayer.kt reads compose.application.resources.dir at runtime to find the
// bundled VLC directory, so packaged users need no separate VLC installation.
// Development builds (gradle run) still fall back to the system VLC.

val bundleVlc by tasks.registering(Copy::class) {
    description = "Copies local VLC files into resources/windows/vlc/ for self-contained packaging. Run once before packageMsi/createDistributable."

    val vlcDir = listOf(
        System.getenv("VLC_DIR"),
        "C:/Program Files/VideoLAN/VLC",
        "C:/Program Files (x86)/VideoLAN/VLC",
    ).filterNotNull().map { File(it) }
     .firstOrNull { it.exists() && File(it, "libvlc.dll").exists() }

    if (vlcDir != null) {
        // Core DLLs
        from(vlcDir) { include("libvlc.dll", "libvlccore.dll") }
        // Only the plugin categories needed for HLS live-stream playback on Windows.
        // Excluded: gui (skins), stream_out, visualization, meta_engine,
        // services_discovery, access_output, mux, keystore, logger, video_splitter.
        from(vlcDir) {
            include(
                "plugins/codec/**",           // H.264 / AAC decoding
                "plugins/access/**",          // HTTP + HLS access
                "plugins/demux/**",           // MPEG-TS / MP4 demuxing
                "plugins/video_output/**",    // Windows video rendering
                "plugins/audio_output/**",    // Windows audio (WASAPI / DirectSound)
                "plugins/audio_filter/**",    // Resampler, volume normalizer
                "plugins/audio_mixer/**",     // Float mixer
                "plugins/video_chroma/**",    // YUV→RGB colour conversion
                "plugins/packetizer/**",      // H.264 packetiser
                "plugins/stream_filter/**",   // HTTPS / m3u8 handling
                "plugins/misc/**",            // Logger, stats, housekeeping
                "plugins/d3d11/**",           // Direct3D 11 output helper
                "plugins/d3d9/**",            // Direct3D 9 fallback
                "plugins/lua/**",             // Lua scripting (playlist / codec detection)
                "plugins/control/**",         // Hotkey / RC control interface
                "plugins/stream_extractor/**",// ZIP / RAR stream extraction
                "plugins/spu/**",             // Sub-picture units (OSD, subtitles)
                "plugins/text_renderer/**",   // FreeType text renderer
                "lua/**",                     // VLC Lua scripts directory
            )
        }
    }

    into(layout.projectDirectory.dir("resources/windows/vlc"))

    doFirst {
        if (vlcDir == null) {
            throw GradleException(
                "VLC not found. Install VLC from https://videolan.org/vlc/ or set the VLC_DIR env var.",
            )
        }
        println("[bundleVlc] Copying VLC from: ${vlcDir.absolutePath}")
    }
}

compose.desktop {
    application {
        mainClass = "com.puretv.twitch.desktop.MainKt"

        // Disable ProGuard/R8 for the release distributable. This app relies on
        // reflection (Koin, Ktor, kotlinx.serialization), where minification is
        // both risky at runtime and fails the build outright on optional missing
        // classes (Netty's lz4 / bouncycastle / tcnative). The bundle is mostly
        // VLC anyway, so the size saving would be negligible.
        buildTypes.release.proguard {
            isEnabled.set(false)
        }

        nativeDistributions {
            // Bundle the FULL JDK module set into the runtime image. jlink's
            // automatic module detection misses java.net.http (used by the
            // updater) and likely other reflectively-loaded modules (Ktor/Netty
            // TLS, crypto), which makes the packaged app die at launch with
            // "Failed to launch JVM" / NoClassDefFoundError. The size cost is
            // small next to the bundled VLC.
            includeAllModules = true

            // Extra resources directory: files in resources/windows/ are copied
            // into the distributable's resources/ folder and available at runtime
            // via System.getProperty("compose.application.resources.dir").
            // The bundleVlc task populates resources/windows/vlc/ with VLC DLLs.
            appResourcesRootDir.set(layout.projectDirectory.dir("resources"))

            // SECTION 12.4 — both installer formats per the spec; jpackage
            // bundles a JVM so end users do not need Java pre-installed.
            // MSI and EXE both require WiX Toolset 3.x on the PATH.
            // Download portable WiX: https://github.com/wixtoolset/wix3/releases
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "PureTV for Twitch"
            packageVersion = appVersion
            description = "Ad-free Twitch viewing for Windows — desktop companion to PureTV for Twitch."
            copyright = "© 2026 PureTV for Twitch. Unaffiliated with Twitch Interactive, Inc."
            vendor = "PureTV for Twitch"

            windows {
                // icon.ico generated by scripts/generate-icon.ps1 and committed to repo.
                // PNG-in-ICO format (Windows Vista+): single 256×256 entry.
                iconFile.set(project.file("src/main/resources/icon.ico"))
                menuGroup = "PureTV for Twitch"
                // PRODUCTION UPGRADE GUID — DO NOT CHANGE.
                // Windows Installer uses this to identify "the same product family"
                // across versions. Changing it post-release makes the next installer
                // install side-by-side with the old version instead of upgrading it,
                // leaving the old build stuck in Add/Remove Programs.
                upgradeUuid = "a75efb70-c7e9-424a-99b8-b6d9a98a0799"
                dirChooser = true
                perUserInstall = true
                shortcut = true
            }
        }

        jvmArgs += listOf("-Xmx512m", "-Xms128m")
    }
}
