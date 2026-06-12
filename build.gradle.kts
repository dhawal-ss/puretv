// Root build file — PureTV for Twitch
// Plugins are applied per-module; this file just declares the version-catalog-driven
// plugin versions so subprojects can `apply` without re-resolving versions.
plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.android) apply false
    // Declaring kotlin.jvm here too — without it, Gradle complains that the
    // Kotlin plugin is "already on the classpath with an unknown version"
    // when app-windows applies kotlin.jvm and core has already pulled in
    // kotlin.multiplatform's classpath. Listing it apply-false aligns versions.
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.ksp) apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
