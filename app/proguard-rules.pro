-keepattributes *Annotation*
-keep class com.mvbar.android.data.model.** { *; }
-keep class retrofit2.** { *; }
-keepclassmembers class * { @retrofit2.http.* <methods>; }

# kotlinx-serialization
-keepattributes InnerClasses
-keep,includedescriptorclasses class com.mvbar.android.**$$serializer { *; }
-keepclassmembers class com.mvbar.android.** {
    *** Companion;
}
-keepclasseswithmembers class com.mvbar.android.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class kotlinx.serialization.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Media3 / ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Coil
-keep class coil.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }

# ArtworkProvider (ContentProvider accessed by Android Auto)
-keep class com.mvbar.android.player.ArtworkProvider { *; }
-keep class com.mvbar.android.player.PlaybackService { *; }
