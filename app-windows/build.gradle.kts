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
val appVersion = "1.15.0"

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
    // Ktor 3.1.1 brings Netty 4.1.118, which has published advisories (HTTP/1 request
    // smuggling, decoder DoS, HTTP/2 floods) fixed in 4.1.124 to 4.1.136. The proxy only
    // listens on 127.0.0.1, but a web page can still send it requests, so pin a patched
    // 4.1 release (same API) until Ktor ships one. Check with an OSV scan before raising.
    implementation(platform("io.netty:netty-bom:4.1.139.Final"))

    implementation(libs.koin.core)
    implementation(libs.koin.compose)

    // SECTION 08.2 [CRITICAL] — VLCJ wraps either the bundled VLC copy placed by
    // `bundleVlc` (packaged distributable) or the user's system VLC install (dev).
    implementation(libs.vlcj)
    // JNA for the libmpv bindings (MpvLibrary). vlcj also pulls JNA, but mpv owns its own declaration.
    implementation(libs.jna)

    implementation(libs.coil.compose)
    implementation(libs.coil.network)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    // Ktor MockEngine — lets desktop ViewModel tests drive core repositories (which hit
    // the real Twitch API base URL) against canned responses instead of the network.
    // Test-only; pinned to the same Ktor version as the rest of the catalog.
    testImplementation(libs.ktor.client.mock)
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
//
// A Sync, not a Copy: anything in resources/windows/vlc/ that the VLC install
// does not provide is deleted, so plugins left over from another VLC version
// (a 4.0 experiment once left 52 of them next to the 3.0.23 ones) can never be
// loaded alongside the real ones. Nothing else lives in that folder; mpv has
// its own sibling folder (bundleMpv).

// Which desktop OS this build runs on: each one packages its own installer (see the
// native runners in release.yml), so the bundling below is per OS too.
val buildOs: String = System.getProperty("os.name", "").lowercase().let {
    when {
        it.startsWith("windows") -> "windows"
        it.startsWith("mac") -> "macos"
        else -> "linux"
    }
}

val bundleVlc by tasks.registering(Sync::class) {
    description = "Syncs the local VLC into the app resources for self-contained packaging (Windows, macOS), removing stale files. Run once before createDistributable."
    // Linux uses the distro's libVLC, which the .deb depends on; nothing is bundled.
    onlyIf { buildOs != "linux" }

    if (buildOs == "macos") {
        // macOS: VLC.app's lib/ and plugins/ side by side, the layout vlcj's macOS discovery
        // expects (VLC_PLUGIN_PATH = lib/../plugins). Every plugin is kept: the app's size
        // does not matter, a missing demuxer or codec does.
        val app = listOf(System.getenv("VLC_APP"), "/Applications/VLC.app").filterNotNull().map { File(it) }
            .firstOrNull { File(it, "Contents/MacOS/lib/libvlc.dylib").exists() }
        if (app != null) {
            from(File(app, "Contents/MacOS/lib")) { include("*.dylib"); into("lib") }
            from(File(app, "Contents/MacOS/plugins")) { into("plugins") }
        }
        into(layout.projectDirectory.dir("resources/macos-arm64/vlc"))
        doFirst {
            if (app == null) throw GradleException("VLC.app not found. Install VLC (brew install --cask vlc) or set VLC_APP.")
            println("[bundleVlc] Copying VLC from: ${app.absolutePath}")
        }
        return@registering
    }

    val vlcDir = listOf(
        System.getenv("VLC_DIR"),
        "C:/Program Files/VideoLAN/VLC",
        "C:/Program Files (x86)/VideoLAN/VLC",
    ).filterNotNull().map { File(it) }
     .firstOrNull { it.exists() && File(it, "libvlc.dll").exists() }

    if (vlcDir != null) {
        // Core DLLs, and the tool the installer runs to write libVLC's plugin cache
        // (puretv.iss): without the cache libVLC opens every plugin DLL on each start.
        from(vlcDir) { include("libvlc.dll", "libvlccore.dll", "vlc-cache-gen.exe") }
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
        if (buildOs == "linux") return@doFirst
        if (vlcDir == null) {
            throw GradleException(
                "VLC not found. Install VLC from https://videolan.org/vlc/ or set the VLC_DIR env var.",
            )
        }
        println("[bundleVlc] Copying VLC from: ${vlcDir.absolutePath}")
    }
}

