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

## 2026-09-08 — Login displays the installed version

Confirmed the mismatch with a live login rendering probe: Android package
metadata reported 1.1.30 while the screen rendered Version 1.1.29. The compiled
login lambda retained the older BuildConfig constant after incremental builds.
The login label now reads PackageManager's installed version at runtime and
retains the Debug fallback for missing/unavailable version information.

Added a test-only login-version scope to the instrumentation runner. It renders
the real LoginScreen with inert callbacks in MainActivity, compares its actual
accessibility text with installed package metadata, saves a screenshot, and
finishes the temporary activity. It does not log out or write authentication
data. Before the fix it failed with rendered [Version 1.1.29]; afterward it passed
with Version 1.1.30. Screenshots confirmed both values. Reopening MainActivity
returned to the signed-in For You screen.

Validation: phone unit tests, lintDebug, assembleDebug and
assembleDebugAndroidTest passed. Updated APK installed with adb install -r.
Local evidence: mvbar/.local/logs/login-version-before.log,
login-version-after.log, login-version-fix-build.log, and the corresponding
login-version-before.png/login-version-after.png screenshots in mvbar/.local.
Prior search-pagination commit fa45389 passed GitHub CI.

Next coverage: check the remaining BuildConfig.VERSION_NAME consumers (settings,
update checks and diagnostics) for similar stale values, then continue the
projected DHU long-form playback and seeking checks.

## 2026-09-08 — Projected long-form playback and seeking

Used the existing official API 34 emulator and an isolated headless DHU session
through port 5277. No user DHU session was running. Android Auto's configured
automatic music resume started the paused test music; paused it before proceeding.
This pass used the installed debug build, not the new signed 1.1.31 release.

Verified the actual projected Podcasts > New Episodes screen, episode artwork,
title, resume position, duration, and playback. Paused at approximately 187 seconds.
The on-screen Forward 15s action advanced to approximately 202 seconds; Back 15s
and simulated steering-wheel next/previous returned the expected positions while
remaining paused and retaining the same episode. The test ended near its initial
paused position; ordinary playback added several seconds to the saved progress.

Verified Audiobooks > 1984 > chapter browsing and playback, artwork, chapter title,
duration, and a 26-item queue. Simulated next advanced about 15 seconds within the
chapter, not to another chapter. Repeated previous clamped at zero while paused.
Selecting chapter 2 from the projected queue changed the active item to index 1
and preserved pause. Tapping its timeline moved to 530162 ms; seeking back to the
start and selecting chapter 1 restored a paused position of zero. Closed DHU and
removed this session's ADB port forward; left the emulator running.

No application defect confirmed in this pass. Audio transport was active, but
audible quality was not assessed. Local evidence: mvbar/.local/auto-longform-*.png
and .local/logs/auto-longform-*.log, including the selected chapter screenshot and
media-session states. Remaining checks include long-form reconnection/resume,
network interruption, remote Connect seeking, rotary input and voice.

## 2026-09-08 — Prevent long-form skipping after network failure

Projected DHU reconnection retained the audiobook chapter and position: disconnect
paused chapter 1 at 35032 ms; reconnect resumed the same chapter under Android
Auto's existing automatic-resume setting.

Confirmed a separate defect by disabling Wi-Fi and mobile data on the test
emulator and seeking outside buffered audio. After three network failures, the
service skipped from chapter 1 to cached chapter 2 and started playing it. Its
music-oriented retry handler treated long-form content as skippable tracks.

Long-form recovery now retries the current item twice, then pauses without
changing item or position and without immediately preparing another retry loop.
Single podcast episodes also receive both retries independently of queue size.
Existing music recovery behavior is retained. Added three policy regression tests
covering single episodes, multi-chapter audiobooks and music behavior.

Validation: all 68 phone unit tests, lintDebug and assembleDebug passed. Installed
the debug APK with adb install -r. Repeated the real projected outage, first
discarding an attempt that hit previously cached audio. An uncached seek failed
three times and remained on chapter 2/index 1 at 975623 ms with a Source error.
After restoring connectivity, a DHU media_play command resumed the same item near
that position (982415 ms observed), with no chapter skip. Android Auto displays
its error screen while stopped; explicit hardware play recovery is verified,
but touch-only recovery from that screen remains a follow-up UX check.

