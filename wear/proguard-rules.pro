# Keep Wearable services entry points reachable for the system.
-keep class * extends com.google.android.gms.wearable.WearableListenerService { *; }
-keep class com.mvbar.android.wear.PhoneSyncService { *; }

# Tiles + Complications service plumbing
-keep class * extends androidx.wear.tiles.TileService { *; }
-keep class * extends androidx.wear.watchface.complications.datasource.ComplicationDataSourceService { *; }

# Compose / Wear Compose runtime — keep public API surface.
-dontwarn com.google.errorprone.annotations.**

# Retrofit
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>

# OkHttp
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# kotlinx.serialization — keep our serializable models + their Companions/serializers.
-keepclassmembers,includedescriptorclasses class com.mvbar.android.wear.net.** {
    *** Companion;
}
-keepclasseswithmembers class com.mvbar.android.wear.net.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.mvbar.android.wear.net.**$$serializer { *; }

