# 完善有声书、视频 UI、逻辑

## Goal

对 有声书 和 视频 两大模块做全面完善，覆盖四方面：
1. **功能缺口补齐** — 补齐缺失的关键功能
2. **播放器交互体验** — 打磨播放器控制/手势/进度/章节/全屏交互
3. **视觉/UI 打磨** — 对齐音乐模块的视觉观感，更精致统一
4. **逻辑健壮性/数据层** — 状态管理、缓存、进度同步、错误处理等

按批次推进：先全局盘点产出改进清单，再分批次实现。

## What I already know

* Android Jetpack Compose 媒体中心应用 (mediahub)，含 音乐 / 有声书 / 视频 三大媒体模块
* 有声书：AudiobookScreen(书库列表/详情/章节) + AudiobookPlayerScreen(播放器)
* 视频：VideoScreen(浏览) + VideoDetailScreen(详情) + VideoPlayerScreen(播放器, 支持手势/全屏/信息面板/宽高比)
* 两者逻辑层有独立 playback engine / ViewModel

## Assumptions (temporary)

* 用户希望在有声书和视频的 UI 与交互逻辑上做增强/完善

## Open Questions

* 具体要完善哪些点？(UI 视觉? 播放逻辑? 新增功能?)

## Requirements (evolving)

* 覆盖 功能缺口补齐 / 播放器交互体验 / 视觉 UI 打磨 / 逻辑健壮性数据层 四方面
* 按批次推进，每批交付可验证的改进
* 依据全局盘点 (research/audit-audiobook-video.md)，分 5 批：
  - **Batch A** 书签功能激活（有声书）：把已实现的 AudiobookBookmarkRepository 接入 ViewModel + PlayerScreen，支持增删/跳转/列表
  - **Batch B** 常驻 now-playing（有声书 + 视频）：泛化 PlaybackDock，支持有声书/视频；关闭播放器后仍可从 dock 回到播放
  - **Batch C** 播放器交互与滑杆一致：共享薄滑杆；视频缓冲指示/剩余时间/双击跳转反馈；有声书滑动关闭 + 睡眠定时器
  - **Batch D** 关闭路径健壮性 + 状态机加固：关闭同步失败提供"仍要关闭"；进度同步门控；有声书时长按 track 求和
  - **Batch E** 视觉统一 + 详情页清理：复用共享按钮组件；视频 spotlight 空态提示
* **Batch F（新增）** 参考主流软件完善三大模块播放页面（Batch A-E 已完成并提交 29d4082）：
  - 音乐播放页 ← 音流（Subsonic/Navidrome 客户端播放页范式）
  - 有声书播放页 ← audiobookshelf 官方 App（章节/书签/睡眠定时器交互）
  - 视频播放页 ← Hills / Yamby（Emby 第三方客户端手势与轨道选择）
  - 依据 research/mainstream-player-reference.md 的特性清单实施

## Acceptance Criteria (evolving)

* [ ] 全局盘点完成，产出优先级改进清单 (research/audit-audiobook-video.md) ✅
* [ ] Batch A-E 每批实现并通过 lint / typecheck / 测试 ✅ (commit 29d4082)
* [ ] Batch F：三大模块播放页对标主流软件的改进实现并通过 lint / typecheck / 测试

## Definition of Done

* lint / typecheck / 测试通过
* 行为变更时更新相关测试

## Out of Scope (explicit)

* 音乐模块本身的改进（但可复用其视觉/交互规范）
* 若某批改动过大会拆成多个子任务

## Research References

* [`research/audit-audiobook-video.md`](research/audit-audiobook-video.md) — 有声书/视频模块全面审计，含 P0-P2 分级发现与 5 个实施批次

## Technical Notes

* 有声书 screen: app/src/main/java/com/nordic/mediahub/ui/AudiobookScreen.kt
* 有声书 player: app/src/main/java/com/nordic/mediahub/ui/AudiobookPlayerScreen.kt
* 视频 screen: app/src/main/java/com/nordic/mediahub/ui/VideoScreen.kt
* 视频 player: app/src/main/java/com/nordic/mediahub/ui/VideoPlayerScreen.kt