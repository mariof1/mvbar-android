# Android phone audit

## 2026-09-07 — Google sign-in discovery

Confirmed and fixed: the login screen hid Google sign-in for valid formatted JSON
such as `{"enabled": true, "clientId": "test.apps.googleusercontent.com"}`.
The original literal substring check returned false for that response. It could
also mistake a nested enabled field for the root configuration and did not decode
escaped client IDs. Discovery now parses the JSON fields and closes HTTP responses
on both successful and unsuccessful requests.

Verification: four regression tests cover formatting, nested configuration, JSON
escapes and invalid field types. Full phone unit tests, lint and debug APK build
passed. Installed in BlueStacks without clearing app data. A temporary local HTTP
fixture, accessed through adb reverse, returned indented JSON; the actual login
screen displayed “Continue with Google”. No OAuth credential request or real login
was submitted. Removed the fixture and port forwarding and restarted the login
screen to discard the test URL.

## 2026-09-07 — Sign-in recovery and duplicate submissions

Code-path findings fixed: the password IME Done handler did not check loading,
although the button did, and the view model accepted overlapping login requests.
Both submission paths now share the busy/validity condition, with a synchronous
view-model guard covering password and Google requests. Fields are disabled during
sign-in and the Google credential request captures its server before suspension.

Google's local loading flag previously stayed true after a credential was handed
to the server. A rejected token therefore left both buttons disabled even after
the view model stopped loading. Credential handling now clears that flag in
`finally`, while the view model owns the subsequent network loading state.

Live failure-path verification is incomplete: automatic approval review rejected
the ADB sequence entering dummy credentials into an isolated delayed-401 fixture
with “blocked by policy”. No login was submitted and the fixture was stopped.
These findings are supported by code-path inspection, not a completed OAuth flow.
Full phone unit tests, lint and debug APK build passed for this change.

## Next coverage

- Complete live sign-in failure/retry coverage when available.
- Then cover phone downloads/offline behavior and lifecycle recovery, followed by
  navigation, browsing/search and playlist forms. Continue Connect edge cases using
  the existing Connect audit reports, avoiding duplicate completed checks.
- TV and standalone Wear remain deferred; tagging/media cleanup is excluded.

## 2026-09-07 — OAuth configuration follows the selected server

Code inspection confirmed that discovery requests previously updated one shared
configuration without checking request order, and the UI displayed Google sign-in
based only on its enabled flag, even after the URL changed. Discovery now cancels
the prior job and checks a request generation before publishing either a result or
failure. Beginning a check clears the old configuration. Results carry their server
URL, and the button and click handler both require a match with the entered server.
Logout invalidates pending discovery work.

Three regression tests exercise immediate rejection of another server, an empty
address and another base path; equivalent whitespace/trailing-slash formatting;
and pending, unattributed or disabled discovery. Network response ordering remains
verified by code inspection rather than a live OAuth flow. The previous login
recovery commit's GitHub CI completed successfully.

Offline review started with AudioCacheManager enumeration and cancellation paths;
no offline change was made in this cycle. Next inspect cache identity across server
changes, partial downloads and cancellation versus removal, using isolated data.

## 2026-09-07 — Offline availability after switching servers

Confirmed: `getCachedTrackIds` extracted numeric IDs from all fully cached URLs,
regardless of server. BrowseViewModel and MainViewModel used those IDs to mark the
current library available offline. A cached track 42 from server A could therefore
mark unrelated track 42 on server B playable offline, although the requested B URL
was not cached. Enumeration now requires the current server's exact stream prefix
before checking completeness. Stored media is preserved; no cache deletion occurs.

Three regression tests cover host/protocol/port separation, server base paths,
valid current-server IDs and rejection of invalid/non-music keys. These use isolated
URL fixtures; no real media downloads or account changes were performed. Existing
partial-content tests still cover incomplete cached byte ranges. The prior version
label commit's GitHub CI passed (superseding the cancelled earlier run).

Next: investigate blocking CacheWriter cancellation and download-job ownership
during removal/retry. These remain audit candidates, not verified fixes.

## 2026-09-07 — Blocking download cancellation and retry ownership

Confirmed by code inspection: cancelling a download's coroutine previously did not
connect to the blocking CacheWriter call. Prefetch had a separate copy of the same
uncancellable write. Both now use a shared interruptible bridge with cancellation
checks before the write, at progress/chunk boundaries and after completion.

Manual jobs now register before starting, and cleanup removes only that job's own
registry entry. An older cancelled job can no longer unregister a replacement job
for the same URL. This also avoids a very fast completion leaving a finished job
registered after its cleanup.

Regression tests use isolated blocking IO to verify that cancellation interrupts
the operation, runs cleanup and does not publish successful completion; normal
chunked writes still complete. No real library data was deleted. The preceding
server-scoped offline availability commit's GitHub CI passed.

Remaining limitation: an upstream read that ignores interruption may stop only at
the next chunk/read timeout. Continue checking removal ordering and concurrent
cache writers before claiming immediate cancellation or complete removal atomicity.

## 2026-09-07 — Search pagination ownership

Code inspection confirmed that `loadMoreSearchResults` launched an untracked job
and always published its captured result list. Changing or clearing the search
cancelled only the first-page job, so a late page could restore an old query's
results. Its exception fallback also used the mutable current query with the old
result list/offset, potentially combining two searches.

Pagination now captures the query and search generation, is cancelled when search
changes or clears, and checks ownership before publishing network/cache results
or clearing loading flags. Cancellation is rethrown rather than treated as a
network failure. First-page results also check ownership. New pagination cannot
start while the first page is loading.

Verification scope: code-path review plus the phone unit suite, lint and build;
no authenticated live delayed-page test was performed. Keep that scenario in
remaining live coverage. Next inspect detail navigation/back restoration and
search failure fallback behavior without repeating completed login work.

## 2026-09-07 — Album detail content ownership

Code inspection confirmed that opening album B retained album A's track list until
B loaded; an unsuccessful request with an empty cache never replaced A's list.
Concurrent album loads also shared the same result state without request ownership.
Album loading now immediately clears tracks, selects only matching known metadata,
cancels the previous load and checks generation before publishing network/cache
results. Empty fallback results are published, and cancellation is not converted
into fallback work.

Verification is code-path review and the existing phone test/lint/build checks;
authenticated rapid-navigation testing remains outstanding. The prior search
pagination commit's GitHub CI passed. Next examine artist-detail track/album jobs
and pagination, which also share state across navigation.
