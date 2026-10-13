# Jellyfin Android TV fork

This fork adds automatic Family and genre folders for movie and show libraries, reduces unnecessary server conversion using the existing player, and removes verified unused code and resources. It starts from upstream `release-0.20.z` at `3867cfa48591fae2f1e084972de6400a165ab3fe`. Changes dated October 12, 2026 retain the original GPL v2 license and upstream notices.

## Automatic folders

Movie and show smart screens show an Automatic folders row. Standard library grids have a folder button in the toolbar. The existing Genres shortcut for movies and shows opens the same picker. Opening a genre uses a server-filtered, paged grid scoped to that library and signed-in user; the picker fetches genre names without downloading titles from every genre.

Family contains three independently paged rows:

- Genres: Family, Kids, Children, Children's, or Childrens.
- Tags: the same names.
- Ratings: G, PG, TV-Y, TV-Y7, TV-Y7-FV, TV-G, or TV-PG.

These are alternatives because Jellyfin intersects different filter types. A title may appear in more than one row. Animation alone does not qualify. Ratings currently use US metadata names; regional classifications need an explicit mapping. All folders are virtual views and leave server files and metadata untouched.

## Playback

The built-in Media3 player remains in use. Hardware decoding stays preferred, with its existing FFmpeg audio extension as a fallback. Audio capability detection in Auto mode now accepts locally decodable AC3, EAC3, DTS, and TrueHD when passthrough is unavailable. Explicit codec-disable and stereo-downmix settings remain respected. DTS/DCA and TrueHD/MLP aliases follow the same capability rules.

New installations default to Original quality, which removes the application bitrate ceiling, and enable local ASS/SSA subtitle rendering through the bundled libass extension. PGS direct playback was already enabled. Quality limits and subtitle rendering can still be changed in settings. Unsupported video, HDR, audio, or subtitle formats retain the server conversion fallback; device limitations and network throughput still determine actual playback.

The fork uses separate application IDs, `org.jellyfin.androidtv.parmesh` and `org.jellyfin.androidtv.parmesh.debug`, so it can coexist with official builds.

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
| Bitrate settings | NaN, infinity, and malformed values could fail conversion | Parse and clamp finite values; support Original explicitly |
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
| Navigation delivery | Rapid actions were dropped; resuming a host reapplied its last action | Buffer actions for active collectors and identify each action to suppress lifecycle replay |
| Library settings | Switching libraries briefly exposed the previous store; missing preference IDs disabled valid libraries | Reset loading state per library and use the library ID fallback |
| Playback negotiation | Failed requests could leave buffering active; empty or unsupported sources could reach the player | Report the original playback error, close failed playback, and reject unusable stream information |
| Playback teardown | Canceled requests were reported as errors and late player callbacks could touch cleared state | Propagate cancellation, bind requests to the player view, and guard cleared callbacks |
| Quality switching | Failure to stop an already-finished encoder aborted the next stream request | Log SDK cleanup failures and continue negotiation while preserving cancellation |

## Cleanup

Removed seven unused implementation files: the PopoverMenu helper, SequenceQueueSupplier, PagedQueueSupplier, and four unused playback queue suppliers. Also removed BasicMediaStream, unused text and timed-event helpers, unused codec/API parameters and fields, the never-emitted navigation Nothing action, and 376 commented-out null codec mappings.

Android lint identified 141 unused resource IDs across the cleanup runs. Their definitions and qualified variants were removed: 3,865 string entries, 102 plural entries, 44 drawable files, two animations, one layout, eight color definitions, and unused fraction, style, integer, and generated app-ID definitions. Resource shrinking and R8 remain enabled for release builds. Framework callbacks, active Kotlin property accessors, and editor previews are retained.

## Validation

The app and preference unit suites pass 148 tests, including 54 new tests covering library-scoped folder filters, alternative Family criteria, genre normalization, bitrate edge cases, audio aliases, subtitle delivery, codec-query caching, old-Android software decoders, seek boundaries and thumbnails, navigation history and lifecycle replay, playback negotiation failures, cancellation, and late player callbacks. Debug and optimized release builds have succeeded. Android lint and Detekt complete with inherited diagnostics; their reports are in the build directories. The final lint report contains zero unused-resource findings, 39 errors, 118 warnings, and 424 hints. The remaining errors concern existing notification permission checks, Leanback restricted APIs, experimental API annotations, resource access, and translated quantity/format checks. A successful lint task is not a claim of zero diagnostics because upstream config permits findings.

An Android emulator with a local synthetic library exercises folder navigation and paging. A first movie-grid request used 36 items on the test viewport, versus the old forced 100. The test library intentionally omits childCount. The existing player also played generated H.264/AAC media directly, rendered embedded SRT subtitles locally, exposed both audio tracks, and handled pause and seeking. An isolated real Jellyfin 12.2.0 server with 80 movies and 80 shows confirmed each Family criterion returns 20 titles per library. The emulator populated the show Family rows, switched to the Spanish audio track locally, and visibly switched English to Spanish embedded subtitles. Selecting 420 Kbit/s triggered server transcoding with `ContainerBitrateExceedsLimit` while preserving Spanish audio and subtitles; the lower-quality video played correctly. Returning to Original restored direct play with both selections retained, and the player performed its initial seek to the previous playback position. This is functional and request-size evidence, not a Fire TV frame-rate or playback benchmark. ASS/PGS rendering, HDR, and sustained playback still need checks on the actual device.

A second sweep reproduced the library reopening crash against the real server and verified reopening movie and show libraries after the fix. Regression tests cover sparse stream IDs, missing sources, mixed subtitle groups, nonblocking preference loads, shared concurrent loads, and preference invalidation on session change.

A third sweep added 30 regression tests and checked playback failures against the real local server through an HTTP fault-injection proxy. A forced 503 response produced the playback error toast and returned to item details instead of remaining in buffering. Backing out during a delayed playback request returned to details without reopening the player or reporting cancellation as an error. A subsequent request played directly and rendered the selected Spanish subtitles. The proxy does not support WebSocket forwarding, so these checks cover local UI and HTTP playback behavior rather than remote-control commands.

Static searches and lint do not prove that every unused callback or implementation has been removed; retained framework and SDK entry points need runtime coverage before further deletion.

## Build and install

Use JDK 21 and the Android SDK, then run:

```shell
./gradlew :app:testDebugUnitTest :preference:testDebugUnitTest :app:assembleDebug
./gradlew :app:lintDebug detekt :app:assembleRelease
```

Debug APKs are signed with the local Android development key. The local `jellyfin-fork-preview.apk` is an optimized release build signed with the standard Android development key for device testing; it is not a production-signed release. Published release APKs require a dedicated signing configuration. Keep published APKs accompanied by the matching GPL source and notices.

Before treating playback performance as established, test the actual Fire TV with representative H.264/HEVC/AV1, Dolby/DTS audio, ASS/PGS subtitles, seeking, resume, quality changes, and HDR files. Check the Jellyfin dashboard to distinguish direct play, remux, audio conversion, and video conversion. The app's existing device-profile report records detected capabilities for the exact device.
