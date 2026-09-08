# Favourites drag regression verification

The drag gesture is owned by the LazyColumn so moving or recycling a keyed row cannot change its pointer coordinates or cancel the gesture. The highlighted row translates to retain the original grab position. Before a reorder, requestScrollToItem preserves the viewport index/offset instead of anchoring to the moved first key. Reorder calculations wait for layout to catch up. Idle state reconciles with the view model after a rejected/offline drop.

The emulator probe renders 30 fixture tracks with in-memory callbacks. It holds the first track, moves it down two rows and back, samples its position against the finger, checks the viewport anchor, verifies one saved order per gesture, and checks no pull-to-refresh occurs. It does not change server favourites or start playback.

Run after building and installing the debug app and test APK:

```text
adb -s emulator-5580 shell am instrument -w -e scope favorites-drag com.mvbar.android.test/com.mvbar.android.auto.AutoBrowseInstrumentation
```

Success returns INSTRUMENTATION_CODE: -1 and the stable-position/viewport/order result. Failures return an error and code 0.
