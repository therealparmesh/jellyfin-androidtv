# Jellyfin Android TV fork

This fork contains bug fixes, performance improvements, lint corrections, and verified dead-code removal. It starts from upstream `release-0.20.z` at `3867cfa48591fae2f1e084972de6400a165ab3fe`. Changes dated October 12–15, 2026 retain the original GPL v2 license and upstream notices.

Automatic Family, genre, and Shorts folders, their navigation hooks, and their tests have been removed from this PR. The original Genres and By letter screens remain. Quality choices, the 100 Mbit/s default, and the default ASS/SSA setting match upstream. No server plugin is required or installed by the app.

The built-in Media3 player remains in use. Hardware decoding stays preferred, with the existing FFmpeg audio extension as a fallback when software codecs are allowed. Auto audio capability detection accepts locally decodable AC3, EAC3, DTS, and TrueHD when passthrough is unavailable. Explicit disable and downmix settings are respected; DTS/DCA and TrueHD/MLP aliases follow the same rules. Unsupported media retains the server conversion fallback.

Separate application IDs, `org.jellyfin.androidtv.parmesh` and `org.jellyfin.androidtv.parmesh.debug`, let this fork coexist with official builds.

## Fixed bugs and performance changes

| Area | Previous behavior | Change |
| --- | --- | --- |
| Genre and letter browsing | Missing or zero child counts prevented queries | Query the server without treating optional counts as an empty-library signal |
| Grid paging | A hard-coded 100 overwrote the calculated page size | Respect the adaptive page size |
| Grid metadata | Every page requested media sources, streams, chapters, and trickplay | Use display fields and fetch full details when selected |
| Browsing lifecycle | Requests could outlive the screen | Tie affected requests to the view lifecycle and cancel superseded retrievals |
| View recreation | Queries and delayed callbacks could target old views | Rebuild adapters and clear callbacks when views are destroyed |
| Static rows | Re-retrieval appended people, chapters, or items again | Clear static row contents before rebuilding |
| Device profiles | Repeated capability calls and exceptions for unsupported MIME types | Cache decoder metadata within each profile query and skip unsupported types |
| Older Fire OS | Disabling software codecs did not filter pre-Android-10 software decoders | Recognize known software decoder names on older Android versions |
| Bitrate settings | NaN, infinity, and malformed values could fail conversion | Parse and clamp finite numeric values; fall back to the existing 100 Mbit/s default |
| Audio language selection | Missing media sources could be dereferenced | Guard the source before searching audio streams |
| Library launch | A nullable library argument was dereferenced | Ignore missing library input safely |
| Session switching | User ID alone determined whether a session changed | Compare server and user IDs |
| Subtitle color presets | Current color was accepted but never displayed as selected | Outline the selected preset |
| Stream selection | Sparse stream IDs were treated as list positions | Resolve audio and subtitle IDs by ID and type; safely handle missing sources |
| Mixed subtitles | Other external subtitle groups shifted embedded track positions | Exclude every external group when selecting an embedded track |
| First library visit | Saved preferences were fetched with `runBlocking` on the UI thread | Suspend loading, share concurrent loads, and wait before displaying settings |
| Settings navigation | Missing libraries crashed lookup; state followed the previous preference store | Allow missing libraries and key state by its store and preference |
| Reopening libraries | Background navigation could create fragments without arguments | Limit host collection to started activities and assign arguments before creation |
| Grid item refresh | A late callback could touch a destroyed view or the wrong selection | Cancel with the view and check the requested item before applying refreshed data |
| Seek positions | Short or unknown durations produced duplicate or negative positions; invalid steps could divide by zero or allocate excessively | Clamp positions, validate steps, and bound allocations while retaining the current position |
| Seek thumbnails | End-of-video previews requested nonexistent sheets; malformed dimensions or negative indices could fail | Clamp to the last real thumbnail and validate dimensions, indices, and arithmetic |
| Navigation history | Reset and replacement disagreed with the fragment back stack; settings Back could remove the root | Preserve history when requested, clear it when closing playback, and retain the settings root |
| Navigation delivery | Rapid actions were dropped; resuming a host reapplied its last action | Keep host subscriptions while stopped, deliver pending actions after Start, and identify actions to suppress replay |
| Library settings | Switching libraries briefly exposed the previous store; missing preference IDs disabled valid libraries | Reset loading state per library and use the library ID fallback |
| Playback negotiation | Failed requests could leave buffering active; empty or unsupported sources could reach the player | Report the original playback error, close failed playback, and reject unusable stream information |
| Playback teardown | Canceled requests were reported as errors and late player callbacks could touch cleared state | Propagate cancellation, bind requests to the player view, and guard cleared callbacks |
| Quality switching | Failure to stop an already-finished encoder aborted the next stream request | Log SDK cleanup failures and continue negotiation while preserving cancellation |
| Audio shuffle | Out-of-range indices, duplicates, replay of the current track, and unstable previews | Shuffle only valid unplayed indices without replacement and preserve cached preview order |
| Audio queue bounds | Empty random queues and negative preview counts could throw | Return empty results for invalid or empty requests and guard empty consumption |
| Audio queue reset/removal | Replacement queues inherited removal counts and shuffle state; deleting the active item left stale playback or duplicate Previous history | Reset counts/order caches and select the replacement without adding the deleted item to history |
| Audio queue display | The UI used `runBlocking` while building upcoming tracks | Suspend updates and cancel superseded refreshes so older results cannot replace the current track |
| Remote video controls | Pause and Unpause both toggled playback; malformed seeks jumped to zero | Honor explicit pause/resume state and ignore seek commands with no position |
| Remote item requests | Missing titles could crash an implicit-position request or clear the playing queue when a position was supplied | Ignore empty requests and report empty results before touching the queue |
| Remote messages | Display messages blocked their coroutine while switching to Main | Suspend the dispatcher switch and avoid displaying a literal null message |
| Photo navigation | Previous could index below zero if a selected image was missing from a nonempty album | Include the selected photo, wrap safely, and avoid querying all photos when its parent is missing |
| Photo loading | Server errors could crash photo loading | Retain a selected image if its album query fails; report item-load errors and return to browsing |
| Photo slideshow | Timer work mutated UI state on IO and recreated its job at every tick; invalid delays could spin; explicit Pause toggled playback | Keep one timer on Main, clamp the interval to the settings range, pause for settings, and distinguish Play/Pause from toggle |
| Photo controls focus | Hiding controls could leave focus on a removed button and stop remote input | Restore focus to the photo player whenever its controls hide |
| Adapter notifications | Clear observers saw stale data; invalid removal lengths threw | Mutate before notifying and validate the complete removal range without integer overflow |

