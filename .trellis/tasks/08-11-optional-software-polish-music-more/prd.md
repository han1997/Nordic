# 继续打磨音乐部分

## Goal

继续提升音乐模块的用户体验，把歌单管理、歌词展示、歌曲库筛选/浏览效率、播放队列细节四个方向都纳入本轮任务，但每个方向都收敛为最小可用、可验证、风险可控的体验切片。

## What I Already Know

* 用户要求“继续打磨音乐部分”，并确认歌单管理入口、播放页歌词体验、歌曲库筛选/浏览效率、播放队列细节四个方向都放在本次任务做。
* 最近几轮已经完成音乐播放队列排序、音乐播放页进度拖动/相对 seek、音乐搜索一键清除等体验改进，本轮不默认重复这些方向。
* 音乐模块主要涉及 `MusicScreenV2`、`MusicBrowseComponents`、`MusicPlayerScreen`、`MusicQueueSheet`、`MusicPlaybackEngine`、`MusicPlaybackViewModel`、`NavidromeRepository` 等文件。
* `NavidromeRepository` 已经具备歌单创建、重命名、添加歌曲、移除歌曲、删除歌单等接口，并已有仓库层测试，但 `MusicScreenV2` 当前主要提供歌单浏览、歌单详情和播放入口。
* 音乐播放页已有歌词展示、收藏、队列、播放控制和进度拖动能力。
* 音乐歌曲页已有排序能力，专辑页已有专辑排序能力。

## Assumptions

* 本轮虽然覆盖四个方向，但每个方向应优先做小型 MVP，而不是一次性重做整个音乐模块。
* 用户更看重可直接体验到的功能或交互补齐，而不是纯内部重构。
* 若改变用户可见行为，应更新 `CHANGELOG.md`。

## Requirements

* 保持现有音乐浏览、播放、歌词、搜索、队列和收藏行为不回归。
* 遵循现有 Material 3 / Nordic design token / 中文文案规范。
* 歌单管理入口：基于已有 Navidrome 仓库能力，在音乐 UI 中补齐用户可见的歌单管理入口，支持创建歌单、重命名歌单、删除歌单，并提供加载、失败和成功后的列表刷新/状态同步。
* 播放页歌词体验：改善歌词区域的可读性和状态反馈，让加载中、无歌词、普通歌词、同步歌词的呈现更明确，避免用户误以为功能失效。
* 歌曲库筛选/浏览效率：在歌曲页提供快速过滤入口，和现有排序能力组合使用，帮助大曲库用户在当前已加载歌曲中快速定位。
* 播放队列细节：补齐队列页的实用控制和状态反馈，例如清空后续队列、定位当前播放项、空状态/边界状态提示等，保持已有拖动排序和上下移动行为不回归。

## Acceptance Criteria

* [ ] 歌单管理动作有明确入口，成功后 UI 状态刷新，失败时显示可理解的中文提示。
* [ ] 歌词区域能区分加载中、无歌词、普通歌词和同步歌词状态，并保持随播放位置更新的既有行为。
* [ ] 歌曲页可在当前歌曲集合中筛选，筛选结果继续遵循当前排序选项，清空筛选可恢复全量列表。
* [ ] 队列页新增的控制不会破坏拖动排序、上下移动、移除和播放指定曲目的现有行为。
* [ ] 现有音乐播放、浏览、队列、搜索和收藏行为不回归。
* [ ] 必要的逻辑或仓库/UI 测试已补充或更新。
* [ ] `compileDebugKotlin`、`testDebugUnitTest`、`lintDebug` 通过。

## Definition Of Done

* PRD 范围经用户确认。
* 代码改动聚焦本轮四个音乐体验方向，避免无关重构。
* 用户可见行为写入 `CHANGELOG.md`。
* 通过完整 Gradle 质量门禁。

## Technical Approach

* 歌单管理优先复用 `NavidromeRepository` 现有 `createPlaylist`、`renamePlaylist`、`deletePlaylist` 方法，在 `MusicScreenV2` 歌单列表/详情页补充轻量弹窗与状态管理，不新增独立 ViewModel。
* 歌曲筛选采用本地过滤：先按标题、歌手、专辑匹配当前 `songs` 集合，再复用 `sortMusicSongs(...)` 排序，相关匹配逻辑放在 `MusicScreenLogic.kt` 便于测试。
* 歌词体验聚焦 `MusicPlayerScreen` 的展示状态和纯逻辑 helper，保留现有歌词获取和同步行选择机制。
* 队列细节优先复用 `MusicPlaybackViewModel.clearUpcomingQueueItems()` 等既有能力，在 `MusicQueueSheet` 增加可见控制、禁用/空状态说明和当前播放定位辅助，不重写队列引擎。

## Decision (ADR-lite)

**Context**: 音乐模块已有较完整的数据层和播放层能力，但部分用户入口和状态反馈仍偏轻。用户确认四个方向都放入本轮任务。

**Decision**: 本轮采用“四个最小切片”的方式：歌单管理补 UI 入口，歌词体验补状态/可读性，歌曲页补本地筛选，队列页补操作反馈。尽量复用现有仓库、播放引擎和纯逻辑 helper，避免引入新的架构层。

**Consequences**: 用户可见提升覆盖面更广，但实现必须严格控制范围；复杂歌单编辑、歌词来源扩展和服务端搜索留给后续任务。

## Out Of Scope

* 不重做整个音乐模块视觉架构。
* 不改变 Navidrome 认证、服务器配置或跨媒体底层播放服务结构，除非后续范围明确要求。
* 不重复本轮之前已完成的搜索清除、播放队列排序、播放页 seek 改动。
* 不实现完整歌单编辑器、批量选择、多选加歌、跨歌单移动、复杂权限模型或离线编辑队列，除非后续明确扩大范围。
* 不引入新的歌词来源、歌词编辑、歌词时间轴校准或卡拉 OK 效果。
* 不引入服务端全库搜索替代歌曲页本地筛选。

## Technical Notes

* Task directory: `.trellis/tasks/08-11-optional-software-polish-music-more`
* Likely relevant specs: `.trellis/spec/backend/index.md`, `.trellis/spec/backend/quality-guidelines.md`, `.trellis/spec/backend/navidrome-integration.md`, `.trellis/spec/backend/documentation-guidelines.md`
* Candidate files inspected via `rg`: music UI, playback, Navidrome repository, related tests.
