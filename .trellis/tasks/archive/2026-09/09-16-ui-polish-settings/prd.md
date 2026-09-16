# UI 精修第八轮:设置与服务器配置域

## Goal

承接音乐、有声书、视频浏览/播放器/详情与 WebDAV 六轮精修,收口设置域:设置主页与导航、服务器列表与编辑器、偏好页、数据/隐私/旧数据页、模块显示页与共享设置组件。保持现有 ConfigRepository 协议、媒体来源状态、连接测试、搜索、模块隐藏守卫与未保存导航守卫不变;只优化布局层级、语义、对比度、触控目标、短屏与大字体可达性,并完善 Debug-only 离线样板。

基线为 `main / 4f88ee8`,当前版本预期从 `0.1.15 / 15` 升至 `0.1.16 / 16`。沿用专用 AVD `nordic-ui-api34 / emulator-5580`,不操作个人设备。

## Requirements

- `SettingsScreen.kt` 继续作为无副作用设置导航层;服务器列表、搜索、模块守卫、连接测试与错误提示的状态归属不变。
- 设置主页:HOME 页分组采用 `SettingsGroup` 卡片语言(与 `ModuleVisibilityPage` 一致),保留 section 标题与各目的地的统计摘要行;隐藏模块的 filtering 逻辑不变。
- 服务器列表页:SERVERS 条目保留 Radio `selected` 语义、名称/账号/测试状态两行、溢出菜单(编辑/测试/删除)与 48dp 触控目标;重构不必引入新的局部自绘控件,优先收敛到共享行组件或等价语义。
- 「尚未添加 xx 服务器」「暂无下载」「没有匹配设置」等页级空态统一为 `MediaStateCard`;错误/忙碌提示按域内既有 `MediaStateCard`(Neutral/Error)呈现,不裸 `Text`。
- `SettingsRow` 保持 64dp 最小高度、`titleMedium` 标题、行内/独立 level、`primaryContainer` 高亮、Switch/Table 语义、enabled/destructive 状态;图标与右箭头配色统一按 `enabled/disabled/onPrimaryContainer` 派生,不引入新 token。
- `SettingsChoiceDialog` 保留 selectableGroup/RadioButton 语义,选项行 ≥56dp;可校对当前值选中强调。
- `PreferenceSettingsPage` 各偏好页继续复用 speed/interval/choice/switch 行;Emby/WebDAV 分节标题与「下次播放生效」说明层级保持。
- `ModuleVisibilityPage` 保持 `SettingsGroup` 分组、至少一个模块的提示与隐藏确认对话框。
- `ServerEditorContent` 表单:密码/API Key 可见性切换、HTTP 使用确认开关、测试中/成功/失败状态、底部说明文字层级保持;校验与流程不变。
- `ConfigCards.kt` 旧式 `Navidrome/Audiobook/VideoConfigCard` 已无生产引用(核实无调用方)——视情况删除或保留为 Debug 呈现,不得进入 Release。
- `UiCatalogSamples.kt` 设置域样板扩展确定性场景(设置主页、服务器列表、偏好页、数据页、空/错/忙碌/长文本),覆盖 320/360/392/720dp、fontScale 1/1.5/2、浅深主题;Debug 样板不访问网络、不创建账号、不写偏好、不启动真实播放。
- 同步 `app/build.gradle.kts` 版本至 `0.1.16 / 16`、`CHANGELOG.md(未发布段)`、`DESIGN.md(第 7/8 节)` 与 `ui-consistency.md` 设置域条目;Release 不得包含 Debug 样板 Activity、资源或类。

## Acceptance Criteria

- [ ] 设置主页、服务器列表/编辑器、偏好页、数据/隐私/旧数据页与模块显示页均完成逐项 UI 结论。
- [ ] 320/360/392/720dp、fontScale 1/1.5/2、浅深主题下,无标题/按钮裁切和低于 48dp 的真实操作目标(行内 Switch 保留标准高度)。
- [ ] 设置搜索路径、模块隐藏守卫、未保存导航守卫、连接测试与保存流程回调不变。
- [ ] 服务器当前项、空态、错误/忙碌、`SettingsRow` enabled/destructive 与样板交互语义符合合同。
- [ ] Debug 样板不访问网络、不创建账号、不写偏好、不启动真实播放;Release 不含 Debug 产物。
- [ ] JVM、Lint、Debug/Release、AndroidTest 编译,以及 Release 签名/manifest/DEX 合同检查通过。
- [ ] 专用模拟器可用时产出 `r8-*` 截图与交互证据;不可用时如实记录未验证,不以编译通过替代视觉验收。

## Definition of Done

- 更新必要的设置域纯函数、Compose/UI 交互测试及 Debug 样板覆盖。
- `implement.jsonl` 和 `check.jsonl` 只包含本任务适用的规范或研究文件。
- 完成 `trellis-check`、`trellis-update-spec`、单次确认提交和任务归档。

## Technical Approach

优先在既有共享组件(`SettingsRow`/`MediaPageHeader`/`MediaStateCard`/`SettingsGroup`)上收敛设置域各页,而非另建第二套设置组件。`UiCatalogSamples` 已含模块/服务器编辑/设置组件样板,本轮在此基础上补设置主页、服务器列表与数据页场景;`ConfigCards.kt` 判定死亡代码后按决策删除或转 Debug 保留。复用现有连接测试、配置读写与导航守卫协议,不引入新的网络、缓存或存储依赖。

## Decision (ADR-lite)

**Context**: 设置域同时被生产 `SettingsScreen`(含子页导航)与 Debug `UiCatalogSamples` 使用,配置读写与连接测试已有稳定协议,旧 `ConfigCards.kt` 三个卡片已无生产引用。

**Decision**: 保留 `SettingsScreen` 的导航/状态所有权,只收敛各页呈现并将其收敛到 `SettingsRow`、`SettingsGroup`、`MediaStateCard` 等共享组件;无引用的 `ConfigCards.kt` 旧卡片判定为死亡代码,按产物决策删除。

**Consequences**: 生产与样板共享真实设置 UI,减少视觉漂移;不改变 ConfigRepository、MediaSource 状态、Emby/WebDAV/Navidrome 连接协议、搜索入口与模块/未保存守卫。

## Out of Scope

- 配置存储、MediaSource 协议、连接测试、缓存、认证流程与错误状态机。
- Media3 播放、进度同步、WebDAV 浏览、音乐/有声书/视频浏览详情与播放器再次精修。
- 首次启动引导、多语言国际化、导航重做、字体/依赖替换、真实设备测试。

## Technical Notes

- 生产入口:`app/src/main/java/com/nordic/mediahub/ui/SettingsScreen.kt`(编辑器 `ServerEditorScreen.kt`)。
- 共享组件:`SettingsComponents.kt`(`SettingsRow`/`SettingsGroup`/`SettingsChoiceDialog`)、`SettingsDataPages.kt`、`SettingsPreferences.kt`(`PreferenceSettingsPage`/`ModuleVisibilityPage`/`SettingsPage`)、`ConfigCards.kt`、`MediaStateComponents.kt`(`MediaStateCard`)。
- Debug 样板:`app/src/debug/java/com/nordic/mediahub/ui/UiCatalogSamples.kt`;宿主 `app/src/debug/java/com/nordic/mediahub/UiCatalogActivity.kt`。
- 版本: `app/build.gradle.kts` `versionCode = 15` / `versionName = "0.1.15"` → `16 / "0.1.16"`。
- 文档:根 `CHANGELOG.md`(未发布段)、`DESIGN.md`(第 7/8 节)、`.trellis/spec/backend/ui-consistency.md`(设置域条目)。