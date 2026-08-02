# 凭据安全加固

- 优先级: P0
- 创建: 2026-08-02
- Assignee: hhy
- 关联审查: `.trellis/workspace/hhy/code-review-2026-08-02.md`

## Goal

消除凭据泄露链：服务器密码/API key 明文落盘（DataStore）、URL 默认降级 http:// 明文传输、长期令牌嵌入媒体 URL 被 ExoPlayer/Coil 磁盘缓存持久化。三处叠加形成完整泄露链，本任务从"落盘加密 + 不默认明文 + 令牌不入磁盘"三方向一次性加固。

## Requirements

### R1 凭据加密存储（决策 A1）
- 新增依赖 `androidx.security:security-crypto:1.1.0`（`app/build.gradle.kts`）
- 新增 `EncryptedConfigStore`：包装 `EncryptedSharedPreferences`（`MasterKey.Builder` AES256_GCM + AES256_SIV/AES256_GCM scheme），文件名 `secret_prefs`
- `EncryptedConfigStore` 暴露与 `ConfigRepository` 现有公开 API 同形的 `Flow<NavidromeConfig>`/`Flow<AudiobookShelfConfig>`/`Flow<String?>`/`Flow<VideoServerConfig>` + 4 个 `suspend fun saveXxx`；通过 `OnSharedPreferenceChangeListener` 回流为 Flow（冷流 + distinctUntilChanged）
- `ConfigRepository` 内部 backing 由 `context.dataStore` 切换到 `EncryptedConfigStore`；**公开签名不变**，调用方零改动
- 一次性迁移：旧 `context.dataStore`（`datastore/settings.preferences_pb`，12 个 key）→ ESP；用 `migrated` 布尔保证幂等；`commit()`（同步）保证持久后再删旧文件；迁移在后台协程跑（不在主线程 `runBlocking`，避免 ANR）

### R2 令牌 URL 磁盘缓存（决策 B1+B2）
- `MusicPlaybackService`：`CacheDataSource.Factory.setCacheKeyFactory { stripAuthQuery(it.uri) }`，剥离 `api_key`/`token`/`u`/`t`/`s`/`v`/`c`（~10 行）；`SimpleCache` 保留
- Emby：`EmbyRepository.streamUrl`/`primaryImageUrl` 去掉 `?api_key=` 查询；共享 `OkHttpClient` 加 `Interceptor` 注入 `X-Emby-Token` header（先 `curl -H "X-Emby-Token: …" <stream-url>` 烟测 stream/image 端点；若拒则回退 `api_key` 查询 + B1 clean key 降级）
- ABS：`AudiobookShelfRepository.toAbsoluteAudioUrl` 去掉 `?token=`；`OkHttpDataSource` 共享 client 加 `Authorization: Bearer` 拦截器
- Navidrome：Subsonic 协议无 header 选项，仅靠 B1 clean key；`t` 为一次性 salted md5（非可复用密码），clean key 后磁盘零泄漏
- Coil：`ImageLoader` 共享 client 加同样 `X-Emby-Token`/`Authorization` 拦截器；13 个 `AsyncImage(model=url)` 站点抽 `AuthedAsyncImage(url)` helper，显式 `diskCacheKey(stripAuth(url))` + `memoryCacheKey(...)`
- `VideoPlaybackEngine`（无磁盘缓存）：Emby video stream 仍改 `X-Emby-Token` header，令牌离开 `MediaItem.uri`/logcat

### R3 cleartext 流量（决策 C1-轻量）
- 三个 normalizer 默认补 `https://`（`NavidromeAuth.kt:27`/`AudiobookShelfAuth.kt:13`/`VideoServerAuth.kt:13` 三处 `"http://$trimmed"` → `"https://$trimmed"`）
- 保留 `startsWith("http://")` 显式 http 分支（LAN 自托管主用例）
- 保持 `usesCleartextTraffic="true"`，不引入 `network_security_config.xml`（NSC 不支持运行时私网 CIDR allowlist，全禁会破自托管）

### R4 测试
- `EncryptedConfigStoreTest`：Flow 回流、distinct、save→emit
- 迁移测试：旧明文→新加密、中断重跑（migrated flag 幂等）、旧文件删除
- `stripAuth` 纯函数测试（各服务 token 参数剥离）
- 现有 `NavidromeAuthTest`/`AudiobookShelfAuthTest`/`VideoServerAuthTest`（待补）/`EmbyRepositoryTest`/`AudiobookShelfRepositoryTest` 更新断言（URL 不再含 token、header 注入）

### R5 兜底
- `allowBackup="false"` 保持
- 永不记录凭据/令牌到 logcat（`HttpLoggingInterceptor` 维持 `Level.NONE`）

## Acceptance Criteria

- [ ] DataStore `settings.preferences_pb` 删除后无明文密码/API key 残留；ESP 文件加密
- [ ] 升级安装：旧明文配置自动迁移到 ESP，用户无需重输；迁移中断重启可重跑
- [ ] `MusicPlaybackService` 缓存目录的 `cached_content_index` 不含 `api_key`/`token`/`t`/`s`/`u` 等鉴权参数
- [ ] Coil 磁盘缓存 key 不含鉴权参数
- [ ] Emby stream/image 请求走 `X-Emby-Token` header（烟测通过）；若服务端拒 header，回退 api_key 查询但 cache key 仍 clean
- [ ] ABS audio 请求走 `Authorization: Bearer` header
- [ ] 三个 normalizer 裸主机名默认 `https://`；显式 `http://` 仍可用
- [ ] `.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug --no-daemon` 通过
- [ ] logcat 无凭据/令牌

## Definition of Done

