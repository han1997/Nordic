# AudiobookShelf 续听与类型化错误

- 优先级: P1
- 创建: 2026-08-02
- Assignee: hhy
- 关联审查: `.trellis/workspace/hhy/code-review-2026-08-02.md`

## 背景

AudiobookShelf 仓库续听功能断裂（`getLibraryItems` 未带 `include=progress`，进度恒 null）；sync/close 调用缺类型化 catch，原始 `IOException` 直达引擎；`bearerToken` 登录未捕获 `EOFException`，破坏类型化错误契约；多处 DTO 非空字段在服务端缺省时 NPE。本任务统一 ABS 的契约正确性与类型化错误，并顺手加固跨服务 DTO null 安全。

## 范围

### In scope
- ABS 续听：`getLibraryItems` 加 `include=progress`，精简 DTO 加 `userMediaProgress`，`toSummary()` 映射进度（`AudiobookShelfApi.kt:221`/`AudiobookShelfRepository.kt:124,260`）
- ABS 类型化错误：`syncProgress`/`closeSession`/`syncAndCloseSession` 加 catch-rethrow 为 `AudiobookShelfApiException`（:214-258）；`bearerToken` 包 `EOFException`（:80）
- `syncAndCloseSession` try/finally 保证 `closeSession` 必跑（:255-258）
- `bearerToken` 观测 401 清缓存重试一次（:77-103）
- `toAbsoluteAudioUrl` token 用 `Uri.encode`/`HttpUrl.Builder`（:343-344）
- ABS DTO 非空字段改可空 + 仓库校验：`AudiobookShelfApi.kt:60-65,93,162-177`
- Emby DTO null 安全：`EmbyApi.totalRecordCount` 改 `Int?`，null 视为"未知"翻页至空/短页（`EmbyApi.kt:37`）

### Out of scope
- Navidrome DTO null 安全（SubsonicError）→ T2
- 凭据/令牌缓存 → T1
- 通用架构 → T4

## 覆盖的审查发现

| ID | 严重度 | 位置 | 概述 |
|---|---|---|---|
| C4 | Critical | `AudiobookShelfApi.kt:221`/`AudiobookShelfRepository.kt:124,260` | 续听进度恒 null |
| H9 | High | `AudiobookShelfRepository.kt:214-258` | sync/close 缺类型化 catch |
| H10 | High | `AudiobookShelfRepository.kt:80` | bearerToken 未捕 EOFException |
| M-ABS DTO | Medium | `AudiobookShelfApi.kt:60-65,93,162-177` | 非空字段缺省 NPE |
| M-非原子 close | Medium | `AudiobookShelfRepository.kt:255-258` | sync 抛则 close 被跳 |
| M-401 重认证 | Medium | `AudiobookShelfRepository.kt:77-103` | token 缓存永不过期 |
| M-token 未编码 | Medium | `AudiobookShelfRepository.kt:343-344` | URL 拼接未编码 |
| L-Emby 分页 | Low | `EmbyApi.kt:37` | totalRecordCount 非空截断分页 |

## 验收标准
- [ ] 续听列表可显示进度/可续播
- [ ] sync/close/bearerToken 错误均为 `AudiobookShelfApiException`，无未类型化异常外泄
- [ ] `syncAndCloseSession` 无论 sync 成败都关闭会话
- [ ] 401 后自动重认证重试一次
- [ ] ABS/Emby DTO 缺省字段不 NPE
- [ ] `.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug --no-daemon` 通过

## 涉及 spec（待 1.3 jsonl 整理）
- `.trellis/spec/backend/audiobookshelf-integration.md`
- `.trellis/spec/backend/emby-integration.md`
- `.trellis/spec/backend/error-handling.md`