Evidence: mvbar/.local/auto-network-before.png, auto-network-failure.png,
auto-network-after-fix.png, auto-network-recovered.png and matching media-session
logs under .local/logs; build log auto-network-fix-build.log. Restored both network
settings, paused the test player, and closed the isolated DHU session. No audible
quality claim is made. Next checks: touch-only recovery, podcast outage regression,
remote Connect seeking, rotary/voice and remaining phone version consumers.

## 2026-09-08 — Touch recovery after exhausted network retries

Confirmed a follow-up UX problem on the projected head unit: after long-form
network retries stopped, the fatal Source error screen had no Play action and
the dashboard transport controls were disabled. Hardware play worked, but the
car touch interface did not offer an equivalent retry at the retained position.

The session's ForwardingPlayer now presents a paused state for recoverable network
errors on paused long-form items. ExoPlayer itself retains the failure, item and
position. Explicit session play prepares the failed stream before playing it.
Other errors and music retain their existing reporting. Suppressing the error
alone was insufficient in live testing because Auto also hides controls for idle
players; the final implementation exposes the paused session state as well.

Validation: 68 unit tests, lintDebug and assembleDebug passed; installed with
adb install -r. Repeated the projected offline/uncached seek. After retries the
head unit retained chapter 1 at 1121239 ms, with artwork, timeline and an enabled
Play button. Restored Wi-Fi/mobile data and tapped that button, without hardware
play: the same chapter resumed from that position (1126157 ms observed). Restored
the test player to paused at zero, closed DHU and removed its port forward.

Evidence: mvbar/.local/auto-touch-recovery-error.png and
auto-touch-recovery-dashboard.png before the fix; auto-touch-final-paused.png and
auto-touch-final-resumed.png afterward, corresponding .local/logs state files,
and auto-touch-recovery-build.log. Prior commit 15831cd passed GitHub CI.
Remaining: podcast-specific outage coverage, remote Connect seeking, rotary/voice,
phone version consumers and service/process-death progress restoration.

## 2026-09-08 — Persist paused seeks before process death

Confirmed on projected Android Auto: paused audiobook chapter 1, sought to
1121239 ms (18:41), waited three seconds, force-stopped only the test app process,
then reopened and reconnected. It resumed near the beginning (10458 ms observed
after automatic playback), losing the paused seek. The snapshot timer only saved
while playing, and pause/seek events did not refresh it.

The playback listener now saves queue position on explicit seek discontinuities
and when playWhenReady becomes false, alongside long-form progress. This makes
paused seeks and the final pause position available without waiting for the
playing-only 30-second timer or a graceful service shutdown.

Validation: 68 unit tests, lintDebug and assembleDebug passed; updated APK installed
with adb install -r. Repeated the same seek/force-stop/reopen/DHU sequence. The
restored buffering state reported exactly 1121239 ms on chapter 1, then playback
advanced normally from that point. Paused and returned the test chapter to zero,
closed DHU and removed the test port forward. No account data was cleared.

Evidence: mvbar/.local/logs/auto-restore-before-stop.log,
auto-restore-before-fix-restarted.log, auto-restore-after-fix-before-stop.log,
auto-restore-after-fix-restarted.log, auto-restore-after-fix-playing.log and
auto-restore-fix-build.log; projected screenshot auto-restore-after-fix.png.
Prior commit ff5209b passed CI. Follow-up: podcast rewind-to-zero restoration
needs separate coverage because its restore path can prefer a larger DB position;
remote Connect, phone version consumers and rotary/voice checks remain open.

## 2026-09-08 — Podcast rewind-to-zero restart check

Tested the existing podcast episode through projected DHU: resumed near three
minutes, paused, sought to zero, waited three seconds, force-stopped the test app,
reopened MainActivity and reconnected the head unit. After queue initialization,
the same episode restored paused at exactly zero. Subsequent Android Auto automatic
playback started from the beginning, not the older listening position. An initial
empty/NONE session was only startup state and was not counted as the result.

