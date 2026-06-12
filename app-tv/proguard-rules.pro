# PureTV for Twitch — TV app ProGuard/R8 rules.
# Mirrors app-android's needs (Media3/ExoPlayer, Room, Koin, kotlinx.serialization)
# plus Compose for TV / Leanback specifics.

# Media3 / ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Compose for TV / Leanback
-keep class androidx.tv.** { *; }
-keep class androidx.leanback.** { *; }

# Room (generated DAO/entity implementations)
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Koin
-keep class org.koin.** { *; }
-dontwarn org.koin.**

# kotlinx.serialization models (Twitch Helix/GQL DTOs in `core`)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class com.puretv.twitch.core.** {
    *** Companion;
}
-keep,includedescriptorclasses class com.puretv.twitch.core.**$$serializer { *; }
-keepclassmembers class com.puretv.twitch.core.** {
    *** serializer(...);
}

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**

# Keep our own model/DTO classes (reflection-free, but names matter for serialization)
-keep class com.puretv.twitch.core.model.** { *; }
-keep class com.puretv.twitch.core.api.** { *; }
