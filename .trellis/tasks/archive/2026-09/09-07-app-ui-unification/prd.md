# 全页面 UI 统一与精细化完善

## Goal

在现有 Nordic Android 应用基础上，继续对所有可达页面、子页面、弹层和关键状态进行逐页 UI 审查与实现级统一，消除音乐、有声书、视频、配置及播放器之间的割裂感，同时保留各媒体类型的内容差异、现有业务能力和播放协议。

## What I already know

* 用户要求覆盖每一个页面，目标是“最精细化的 UI 统一、完善，不要有割裂感”。
* 仓库为 Kotlin + Jetpack Compose + Material 3 的单一 Android app。
* 现有共享基础设施包括 NordicTheme、Typography/Shape/Spacing/Alpha/Motion token、MediaPageHeader、MediaStateComponents、媒体选择/分段/搜索/播放器共享组件。
* 页面入口集中在 MainActivity，主要领域页面包括音乐库及其详情/弹层、 有声书库及播放器弹层、视频库/详情/播放器，以及 ServerConfigScreen 配置流程。
* 上一轮任务已完成首批共享 token、页面头部、跨媒体选择组件和部分音乐页面统一，但现有 coverage/audit 记录明确标注大量页面仍待真实设备验收与逐项精修。
* 当前工作区只有用户已有的 `gradle.properties` 修改，不能覆盖或提交该修改。

## Assumptions (temporary)

* 本任务采用分阶段推进，但最终验收标准仍覆盖完整页面矩阵，而非只完成少数示例页面。
* 不引入新的媒体服务、广告、社交功能、字体文件或无依据的新图标库。
* 真实设备验收由用户执行并提供反馈；自动化检查负责编译、单测、lint、设计契约和可静态验证的页面覆盖。

## Open Questions

* 已确认首批同时纳入无障碍、响应式和跨媒体播放状态回归；未来演进、关联场景及失败/边界情况也纳入每批验收。

## Requirements (evolving)

* 首批先统一跨页面共享壳层与基础交互，再按媒体域逐页精修；不把不同媒体的必要信息架构强行抹平。
* 首批共享壳层范围：全局底部导航、Playback Dock、统一页面头/返回/菜单、加载/空/错误卡、通用搜索/分段/选择控件，以及播放器和配置弹层的基础容器/间距/操作反馈。
* 首批验收同时覆盖触达区域与语义、字体缩放/横屏/系统栏安全区，以及音乐/有声书/视频切换和 Dock 状态。
* 每批还需验证主题切换、配置切换、返回栈、重复点击、网络失败、空数据、缺失封面、超长中文和键盘安全区等关联与边界场景。
* 每完成一个页面批次，产出 APK、对应页面/状态清单和未完成项，由用户在真实设备上验收；未反馈项保持“待验收”，不提前标记完成。
* 统一应用级导航、页面头部、返回/关闭、操作菜单、内容边距、标题层级、列表密度、封面圆角、按钮/点击区域、分段选择、搜索输入和反馈状态。
* 统一浅色/深色主题、小屏/横屏/大字体、系统栏/刘海/键盘安全区以及长文本和缺失图片表现。
* 保留音乐、有声书、视频在信息架构、封面比例、播放器控件和内容语义上的必要差异；统一的是设计语言与交互契约，不是强行使用同一布局。
* 对每个页面记录源码审查、自动化验证、渲染/交互证据和未完成项，未有证据的页面不得标记完成。

## Acceptance Criteria (evolving)

* [ ] 所有 MainActivity 可达主页面、详情页、播放器和弹层均纳入覆盖矩阵。
* [ ] 共享 token 与组件在所有适用页面实际使用，页面间无明显重复且互相冲突的私有实现。
* [ ] 每个页面至少覆盖加载、空、错误/重试、正常内容及关键操作状态（按适用性）。
* [ ] 编译、相关单测、lint 通过；新增/变更的共享行为有回归测试。
* [ ] 浅色/深色、紧凑宽度、大字体和横屏行为有可追溯验证记录。
* [ ] README、CHANGELOG、DESIGN 与 coverage/audit 文档反映真实完成度，不以静态检查代替真实设备验收。

## Definition of Done (team quality bar)

* 分阶段实现并逐批运行 compile + unit test + lint。
* 页面覆盖矩阵中的每一项都有源码、自动化或真实设备证据；未验收项明确保留为待办。
* 不破坏现有媒体切换、播放进度、配置保存和服务协议。
* 任务完成前完成最终质量检查、必要的 spec 更新、代码提交和 Trellis 归档。

## Out of Scope (explicit)

* 新增媒体服务、广告、社交流或与 UI 统一无关的后端功能。
* 以批量替换数值、截图或静态文本检查冒充逐页设计与交互验收。
* 未经确认的业务语义变更、媒体切换协议重写或删除现有页面能力。
* 自动操作或截取用户真实设备；设备验收由用户直接执行。

## Technical Notes

* 重点源码入口：`MainActivity.kt`、`MusicScreenV2.kt`/`MusicScreenV2Pages.kt`、`AudiobookScreen.kt`/`AudiobookPlayerScreen.kt`、`VideoScreen.kt`/`VideoDetailScreen.kt`/`VideoPlayerScreen.kt`、`ServerConfigScreen.kt`、`SharedComponents.kt`、`MediaStateComponents.kt`、`ui/theme/*`。
* 现有参考资料：上一轮任务归档目录中的 `research/ui-coverage.md` 与 `research/design-audit.md`，以及 `.trellis/spec/backend/music-ui.md`、`.trellis/spec/backend/ui-consistency.md`、`.trellis/spec/backend/quality-guidelines.md`。
* 需要在需求确认后补充 `research/`、`implement.jsonl` 和 `check.jsonl`，再激活任务进入实现阶段。

## Current Progress

* 第一批已统一播放器共享选择行：章节、倍速、定时器等弹层使用一致的选中/未选中容器、圆角、文字层级与勾选反馈。
* Playback Dock 播放/暂停点击区域已提升到 48dp；底部导航已加入 `selectableGroup`、`Role.Tab` 与选中语义。
* `compileDebugKotlin`、`testDebugUnitTest`、`lintDebug`、`assembleDebug` 已通过。
* 已生成第一批 APK 和 `manual-checklist.md`；真实设备反馈前保持“待验收”，任务继续推进后续共享壳层页面。

## Decision (ADR-lite)

**Context**: 全应用页面数量多，若直接逐页修改，容易产生新的局部规范和跨页面漂移。

**Decision**: 采用“共享壳层 → 音乐 → 有声书 → 视频 → 配置与全局回归”的分阶段顺序；每一阶段都必须完成源码审查、自动化检查，并记录真实设备待验项。

**Consequences**: 前期共享组件投入较大，但后续页面可以复用稳定的视觉与交互契约；媒体域仍保留内容差异，避免统一化变成布局强行同质化。

**Validation cadence**: 每个批次完成后交付 APK 与逐项真实设备验收清单，再进入下一批。