No defect reproduced in this path and no app code changed. This specifically
tests reopening the phone app before projection; service-only restoration without
MainActivity remains unverified and must not be inferred from this pass. Paused
the test player and closed the isolated DHU session/port forward afterward.

Evidence: mvbar/.local/logs/podcast-zero-before-stop.log,
podcast-zero-before-fix-restarted.log (filename predates the passing result),
podcast-zero-restored-playing.log and .local/podcast-zero-*.png. Commit d82b6e1
passed GitHub CI. Next: service-only restoration, remote Connect controls, podcast
network failure, phone version consumers, rotary and voice.

## 2026-09-08 — Podcast service restoration and network outage

Extended the zero-position check without reopening MainActivity: force-stopped
the test app after a paused seek to zero, explicitly started PlaybackService,
then reconnected DHU. The same episode played from the beginning (6592 ms observed),
not its former listening position. This verifies the explicit service-start path.

Also tested a single-episode queue with Wi-Fi and mobile data disabled and an
uncached seek to 3445014 ms. Logcat showed the initial network failure and two
retries. The projected player then remained paused at that position with Play
available. Restored both network settings and tapped Play: the same episode
resumed from the retained position, with the projected timeline advancing.

No defect reproduced and no application code changed. Returned the episode to
approximately its pre-test one-minute position, paused it, closed DHU and removed
the test port forward. Evidence: mvbar/.local/logs/podcast-service-*.log,
podcast-outage-paused.log, podcast-outage-recovered.log and the corresponding
.local/podcast-outage-*.png screenshots. Prior docs commit 3e02e14 passed CI.
Next priority: remote Connect controls and phone UI/version consumers; rotary,
voice and audible quality remain unverified.

## 2026-09-08 — Installed version in Settings and update checks

Confirmed on the phone UI: Android package metadata was 1.1.31, but Settings
displayed Version 1.1.27. The update dialog also offered the already-installed
1.1.31 release for download. Settings and AppUpdateManager used version constants
retained from earlier incremental compilation, as previously seen on login.

Added an installed-package version helper and used it for Settings, the update
dialog's version text, update comparison and updater request headers. Missing
metadata renders a Debug fallback; update checking reports an error rather than
comparing an unknown version. No APK was downloaded or installed by the updater.

Validation: 68 unit tests, lintDebug and assembleDebug passed. Installed the debug
APK with adb install -r. Live Settings now shows Version 1.1.31 with Latest, and
the update dialog says "You are on the latest version." Signed-in state and paused
playback were preserved. Evidence: mvbar/.local/settings-version-before/after.png,
settings-update-before/after.png, matching XML dumps and
.local/logs/settings-version-fix-build.log. Prior docs commit d9b435b passed CI.
Remaining: other version consumers in diagnostics/API/Connect may need separate
verification; prioritize remote Connect controls next.

## 2026-09-08 — Bidirectional web/Android Connect music controls

Used a temporary localhost web tab and the official phone emulator, both signed
in to the existing account. Both appeared in the Connect pickers. Started the
13-song Made For You queue on Android from the web controller. Web pause and
timeline seek produced PAUSED at 95000 ms in Android's media session; Next changed
the track while retaining pause.

Transferred the queue to the browser, still paused. Selected the browser from
Android's Connect picker; its full-player timeline seek moved the web player to
133.1 seconds (2:13 of 4:26). Android Play started browser playback. Paused the web
player and transferred back to Android: queue size 13, index 1, position 167753 ms,
PAUSED. Closed the temporary browser. No unrelated player was active.

Coverage limitation confirmed: the Android Connect registration advertises music,
remote-control and transfer, while connectStateJson filters out nonpositive track
IDs. The paused podcast was consequently omitted from its advertised state and
the web controller had no podcast transport controls. This is existing music-only
scope, not a verified podcast-transfer implementation; full long-form Connect
support requires coordinated protocol/client work and remains open.

No new music-control defect confirmed. Local media-session evidence:
mvbar/.local/logs/connect-web-to-android-seek.log and
connect-transfer-back-android.log; browser accessibility observations confirmed
the reverse seek/play. Prior code commit 0af5c51 passed CI. Next: assess the scope
and requirements for long-form Connect, notification controls while remotely
playing, disconnected targets, and remaining stale version consumers.

