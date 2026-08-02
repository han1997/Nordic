# Navidrome 集成契约补齐

- 优先级: P0
- 创建: 2026-08-02
- Assignee: hhy
- 关联审查: `.trellis/workspace/hhy/code-review-2026-08-02.md`

## 背景

对照 `navidrome-integration.md`，Navidrome 仓库与 API 接口**完全缺失** star/unstar/getStarred2、playlist CRUD、getSimilarSongs、scrobble 等核心契约端点。收藏、歌单、相似推荐、播放上报等用户可见功能无法实现。同时 `SubsonicError` DTO 字段非空，缺省时 NPE。

## 范围

### In scope
- 补齐 Retrofit 端点：`star`/`unstar`/`getStarred2`/`createPlaylist`/`updatePlaylist`/`deletePlaylist`/`getSimilarSongs`/`scrobble`
- 补齐对应 `NavidromeRepository` 方法（按 spec 签名与校验矩阵）
- `SubsonicError.code/message` 改可空 + null-safe 错误格式化（`NavidromeApi.kt:42-45`、`NavidromeRepository` 错误分支）
- 仓库方法遵循类型化错误与 Response 校验（`error-handling.md`）

### Out of scope
- 通用 DTO null 安全（ABS/Emby，见 T5）
- 收藏/歌单的 UI 接入（待架构任务后另排）
- 令牌 URL 缓存（见 T1）

## 覆盖的审查发现

| ID | 严重度 | 位置 | 概述 |
|---|---|---|---|
| H5 | High | `data/NavidromeRepository.kt` / `api/NavidromeApi.kt` | star/unstar/getStarled2/playlist CRUD/getSimilarSongs/scrobble 全缺 |
| M-SubsonicError | Medium | `api/NavidromeApi.kt:42-45` | 非空字段缺省 NPE |

## 验收标准
- [ ] 上述 8 类端点均有 Retrofit 接口 + 仓库方法，签名匹配 spec
- [ ] `getStarred2`、playlist CRUD、scrobble 有 MockWebServer 单元测试（参考 `NavidromeRepositoryTest` 深度）
- [ ] `SubsonicError` 可空，错误分支 null-safe
- [ ] `.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug --no-daemon` 通过

## 涉及 spec（待 1.3 jsonl 整理）
- `.trellis/spec/backend/navidrome-integration.md`
- `.trellis/spec/backend/error-handling.md`
- `.trellis/spec/backend/logging-guidelines.md`
