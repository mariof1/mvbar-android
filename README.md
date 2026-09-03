# MVBar Android clients

This repository builds three independently installable clients that share one version and release key:

- `:app` — Android phone/tablet and Android Auto (`com.mvbar.android`)
- `:tv` — Android TV / Google TV (`com.mvbar.android.tv`)
- `:wear` — Wear OS companion (`com.mvbar.android.wear`)

Keeping the clients in one repository lets them share protocol and formatting code while retaining separate manifests, launchers, dependencies, and user interfaces.

## Android TV

The TV client is remote-first and currently supports:

- persistent email/password sign-in to an HTTPS or LAN HTTP MVBar server;
- personal recommendation buckets, recently added music, albums, favorites, podcasts, audiobooks, and playlists;
- collaborative playlist details plus create, add, remove, and delete actions where the account has permission;
- text and voice search, artist pages, album track lists, and unified artist/disc/track metadata;
- authenticated playback, a full-screen queue, shuffle/repeat/seek controls, audio focus, and remote media keys;
- song favorites, play-next, playlist, friend sharing, and radio actions from long-press menus;
- WebSocket-driven live library updates with a periodic refresh fallback; and
- an Android TV home channel with protected cached artwork and direct play/continue deep links.

Build a development APK:

```bash
./gradlew :tv:assembleDebug
```

The APK is written to `tv/build/outputs/apk/debug/tv-debug.apk`. Install it on a connected TV with:

```bash
adb install -r tv/build/outputs/apk/debug/tv-debug.apk
```

For a signed local release, supply the existing `MVBAR_RELEASE_*` signing values through `local.properties`, Gradle properties, or environment variables, then run:

```bash
./gradlew :tv:assembleRelease
```

The **Android APK** GitHub Actions workflow can build only `tv` for development testing. A tagged release always builds and publishes phone, TV, and Wear APKs together.