## 2026-09-08 — Runtime versions in API, Connect and diagnostics

Confirmed further stale compiled constants: ApiClient and DebugLog contained
1.1.27, and SocialRealtimeManager contained 1.1.28, while the installed APK was
1.1.31. These paths populate HTTP/WebSocket headers, Connect registration and
diagnostic text/upload metadata.

ApiClient and DebugLog now read installed package metadata during application
initialization; Connect uses the same API version value. No runtime
BuildConfig.VERSION_NAME references remain in the phone app. The three compiled
classes no longer contain the stale version strings.

Added instrumentation scope `version-metadata`, which compares the initialized
API/Connect value and diagnostic header with PackageManager without uploading
logs or sending messages. The probe runs its reads on the main thread after
application initialization; reading immediately on the instrumentation thread
initially raced startup and saw the fallback value. Final live result: installed,
API/Connect and diagnostic versions all 1.1.31, INSTRUMENTATION_CODE -1.

Validation: 68 unit tests, lintDebug, assembleDebug and assembleDebugAndroidTest
passed; installed both APKs with data preserved and reopened MainActivity after
the probe. Evidence: mvbar/.local/logs/version-metadata-live.log,
version-metadata-fix-build.log and version-metadata-probe-build.log. This validates
runtime values used by the sender code, not a captured network registration.
Prior commit a17fdb6 passed CI. Next: remote notification controls and target
disconnect behavior; full long-form Connect remains unsupported.

## 2026-09-08 — Background remote notification and browser disconnect

Opened an isolated localhost browser player, started Made For You, selected that
browser on the official Android emulator, then backgrounded MVBar. The Android
notification shade showed Plan B and its artist, remote output, timeline and
transport controls. Notification Pause paused the browser at 78.6 seconds; Next
changed to the Sharon Van Etten / Josh Homme track while paused. Notification Play
started that track in the browser, confirmed by its advancing position and Pause
control. These were actual notification taps, not direct transport commands.

Paused and closed the temporary browser. The remote media card disappeared from
the notification shade, and dumpsys media_session no longer listed MVBar Connect.
Reopened Android: only the local player remained available and its prior paused
track was shown. No unrelated playback was active. Screenshot evidence:
mvbar/.local/remote-notification.png; browser accessibility observations verified
the command outcomes. No defect confirmed or production code changed. Previous
commit 9c8d2a8 passed GitHub CI. Still to check: remote notification seeking,
locked-screen controls and abrupt network loss rather than graceful tab closure;
full long-form Connect remains unsupported.

## 2026-09-08 — Remote notification seek and background foreground-service crash

Notification timeline seeking moved the paused browser's Plan B to 96.1 seconds,
confirmed in the browser. Disabled the official emulator's Wi-Fi and mobile data:
remote controls disappeared and the browser retained its paused position. After
restoring connectivity and selecting the paused browser again, the local queue
transferred to it. The app subsequently crashed while backgrounded as remote
selection cleared. Logcat confirmed ForegroundServiceStartNotAllowedException in
PlaybackService.onUpdateNotification, called by the Connect-selection collector.

The notification override forced foreground execution whenever a queue existed,
including paused local queues resurfacing after remote control. It now passes
Media3's startInForegroundRequired flag through unchanged. Actual playback still
uses Media3 foreground management; merely retaining a paused queue no longer
requests a prohibited background foreground-service start.

Validation: unit tests, lintDebug and assembleDebug passed; installed the updated
APK with data preserved. Repeated remote selection, background network outage,
network restoration and selection with a paused local queue. Process 3531 survived
throughout, with no crash-buffer entries for that process. The paused local media
notification returned; tapping Play resumed local playback (PLAYING at 12153 ms).
Paused it again, restored connectivity and closed the isolated browser. Evidence:
mvbar/.local/logs/remote-foreground-crash.log, remote-foreground-fix-build.log and
remote-foreground-after-crash-check.log. Previous docs commit b3248ed passed CI.
Still open: locked-screen controls and extended reconnect/selection stability.

