# Error Handling

> How errors are handled in this project.

---

## Error Types

### NavidromeApiException

Typed exception for Subsonic API errors, replacing fragile `e.message?.contains("Subsonic错误")` string checks.

```kotlin
class NavidromeApiException(
    val kind: Kind,
    message: String
) : Exception(message) {
    enum class Kind { HTTP, SUBSONIC, API }
}
```

- `Kind.HTTP` — HTTP-level failure (non-2xx status)
- `Kind.SUBSONIC` — Subsonic protocol error (status != "ok")
- `Kind.API` — Subsonic response body failure such as an empty 200 JSON body or a null parsed body

**Why**: String-based error classification (`e.message?.contains("Subsonic错误")`) is fragile — any message rewording silently breaks error handling. Typed exceptions make the contract explicit and compiler-enforced.

---

## Error Handling Patterns

### API Repository Catch-Rethrow Pattern

Every public method in `NavidromeRepository` uses this pattern:

```kotlin
suspend fun getRecentAlbums() = try {
    val auth = config.authParams()
    // ... API call using requireResponse()
} catch (e: NavidromeApiException) {
    throw e  // rethrow typed errors as-is
} catch (e: Exception) {
    throw Exception("获取专辑失败: ${e.message}")  // wrap unknown errors with context
}
```

**Rule**: Every public `NavidromeRepository` method must include both catch blocks. Leaving out the `NavidromeApiException` catch causes API errors to lose their typed classification when wrapped a second time.

### requestSubsonic() / requireResponse() Contract

```kotlin
private suspend fun requestSubsonic(request: suspend () -> Response<SubsonicResponse>): SubsonicData
private fun Response<SubsonicResponse>.requireResponse(): SubsonicData
```

- HTTP failure → throws `NavidromeApiException(Kind.HTTP, ...)`
- Subsonic error → throws `NavidromeApiException(Kind.SUBSONIC, ...)`
- Empty body failure, including Retrofit/Gson `EOFException` before a `Response` is returned → throws `NavidromeApiException(Kind.API, ...)`
- Success → returns `SubsonicData`

---

## Common Mistakes

### Catching Exception first, NavidromeApiException second

**Wrong**:
```kotlin
} catch (e: Exception) {
    throw Exception("获取失败: ${e.message}")
}
// NavidromeApiException is already caught above — this block is unreachable
} catch (e: NavidromeApiException) { ... }
```

**Correct**:
```kotlin
} catch (e: NavidromeApiException) {
    throw e
} catch (e: Exception) {
    throw Exception("获取失败: ${e.message}")
}
```

### Double-wrapping error messages

When `NavidromeRepository.getAlbumSongs()` wraps an error as `"获取专辑曲目失败: ..."`, and the caller wraps it again as `"获取专辑曲目失败: ${e.message}"`, the user sees `"获取专辑曲目失败: 获取专辑曲目失败: Connection refused"`. To avoid this, callers should check `e is NavidromeApiException` before re-wrapping, or use a different prefix.

---

## API Error Responses

Subsonic API returns errors in `SubsonicData.error`:
```json
{ "subsonic-response": { "status": "failed", "error": { "code": 70, "message": "..." } } }
```

Common error codes:
- `0` — Generic error
- `10` — Required parameter missing
- `40` — Wrong username or password
- `70` — User not authorized
