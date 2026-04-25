# Keep Wearable services entry points reachable for the system.
-keep class * extends com.google.android.gms.wearable.WearableListenerService { *; }
-keep class com.mvbar.android.wear.PhoneSyncService { *; }

# Tiles + Complications service plumbing
-keep class * extends androidx.wear.tiles.TileService { *; }
-keep class * extends androidx.wear.watchface.complications.datasource.ComplicationDataSourceService { *; }

# Compose / Wear Compose runtime — keep public API surface.
-dontwarn com.google.errorprone.annotations.**
