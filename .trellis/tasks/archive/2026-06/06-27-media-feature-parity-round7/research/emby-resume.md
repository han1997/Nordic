# Emby Continue Watching Research

## Source

- Official Emby REST API reference page: `GET /Users/{UserId}/Items/Resume`
- Page title observed: `getUsersByUseridItemsResume`
- Summary: "Gets items based on a query." Requires authentication as user.

## Relevant Endpoint

```http
GET /Users/{UserId}/Items/Resume
X-Emby-Token: <token>
```

Useful query parameters for this app:

- `MediaTypes=Video`
- `IncludeItemTypes=Movie,Episode,Video`
- `Limit=<n>`
- `Fields=Overview,ProductionYear,RunTimeTicks,ChildCount,ImageTags`

The reference also lists `Filters` values including `IsResumable`, but the resume endpoint is already the direct contract for continue-watching rows.

## Relevant Response Fields

Items use the same BaseItemDto-style response as `Users/{userId}/Items`.

`UserData` is important for resume:

```json
{
  "UserData": {
    "PlayedPercentage": 42.5,
    "PlaybackPositionTicks": 1230000000,
    "PlayCount": 0,
    "IsFavorite": false,
    "LastPlayedDate": "2026-06-27T10:00:00Z",
    "Played": false,
    "ItemId": "..."
  }
}
```

Use `PlaybackPositionTicks / 10_000_000` for absolute video resume seconds.

## Repo Constraints

- `EmbyRepository` already owns auth/session selection, `GET Users/{userId}/Items`, image URL building, and tick-to-seconds conversion.
- `VideoScreen` already loads a `VideoCatalog`; adding continue-watching to that catalog keeps the home refresh path single-source.
- `VideoPlaybackEngine.play()` currently starts at zero. To make resume meaningful, `VideoPlaybackInfo` needs a start/resume position and the engine should seek before playback starts.

## Recommended MVP

Add server-backed continue watching:

- Add `EmbyApi.getResumeItems(...)`.
- Map `UserData.PlaybackPositionTicks`, `PlayedPercentage`, `Played`, and `LastPlayedDate` into a small `VideoProgress`.
- Add `VideoCatalog.resumeItems`.
- Render a compact horizontal "Continue Watching" shelf above the library item list.
- Starting playback from a resume item seeks to `VideoPlaybackInfo.resumePositionSeconds`.

## Out of Scope

- Mark played/unplayed mutations.
- Favorite toggles.
- Full history page.
- Local fallback resume cache if Emby is unreachable.
