-keepattributes *Annotation*,InnerClasses
-keep class com.mvbar.android.tv.data.** { *; }
-keepclassmembers class * { @retrofit2.http.* <methods>; }
-keep,includedescriptorclasses class com.mvbar.android.tv.**$$serializer { *; }
-keepclassmembers class com.mvbar.android.tv.** { *** Companion; }
-keepclasseswithmembers class com.mvbar.android.tv.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn androidx.media3.**
