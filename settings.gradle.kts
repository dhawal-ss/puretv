rootProject.name = "PureTVforTwitch"

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

include(":core")
include(":app-android")
include(":app-tv")
include(":app-windows")
