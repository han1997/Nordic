# UI 精修第十轮:底部 Dock 全局收口

## Goal

承接第九轮音乐播放器,收口全局底部 Dock(`PlaybackDock.kt`):播放/暂停按钮、收起把手、导航项的语义补全,以及迷你播放条在缓冲与错误状态下的颜色区分。保持 Dock 滚动隐藏/显示、模块导航、媒体域分派与播放协议不变;只补语义与可达性细节,不新增可见按钮、不改布局几何。

基线为 `main / 619cb6c`,当前版本预期从 `0.1.17 / 17` 升至 `0.1.18 / 18`。沿用专用 AVD `nordic-ui-api34 / emulator-5580`,不操作个人设备。

## Requirements

- `DockPlayPauseButton`(PlaybackDock.kt:95)补 `role = Role.Button`,与全应用运输控制语义一致;播放/暂停 `contentDescription` 保持动态切换,样式与回调不变。
- `BottomDockHandle`(:214)补 `role = Role.Button`,保留既有 `contentDescription = "显示底部导航"`。
- `PolishedNavItem`(:321)图标 `contentDescription = label` → `null`,消除与下方 Text label 的重复朗读;`selected`/`Role.Tab` 语义保留。标签 `Text` 保留原字体与复制语义。
- 迷你播放条区分缓冲与错误:`PolishedNowPlayingBar` / `PolishedPlaybackDock` 增加 `statusIsError: Boolean = false` 参数;非空 `playbackStatus` 且 `statusIsError=true` 时选用 `colorScheme.error` 文字色,否则沿用 primary。MainActivity 调用点按「三个媒体域任一 errorMessage 非空」推导该参数。
- 不新增可见 Dock 元素、不改 `resolveBottomDockScrollIntent` 手势协议、不改 `visibleDomains` 单/双/三模块分派、不重构 `playbackStatus` 收集逻辑本身。
- 调试样板 `UiCatalogSamples.kt` 视需要补 Dock 媒体域场景(音乐/有声书/视频 now-playing 与错误/缓冲状态),复用生产 `PolishedPlaybackDock`;现有 `LibrarySample` 的 Dock 保持。
- 新增/更新 `UiCatalogInteractionTest` 断言:NavItem 无重复朗读(图标不贡献新文本)、迷你播放条错误/缓冲 subtitle 颜色、Dock 播放按钮与 Handle 的 Role、Handle 点击回调。
- 同步版本 `0.1.18 / 18`、`CHANGELOG.md`(未发布段)、`DESIGN.md`(播放器、队列与底部面板节)与 `ui-consistency.md`(Dock 相关条目);Release 不得包含 Debug 样板。

## Acceptance Criteria

- [ ] Dock 播放/暂停按钮、收起把手、导航项的语义与可达性符合合同(Dock 按标签可操作,不再有重复朗读)。
- [ ] 迷你播放条在缓冲状态显示强调色、错误状态显示 error 色,其余显示 onSurfaceVariant。
- [ ] 320/360/392/720dp、fontScale 1/1.5/2、浅深主题下 Dock 无可达性裁切或低于 48dp 的操作目标回归。
- [ ] JVM、Lint、Debug/Release、AndroidTest 编译,以及 Release 签名/manifest/DEX 合同检查通过。
- [ ] 专用模拟器可用时产出 `r10-*` 截图与交互证据;不可用时如实记录未验证。
- [ ] Dock 隐藏/显示手势、模块导航与媒体域回调保持不变。

## Definition of Done

- 更新必要的 Dock 纯函数、Compose/UI 交互测试与调试样板覆盖。
- `implement.jsonl` / `check.jsonl` 只包含本任务适用的规范或研究文件。
- 完成 `trellis-check`、`trellis-update-spec`、单次确认提交和任务归档。

## Technical Approach

在 `DockPlayPauseButton` 的 `clickable` 补 `role = Role.Button`,`BottomDockHandle` 补 `role = Role.Button`,`PolishedNavItem` 图标 `contentDescription = null`。迷你播放条新增 `statusIsError` 布尔参数,由 MainActivity 按错误来源派生;测试在样板里直接构造两种状态断言颜色。调试样板复用生产 Dock 组件,交互测试断言语义与颜色,截图沿用 UiCatalogScreenshotTest 参数流。

## Decision (ADR-lite)

**Context**: 全局 Dock 三个元素存在语义缺口:播放/暂停与收起把手无 Button 角色,导航项图标与文本 label 重复朗读;迷你播放条用单色承载缓冲与错误两类状态,错误时无警示色。

**Decision**: Dock 语义补全(Role.Button + 图标朗读去重),迷你播放条通过 `statusIsError` 显式区分错误与强调。

**Consequences**: TalkBack 朗读与按钮辨识改善;错误状态获得 error 色警示;不改变视觉布局与播放/导航回调。

## Out of Scope

- 无障碍自定义动作重构(CustomAccessibilityAction 后续轮次处理)。
- 视频/音乐/有声书播放器与浏览域、队列/弹层、设置域再次精修。
- 新增可见 Dock 元素、导航重做、字体/依赖替换、真实设备测试。

## Technical Notes

- 生产入口:`app/src/main/java/com/nordic/mediahub/ui/PlaybackDock.kt`(`PolishedPlaybackDock` / `DockPlayPauseButton` / `BottomDockHandle` / `PolishedBottomNav` / `PolishedNavItem` / `PolishedNowPlayingBar`)。
- 调用点:`app/src/main/java/com/nordic/mediahub/MainActivity.kt`(Dock 槽位、`playbackStatus` 与错误源)。
- 调试样板:`app/src/debug/java/com/nordic/mediahub/ui/UiCatalogSamples.kt`(`LibrarySample` 使用 Dock)。
- 版本:`app/build.gradle.kts` `versionCode = 17 / "0.1.17"` → `18 / "0.1.18"`。
- 文档:根 `CHANGELOG.md`(未发布段)、`DESIGN.md`(播放器、队列与底部面板节)、`.trellis/spec/backend/ui-consistency.md`。