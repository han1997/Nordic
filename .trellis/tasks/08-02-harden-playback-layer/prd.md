# 播放层健壮性加固

- 优先级: P0
- 创建: 2026-08-02
- Assignee: hhy
- 关联审查: `.trellis/workspace/hhy/code-review-2026-08-02.md`

## 背景

`playback/` 层存在多处生命周期与健壮性缺陷：MediaController 断连后引擎变死桩无重连；视频 ExoPlayer 不处理音频焦点/变噪；单一共享 ExoPlayer 同时服务音乐与有声书可互相覆盖；三引擎 `onPlayerError` 吞异常无日志；`MusicPlaybackService` 缓存构造未守、audioSessionId 不随重建刷新、MediaSession 无 Callback 校验调用方。这是 P0 用户体验与稳定性重点。

## 范围

### In scope（仅 `playback/` 包 + `MusicPlaybackService`）
- MediaController 断连恢复：改用 `MediaController.Listener.onDisconnected`，置空 controller、重连、重发 pending 命令（`MusicPlaybackEngine.kt:136-185`、`AudiobookPlaybackEngine.kt:66-94`）
- 视频 ExoPlayer 配 `AudioAttributes`(USAGE_MEDIA, MOVIE, handleAudioFocus=true) + `setHandleAudioBecomingNoisy(true)`（`VideoPlaybackEngine.kt:61`）
- 共享 player 所有权：service 两 player/两 session 按域区分，或引擎层 token/lease 强制单域（`MusicPlaybackService.kt:103`、`AudiobookPlaybackEngine.kt:54-58`、`MusicPlaybackEngine.kt:123-127`）
- 三引擎 `onPlayerError` 加 `Log.e`，并在 spec 注册播放子系统 tag
- `MusicPlaybackService`：SimpleCache 构造 try/catch 降级、audioSessionId 随重建重发、MediaSession 加 Callback 校验 `controllerInfo.packageName`、评估改名 `MediaPlaybackService`、`cache!!` 改局部 val
- `MusicPlaybackEngine`：`togglePlayPause` 断连分支带位置或 no-op、`playQueue` 同步置 `cachedTimelineGeneration=-1`、`seekToNext/Previous` 去掉陈旧位置同步发布
- `AudiobookPlaybackEngine`：单 `pendingSession` 覆盖时清旧 state

### Out of scope
- `MainActivity` 内同步循环/关闭编排的吞错误修复 → 由 T4（ViewModel 抽取）一并处理，避免对 MainActivity 双重编辑
- ui→api/领域模型 → T4

## 覆盖的审查发现

| ID | 严重度 | 位置 | 概述 |
|---|---|---|---|
| C2 | Critical | `MusicPlaybackEngine.kt:136-185`/`AudiobookPlaybackEngine.kt:66-94` | MediaController 断连无恢复 |
| H6 | High | `VideoPlaybackEngine.kt:61` | 视频忽略音频焦点/变噪 |
| H7 | High | `MusicPlaybackService.kt:103`/两引擎 | 共享 ExoPlayer 可互相覆盖 |
| H8 | High | 三引擎 `onPlayerError` | 吞异常无 Log.e |
| M-服务 | Medium | `MusicPlaybackService.kt:48-55,32-34,103-106,27,58` | 缓存未守/audioSessionId/无 Callback/命名/cache!! |
| M-引擎 | Medium | `MusicPlaybackEngine.kt:411-418,315-319,322-330`/`AudiobookPlaybackEngine.kt:129-137` | toggle/timeline/seek/pendingSession |

## 验收标准
- [ ] service 被杀后引擎可重连并恢复 pending 命令（含 instrumentation 测试）
- [ ] 视频拔耳机暂停、不抢焦点
- [ ] 音乐/有声书切换不会静默覆盖对方播放列表
- [ ] 播放失败有 `Log.e` 记录 throwable
- [ ] `MusicPlaybackService` 缓存损坏不致 onCreate 崩
- [ ] `.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug --no-daemon` 通过

## 涉及 spec（待 1.3 jsonl 整理）
- `.trellis/spec/backend/error-handling.md`
- `.trellis/spec/backend/logging-guidelines.md`
- `.trellis/spec/backend/quality-guidelines.md`
