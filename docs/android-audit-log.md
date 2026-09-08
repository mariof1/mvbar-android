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

## 2026-09-07 — Artist details and pagination

Code-path review confirmed that track, album and pagination requests could publish
after another artist was selected, including writing the old artist's metadata
into the new selection. Changing artists now clears old tracks and disables
pagination until the first result, cancels both detail children and the page job,
and advances request ownership. Each network/cache result checks that ownership.
Pagination captures its artist name and ID; cancelled work cannot trigger offline
fallback or clear a newer page's loading indicator.

Validation uses the existing phone tests, lint and APK build; authenticated rapid
artist-switching remains pending live coverage. The preceding album fix's CI
passed. Next audit genre/country/language detail pagination and visible loading or
error feedback, then move beyond request ownership to other UX functions.

## 2026-09-07 — Genre, country and language pagination

The same unowned-request pattern was confirmed in all three category detail
loaders and their pagination methods. New selections now cancel prior detail/page
jobs and advance a generation; network and cached results must still own that
generation before publishing. Page requests capture the category name and cannot
start until first-page loading has finished. Cancellation does not invoke offline
fallback, and old finalizers cannot clear a newer request's loading state.

Verification covers source review and the existing phone test/lint/build checks;
delayed-response navigation remains pending live coverage. The preceding artist
fix's GitHub CI passed. Next focus on visible empty/error states and retry controls
rather than treating further request-ownership changes as new UI coverage.

## 2026-09-07 — Search failure recovery

Confirmed code path: if server search threw and cached search then threw, the
fallback exception escaped before the loading flag was cleared. Search now clears
loading in an ownership-checked `finally`, catches fallback errors, discards stale
results on total failure and exposes a user-facing error with Retry. Successful
cache fallback explains that results are cached and also offers Retry. The normal
“No results found” prompt is suppressed for errors so a failed search is not
presented as a successful empty result. New/cleared searches reset the error.

Verification uses source-path review and existing phone tests/lint/build. The new
failure UI has not been exercised in an authenticated live screen; retain mocked
server/cache failure coverage as outstanding rather than claiming a full UI pass.
Next inspect podcast/audiobook loading failures and recovery.

## 2026-09-07 — Podcast and audiobook unavailable screens

Both detail composables returned a text-only “not found” screen when their item
was null after loading. This also occurs for failed requests, and removed all
on-screen navigation/retry controls. Their unavailable states now offer Back and
Retry and use “Unable to load” wording. Retry loads the current navigation route's
ID, so it works even when no selected item was returned. It does not depend on the
podcast refresh action, which requires an already-loaded selection.

Verification scope is composable/navigation code review and phone tests/lint/build;
the authenticated unavailable-screen UI still needs a live pass. No subscriptions,
progress or playback were changed. Next inspect long-form cache fallback and loading
state ownership, including list/detail requests sharing one loading flag.

## 2026-09-07 — Cached audiobook detail recovery

The previous loader restored cached chapters but left the selected book null on
an online API failure. Offline metadata also came only from the in-memory list,
so opening detail before loading that list hid valid cached content behind the
unavailable screen. Detail now reads the book by ID from Room alongside chapters,
preserves that fallback when the API fails, and has its own loading state so a
list request cannot dismiss its spinner. A new detail request cancels the old job
and verifies ownership before publishing cached or remote content.

The Room lookup uses the existing table; no schema migration or data deletion is
needed. Verification includes Room query generation, existing phone tests, lint
and APK build; an authenticated offline playback/UI pass remains outstanding.
Next inspect podcast cache metadata fallback and long-form resume state.

## 2026-09-07 — Podcast cache and detail ownership

Confirmed: the detail loader did not clear the previous selection/episodes, and
only published cache entries when nonempty. Opening an uncached podcast offline
therefore retained another podcast's content; an online failure could also skip
fallback/error handling because the unrelated episode list was nonempty.

New detail requests clear stale content and errors, load metadata directly by ID
with its matching episodes, retain that cache on network failure and reject older
responses. Detail loading is separate from the podcast list spinner. Cancelled
requests cannot publish errors or clear a newer loading state. The added Room
query uses the existing schema and does not change stored data.

Validation: Room query generation and existing phone tests/lint/build. Live
offline navigation remains pending. Next inspect chapter/episode resume behavior
and persisted progress rather than repeating detail-request isolation.

## 2026-09-07 — Audiobook resume timing

Confirmed code path: `playChapter` launched an unowned 500 ms delayed seek against
the current player. A quick switch could apply the old chapter's saved position to
new playback, while a slow load could miss the intended resume. Saved position is
now attached only to the selected chapter's media item through PlayerManager's
existing customResumePositions mechanism. PlaybackService applies it at READY and
replaces pending resume state on item transitions, as it already does for podcasts.

