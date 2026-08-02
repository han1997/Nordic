# 修复 MusicDownloadManager

- 优先级: P2
- 创建: 2026-08-02
- Assignee: hhy
- 关联审查: `.trellis/workspace/hhy/code-review-2026-08-02.md`

## 背景

`MusicDownloadManager` 有多处正确性缺陷：失败时删错临时文件名致孤儿累积；`renameTo` 忽略返回值致状态错报 DOWNLOADED；失败路径未关 response 泄漏 OkHttp 连接；下载去重 check-then-launch 竞态可双开；扩展名映射把 `audio/m4b`/`audio/mp4` 错误映射成 `mp3`。

## 范围

### In scope
- 临时文件清理：删除 :104 创建的同一 `tempFile` 引用（或重构 `"${song.id}.${extension}.tmp"`）（`:136`）
- `renameTo` 检查返回值，失败回退 copy-then-delete 或标失败（`:128-131`）
- 失败/早返回路径 `response.close()`/`body?.close()`（`:89-97`）
- 下载去重原子化：`putIfAbsent` 或同步 check-and-launch（`:66`）
- 扩展名映射加 `m4b`/`mp4` 显式分支（`:219-230`）

### Out of scope
- 下载 UI/状态展示重构
- 通用缓存策略

## 覆盖的审查发现

| ID | 严重度 | 位置 | 概述 |
|---|---|---|---|
| C5 | Critical | `MusicDownloadManager.kt:136` | 删错临时文件名致孤儿累积 |
| M-renameTo | Medium | `:128-131` | 忽略返回值，状态错报 |
| M-未关 response | Medium | `:89-97` | 泄漏 OkHttp 连接 |
| M-去重竞态 | Medium | `:66` | check-then-launch 可双开 |
| L-扩展名 | Low | `:219-230` | m4b/mp4 错映射为 mp3 |

## 验收标准
- [ ] 失败时无孤儿临时文件残留
- [ ] 重命名失败不被错报为 DOWNLOADED
- [ ] 失败路径不泄漏连接
- [ ] 并发 `downloadSong` 同 id 不双开
- [ ] m4b/mp4 扩展名正确
- [ ] `MusicDownloadManagerTest` 补充上述路径用例
- [ ] `.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug --no-daemon` 通过

## 涉及 spec（待 1.3 jsonl 整理）
- `.trellis/spec/backend/database-guidelines.md`
- `.trellis/spec/backend/error-handling.md`
