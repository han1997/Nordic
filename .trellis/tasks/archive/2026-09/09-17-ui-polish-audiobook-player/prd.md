# UI 精修第十一轮:有声书播放器收口

## Goal

承接前十轮(设置、音乐播放器、Dock 等),收口有声书播放器(`AudiobookPlayerScreen`)的睡眠定时面板与主体。核心修正睡眠定时面板的预选条目与固定分钟条目在默认值(30 分钟)下完全重复、非默认预设与固定条目语义混杂的问题;并核对播放器主体、章节/书签面板在短屏与大字体下的触控与语义。保持 AudiobookShelf 播放协议、书签持久化、睡眠定时器与草稿/关闭保护不变;只做面板条目收敛与语义补全,不新增可见按钮。

基线为 `main / 9f0f829`,当前版本预期从 `0.1.18 / 18` 升至 `0.1.19 / 19`。沿用专用 AVD `nordic-ui-api34 / emulator-5580`,不操作个人设备。

## Requirements

- `AudiobookSleepTimerSheet`(AudiobookPlayerScreen.kt:402-427)修正条目重复:`SLEEP_MINUTE_OPTIONS` 固定分钟条目中,与当前预选值相同的条目被唯一「使用预选:N 分钟」取代,不再并列渲染「30 分钟后停止」与「使用预选:30 分钟」两个等效动作;预选为非默认值时(如 45)固定列表仍保留 30 等其余项。
- `AudiobookSleepTimerSheet` 保留「关闭」与「本章结束」两条动作的行;面板 subtitle 使用既有 `sleepTimerRemainingLabel` 表达激活状态。
- 播放器主体、章节列表、书签面板在两个 `MediaPlayerSheet` 宿主下保持既有滚动/触控/选中语义;仅核对,不改协议。
- `AudiobookChapterListSheet` / `AudiobookBookmarkSheet` 继续复用 `MediaPlayerChoiceRow`/`MediaPlayerSheet`,不引入局部自绘选择样式。
- 调试样板(audiobook `ab_sleep` / `ab_player` 等)覆盖预选默认与非默认两种用例;交互测试断言睡眠面板无「使用预选:N 分钟」与「N 分钟后停止」重复的并行条目,且预选选中行语义正确。
- 同步版本 `0.1.19 / 19`、`CHANGELOG.md`(未发布段)、`DESIGN.md`(播放器、队列与底部面板节);Release 不得包含 Debug 样板。

## Acceptance Criteria

- [ ] 睡眠定时面板在预选=30(默认)时只渲染一条等价值;预选=45 时「使用预选:45 分钟」与「30 分钟后停止」并行存在且不重复。
- [ ] 「关闭」「本章结束」动作与激活状态语义保持;预选选中行与共享选择行一致。
- [ ] 章节/书签/倍速面板与播放器主体在 320/360/392/720dp、fontScale 1/1.5/2、浅深主题下无可达性裁切或低于 48dp 的操作目标回归。
- [ ] JVM、Lint、Debug/Release、AndroidTest 编译,以及 Release 签名/manifest/DEX 合同检查通过。
- [ ] 专用模拟器可用时产出 `r11-*` 截图与交互证据;不可用时如实记录未验证。
- [ ] AudiobookShelf 播放、书签、睡眠定时回调协议不变。

## Definition of Done

- 更新必要的播放器纯函数、Compose/UI 交互测试与调试样板覆盖。
- `implement.jsonl` / `check.jsonl` 只包含本任务适用的规范或研究文件。
- 完成 `trellis-check`、`trellis-update-spec`、单次确认提交和任务归档。

## Technical Approach

在 `AudiobookSleepTimerSheet` 构造固定分钟列表时过滤掉与 `audiobookSleepMinutes` 相等的项,预选条目单独渲染为「使用预选:N 分钟」(选中语义随激活状态)。这是纯 UI 层收敛,不触碰 `onSet`/`onCancel` 回调与 `AudiobookPlaybackState`。

## Decision (ADR-lite)

**Context**: 睡眠定时面板预选与固定分钟列表在默认 30 分钟时重复,非默认预设与固定条目并存造成两个语义相近动作。

**Decision**: 固定分钟条目列表过滤掉与当前预选值相同的项,预选条目始终单独存在。

**Consequences**: 面板简洁无重复;非默认预设下行为仍一致(预选行开启对应定时)。不改变睡眠定时业务协议。

## Out of Scope

- AudiobookShelf 播放引擎、书签/进度持久化、睡眠定时器本身。
- 自定义无障碍动作扩展(音乐播放器已实现,有声书本轮不做)。
- 音乐播放器、视频、设置、Dock 再次精修;导航重做、字体/依赖替换、真实设备测试。

## Technical Notes

- 生产入口:`app/src/main/java/com/nordic/mediahub/ui/AudiobookPlayerScreen.kt`(`AudiobookPlayerScreen` / `AudiobookSleepTimerSheet` / `AudiobookChapterListSheet` / `AudiobookBookmarkSheet`)。
- 预选来源:`LocalAppPreferences.current.audiobookSleepMinutes`;常量 `SLEEP_MINUTE_OPTIONS`(`data/AppPreferences.kt`)。
- 调试样板:`app/src/debug/java/com/nordic/mediahub/ui/AudiobookCatalogSamples.kt`(`AudiobookPlayerScreen` 与四个 sheet)。
- 版本:`app/build.gradle.kts` `versionCode = 18 / "0.1.18"` → `19 / "0.1.19"`。
- 文档:根 `CHANGELOG.md`(未发布段)、`DESIGN.md`(播放器、队列与底部面板节)。