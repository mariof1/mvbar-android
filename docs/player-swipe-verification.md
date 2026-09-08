# Portrait player gestures

The portrait player keeps its existing artwork and media controls on one vertical surface. The queue is visible below it and expands to 75% of the viewport. There is no compact replacement player or queue expansion button. Landscape retains its side-by-side layout.

- Drag up to reveal the queue; reverse direction while holding to follow the finger.
- Release to snap to the nearest full-player/expanded-queue position.
- Drag down from the expanded queue header to restore the full player. Within a list, downward scrolling reaches the list's top before moving the surface.
- A separate downward pull of 96 dp (or 15% of a shorter viewport) minimizes the full player. Collapsing the queue cannot also dismiss it in the same drag.
- Horizontal seeking and queue list scrolling retain their own gestures.
- The stationary backdrop uses the web player's 40 dp blur, 600 dp blur fade distance, and 480 dp scrim fade distance. Android 12+ blurs the app underneath; older Android versions retain the progressive dark scrim because Compose blur is unavailable there.

`player-swipe` instrumentation renders the actual portrait player with 30 fixture tracks and isolated playback callbacks. It checks held reversals, nearest-position snapping, queue height and scrolling, selecting a queue item, independent seeking, cancelled dismissal, progressive blur, and short-pull dismissal. It never calls the real playback or queue mutation functions.

```powershell
./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e scope player-swipe com.mvbar.android.test/com.mvbar.android.auto.AutoBrowseInstrumentation
```

Run on an idle emulator; instrumentation replaces the activity content temporarily and finishes the test activity afterward. The probe refreshes accessibility nodes before measuring moving elements and waits for settled callbacks on the UI thread instead of assuming animations finish within a fixed wall-clock delay.
