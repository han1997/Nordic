# 中文化沟通与文档更新规范

## Goal

把“项目、沟通都要中文化；修改需要写更新日志；必要内容写到 README”固化到 Trellis 开发规范中，让后续开发、审查和 AI 协作都有明确、可检查的文档约束。

## What I already know

* 用户要求：项目、沟通都要中文化，修改需要写更新日志，必要内容写到 README，并将这些写入开发规范。
* 项目已有中文 `README.md`，说明产品、特性、服务器配置、技术栈和构建要求。
* 项目已有中文 `CHANGELOG.md`，采用“更新日志 / 版本 / 新增 / 改进”等中文结构。
* 当前 Trellis 可用开发规范入口为 `.trellis/spec/backend/index.md`，该层覆盖单体 Android/Kotlin Compose app。
* 现有规范文件包括 `quality-guidelines.md`、`directory-structure.md`、`error-handling.md` 等，但没有专门的中文化/文档更新规范。

## Assumptions

* “项目中文化”优先指用户可见内容、项目文档、任务/PRD/日志、开发沟通和更新说明使用中文；代码标识符、第三方 API 名称、协议字段和错误类型等保持技术原名。
* “修改需要写更新日志”指用户可见功能变化、行为变化、配置/构建/依赖变化、重要修复和兼容性影响都要更新 `CHANGELOG.md`；纯内部重构可在无用户可见影响时不写。
* “必要内容写到 README”指安装/构建/配置/使用方式、重要能力、限制或服务接入说明变化时更新 `README.md`；普通修复不强制更新 README。

## Requirements

* 新增或更新 Trellis 开发规范，明确中文化沟通与项目文档规则。
* 规范必须覆盖：开发沟通语言、用户可见 UI 文案、任务/PRD/提交说明/更新日志/README 的语言要求。
* 规范必须明确 `CHANGELOG.md` 更新触发条件和免更新条件。
* 规范必须明确 `README.md` 更新触发条件和免更新条件。
* 规范必须更新 spec 索引和预开发清单，让后续实现/检查代理会读取该规范。
* 不强制翻译技术专有名词、代码标识符、API 字段、库名、协议名和英文错误类型。

## Acceptance Criteria

* [ ] `.trellis/spec/backend/` 下存在明确的中文化/文档更新规范。
* [ ] `.trellis/spec/backend/index.md` 将该规范加入 Guidelines Index。
* [ ] `.trellis/spec/backend/index.md` 的 Pre-Development Checklist 要求开发前读取该规范。
* [ ] 规范明确什么时候更新 `CHANGELOG.md`。
* [ ] 规范明确什么时候更新 `README.md`。
* [ ] 规范明确中文优先与技术原名保留的边界。

## Definition of Done

* 文档改动完成并通过基本 Markdown/文本检查。
* 如本任务本身改变开发规范，应更新 `CHANGELOG.md` 记录规范变化。
* 任务归档并记录会话。

## Out of Scope

* 不在本任务中全面翻译现有英文 `PRODUCT.md` / `DESIGN.md`。
* 不批量重写历史 `CHANGELOG.md` 或历史提交信息。
* 不修改 Trellis 工作流脚本逻辑。

## Technical Approach

* 新增 `.trellis/spec/backend/documentation-guidelines.md`，写入中文化、更新日志、README 更新规则。
* 更新 `.trellis/spec/backend/index.md`，把文档规范加入索引和预开发清单。
* 更新 `CHANGELOG.md`，记录本次开发规范新增。

## Technical Notes

* Inspected: `README.md`, `CHANGELOG.md`, `.trellis/spec/backend/index.md`.
