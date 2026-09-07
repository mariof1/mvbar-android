# Connect remote media notification — 2026-09-07

Remote playback previously appeared only inside the app: PlaybackService exposed the
phone's local player to Android. A separate transport-only foreground service now
publishes the selected remote song and device through a platform media session and
media-style notification. It does not create a player or request audio focus.

Previous, play/pause, next, stop and session seek commands target the selected Connect
device. Pending notification actions carry the device ID so an action from an old
notification cannot operate a newly selected device. Local notifications are hidden
while remote music is selected and restored on return to local playback. Clearing the
remote track, deselecting the device, disconnecting or signing out tears down the
remote session. Android can retain the local session as its hardware-button target;
the local callback therefore also routes transport keys to the selected remote.

## Verified

- `:app:testDebugUnitTest :app:assembleDebug`: successful; 44 tests, no failures.
- Installed the final debug APK in BlueStacks (Android 9), preserving app data.
- Transferred a three-song queue from Android to an isolated browser receiver.
- Pressed Home, expanded Android's notification shade, and confirmed the song,
  artist, remote device name and previous/pause/next actions. No duplicate local
  playback notification was visible.
- Tapped next, pause, play and previous in the notification. Captured the commands
  arriving from Android at the browser; verified next-song and play/pause state.
- Repeated next, pause, play and previous using Android media-key events on the final
  APK, with the app in the background. Commands reached the browser.
- Closed the browser receiver: remote media session, service and notification were
  removed. The test cleared its local queue afterwards.
- No fatal exceptions or foreground-notification errors in inspected runtime logs.

Session seeking is implemented but not exercised through Android 9's compact
notification UI. Physical-headset behavior and newer Android notification layouts
still need device coverage. Podcast/audiobook transfer remains unsupported.

## CI lint follow-up

The initial notification commit failed `:app:lintDebug` with
`MissingOnPlayFromSearch`: the Android Auto detector also inspects the remote
transport-only callback. That callback now has a documented, method-scoped
exemption. It neither advertises search playback nor exposes an Auto browser
service; Android Auto remains handled by PlaybackService. No project-wide lint
rules or baselines were changed. Local `:app:testDebugUnitTest :app:lintDebug
:app:assembleDebug --build-cache --parallel --console=plain` passed after the fix.