## 2026-09-08 — Swipe-lock remote controls

CI passed for ae5c926. Selected an isolated playing browser from Android, enabled
the emulator's swipe lock temporarily (locksettings get-disabled was true), then
slept and woke the phone. The actual lock screen displayed remote track metadata,
transport controls and seeking. Pause stopped the browser at 99.7 seconds; Next
changed track while retaining pause; timeline seeking moved it to 133.1 seconds.
Play resumed it and the browser advanced to 137.3 seconds. Browser accessibility
observations confirmed each command, independently of the Android card.

Paused and closed the browser, restored locksettings set-disabled true and reopened
MVBar. Process 3531 remained alive. Screenshot: mvbar/.local/connect-lock.png.
No new defect confirmed. This covers a swipe lock, not PIN-protected keyguard.
Extended reconnect/selection stability and projected Auto lifecycle checks remain.

## 2026-09-08 — Projected Auto regression after foreground-service fix

CI passed for b896f93. No existing DHU process was active. Started an isolated
headless DHU through port 5277 with the phone's existing paused 13-track music
queue. The dashboard displayed Plan B, artist and artwork and resumed under the
existing Android Auto auto-start setting. Tapped dashboard Pause: PAUSED at
52028 ms. Quit DHU and backgrounded the app; the same queue and position remained.

Reconnected DHU. After the initial loading card, artwork and transport controls
returned; the session showed the same track and queue, playing from the retained
position (64653 ms observed). Dashboard Pause worked again. Closed DHU, removed
the test forward and reopened the phone app. No crash or playback-state regression
confirmed. This verifies projected UI and session state, not audible quality.
Evidence: mvbar/.local/auto-foreground-second-connection.png and
.local/logs/auto-foreground-{paused,disconnected,reconnected}.log.
Corrected the verification summary's stale podcast coverage exclusions to match
the earlier completed outage and service-restoration tests.

## 2026-09-08 — Cached music playback and queue transition offline

CI passed for 73aa5eb. Reviewed manual download completion/failure/cancellation
paths without changing code. With Plan B marked Cached and initially paused at
85828 ms, disabled emulator Wi-Fi and mobile data. Used the full-player slider
to seek to about 152 seconds and Play: media session PLAYING at 152841 ms with
no error. Sought near the end; playback advanced naturally to queue index 1,
the Sharon Van Etten / Josh Homme track, PLAYING at 6342 ms without network.

Paused, returned to Plan B, restored approximately 1:25 using the slider and
reenabled Wi-Fi/mobile data. Existing queue and cache were retained. Evidence:
mvbar/.local/logs/offline-music-before.log and offline-music-transition.log.
No defect confirmed. This tests existing cached audio, not manual download
interruption/retry, cache eviction, or audible output; those remain separate gaps.

## 2026-09-08 — Podcast download failure hides Retry controls

Started Download for offline for the continued TaZ podcast episode with emulator
Wi-Fi and mobile data disabled. The card rendered the nested IOException /
UnknownHostException text in its bottom row. This consumed the row's available
width: Episode options and Mark played disappeared from the accessibility tree,
making Retry inaccessible from that card.

Both podcast error labels now use concise "Download failed" text with a bounded
width. Detailed download exceptions remain logged by AudioCacheManager. This
keeps the Continue card's controls visible and avoids showing raw exceptions in
the episode-list view as well.

Validation: unit tests, lintDebug and assembleDebug passed; installed updated APK
with data preserved. Repeated the offline download failure: progress, Offline,
Download failed, Episode options and Mark played all remained visible. Opened
the menu and verified Retry offline download; restored networking and tapped it.
Download progress advanced from 6% to 29%, then completed with the Cached badge.
Retained the completed cache entry. Playback and played status were not
changed. Evidence: mvbar/.local/download-error-before.png,
download-error-after.png and .local/logs/download-error-layout-build.log.
Prior commit 6091af1 passed CI.

## 2026-09-08 — Offline count includes incomplete cache entries

