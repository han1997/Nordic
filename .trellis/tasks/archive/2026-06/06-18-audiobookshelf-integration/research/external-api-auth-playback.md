# AudiobookShelf external API: auth, library, playback

## Sources

* Official repo auth routes: https://raw.githubusercontent.com/advplyr/audiobookshelf/master/server/Auth.js
* Official repo API routes: https://raw.githubusercontent.com/advplyr/audiobookshelf/master/server/routers/ApiRouter.js
* Official repo router mount: https://raw.githubusercontent.com/advplyr/audiobookshelf/master/server/Server.js

## Auth routes confirmed

* `POST /login`
  * Local username/password login.
  * Returns user payload plus access token.
  * Can also return refresh token for API/mobile callers when `x-return-tokens: true` is set.
* `POST /auth/refresh`
  * Refreshes access token using refresh token cookie or `x-refresh-token` header.
* `GET /auth/openid`
  * Starts OpenID Connect login flow.
* `GET /auth/openid/callback`
  * Completes OpenID Connect login flow.
* `POST /logout`
  * Invalidates refresh token/session.

## Library/content routes confirmed

* `GET /api/libraries`
* `GET /api/libraries/:id`
* `GET /api/libraries/:id/items`
* `GET /api/libraries/:id/collections`
* `GET /api/libraries/:id/series`
* `GET /api/libraries/:id/search`
* `GET /api/items/:id`
* `GET /api/items/:id/cover`

These are enough for an MVP that logs in, lists audiobook libraries, shows item cards, opens details, and renders covers.

## Playback/progress routes confirmed

* `POST /api/items/:id/play`
* `POST /api/items/:id/play/:episodeId`
* `GET /api/me/progress/:id/:episodeId?`
* `PATCH /api/me/progress/:libraryItemId/:episodeId?`
* `POST /api/me/item/:id/bookmark`
* `PATCH /api/me/item/:id/bookmark`
* `DELETE /api/me/item/:id/bookmark/:time`
* `POST /api/session/:id/sync`
* `POST /api/session/:id/close`

These indicate AudiobookShelf playback is session-based, not just a raw file URL fetch. If the app wants reliable resume/progress semantics, it should integrate session start + sync/close rather than only extracting a stream URL.

## Implications for Nordic

* MVP can safely target audiobook libraries only and still use stable first-party endpoints.
* Local username/password is the simplest login path because current app config already stores `serverUrl`, `username`, `password`.
* OpenID can be added later, but it requires an external browser/custom-tab style flow and token/callback handling that the current app does not yet have.
* Playback should be designed around AudiobookShelf sessions and progress sync, not only around generic ExoPlayer streaming.