The view model now updates playing-book/chapter state only after playback startup
is accepted. A missing selected chapter does not silently play the first one. If
stored progress references a removed chapter, Continue starts the first chapter at
zero rather than seeking it to another chapter's position.

Verification is code-path review plus existing phone tests/lint/build; live slow
load and rapid-playback-switch scenarios remain pending. No real playback or
progress was changed during this audit.

## 2026-09-07 — Audiobook progress identity

Confirmed: the audiobook progress timer remained active after switching media and
derived a chapter ID from any negative track ID. A sufficiently large podcast
episode ID could produce a positive bogus chapter ID and enqueue audiobook progress.
Saving now requires audiobook mode, rejects podcast mode explicitly, and checks
that the decoded chapter belongs to the known chapter list. Long arithmetic avoids
overflow while validating IDs.

Three isolated regression tests cover a valid chapter, a podcast with a colliding
numeric ID, unknown/other chapters, music and no active track. No actual progress
was submitted. The related playing-chapter display collector still warrants a
separate review; this change guards progress persistence.

## 2026-09-07 — Podcast progress and chapter indicator identity

The podcast timer had the inverse collision: it matched only the negative track
ID, allowing an audiobook chapter with that same numeric ID to update the previous
podcast's progress. Saving now requires actual podcast mode, a matching positive
episode ID, active playback and positive progress. Three regression tests cover a
valid episode, an audiobook ID collision and unrelated/inactive playback.

The audiobook playing-chapter collector now uses the same known-chapter/media-mode
guard as its progress timer instead of decoding every negative ID. This prevents
podcast transitions from changing the displayed audiobook chapter. No real progress
or media was modified during verification. Continue with queue actions and playlist
forms next; authenticated playback coverage remains outstanding.

## 2026-09-07 — Live verification on BlueStacks and proxy web app

Ran authenticated checks against https://music2.faldasz.com using isolated browser
players and the installed Android phone app (1.1.29). All nine web-to-web checks
passed: paused transfer, pointer/keyboard remote seek, playing seek/progress,
return transfer, queue boundaries, selection of an already playing receiver,
and WebSocket disconnect/reconnect with subsequent control.

All twelve Android/web checks passed: paused queue selection, actual advancing
playback, seek, pause, next/previous, queue play/add/play-next, reorder/remove,
clear upcoming, playing transfers both directions, paused transfer preserving
position, and discovery after process restart without resurrecting a stopped
queue. The original seven-song Android queue, selected index and paused position
were saved and restored. Other existing players were not controlled. Local JSON
evidence is in mvbar/.local/connect-web-edge-live-results.json and
mvbar/.local/connect-emulator-live-recheck-results.json.

UI checks opened the subscribed podcast and its 445-episode detail, audiobook
list and 1984 detail, rapidly replaced search rock with Nocturnal, and opened the
matching album. No stale rock results appeared in the observed final UI. This
was ordinary live latency, not a deterministic delayed-response race test.
The sampled device log contained no fatal exception/ANR matches.

Reproduced a new UI issue: 1984's header said 0 ch while its loaded list showed
26 chapters. The detail header now derives the count from the displayed chapter
list once available, retaining metadata only while awaiting the initial load.
Phone unit tests, lint and APK build passed. Installed the rebuilt APK preserving
data and re-opened 1984: header now shows 26 ch, matching 26 chapters below.

Offline verification remains pending: disabling emulator mobile data did not
remove its virtual eth0 network; mobile data was restored. This pass also does
not establish delayed login failure/OAuth ownership, forced request races,
interrupted downloads, or long-form resume/progress behavior. Those earlier
code-review/test-only entries remain explicitly unverified live.

## 2026-09-07 — Long-form playback and server outage live verification

BlueStacks phone app, authenticated proxy server, dedicated idle web receiver:

- Audiobook Continue: saved 962403 ms in chapter 41 resumed and advanced to
  966068 ms before hardware Pause. Rapid Continue then chapter-two selection
  produced active queue item 1 at 3216 ms, not the first chapter's resume offset.
- Paused audiobook Back 15s clamped 3216 ms to zero; Forward 15s produced exactly
  15000 ms, retaining the paused state.
- Podcast Continue: saved 183769 ms resumed and advanced to 187344 ms before Pause.
- After switching back to music and playing for 18 seconds (past the long-form
  timers), audiobook progress remained 962403 ms and podcast progress 183769 ms.
  This checks ordinary media identity changes, not synthetic colliding IDs.
