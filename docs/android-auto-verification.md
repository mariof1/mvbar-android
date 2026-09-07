# Android Auto verification — 2026-09-07

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

```powershell
. ./dev-env.ps1
./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest
adb -s DEVICE install -r app/build/outputs/apk/debug/app-debug.apk
adb -s DEVICE install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s DEVICE shell am instrument -w com.mvbar.android.test/com.mvbar.android.auto.AutoBrowseInstrumentation
```

Still unverified on DHU: MVBar launcher appearance, touch/rotary navigation,
voice search, actual audio, transport/seek controls, long-form playback, driving
restrictions and reconnection during playback. Media-browser success does not
substitute for these car-interface tests.

## BlueStacks limitation follow-up

Confirmed /system/etc/audio_policy.conf defines only primary, a2dp and usb audio
modules, with no remote_submix input/output route. Runtime audio-policy inspection
also showed no remote-submix route. This matches AudioRecord's -22 initialization
failure, rather than a networking problem: ADB and the DHU TLS handshake succeeded.
The documented DHU configuration exposes input, display and sensor controls but
no option that supplies the missing Android audio route. A compatible Android
image/emulator or physical phone is required for the remaining projected UI/audio
tests. The user cannot currently provide a physical phone.