CI passed for 863897d. While reviewing cache management, Settings reported
11 audio items available offline, but Manage listed 8 complete items (7 tracks
and 1 episode). getCachedTrackCount counted every cache key, including partial
stream buffers. It now uses getCachedKeys, the existing complete-file filter.
Storage usage still includes partial buffers because they occupy disk space.

Validation: unit tests including partial/missing-range cache coverage, lintDebug
and assembleDebug passed. Installed updated APK with data preserved. Live
Settings now reports 8 audio items available offline; Manage reports 8 items.
Both show 162 MB. No cache was removed. Evidence:
mvbar/.local/offline-count-settings-after.xml and
.local/logs/offline-count-fix-build.log. During this check, cached music rows
showed Track #ID / Unknown; metadata persistence for prefetched mix tracks needs
investigation in a later pass. Cache removal races remain unconfirmed.

## 2026-09-08 — Metadata missing for prefetched music

CI passed for 86acc7b. Confirmed cached tracks appeared as Track #ID / Unknown
because prefetch saved audio without inserting queue metadata into the local
track index. CacheBrowserScreen resolves track labels from that index; mix and
Connect queues can contain tracks absent from it.

Prefetch now snapshots positive-ID queue metadata and inserts missing records
before downloading audio. Room conflict-ignore preserves existing library rows;
podcast/audiobook pseudo-tracks are excluded, and metadata write failures are
logged without preventing audio prefetch. Existing complete audio still skips
the download as before. This covers the prefetch path; manual ID-only downloads
and queues with prefetch disabled remain separate metadata coverage gaps.

Validation: unit tests, lintDebug and assembleDebug passed. Installed updated APK
with data preserved; the existing restored queue populated metadata. Live Manage
now shows titles/artists/albums for previously anonymous tracks, including Plan B,
Care, Heatwave and Ready For Your Love. It still shows 8 items / 162 MB. Playback
remained paused and no cache was deleted. Evidence: mvbar/.local/cache-metadata-after.xml
and .local/logs/cache-metadata-fix-build.log.

## 2026-09-08 — Cache filters break at large text sizes

CI passed for 46f3c4b. Tested cache management at font scales 1.5 and 2.0.
At 2.0, the fixed-width filter row squeezed Episodes into four lines, greatly
increasing its height and pushing content down. Added horizontal scrolling to
the filter row so chips measure at their natural width.

Validation: unit tests, lintDebug and assembleDebug passed; installed updated APK
without clearing data. At 2.0, labels remained on one line. Swiped the filter row
left and tapped Episodes: it correctly showed the one cached podcast with its
Remove control still visible. No removal was performed. Restored the original
unset font_scale setting. Evidence: mvbar/.local/cache-font-before.png,
cache-font-after.png and .local/logs/cache-font-fix-build.log.

## 2026-09-08 — Cache removal cancellation

CI passed for d11c4d6. Tapped a cached track's trash icon: it changed to the
second-step Remove button without deleting audio. Pressed system Back to abandon
removal. Settings still reported 8 complete items / 162 MB. Opened Clear offline
audio, verified its explanation and Cancel action, then cancelled. Counts and
paused playback remained unchanged. No new defect confirmed; actual deletion,
simultaneous writers and eviction remain untested to preserve the existing cache.

## 2026-09-08 — Rotary input setup and dashboard transport

CI passed for 40755d9. Default touchscreen DHU did not expose rotary focus.
Restarted the isolated headless session with the SDK's config/rotary.ini
(touch=false, controller=true) using --config and port 5277. This configuration
showed focus outlines. dpad rotate right traversed dashboard focus; dpad right
moved from the map area to MVBar's Play button. dpad click started Plan B,
confirmed PLAYING at 10059 ms; a second click paused it. No touch input was used
for this rotary-configured transport test. Closed DHU and removed the test forward.

Evidence: mvbar/.local/auto-rotary-config.png and
.local/logs/auto-rotary-{play,pause}.log. This is limited dashboard transport
coverage: full MVBar browse/queue traversal, driving restrictions, voice and
audible quality remain unverified. The SDK CLI help also exposes restrict
none/all and focus audio/nav/video for future isolated tests.

## 2026-09-08 — Rotary browse follow-up inconclusive

