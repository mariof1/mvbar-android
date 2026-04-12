# Copilot instructions (mvbar-android)

## Build commands

### Android Studio (primary)
- Open the project root in Android Studio (Iguana+ recommended).
- Sync Gradle, then **Build > Make Project** or `./gradlew assembleDebug`.

### Command line
- Debug APK: `./gradlew assembleDebug`
- **Release APK** (preferred): `./gradlew assembleRelease` — always build release. The signing keystore `mvbar-release.jks` must be in the project root (gitignored, never commit it). If missing, copy from `C:\Users\mariusz.faldasz\StudioProjects\mvbar-android\mvbar-release.jks`.
- Install on device: `adb install -r app/build/outputs/apk/release/app-release.apk`
- Lint: `./gradlew lint`
- All checks: `./gradlew check`

### Gradle details
- Kotlin **2.0.10**, Compose compiler via `kotlin-compose` plugin, KSP for Room codegen.
- JDK **17** target. Min SDK **26**, target/compile SDK **34**.
- Compose BOM **2024.12.01** pins all Compose library versions.

## High-level architecture

### MVVM + Compose
The app follows **MVVM** with Jetpack Compose for the entire UI. ViewModels expose `StateFlow`; screens collect state via `collectAsState()`.

```
ui/screens/*  →  viewmodel/*  →  data/repository/*  →  data/api/ + data/local/
```

- **ViewModels**: `MainViewModel` (~29 KB, orchestrates player + app state), `BrowseViewModel` (~23 KB, library/browse), `AuthViewModel`, `AudiobookViewModel`, `PodcastViewModel`.
- **Repositories**: `AuthRepository` (token lifecycle, Google Sign-In), `MusicRepository` (browse, search, playlists).
- No DI framework — repositories and singletons are wired manually via the `MvbarApp` application class.

### Media playback (Media3 / ExoPlayer)
- `PlaybackService` (~100 KB) is the core — a `MediaLibraryService` that provides background playback, notification controls, and Android Auto browsing.
- `PlayerManager` wraps ExoPlayer state and exposes it to ViewModels.
- HLS streaming is supported via `media3-exoplayer-hls`; artwork loaded through `AuthBitmapLoader` which injects auth tokens.
- `AudioCacheManager` handles offline audio caching strategy.

### Android Auto & voice
- `PlaybackService` implements `MediaBrowserService` for Android Auto media browsing.
- `automotive_app_desc.xml` declares media use.
- `MainActivity` handles `MEDIA_PLAY_FROM_SEARCH` intents for Google Assistant voice commands.
- `ArtworkProvider` is a `ContentProvider` that serves album art URIs to external consumers (Auto, notifications).

### Data layer
- **Network**: Retrofit 2 + OkHttp with `kotlinx-serialization` JSON converter. `ApiClient` is a singleton that manages the base URL, auth token injection (both `Authorization` header and cookie), and token refresh.
- **Local DB**: Room with entities in `data/local/entity/`, DAOs in `data/local/dao/`, type converters in `Converters.kt`.
- **Offline resilience**: `ActivityQueue` queues user actions (plays, favorites) when offline and flushes on reconnect. `NetworkMonitor` observes connectivity.
- **Background sync**: `SyncWorker` + `FavoritesSyncWorker` via WorkManager handle periodic data sync.
- **Preferences**: `AaPreferences` wraps DataStore for user settings (server URL, tokens, playback prefs).

### Relationship to mvbar server
This app is the Android client for the **mvbar** self-hosted music server (`mariof1/mvbar`). Key integration points:
- API endpoints are under `/api/*` and `/rest/*` (Subsonic-compatible).
- Auth via JWT — the app stores the token and sends it as `Authorization: Bearer` header and as `mvbar_token` cookie (cookie needed for streaming routes that pass through Next.js).
- Streaming goes through `/api/stream/*` and `/api/hls/*`.
- WebSocket at `/api/ws` for live library update notifications.
- Meilisearch-powered search is consumed via `/api/search`.

## Key conventions

### Package structure
Feature screens live in `ui/screens/<feature>/` with a matching ViewModel in `viewmodel/`. Reusable Compose components go in `ui/components/`. Navigation is centralized in `ui/navigation/AppNavigation.kt` (~55 KB).

### State management
All reactive state uses Kotlin `StateFlow` (not LiveData). ViewModels use `MutableStateFlow` internally and expose read-only `StateFlow`. UI collects via `collectAsState()`.

### Networking pattern
All API calls go through `MvbarApi` (Retrofit interface) → called from repositories → consumed by ViewModels. The `ApiClient` singleton handles token injection and base URL configuration. Cleartext traffic is permitted (`network_security_config.xml`) since servers may run on local networks without TLS.

### Image loading
Coil with a custom auth-aware OkHttp client (configured in `MvbarApp`). Auth tokens are injected into image requests so artwork loads work behind authentication.

### Theme
Dark theme only — background `#0A0A0A`, accent cyan `#06B6D4`. Uses `Theme.Mvbar` (Material NoActionBar) with transparent status/navigation bars for edge-to-edge display.

## Android Auto testing

### Desktop Head Unit (DHU)
- DHU is installed at: `C:\Users\mariusz.faldasz\AppData\Local\Android\Sdk\extras\google\auto\desktop-head-unit.exe`
- The phone connects via ADB over WiFi. List devices with `adb devices`, then connect to the available device.
- To test Android Auto: start DHU with `desktop-head-unit.exe` while the phone is connected via ADB.
- The `PlaybackService` implements `MediaBrowserService` for Auto; browse tree and voice commands can be tested through DHU.

## Infrastructure context

- **Production server**: Runs on Raspberry Pi (`lanadmin@rpi5`) as a Portainer Docker stack.
- **Dev server**: `lanadmin@pxlin-dev01:/mvbar`.
- **Docker image**: `mars148/mvbar` on Docker Hub.
- **All changes** to this repo should be committed and pushed to GitHub.
