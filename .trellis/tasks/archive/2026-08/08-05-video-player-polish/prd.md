# 视频播放器界面美化与播放逻辑优化

## Goal

优化视频播放体验：保留当前 Media3/ExoPlayer 作为默认播放内核，先提升播放器界面、控制交互和播放逻辑稳定性，同时通过轻量播放后端抽象为未来 mpv/libVLC 技术验证预留扩展点。

## What I already know

* 用户认可推荐方向：先做 Media3 UI/逻辑优化和后端抽象预留，不立刻替换为 mpv/libVLC。
* 当前 UI 入口在 `app/src/main/java/com/nordic/mediahub/ui/VideoPlayerScreen.kt`。
* 当前播放内核在 `app/src/main/java/com/nordic/mediahub/playback/VideoPlaybackEngine.kt`，直接持有 `ExoPlayer`、Surface attach/detach、播放/暂停、seek、比例模式、错误状态和位置轮询。
* 现有播放器已经支持：全屏切换、Fit/Crop/Fill、10 秒后退/30 秒前进、拖动进度、自动隐藏控制层、Emby 鉴权 header、错误/缓冲状态、未知时长时间轴。
* 近期已明确不建议短期直接集成 mpv/libVLC；只有遇到 Media3 格式兼容、复杂字幕或音轨能力瓶颈时再做独立预研。

## Assumptions (temporary)

* 本任务不引入 mpv/libVLC native 依赖，不改变当前 Emby 播放 URL 和进度同步协议。
* 本任务优先改善手机横竖屏播放体验，不新增平板/TV 专属布局。
* UI 美化应保持现有 Compose/token 体系，不引入新的设计系统或播放器 SDK UI。
* 播放后端抽象只抽到可测试、低风险的边界，避免为了未来能力大规模重写。

## Open Questions

* 无。

## Requirements (evolving)

* 保留 Media3/ExoPlayer 作为默认播放内核。
* 采用重设计版：播放器 UI 大改为沉浸式媒体优先体验，参考 Infuse/Netflix 的控制层思路，但保持 Nordic 现有轻盈、半透明、内容优先的设计语言。
* 重做播放器控制层，使播放页比当前黑底浮层更清晰、现代，并适配普通模式和全屏模式。
* 控制层应分为清晰的视觉区域：顶部退出/标题/状态，中部播放反馈，底部时间轴/主控/辅助控制。
* 控制层视觉应避免厚重工具感；使用渐变遮罩、半透明 surface、稀缺 accent、清晰按钮组，而不是大面积实色面板。
* 增加轻量播放信息面板入口，用于在播放页内查看当前视频的标题、类型、年份、时长、观看进度、评分和简介，不离开播放器。
* 信息面板只消费当前 `VideoItem` 已有字段；字段缺失时隐藏对应项或显示温和空状态，不新增 Emby 请求。
* 优化播放逻辑边界：减少 UI 与 ExoPlayer 细节耦合，明确播放后端接口或控制器边界。
* 保留现有能力：播放/暂停、关闭、seek、快退/快进、比例切换、全屏、错误/缓冲状态、进度拖动、未知时长兼容。
* 不影响 Emby 播放进度上报、停止播放上报和鉴权 header。

## Acceptance Criteria (evolving)

* [ ] 播放器 UI 有明显重设计效果：媒体画面优先，控制层像播放器 chrome，而不是普通表单/卡片面板。
* [ ] 顶部信息、底部控制、状态提示和进度区域层级清晰，关键操作可快速识别。
* [ ] 底部时间轴和主控区域在横屏/竖屏下都保持可用，控件尺寸稳定。
* [ ] 普通模式和全屏模式下控件不重叠，文字不溢出关键按钮。
* [ ] 控制层自动隐藏、点击显示/隐藏、拖动进度时不误隐藏。
* [ ] 播放信息面板可从播放器控制层打开/关闭，展示标题、类型、年份、时长、观看进度、评分和简介；字段缺失或没有简介时优雅降级。
* [ ] 播放逻辑存在清晰抽象边界，未来可在不改 UI 的前提下替换或试验其他播放后端。
* [ ] 现有播放核心行为的单元测试继续通过，并补充新增纯逻辑测试。
* [ ] `compileDebugKotlin`、`testDebugUnitTest`、`lintDebug` 通过。
* [ ] 如用户可见行为变化，更新 `CHANGELOG.md`。

## Definition of Done

