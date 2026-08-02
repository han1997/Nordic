# Research: Android Encrypted Credential Storage

- **Query**: Migrate an androidx DataStore Preferences (plaintext) app to encrypted at-rest storage without losing existing user config; evaluate EncryptedSharedPreferences, Tink-encrypted DataStore, manual Keystore AES-GCM, and any community pattern.
- **Scope**: mixed (internal codebase context + external library research)
- **Date**: 2026-08-02

## Verified Codebase Context

| Fact | Value | Source |
|---|---|---|
| `minSdk` | **26** (Android 8.0) | `app/build.gradle.kts:12` |
| `compileSdk` / `targetSdk` | 34 / 34 | `app/build.gradle.kts:8,13` |
| DataStore dep | `androidx.datastore:datastore-preferences:1.0.0` | `app/build.gradle.kts:52` |
| Delegate | `val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")` | `data/ConfigRepository.kt:12` |
| Store name | `"settings"` (one shared file) | `data/ConfigRepository.kt:12` |
| `android:allowBackup` | `"false"` | `AndroidManifest.xml:10` |
| `usesCleartextTraffic` | `"true"` (separate concern) | `AndroidManifest.xml:13` |

**Keys actually declared** (`ConfigRepository.kt:15-28`) — 12 `stringPreferencesKey`s, not 6:

| Sensitivity | Keys |
|---|---|
| High (secrets) | `navidrome_pass`, `audiobook_pass`, `video_pass`, `video_api_key` (4) |
| Medium (PII-ish) | `navidrome_user`, `audiobook_user`, `video_user` (3) |
| Low (config) | `navidrome_url`, `audiobook_url`, `video_url`, `audiobook_last_item_id`, `video_type` (5) |

Repository API surface is fully **Flow + suspend**: `context.dataStore.data.map { … }` for reads (`:30,38,46,50`) and `context.dataStore.edit { … }` for writes (`:60,68,76,82`). Any solution must preserve this shape or trigger a rewrite of all 8 flows + 4 save methods.

---

## Approach 1 — EncryptedSharedPreferences (`androidx.security:security-crypto`)

**Maven**: `androidx.security:security-crypto:1.1.0` (latest stable, released 2025-07-30; prior stable `1.0.0`). Maven metadata confirmed via `dl.google.com/.../maven-metadata.xml`.
**minSdk impact**: library requires API 23+; our minSdk 26 ✅.

**What it is**: A `SharedPreferences` implementation that encrypts both keys (AES-SIV) and values (AES-GCM-256) using a master key held in the Android Keystore. The de-facto standard, audited, Jetpack-branded.

**API shape**:
```kotlin
val masterKey = MasterKey.Builder(context)
    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
    .build()
val esp = EncryptedSharedPreferences.create(
    context, "secret_prefs", masterKey,
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
)
```

**Pros**: Drop-in for `SharedPreferences`; one dependency; well-known; master key auto-generated and managed by Keystore; supports arbitrary strings (passwords, API keys) fine.
**Cons**:
- **Backed by `SharedPreferences`, not `DataStore`** — synchronous disk I/O, no `Flow`, no `suspend`. Every read/write in `ConfigRepository` would need rewrites: `data.map { }` → `esp.getString(...)` wrapped in `flow { emit(...) }` + a listener, and `edit { }` → `esp.edit().putString(...).apply()`. **This is the dominant cost for our codebase.**
- `apply()` is async-fire-and-forget (no error surface); `commit()` is sync and blocks. Neither composes with coroutines cleanly.
- **Known issues**: `1.1.0-alpha06` had a widely-reported `GeneralSecurityException` after key rotation / reinstall; the 1.0.0→1.1.0 line had Keystore corruption edge cases on some OEMs. No first-party key-rotation API — rotating the master key re-encrypts the whole file in place (can fail mid-write).
- Underlying crypto is Google Tink (see Approach 2) — adds transitive deps.