## Adversarial review

The October 15 review compared the complete PR against the release baseline and challenged lifecycle cancellation, delayed callbacks, sparse or missing metadata, repeated commands, queue replacement, and concurrent retrievals. Confirmed regressions were reproduced with failing tests before fixes where practical:

- Navigation emitted while a host was stopped lost intermediate actions and disagreed with repository history. The collector now stays subscribed and waits for Start before applying each action.
- A failed display-preference load permanently cached defaults. Failed loads remain eligible for retry.
- An old supplier completing after queue clear resurrected removed entries. Queue generations reject stale supplier results and index changes.
- Concurrent peeks could append the same supplier slot twice. Cache insertion checks the supplier cursor under synchronization.
- A delayed audio-row refresh could replace the playing item with a previous track. Combined state collection cancels superseded updates.
- A completion event from an old stream could advance a replacement queue. Completion checks the active entry and queue generation. Stopping playback during a pending automatic advance could also restart it; selection versions now prevent older requests from overriding a newer selection.
- Canceled track loads could add phantom Previous history. History is committed after loading; tests also cover replacement while an old supplier remains pending.
- Live TV request cancellation could invoke callbacks after the view was destroyed, and failed parallel requests could escape as uncaught parent failures. Structured request scopes contain failures; cancellation propagates without callbacks. Tests cover both timer-only and combined requests.

