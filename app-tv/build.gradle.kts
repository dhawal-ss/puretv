import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Release signing credentials live in a gitignored keystore.properties at the
// repo root (see .gitignore). When it's absent (e.g. a contributor checkout or
// CI without the key), the release build stays UNSIGNED rather than failing.
// Only the machine that holds the key can cut an installable release.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) FileInputStream(keystorePropsFile).use { load(it) }
}
val hasReleaseSigning = keystorePropsFile.exists()

/**
 * SECTION 12.2 — Android TV build config. Same toolchain/minSdk as the
 * phone/tablet app but its own applicationId/namespace and a 10-foot UI.
 *
 * [CRITICAL] app-tv shares NO UI code with app-android intentionally — only
 * the `core` module. This lets the Leanback/D-pad experience be purpose-built
 * without compromising the touch-first phone UI (see Section 7).
 */
android {
    namespace = "com.puretv.twitch.tv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.puretv.twitch.tv"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a") }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Sign with the release key when it's present; unsigned otherwise.
            signingConfig = if (hasReleaseSigning) signingConfigs.getByName("release") else null
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // Kotlin/KSP default to the build JDK (17); javac is pinned to 11 above.
    // KSP fails the build on that mismatch (same break that kept app-android
    // out of CI), so pin Kotlin to 11 too.
    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures { compose = true }

    lint {
        // Work around a lint tooling crash (not a code defect): under this
        // Kotlin 2.0 / AGP 8.7 combo the NullSafeMutableLiveData detector
        // (NonNullableMutableLiveDataDetector) throws IncompatibleClassChangeError
        // while analysing AppSettingsStore.kt, which fails `lintVitalRelease` and
        // blocks the release APK. We don't use LiveData at all, so the check is
        // irrelevant here. checkReleaseBuilds=false is the belt-and-suspenders so
        // no other detector crash can block cutting a release.
        disable += "NullSafeMutableLiveData"
        abortOnError = false
        checkReleaseBuilds = false
    }
}

// PureTvTvDatabase declares exportSchema = true, so point Room's KSP processor
// at a checked-in schemas/ directory (mirrors app-android).
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":core"))

    // Compose for TV — purpose-built focusable components (Card, focus
    // restoration, scale-on-focus indication) instead of Material3 touch widgets.
    implementation(libs.compose.tv.foundation)
    implementation(libs.compose.tv.material)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.runtime)
    // Full Material icon set (Pause, Apps, Settings, etc.) used by the TV
    // player/nav chrome. R8 + shrinkResources strips the unused icons in release.
    implementation(compose.materialIconsExtended)
    implementation(libs.leanback)

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    // Provides koinViewModel() for Compose (mirrors app-android). koin.compose
    // alone doesn't expose the Android ViewModel-scoped variant the screens use.
    implementation(libs.koin.androidx.compose)

    implementation(libs.kotlinx.coroutines.android)

    // Ktor OkHttp engine: coreModule's shared HttpClient needs a platform engine
    // (mirrors app-android / core's android target). Without it the app compiles
    // but crashes at first network call: no HttpClientEngine on the classpath.
    implementation(libs.ktor.client.okhttp)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.media3.session)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.datastore.preferences)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    implementation(libs.coil.compose)
    implementation(libs.coil.network)

    // QR generation for the device-code login screen (encoder only, no Android
    // integration module needed; we render the BitMatrix to a Compose bitmap).
    implementation("com.google.zxing:core:3.5.3")
}