// ── mpv bundling ───────────────────────────────────────────────────────────────
// Mirrors bundleVlc: copies the libmpv runtime + the Anime4K shader into
// resources/windows/mpv/ (gitignored) for self-contained packaging. The DLL comes
// from the MPV_DIR env var (set locally or by the release workflow); the shader is
// committed in app-windows/shaders/. MpvPlayer reads compose.application.resources.dir
// at runtime to find this dir. Run as a SEPARATE Gradle invocation before
// createReleaseDistributable (same undeclared-dependency reason as bundleVlc).
val bundleMpv by tasks.registering(Copy::class) {
    description = "Copies libmpv-2.dll (from MPV_DIR) + the Anime4K shader into resources/windows/mpv/ for packaging."
    val mpvDir = System.getenv("MPV_DIR")?.let { File(it) }?.takeIf { File(it, "libmpv-2.dll").exists() }
    if (mpvDir != null) {
        from(mpvDir) { include("libmpv-2.dll") }
    }
    from(layout.projectDirectory.dir("shaders")) { include("*.glsl"); into("shaders") }
    into(layout.projectDirectory.dir("resources/windows/mpv"))
    doFirst {
        if (mpvDir == null) {
            throw GradleException("libmpv not found. Download a libmpv build from https://mpv.io/installation/ (or the shinchiro Windows builds) and set the MPV_DIR env var to the directory containing libmpv-2.dll.")
        }
        println("[bundleMpv] Copying libmpv from: ${mpvDir.absolutePath}")
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
            // The bundleVlc task populates resources/windows/vlc/ with VLC DLLs, and
            // bundleMpv populates resources/windows/mpv/ with libmpv + the Anime4K shader.
            appResourcesRootDir.set(layout.projectDirectory.dir("resources"))

            // jpackage bundles a JVM so end users do not need Java pre-installed, and only
            // builds the formats of the OS it runs on. MSI and EXE need WiX Toolset 3.x
            // (the shipped Windows installer is Inno Setup over the app image, see
            // release.yml). The macOS .dmg is made by release.yml too, after an ad-hoc
            // signature, since there is no Developer ID to let jpackage sign it.
            targetFormats(TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "PureTV for Twitch"
            packageVersion = appVersion
            description = "Ad-free Twitch viewing on the desktop."
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
            macOS {
                iconFile.set(project.file("packaging/icon.icns"))
                bundleID = "com.puretv.twitch"
                dockName = "PureTV"
                appCategory = "public.app-category.entertainment"
                minimumSystemVersion = "12.0"
            }
            linux {
                iconFile.set(project.file("packaging/icon.png"))
                // Debian package names are lower case without spaces.
                packageName = "puretv-for-twitch"
                debMaintainer = "dhawal-ss@users.noreply.github.com"
                appCategory = "video"
                menuGroup = "AudioVideo;Video;Player;"
                shortcut = true
            }
        }

        // Media UI GC tuning. The old -Xmx512m + default G1 (200ms pause target) meant a
        // stop-the-world could drop ~12 frames = a visible hitch, and the small heap filled
        // fast (Coil's decoded-image cache alone is a fraction of it), forcing frequent GCs.
        // Native memory (VLC/mpv, skia bitmaps) is off-heap and unaffected by -Xmx; this only
        // bounds on-heap churn (Compose, chat, image cache):
        //  - more headroom so collections are rarer,
        //  - a 50ms pause target so a collection costs ~3 frames, not ~12,
        //  - string dedup (chat produces many duplicate short strings).
        jvmArgs += listOf(
            "-Xms256m",
            // 1536m (was 1024m): leaves headroom for the in-app updater, which Ed25519-verifies
            // the ~170MB installer. Pure Ed25519 buffers the whole message internally, so verify
            // needs a couple hundred MB transient on top of the running UI's working set; at
            // 1024m that tipped over into OutOfMemoryError mid-update. Native VLC/mpv is off-heap.
            "-Xmx1536m",
            "-XX:+UseG1GC",
            "-XX:MaxGCPauseMillis=50",
            "-XX:+UseStringDeduplication",
        )
    }
}