- 上述 R1-R5 全部落地，Acceptance Criteria 全绿
- 迁移路径在干净安装（无迁移）+ 升级安装（有迁移）两端各验证一次
- spec 更新（`database-guidelines.md` 记录 ESP 迁移与 key 清单；`logging-guidelines.md` 确认 `Level.NONE` 不变；三家集成契约记录 header 鉴权 + clean cache key 约定）

## Technical Approach

**三层加固**：
1. **落盘加密**：`EncryptedSharedPreferences`（security-crypto:1.1.0）替代明文 DataStore，`EncryptedConfigStore` Flow 适配器保持 `ConfigRepository` 公开 API 不变；后台协程一次性迁移 12 key + 删旧文件。
2. **令牌离盘**：Media3 `CacheKeyFactory` + Coil `diskCacheKey` 剥离 auth（全服务、保 SimpleCache）；Emby/ABS 进一步改 header 鉴权令令牌离网络 URL；Navidrome 靠 clean key（协议无 header）。
3. **不默认明文**：normalizer 默认 `https://`，保留显式 `http://` 兼容 LAN 自托管。

**关键实现点**：
- `EncryptedConfigStore` 用 `OnSharedPreferenceChangeListener` → `callbackFlow` → `distinctUntilChanged` 暴露冷 Flow；`suspend save` 用 `commit()`（同步，保证持久）而非 `apply()`。
- `stripAuth(url)` 纯函数（OkHttp `HttpUrl.parse` → 去掉指定 query key → `toString()`），ExoPlayer `CacheKeyFactory` 与 Coil `diskCacheKey` 共用。
- Emby/ABS OkHttp `Interceptor` 按 host/baseUrl 匹配注入对应 header，避免误注到无关请求。
- 迁移幂等：ESP `migrated=false` → 后台 `runBlocking { context.dataStore.data.first() }`（IO dispatcher）→ `commit()` → `migrated=true` → 删 `datastore/settings.preferences_pb`。

## Decision (ADR-lite)

**Context**: 凭据明文落盘 + 默认 http 明文传输 + 令牌入磁盘缓存，三处叠加泄露链。需选加密存储方案、令牌缓存策略、cleartext 策略。

**Decisions**:
- **A1 EncryptedSharedPreferences + Flow 适配器**：选维护中 Jetpack 审计库（放弃 A2 tink-android 已归档、A3 手写 crypto）。
- **B1+B2 cache key 剥离 + Emby/ABS header 鉴权**：保 SimpleCache 同时令牌离盘离 URL（放弃 B3 本地代理 over-engineered）。
- **C1-轻量 normalizer 默认 https + 保留显式 http**：兼容 LAN 自托管主用例（放弃 NSC 全禁，会破私网运行时输入）。

**Consequences**:
- +1 依赖（security-crypto）；ConfigRepository 内部重写为 ESP + Flow 适配（~100-150 行）；调用方零改动。
- Emby/ABS 仓库 URL 构造 + OkHttp client 改 header 注入；13 个 AsyncImage 站点经 helper 统一。
- 迁移逻辑需测试覆盖；Emby header 需烟测 + 降级回退。
- 未来增强（不在 MVP）：Keystore 主密钥 `setUserAuthenticationRequired` 生物识别解锁。

## Out of Scope

- 三家服务业务逻辑契约（T2 Navidrome、T5 ABS 续听/类型化错误）
- 通用 DTO null 安全（T5）
- `toVideoServerType` 默认回退 EMBY 等配置持久化正确性（T9）
- ViewModel/领域模型架构（T4）
- 生物识别解锁凭据（未来增强）

## Research References

- [`research/android-encrypted-credential-storage.md`](research/android-encrypted-credential-storage.md) — 12 key（4 高危）；ESP 需重写 Flow/suspend；Tink 保留 delegate 但已归档；manual Keystore 可选加密仅 4 key。
- [`research/media-url-token-cache-strategy.md`](research/media-url-token-cache-strategy.md) — Media3 仅持久化 cache key；#3 clean key + #4 Emby/ABS header（已验证服务端支持）；Navidrome 无 header 靠 #3；Emby X-Emby-Token 需 curl 烟测。

## Technical Notes

- `minSdk=26`（`app/build.gradle.kts:12`），security-crypto（API 23+）/tink（API 19+）均兼容。
- 现状：`allowBackup="false"`（manifest:10）已设；`HttpLoggingInterceptor` 全 `Level.NONE`（4 处）；`MusicPlaybackService` SimpleCache 100MB LRU 无 `CacheKeyFactory`（:48-60）；`VideoPlaybackEngine` 无磁盘缓存；Coil `ImageLoader`（MainActivity:203-213）无显式 `diskCache` 用默认。
- `MusicDownloadManager` 下载 URL 带 Navidrome auth 查询参数，但下载文件为媒体字节（无 token 落盘），无需改。
- `EmbyApi`/`AudiobookShelfApi` 已对 JSON API 用 `@Header("X-Emby-Token")`/`@Header("Authorization")`，证明服务端支持 header 鉴权；本任务把同一机制扩到媒体流/图片端点。

## 涉及 spec（待 1.3 jsonl 整理）

- `.trellis/spec/backend/database-guidelines.md`（ESP 迁移、key 清单）
- `.trellis/spec/backend/directory-structure.md`（EncryptedConfigStore 位置）
- `.trellis/spec/backend/navidrome-integration.md` / `emby-integration.md` / `audiobookshelf-integration.md`（header 鉴权 + clean cache key 约定）
- `.trellis/spec/backend/logging-guidelines.md`（`Level.NONE` 不变、凭据不入日志）
