# 配置域与公共状态组件精修

## Goal

全页面 UI 统一任务第六批（收官批）：精修配置域表单与卡片、公共 MediaStateCard 语义与边框，并同步 CHANGELOG/DESIGN 文档反映第三至六批真实完成度。完成后覆盖矩阵全部行达到「已审查/已改动」状态。

## What I already know

* 配置域割裂点（源码证据）：
  1. 视频服务器类型选择器裸 clickable、无 selectable/Role/selected 语义、primary 0.16 选中色（ConfigCards.kt:120-165）——「后续支持」副文案与下方说明文字重复。
  2. ServerConfigCard 容器不透明 surfaceVariant + shape sm，与卡片家族（0.42-0.5 alpha + md/lg）不一致（:265-287）。
  3. 卡片标题无 heading() 语义（:279-283）。
  4. ConfigTextField label 独立 Text 无关联语义；placeholder bodyMedium/faint 与输入值默认 bodyLarge 层级错位（design-audit 第 6 条遗留）（:290-316）。
  5. ServerConfigActions 用 M3 Button/OutlinedButton（40dp < 48dp），与 Primary/SecondaryActionButton 语言不同（:214-248）。
  6. statusMessage 无 maxLines 限制（:241-247）。
* 公共割裂点：MediaStateCard 标题无 heading()（:83-87）；Compact 边框 0.05f ≠ 0.045f（:72-76）。
* 文档：CHANGELOG 未发布段仅记录到第二批；DESIGN.md 第 7/8 节未覆盖跨域共享组件现状。

## Requirements (confirmed)

* 类型选择器迁移 MediaChoiceChip（supported → enabled），外层 selectableGroup()，删除「后续支持」副文案（说明行已覆盖）。
* ServerConfigCard：surfaceVariant 0.5 alpha + shape md；标题 heading()。
* ConfigTextField：label 移入 TextField label 参数；placeholder/输入统一 bodyLarge；删除手写 label Text。
* ServerConfigActions：Button → PrimaryActionButton、OutlinedButton → SecondaryActionButton（横排 weight 布局）。
* statusMessage：maxLines = 3 + Ellipsis。
* MediaStateCard：标题 heading()；边框 0.045f。
* CHANGELOG 补第三/四/五/六批改进条目；DESIGN.md 第 7 节补跨域共享组件现状。
* 不改变测试连接/保存流程、ConfigRepository 协议与既有业务行为。

## Acceptance Criteria

* [ ] 配置页表单/按钮/卡片与全应用同一视觉与语义语言；TalkBack 可按标题导航、字段名可朗读。
* [ ] 触达目标 ≥48dp；状态消息不撑开卡片。
* [ ] compile + testDebugUnitTest + lintDebug + assembleDebug 全绿。
* [ ] CHANGELOG/DESIGN 反映第三至六批真实完成度（不宣称真机已验收）。
* [ ] 覆盖矩阵全部行达到「已审查/已改动」；manual-checklist 第六批（含全局回归清单）+ APK 交付。
* [ ] spec 更新：ui-consistency.md 配置域条目。

## Out of Scope

* 测试连接/保存流程与 ConfigRepository 协议。
* 服务器类型「后续支持」业务语义变更。
* 真机验收（用户执行）。

## Current Progress

* 计划已确认，任务已创建。