- Selecting the dedicated web receiver from a paused podcast displayed the music-
  only transfer notice and retained local playback. Receiver remained paused at
  zero. Podcast/audiobook transfer support itself is still not implemented.
- Simulated server unreachability using a temporary BlueStacks HTTP proxy pointing
  to closed loopback port 9; device log confirmed connection refusal. This tests
  request failure with a network present, not Android's no-network state.
- Podcast cached metadata and 445 episodes remained navigable during the outage.
- Search showed its cached-results/server-unavailable banner; after clearing the
  proxy, Retry returned Nocturnal albums/songs and removed the error banner.

Found and fixed a cache data-loss bug: audiobook metadata insertion used SQLite
REPLACE on a parent with ON DELETE CASCADE chapters. Returning to the list erased
previously cached chapter rows. Full sync also deleted every parent before
refetching chapters, losing existing chapters if a later request failed.
Audiobook insertion now uses Room Upsert; full list replacement upserts retained
books and deletes only absent IDs. No schema migration or server changes needed.

Live regression: before the change, 1984 showed No chapters during the outage
although its 26 chapters had previously loaded. After rebuilding/installing,
loaded the detail, returned to the refreshed list, restarted with the failing
proxy and reopened 1984: all 26 chapters remained visible. Evidence UI hierarchy:
mvbar/.local/live-cache-fixed-ui.xml. Phone tests, lint and APK build passed.

Cleanup: test proxy disabled (empty host, port zero), successful API/search
recovery observed, temporary web receiver and observers closed. Original Android
seven-song queue restored, index 3, (I Just) Died In Your Arms, paused at zero.
An older observer connection stopped receiving updates; restoration succeeded
with a fresh observer. This was not established as an app reconnect defect.

Still pending: true device-offline transitions, interrupted downloads, forced
slow response races, and login failure/OAuth cases. Long-form offline progress
restoration and uncached-detail error wording need further coverage.

## 2026-09-07 — Uncached audiobook failure and Retry live checks

Reproduced on BlueStacks: with requests refused by a temporary loopback proxy,
opening A Fleet In Being (cached metadata, uncached chapters) displayed No chapters,
0 ch, and an enabled Play button that could not start anything. This confused a
failed request with a successful empty response and provided no inline recovery.

Added a generation-owned detail error, cleared by each new detail request. When
no chapters are available, request failure (or no network) gives a clear message
and Retry. Play is disabled until chapters exist, and failure retains the known
metadata chapter count. Cached chapter lists continue to work without an error.

Live checks after unit tests/lint/build and installing the rebuilt APK:
- Refused request shows Unable to load chapters with Retry and the known 1 ch.
- UI hierarchy confirms Play enabled=false.
- Repeated Retry under the same outage returns to the recoverable error state.
- Opening cached 1984 during the outage shows its 26 chapters without the previous
  error; returning to the uncached book shows its own error, not 1984's chapters.
- Clearing the test proxy and pressing Retry loads Fleet In Being, removes the
  error, and enables Play. No app restart is needed for recovery.

Connection restored (empty proxy host, port zero). Playback was never started by
this pass; the original Cutting Crew song remains paused at zero, queue index 3.
Local failure UI evidence: mvbar/.local/live-uncached-error-ui.xml. This verifies
server unreachability; true Android no-network transitions remain untested.

## 2026-09-07 — Remote seeking and artist Back navigation live checks

Current phone APK and isolated Audit web A/B receivers: all four native UI Connect
checks passed. Android picker transferred the paused queue to web B; a drag
produced 104018 ms against duration 171453 ms. After changing songs, a second drag
produced 154621 ms against duration 257000 ms (60.16%), demonstrating the updated
duration. Changing the receiver song during an in-flight drag sent no stale seek.
Dedicated players closed and the original seven-song Android queue was restored.
Evidence: mvbar/.local/connect-native-ui-recheck-results.json.

Additional live UI checks: a nonexistent search displayed its empty-result state;
Clear returned recent searches; selecting the recent Limp Bizkit artist opened
its detail. Repeated scrolling loaded rows beyond 100 without getting stuck.
The sampled log contained no fatal exception/ANR matches.

Found a Back-navigation regression: opening an album through search from the
scrolled artist, then pressing Back, reset the artist page to its header. The
artist route unconditionally reloaded its detail on re-entry, clearing tracks
and clamping the restored lazy-list position. It now retains the already loaded
matching artist and pages; a different artist or empty tracks still loads.

