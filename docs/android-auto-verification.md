# Android Auto verification — 2026-09-07

## Official emulator follow-up — 2026-09-08

The BlueStacks blocker below has been bypassed using an official Android emulator.
Installed Android Emulator 37.1.11 and the Google Play Android 14 x86_64 image
(API 34, revision 14). Created `MVBar_Auto_API34` using the Pixel 5 profile,
3 GB RAM, software graphics and Windows Hypervisor Platform acceleration.
ADB serial: `emulator-5580`. Runtime audio policy exposes remote-submix input and
output routes and active AudioRecord clients during Android Auto playback.

Installed the existing MVBar debug APK (package version 1.1.30) and Android Auto
17.5.663218 using the Google Play-installed base/split APKs from BlueStacks.
Enabled Android Auto development mode, unknown sources for the debug app,
the head-unit server and notification access through Android settings.
Reconnected DHU after granting notification access to complete setup.
Signed MVBar into the configured development server on this new test device.

Live checks through the actual Windows DHU 2.0 interface passed:

- Android Auto dashboard and launcher render; MVBar has its full logo.
- MVBar's For You browse view loads and selecting Made For You starts a mix.
- Now Playing displays artwork, title and artist; playback position advances.
- DHU media-pause pauses playback (first observation: 15,575 ms).
- Next while paused changes to When It Was Good, index 1, paused at zero.
- Previous returns to Baby When the Light, index 0, paused at zero.
- DHU media-play resumes; its on-screen pause button pauses again (16,187 ms).
- Queue view displays the expected track order.
- Disconnect/reconnect restores MVBar on the dashboard. Android Auto's enabled
  Start music automatically setting resumes playback; it was paused again after
  verification.

DHU reported buffered audio on stream closure and Android showed active
remote-submix capture. No NO_AUDIO_CAPTURE or MVBar playback exception was
observed in the inspected logs. Audible output quality was not independently
assessed. This is an initial smoke test, not complete Android Auto certification:
voice, rotary input, driving restrictions, seek, long-form media and network-loss
edge cases still need projected-interface checks. Google Maps location setup
remains incomplete on this test emulator and is unrelated to MVBar playback.

Local evidence is in the web repository's ignored `.local` folder:
`auto-dhu-launcher2.png`, `auto-dhu-mvbar.png`, `auto-dhu-mix.png`,
`auto-dhu-next.png`, `auto-dhu-queue.png` and `auto-dhu-final.png`.
The login screen displayed 1.1.29 despite package metadata reporting 1.1.30;
this build/version-display discrepancy needs a separate check.

To resume on this workstation:

1. Start the local npm server using the desktop **Start MVBar** shortcut.
2. Start the existing AVD with the SDK emulator executable, for example
   `emulator -avd MVBar_Auto_API34 -port 5580 -gpu software -memory 3072`.
   Reuse it if `adb devices` already lists `emulator-5580`.
3. Open Android Auto settings on the emulator and use the overflow menu's
   **Start head unit server** if it is not already running.
4. Run `adb -s emulator-5580 forward tcp:5277 tcp:5277`, then start the SDK's
   `extras/google/auto/desktop-head-unit.exe --adb=5277`.
   For scripted verification use `--headless` and DHU console commands.

No BlueStacks system modifications were needed. Existing BlueStacks application
data was preserved. The emulator's MVBar instance is a separate Connect player.

## Live results

BlueStacks Android 9, MVBar debug 1.1.29, Android Auto installed through Google Play,
and SDK Desktop Head Unit 2.0 (Windows, build 2022-03-30-438482292).

