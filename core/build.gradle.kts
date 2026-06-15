import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

// ── Twitch client secret (kept OUT of version control) ───────────────────────
// The secret is injected at build time from the `TWITCH_CLIENT_SECRET` env var
// (used by CI) or a gitignored `secrets.properties` (used locally), and falls
// back to a placeholder so a fresh clone still builds. The generated file lives
// under build/ (gitignored) and is never committed — so the public repo never
// carries the real secret.
val generateTwitchSecrets by tasks.registering {
    val outDir = layout.buildDirectory.dir("generated/twitchsecrets/kotlin")
    outputs.dir(outDir)
    val secretsFile = rootProject.layout.projectDirectory.file("secrets.properties").asFile
    doLast {
        val fromEnv = System.getenv("TWITCH_CLIENT_SECRET")?.takeIf { it.isNotBlank() }
        val fromProps = secretsFile.takeIf { it.exists() }?.let { file ->
            Properties().apply { file.inputStream().use { load(it) } }
                .getProperty("twitch.client.secret")?.takeIf { it.isNotBlank() }
        }
        val secret = fromEnv ?: fromProps ?: "PASTE_YOUR_CLIENT_SECRET_HERE"
        val pkgDir = outDir.get().dir("com/puretv/twitch/core/api").asFile
        pkgDir.mkdirs()
        File(pkgDir, "TwitchSecrets.kt").writeText(
            """
            |package com.puretv.twitch.core.api
            |
            |/** Generated — value injected from env TWITCH_CLIENT_SECRET or secrets.properties (both gitignored). */
            |internal object TwitchSecrets {
            |    const val CLIENT_SECRET = "$secret"
            |}
            |
            """.trimMargin(),
        )
    }
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    jvm("desktop")

    sourceSets {
        commonMain {
            kotlin.srcDir(generateTwitchSecrets)
            dependencies {
                implementation(libs.kotlin.stdlib)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)

                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.websockets)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.json)
                implementation(libs.ktor.client.logging)

                implementation(libs.koin.core)
            }
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.kotlinx.coroutines.android)
        }
        val desktopMain by getting {
            dependencies {
                implementation(libs.ktor.client.okhttp)
            }
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

android {
    namespace = "com.puretv.twitch.core"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