Phone tests, lint and build passed, then installed the rebuilt APK. Live regression
scrolled Limp Bizkit to rows 100-102, opened Nocturnal through search, and pressed
Back. Nookie (row 100) had identical UI bounds before/after:
[227,474][276,493]. Evidence: mvbar/.local/artist-scroll-before.xml and
artist-scroll-after.xml. This establishes artist-to-album-to-artist recovery;
returning through multiple different artist details is a separate untested case.
Playback remained paused at the original Cutting Crew song after cleanup.

## 2026-09-07 — Smart playlist form and criteria keyboard live audit

Used Easy Listening's edit draft without saving changes. Verified empty name
turns Save Changes off; Cancel/reopen restores the name. Entered minimum duration
999 with maximum 480: submitting was rejected locally with Duration minimum must
not exceed maximum. Cancel/reopen restored 88/480. Date picker opened within the
landscape viewport and Cancel left dates untouched. Exact selected genre labels
were excluded from pop suggestions (case-distinct library tags remain distinct).

Reproduced keyboard failure: Down then Enter in genre suggestions did not select
an item. Added Up/Down navigation, an active-row highlight, Enter selection, and
scrolling to the active row. Mouse and keyboard share selection behavior. Query
changes clear obsolete suggestions immediately; selecting cancels pending work,
and cancelled suggestion requests rethrow cancellation instead of publishing an
empty list over a newer request.

Phone tests, lint and APK build passed; installed the new APK in BlueStacks.
Live regression: typing pop, Down, Enter added Art Pop and cleared the field.
Typing again retained input focus, excluded Art Pop from suggestions, and
Down/Down/Up/Enter added Electro Pop. Cancel and reopen returned the original
nine genres and duration 88/480. Local UI evidence:
mvbar/.local/criteria-keyboard-added-ui.xml. No playlist was saved or deleted.
The original Cutting Crew song remained paused at zero, queue index 3.
Long-list keyboard scrolling is implemented but was not exercised beyond the
visible pop results in this pass.

## 2026-09-07 — Android Auto / DHU investigation

See android-auto-verification.md. BlueStacks connected to DHU but Android Auto
failed before rendering with NO_AUDIO_CAPTURE / REMOTE_SUBMIX initialization
failure. Setup and dependencies installed; physical-device continuation requested.
A real legacy MediaBrowser probe exposed ignored pagination across categories.
Fixed page slicing, added three unit tests and a reusable read-only on-device
instrumentation probe. Tests/lint/build and the live probe passed after the fix.
Actual DHU UI, audio and car-control behavior remain unverified.

## 2026-09-08 — Working Android Auto emulator and DHU smoke test

Replaced the blocked BlueStacks test path with the official Google Play API 34
emulator, keeping BlueStacks data intact. The new image has remote-submix capture;
DHU now renders the dashboard and MVBar launcher/browse/player/queue interfaces.
Verified mix playback, advancing position, metadata/artwork, pause/play,
next/previous while paused, on-screen pause, and reconnection with Android Auto's
configured automatic music resume. Paused the test player and closed DHU afterward.
Detailed setup, evidence and remaining projected-interface tests are recorded in
android-auto-verification.md. No application code was changed for this setup.
Also observed a login version-label/package-version discrepancy for later checking.

## 2026-09-08 — Android Auto search pagination

Confirmed on the official API 34 emulator through a Media3 browser: searching
`love` announced 22 results, but requesting five items returned seven on both
page 0 and page 1, with all seven repeated. The backend search limit applies to
each media type, and onGetSearchResult both ignored the page and returned the
entire mixed result list. Media3 also logged an oversized-result error.

Search notification and retrieval now use the same existing per-type limit of
20. The service slices the combined list by page/pageSize while keeping the full
list in its playback cache. The existing bounded search scope is retained; this
does not add full-catalog search pagination.

Extended the read-only instrumentation probe to call search, await its count
notification, verify distinct pages, total coverage, end-of-list and consistent
ordering with different page sizes. After installing the updated APK with data
preserved, the full live probe passed: 11 browse categories, search pages
5/5/5/5/2, 22 unique results, zero overlap, empty terminal page, and matching
seven-item page ordering. This is a MediaBrowser interface regression, not a
new projected DHU search UI test. No playback commands were sent.

Validation: 65 phone unit tests passed, lintDebug, assembleDebug and
assembleDebugAndroidTest passed. Local evidence: auto-search-before-official.log,
auto-search-after-official.log and auto-search-fix-build.log in mvbar/.local/logs.

Follow-up: the login version discrepancy persists in compiled output:
LoginScreenKt$LoginScreen$4$1.class contains 1.1.29 while version.properties and
generated BuildConfig report 1.1.30, including after this incremental build.
Investigate stale compile-time version inlining separately without clearing
the signed-in user's data.