* 符合 Compose/token/spec 规范。
* 新增或调整测试覆盖播放器逻辑变化。
* Gradle 校验通过：`compileDebugKotlin`、`testDebugUnitTest`、`lintDebug`。
* 如形成新的播放器抽象约定，更新 `.trellis/spec/backend/emby-integration.md` 或质量规范。

## Out of Scope

* 不在本任务直接集成 mpv、libVLC 或其他 native 播放内核。
* 不新增字幕选择、音轨选择、倍速、投屏、画中画、下载缓存等大功能，除非后续明确纳入 MVP。
* 不新增“下一集”入口或播放队列上下文传递；电视剧连续播放作为后续独立任务。
* 不显示 streamUrl、解码器、分辨率、日志状态等调试型技术信息。
* 不重写 Emby repository、播放进度同步协议或媒体鉴权机制。
* 不做 TV/遥控器专属交互。

## Technical Approach

* 先保留 `VideoPlaybackEngine` 的 Media3 实现，梳理 UI 依赖的状态和命令。
* 优先提取轻量后端边界，例如接口承载 `state`、`attachSurface`、`detachSurface`、`play`、`playFromStart`、`togglePlayPause`、`seekTo`、`seekBackBy`、`seekForwardBy`、`cycleAspectRatio`、`stop`、`release`。
* `VideoPlayerScreen.kt` 继续只渲染状态并发出用户意图，不直接了解后端实现。
* UI 重设计集中在播放器 chrome：顶部标题/状态、底部进度与控制、中心缓冲/错误、按钮密度、全屏安全区和自动隐藏体验。
* 播放信息面板作为播放器内 overlay/bottom sheet 风格面板实现，只消费当前 `VideoItem` 已有字段，不新增 Emby 请求；内容范围为轻量用户信息，不展示调试字段。
* 设计方向采用 `Operate` 模式：用户目标是在播放中快速理解状态并完成控制，视觉表达服务于播放，不抢占画面。
* 遵循 Nordic 设计上下文：内容优先、半透明层、轻盈毛玻璃感、稀缺 violet/cyan accent、避免深色科技风和默认 Material 面板感。

## Decision (ADR-lite)

**Context**: 用户希望美化视频播放界面并优化播放器逻辑，同时询问是否应集成 mpv/libVLC 等第三方播放器内核。

**Decision**: 当前任务先保留 Media3/ExoPlayer，做播放器 UI 和逻辑抽象优化；mpv/libVLC 只作为未来兼容性瓶颈出现后的独立预研方向。

**Consequences**: 短期实现风险低，能复用现有 Emby 鉴权、进度同步和 Media3 生命周期；代价是暂不解决 Media3 不支持的极端格式/字幕能力，后续需通过抽象边界降低试验成本。

## Design Brief

* Job and audience: 自托管视频用户进入播放页，通常处于观看中或准备继续观看的状态；播放器应让用户立刻识别当前视频、播放状态和主要控制。
* Outcome and proof: 用户能在不离开画面的情况下完成播放/暂停、seek、快退/快进、比例切换、全屏和关闭；控制层隐藏后画面成为唯一主角。
* Selected direction: 沉浸式播放器 chrome，顶部信息轻量，底部控制像专用播放控制台，状态反馈居中但不遮挡核心观看区域。
* Scope and boundaries: 只改 Android Compose 播放页和必要播放逻辑边界；包含当前视频信息面板；保留现有 Media3 能力，不做 mpv/libVLC、字幕/音轨/倍速/下一集等大功能。
* States and ranges: 无视频、缓冲、错误、播放中、暂停、拖动进度、未知时长、普通/全屏、长标题和无元数据都要可读。
* Interaction and layout: 点击画面切换控制层；拖动进度期间控制层保持可见；主播放按钮最突出，辅助按钮成组并降低视觉权重；信息面板从控制层入口打开，可关闭返回播放；横竖屏安全区不压住控制。
* Constraints: 必须使用现有 Compose/token/theme；不能直接让 UI 依赖 ExoPlayer；中文 UI 文案保持可读；新增纯逻辑应有测试。

## Technical Notes

* Inspected: `VideoPlayerScreen.kt`, `VideoPlaybackEngine.kt`.
* Impeccable context loaded for `VideoPlayerScreen.kt`; project visual world is Nordic Media Hub: 内容优先、半透明层、轻盈、避免工具感深色主题。
* Current task path: `.trellis/tasks/08-05-video-player-polish`.