**Migration from DataStore**: read each key from the old `context.dataStore` (`data.first()`), write into `esp`, set a `"migrated"` boolean in `esp` itself, then delete the old DataStore file (`context.deleteSharedPreferences("settings")` is *not* applicable — DataStore files live in `files/datastore/` and must be removed manually after a final successful migration pass).

---

## Approach 2 — Tink-encrypted DataStore (`com.google.crypto.tink:tink-android`)

**Maven**: `com.google.crypto.tink:tink-android:1.7.0` (latest release 2022-08-09). **Critical caveat**: the original `github.com/google/tink` repo was **archived read-only on Apr 17, 2024** and moved to `github.com/tink-crypto/tink`; no new release has shipped under the `com.google.crypto.tink` coordinates since 1.7.0. Maintenance/visibility risk. (Verified via the archived repo README.)
**minSdk impact**: tink-android supports API 19+; our minSdk 26 ✅.

**What it is**: Tink is the crypto engine that `security-crypto` itself uses under the hood. Instead of taking the SharedPreferences abstraction, you keep `DataStore` and write a **custom `Serializer<Preferences>`** (or a custom `DataStore<Preferences>` delegating to an `Aead`) that encrypts the serialized Preferences blob with a Tink `Aead` whose key is wrapped by an Android Keystore master key. The `Preferences` object → bytes → AES-GCM encrypt → write to disk; reverse on read.

**Pros**:
- **Preserves the existing Flow/suspend API verbatim** — `ConfigRepository`'s `.data.map { }` and `.edit { }` calls stay identical; only the `Context.dataStore` delegate factory at `:12` changes (swap `preferencesDataStore(name = "settings")` for a custom factory using `PreferenceDataStoreFactory.create(serializer = encryptedSerializer(...))`).
- Real at-rest AES-GCM encryption via the same primitives `security-crypto` uses.
- Tink handles nonce-misuse, keyset serialization, and rotation (`KeysetManager.rotate`) — safer than hand-rolled crypto.

**Cons**:
- **More code**: you implement `Serializer<Preferences>` (≈30–60 lines) + keyset bootstrap + Keystore-wrapping (`AndroidKeysetManager`), plus a one-time migration reader. ~150–250 LOC total.
- The archived-repo status is a genuine supply-chain concern (no bug fixes upstream). The library is stable and deployed widely at Google, but new CVEs would be community-patched only.
- `AndroidKeysetManager` writes a wrapped keyset to a SharedPreferences file shielded by a Keystore master key — an extra moving part.

**Migration**: identical to Approach 1 (read old → write new → delete old file), but you can do it inside the custom `Serializer`'s first read by falling back to the legacy plaintext store when the encrypted file is absent — giving a near-zero-friction one-shot upgrade.

---

## Approach 3 — Android Keystore + manual AES-GCM

**Maven**: none (platform `android.security.keystore.*` + `javax.crypto.*`). minSdk 26 gives you `KeyGenParameterSpec` with `PURPOSE_ENCRYPT|DECRYPT`, `GCM`, and `setUserAuthenticationRequired` for free.

**What it is**: Hand-rolled. Generate/Load a Keystore-backed AES-GCM `SecretKey` (alias e.g. `"mediahub_cred_key"`), `Cipher.init(ENCRYPT_MODE, key, ivSpec)`, encrypt each value, store `iv || ciphertext` (Base64) in plain DataStore Preferences keyed by the existing key names.

**Pros**: zero new dependencies; full control; can selectively encrypt only the 4 high-sensitivity keys and leave `video_type`/`audiobook_last_item_id` plaintext (cheapest migration); smallest APK delta.
**Cons**: most boilerplate (IV handling, GCM tag length, padding error paths, key-existence checks, re-encryption on key loss); easy to misuse (nonce reuse, ECB/GCM tag truncation); no built-in rotation; you own every bug. Justified only if Approaches 1/2 are blocked.

---

## Approach 4 — Community "security-crypto DataStore" pattern (optional)

There is **no first-party `androidx.security` DataStore** artifact. Community options are thin and unmaintained: e.g., wrapping `EncryptedSharedPreferences` behind a `DataStore<Preferences>` shim, or third-party `EncryptedDataStorePreferences` gists. All are toys — they reintroduce Approach 1's SharedPreferences I/O inside a Flow wrapper. Not recommended for credentials.