The head-unit server connected over ADB port 5277. DHU negotiated protocol 1.7 and
TLS successfully. Completed Android Auto first-run dependencies (Google App,
Maps, text-to-speech) and setup. Reconnection still failed before a rendered car
interface with CAR_SERVICE_INIT_ERROR / NO_AUDIO_CAPTURE. Device logs show
REMOTE_SUBMIX AudioRecord initialization failing with status -22, followed by
Null AudioRecord; abandoning capture. Google Play services already has
CAPTURE_AUDIO_OUTPUT, MODIFY_AUDIO_ROUTING and RECORD_AUDIO permission.
This indicates a BlueStacks audio capture limitation. It is not a successful
head-unit UI/playback test and was not traced to MVBar. A physical Android phone
is needed to continue DHU verification. The temporary ADB forward was removed;
Android Auto and its setup remain installed for further testing.

Official DHU setup reference:
https://developer.android.com/training/cars/testing/dhu

## MVBar defect found and fixed

A read-only instrumentation probe connected through Android's legacy MediaBrowser
API, exercising the interface used by Android Auto. All 11 root browse categories
were discoverable with named items. However, onGetChildren ignored page/pageSize:
requesting 10 albums returned 100, and page 1 repeated those same 100. Other
categories repeated their whole result list as well.

The service now returns the requested slice, keeping the full internal browse
cache used for playback. Long arithmetic protects offset calculations; unbounded
legacy requests still receive all items in the existing result set. Three unit
tests cover distinct/short/empty pages, unbounded requests, invalid values and
overflow. This fixes paging within the current result set; existing category
fetch limits are unchanged and full-catalog enumeration is not established.

Rebuilt phone tests, lint, APK and instrumentation APK successfully. Installed
both and reran the live probe. Categories returned at most 10 children; album and
artist pages were distinct. All observed second pages had zero overlap, and
small categories correctly returned an empty second page. No playback commands
were sent by the probe.

## Reusable read-only probe

The instrumentation runner is AutoBrowseInstrumentation, included only in the
test APK. It checks a connected, signed-in MVBar app's browse tree without
starting media or editing library/account data. It emits JSON and returns
INSTRUMENTATION_CODE: -1 on success; code 0 includes an error on failure.
It also checks Media3 search pagination with the query `love`, which requires
more than five results in the test library. Add `-e scope search` to the
instrument command to skip the category walk and run only root/search checks.

The 2026-09-08 search regression confirmed repeated, oversized pages before the
fix (seven results for a five-item request, repeated on page 1). After the fix,
the complete announced result set contained 22 distinct items across pages of
5/5/5/5/2, with an empty terminal page and stable ordering at a different page
size. See android-audit-log.md for implementation and verification details.

The same test runner also accepts `-e scope login-version` for a phone UI version
check. This temporarily renders the real login form with inert callbacks,
without logging out, compares the visible label with installed package metadata,
and closes the temporary activity. It does not run the Auto browse/search probe
in this mode. Reopen MVBar normally afterward to return to the signed-in UI.

```powershell
. ./dev-env.ps1
./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest
adb -s DEVICE install -r app/build/outputs/apk/debug/app-debug.apk
adb -s DEVICE install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s DEVICE shell am instrument -w com.mvbar.android.test/com.mvbar.android.auto.AutoBrowseInstrumentation
```

The later official-emulator smoke tests verified the projected launcher, touch
browsing, music transport and reconnect. The 2026-09-08 long-form pass additionally
verified podcast and audiobook playback, on-screen and hardware 15-second seeking,
rewind clamping at zero, audiobook queue selection while paused and timeline
seeking. See android-audit-log.md for observations and evidence.

Still unverified: rotary navigation, voice search, audible quality, driving
restrictions, long-form reconnect/resume and network interruption. Media-browser
success does not substitute for projected car-interface tests.

## BlueStacks limitation follow-up

Confirmed /system/etc/audio_policy.conf defines only primary, a2dp and usb audio
modules, with no remote_submix input/output route. Runtime audio-policy inspection
also showed no remote-submix route. This matches AudioRecord's -22 initialization
failure, rather than a networking problem: ADB and the DHU TLS handshake succeeded.
The documented DHU configuration exposes input, display and sensor controls but
no option that supplies the missing Android audio route. A compatible Android
image/emulator or physical phone is required for the remaining projected UI/audio
tests. The user cannot currently provide a physical phone.