## Cleanup

Removed seven unused implementation files: PopoverMenu, SequenceQueueSupplier, PagedQueueSupplier, and four playback queue suppliers. Also removed BasicMediaStream, unused text and timed-event helpers, unused codec/API parameters and fields, the never-emitted navigation Nothing action, and 376 commented-out null codec mappings. Earlier loops removed 11 unused methods and the unused KoinComponent role on Utils. Kotlin property consumers and framework callbacks were checked before deletion; active overlay accessors remain.

Android lint identified 141 unused resource IDs. Their definitions and qualified variants were removed: 3,865 string entries, 102 plural entries, 44 drawables, two animations, one layout, eight colors, and unused fraction, style, integer, and generated app-ID definitions. R8 and resource shrinking remain enabled in release builds.

## Validation

The app, preference, and playback-core suites pass 188 tests (168 + 2 + 18). This review adds 13 focused regressions and removes the 17 tests belonging to the withdrawn folder features. Debug and optimized release builds pass. Android lint reports zero errors, 115 warnings, 424 hints, and zero unused-resource findings. Detekt completes with non-fatal style diagnostics.

Android lint uses `abortOnError` and checks dependencies. Corrections include public ViewGroup operations instead of a restricted Leanback type, file-level Media3 opt-ins, configuration-aware voice-search resources, three Thai format strings, Serbian second-count strings, and decimal plural forms for Czech, Slovak, and Lithuanian. Two narrow exemptions cover the existing media-session notification sender: the method building MediaStyle notifications with a MediaSession and that exact sender's class in the cross-module manifest check. Other notification checks remain enabled. [Android documents the media-session exemption](https://developer.android.com/develop/ui/compose/notifications/notification-permission#media-sessions).

Earlier local runs verified direct H.264/AAC playback, local embedded SRT rendering, English/Spanish audio and subtitle selection, seeking, explicit remote Pause/Unpause, and reduced-quality transcoding with selections retained. HTTP fault injection verified that a forced 503 returns to details with a playback error and that backing out during a delayed request does not reopen playback. Photo checks covered Previous wrapping, explicit Play/Pause, and focus after hiding controls. Those tests used an isolated Jellyfin 12.2.0 server and an API 36 emulator.

The October 15 emulator run confirms the feature-free movie and show grids, the existing movie smart screen and Genres rows, the alphabet picker, library reopening, and the original quality menu with 100 Mbit/s selected. The movie grid again requests 36 items on this viewport; ordinary genre rows request 40 display-only entries per genre. Direct playback reports Spanish audio index 2 and embedded subtitle index 4; the paused frame visibly renders the Spanish fixture text. Settings, playback, and queue regression suites cover the affected state transitions. No Fire TV connection was attempted.

These checks establish scoped functional behavior and smaller browse requests. They do not establish Fire TV frame rates, sustained decoding performance, ASS/PGS or HDR compatibility, or that every bug and unused callback in the entire application has been found. Device testing remains deferred.

## Build and install

Use JDK 21 and the Android SDK:

```shell
./gradlew :app:testDebugUnitTest :preference:testDebugUnitTest :playback:core:testDebugUnitTest :app:assembleDebug
./gradlew :app:lintDebug detekt :app:assembleRelease
```

This machine's JDK 21.0.2 C1 compiler crashed during earlier builds. Local verification uses `-Dorg.gradle.jvmargs="-Xmx4g -XX:-TieredCompilation -Dfile.encoding=UTF-8"` as a JVM workaround; it does not change the APK's runtime behavior.

Debug APKs use the local Android development key. The local `jellyfin-fork-preview.apk` is an optimized release build signed with that development key for testing. Published releases require a dedicated signing configuration and matching GPL source and notices.
