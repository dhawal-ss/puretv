plugins {
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.puretv.twitch.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.puretv.twitch.android"
        minSdk = 26       // Android 8.0 — required for ExoPlayer low-latency live mode
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a") } // skip x86 to cut APK size
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // signingConfig = signingConfigs.getByName("release") — wire up your own keystore
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
    // KSP fails the build on that mismatch, so pin Kotlin to 11 too. This is
    // the build break that kept app-android out of CI.
    kotlinOptions {
        jvmTarget = "11"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// PureTvDatabase declares exportSchema = true, so point Room's KSP processor at
// a checked-in schemas/ directory. This emits the version JSON that future
// Migration tests assert against; without it exportSchema is a silent no-op.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":core"))

    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.foundation)
    implementation(libs.compose.runtime)
    // Full Material icon set (send, fullscreen, shield, etc.) for the player and
    // chat chrome. R8 + shrinkResources strips the unused icons from release.
    implementation(compose.materialIconsExtended)
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    implementation(libs.koin.androidx.compose)

    implementation(libs.kotlinx.coroutines.android)

    // Ktor OkHttp engine: coreModule's shared HttpClient needs a platform engine
    // (mirrors app-windows / core's android target). Pulls ktor-client-core in.
    implementation(libs.ktor.client.okhttp)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)
    implementation(libs.media3.datasource.okhttp)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.datastore.preferences)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    implementation(libs.workmanager)
    implementation(libs.coil.compose)
    implementation(libs.coil.network)
}