CI passed for 455950c. Used isolated rotary.ini DHU on port 5277. Media shortcut
and controller clicks reached the full player and the For You browse screen.
Before category focus could be established, the projected UI returned to playback
with a different track (Nienawidze urodzin / Filipek). Input/render timing did not
establish whether a delayed click selected a mix or playback changed independently.
Do not count this as verified category traversal or a confirmed navigation defect.
Closed the isolated DHU and removed the forward; no production changes made.
Evidence: mvbar/.local/auto-rotary-browse.png and
.local/logs/auto-rotary-browse-final.log. Next rotary pass should wait for stable
screens between each input and verify the focused control before activating it.

## 2026-09-08 — Cached episode missing its show name

The cache browser previously showed the downloaded TaZ episode with generic
subtitle Podcast despite its parent show being stored locally. It only used the
episode's optional denormalized podcastTitle. It now loads each distinct parent
podcast from Room and uses its title when that field is absent or blank. This
does not require networking or modify episode progress.

Validation: unit tests, lintDebug and assembleDebug passed; installed APK with
data preserved. Disabled Wi-Fi/mobile data, opened Manage and selected Episodes:
the subtitle now reads Zurnalista - Rozmowy bez kompromisow (with Polish accents
in the actual UI). Restored networking. Evidence:
mvbar/.local/cache-podcast-title-offline.xml and
.local/logs/cache-podcast-title-build.log. CI for bb9da6d passed; a separate
user-triggered Android APK workflow was in progress at the start of this pass.

## 2026-09-08 — Storage totals after returning from Manage

Both f128600 CI and the separate Android APK workflow completed successfully.
Manage showed 11 complete items / 191 MB after recent background prefetch.
System Back returned to Settings, which correctly refreshed to 11 audio items
available offline / 191 MB. The earlier 8-item display was not a persistent
return-navigation defect. No code change required. Playback remained paused;
no downloads were removed. Refresh while staying on an already open Settings
screen and simultaneous cache writers remain separate unverified cases.

## 2026-09-08 — Offline search clearing and reconnect

CI passed for d9c34df. Disabled emulator Wi-Fi/mobile data and searched Plan:
results rendered. Clear restored recent searches without retaining the result
list. An unmatched test query showed No results found / Try a different search
term. Restored networking, cleared it and searched Nocturnal: albums and songs
loaded without reopening the app. Cleared and closed search afterward. No result
was selected, favorites/history were not edited, and playback remained paused.
No defect confirmed. This covers search UI recovery, not an assertion that every
returned item is downloaded or playable offline. The upstream 1.1.33 version bump
was preserved by the previous clean rebase.

## 2026-09-08 — Podcast discovery request cancellation

Source review found podcast discovery launched untracked requests and clearSearch
only emptied results. A response completing after dialog close could repopulate
state; overlapping submissions could also overwrite newer results/loading state.
Unlike library search, this path had no cancellation or request-generation guard.

Added a tracked search job and generation checks. New searches cancel older jobs
and reset results; clearing search cancels and invalidates pending responses and
resets loading. Cancellation is rethrown rather than logged as a search failure.
Only the current request may publish results or finish its loading state.

Validation: unit tests, lintDebug and assembleDebug passed; installed updated APK.
Submitted science then immediately closed discovery; reopening showed a clean idle
dialog. A fresh normal science search returned Discovery, Science Magazine Podcast
and Science Friday. Closed without subscribing or changing playback. Evidence:
mvbar/.local/podcast-search-after.xml and .local/logs/podcast-search-cancel-build.log.
The race was identified from control flow; live checks cover close/reopen and normal
completion, not deterministic forced response reordering. CI for 7094937 passed.

## 2026-09-08 — Podcast preview offline/reconnect

CI passed for a0abf95. Searched science, disabled emulator Wi-Fi/mobile data and
opened Discovery's Details. The preview retained show/author metadata and showed
Podcast details need a network connection. Done returned to discovery results.
Restored networking and reopened Details: language and description loaded
(Explorations in the world of science). Closed preview and discovery without
subscribing or changing playback. No defect confirmed in this flow. Preview
request response-reordering remains unverified; search cancellation and preview
cancellation are distinct paths.
