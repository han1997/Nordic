# Logging Guidelines

> Logging conventions for repository, network, playback, and UI diagnostics.

## Overview

The Android app uses `android.util.Log` directly. Network repositories also create an OkHttp `HttpLoggingInterceptor`, but the current convention sets interceptor level to `HttpLoggingInterceptor.Level.NONE` so request/response bodies and auth data are not emitted by default.

Reference files:
- `app/src/main/java/com/nordic/mediahub/data/NavidromeRepository.kt`
- `app/src/main/java/com/nordic/mediahub/data/AudiobookShelfRepository.kt`
- `app/src/main/java/com/nordic/mediahub/data/EmbyRepository.kt`
- `app/src/main/java/com/nordic/mediahub/ui/MusicHomeSections.kt`

## Tags

Use short, domain-specific tags:

- `NavidromeApi` for low-level Navidrome HTTP interceptor messages.
- `NavidromeRepo` for Navidrome repository milestones and errors.
- `AudiobookShelfApi` for AudiobookShelf HTTP interceptor messages.
- `EmbyApi` for Emby HTTP interceptor messages.
- `MusicArtwork` for image-loading diagnostics in music UI.

Prefer an existing tag when modifying nearby code. Add a new tag only when the log belongs to a distinct subsystem.

## Levels

- `Log.d` is acceptable for development-only diagnostics such as request lifecycle milestones, loaded counts, or artwork loading callbacks.
- `Log.e` is used when an operation failed and the exception object is useful during debugging, for example repository fetch failures.
- Avoid `Log.i` and `Log.w` unless a new subsystem has a clear operational reason to distinguish informational and warning logs.

## What To Log

Good candidates:
- Which high-level fetch started or completed, such as loading albums or preparing artists.
- Counts after mapping, for example number of albums or artists prepared.
- Exceptions at repository boundaries when the UI also receives a user-facing error.
- Image/artwork loading failures where the failure is otherwise silent.

Keep logs short and scoped to developer diagnosis. User-facing copy belongs in UI state, not log messages.

## What Not To Log

Never log:
- Passwords, API keys, bearer tokens, Subsonic auth token/salt pairs, or full authenticated media URLs.
- Full response bodies from media servers.
- User library titles at large scale when a count is enough.

Do not raise `HttpLoggingInterceptor` above `NONE` in committed code unless the change also redacts secrets and has tests or a clear debug-only build gate.

## Error Reporting Pattern

Repositories should throw typed exceptions or contextual `Exception` messages for the UI to render. Logs are secondary diagnostics; they must not be the only way an error is surfaced.

Example pattern in `NavidromeRepository`:

- log a repository error with `Log.e("NavidromeRepo", "...", e)`
- rethrow `NavidromeApiException` unchanged when classification matters
- wrap unknown exceptions with user-context text for the caller

## Common Mistakes

- Do not leave temporary `println` or ad hoc debug logs in source.
- Do not log auth URLs built by `buildAuthedMediaUrl(...)`.
- Do not depend on log message text for control flow; use typed exceptions.