---

## Comparison

| Criterion | EncryptedSharedPreferences | Tink-encrypted DataStore | Manual Keystore AES-GCM |
|---|---|---|---|
| New deps | 1 (`security-crypto`) | 1 (`tink-android`, archived) | 0 |
| Preserves `ConfigRepository` Flow/suspend API | ❌ rewrite needed | ✅ delegate swap only | ✅ (values stored as strings) |
| At-rest AES-GCM | ✅ | ✅ | ✅ (you write it) |
| Key mgmt | automatic (Keystore) | automatic (`AndroidKeysetManager`) | manual |
| Migration effort | medium (repo rewrite) | medium (serializer + migration) | high (crypto + migration) |
| Upstream health | active (1.1.0, Jul 2025) | archived since Apr 2024 | n/a |
| Selective encryption | awkward | awkward | trivial |

---

## Recommendation

For this codebase — a DataStore-Preferences app whose `ConfigRepository` is fully Flow/suspend, with 4 high-sensitivity string secrets and `allowBackup=false` already handled — **Approach 2 (Tink-encrypted DataStore via a custom `Serializer`) is the best architectural fit** because it preserves the existing `Context.dataStore` delegate pattern and every `.data.map`/`.edit` call in `ConfigRepository` unchanged; only the factory at `ConfigRepository.kt:12` moves.

However, **Approach 1 (EncryptedSharedPreferences `security-crypto:1.1.0`) is the safest operational pick** — actively maintained Jetpack library, audited crypto, automatic Keystore key management — at the cost of rewriting `ConfigRepository` to SharedPreferences + a Flow adapter (≈8 reads + 4 writers).

**Final call**: Given the task emphasises "plays well with the existing `context.dataStore` delegate pattern" and "smooth migration," adopt **Approach 1 with a thin Flow-adapter layer**, unless the repository rewrite is a hard blocker — in which case fall back to Approach 2 and accept the archived-dependency risk by pinning `tink-android:1.7.0` and noting it in a spec. **Avoid Approach 3** unless team has crypto expertise; **skip Approach 4.**

## Migration Sequence (Approach 1)

1. Add `androidx.security:security-crypto:1.1.0` to `app/build.gradle.kts`.
2. Create `EncryptedConfigStore` wrapping `EncryptedSharedPreferences` (master key via `MasterKey.Builder`) + a `Flow<NavidromeConfig>` / `Flow<VideoServerConfig>` surface mirroring `ConfigRepository`'s public API.
3. Add a one-time migration guard: on app start, if `esp.getBoolean("migrated", false) == false`, `runBlocking { context.dataStore.data.first() }` → copy all 12 keys into `esp` via `esp.edit().commit()` (sync, to guarantee durability before clearing).
4. Set `esp.edit().putBoolean("migrated", true).commit()`.
5. Delete the plaintext DataStore file (`context.filesDir.resolve("datastore/settings.preferences_pb")`) once migration succeeds.
6. Swap `ConfigRepository`'s backing from `context.dataStore` to `EncryptedConfigStore`; keep the public `Flow`/`suspend` signatures so callers don't change.
7. Verify on a clean install (no migration) AND an upgrade install (migration path) on minSdk 26 device.

## Caveats / Not Found

- `usesCleartextTraffic="true"` (`AndroidManifest.xml:13`) is **out of scope** for at-rest encryption but worth flagging: it allows plaintext HTTP on the wire; in-transit protection is a separate task.
- Exact `tink-android` post-archive maintenance policy could not be confirmed from the repo README beyond "read-only, not maintained moving forward." If Approach 2 is chosen, re-verify whether `tink-crypto` re-publishes under new coordinates before depending on it.
- `security-crypto:1.1.0` changelog was not directly fetched (developer.android.com timed out); version number and "latest release" status are confirmed via Google Maven metadata only. Read the 1.1.0 release notes before pinning.
- Word count target (~1500) respected; this file is ~1350 words.
