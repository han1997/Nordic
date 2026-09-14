# 首轮样板工作提交计划（已获一次确认）

## 提交方案

2026-09-14 用户回复“确认”，接受当前样板方向及证据限制，并明确批准 **A+B 全部 55 个文件（含 27 个既有 WIP）**，合并为 **1 个完整工作提交**：

```text
feat(ui): 打磨首轮样板并建立模拟器验收底座
```

- 内容：首轮样板及必要共享组件、Debug 目录/三张小型封面资源、JVM/UI 回归、设计/使用文档、规范、任务覆盖与小型证据索引。
- 应用版本为已验证的 **0.1.9 / 9**，较基线 0.1.8/8 递增一次。不得为文档整理或重跑测试再次递增。
- 两组属于一个可运行的改动单元：A 组新增验收依赖 B 组样板接口。**A+B 已获用户一次批准，一并提交，不拆出缺失依赖的片段。**
- 共 55 个候选路径；不含 build 目录、APK、原始截图、日志、缓存、keystore 或本机配置。三张 debug drawable 是样板运行资源，不是验收截图。
- 不 amend、不 push；不把归档/日志提交穿插在工作提交之前。先完成工作提交，再归档首轮样板任务并记录日志；不自动启动全量页面迁移。

## A. 前一续跑轮次已编辑/新建（28 个，已批准）

- `.trellis/spec/backend/index.md`
- `.trellis/spec/backend/music-ui.md`
- `.trellis/spec/backend/quality-guidelines.md`
- `.trellis/spec/backend/ui-catalog-verification.md`
- `.trellis/spec/backend/ui-consistency.md`
- `.trellis/tasks/09-13-ui-polish-samples/check.jsonl`
- `.trellis/tasks/09-13-ui-polish-samples/implement.jsonl`
- `.trellis/tasks/09-13-ui-polish-samples/prd.md`
- `.trellis/tasks/09-13-ui-polish-samples/research/commit-plan.json`
- `.trellis/tasks/09-13-ui-polish-samples/research/commit-plan.md`
- `.trellis/tasks/09-13-ui-polish-samples/research/component-inventory.json`
- `.trellis/tasks/09-13-ui-polish-samples/research/coverage.md`
- `.trellis/tasks/09-13-ui-polish-samples/research/debug-retrospective.md`
- `.trellis/tasks/09-13-ui-polish-samples/research/evidence-index.md`
- `.trellis/tasks/09-13-ui-polish-samples/research/evidence-summary.json`
- `.trellis/tasks/09-13-ui-polish-samples/research/fixture-boundary.md`
- `.trellis/tasks/09-13-ui-polish-samples/research/make-review.py`
- `.trellis/tasks/09-13-ui-polish-samples/research/page-coverage.json`
- `.trellis/tasks/09-13-ui-polish-samples/research/quality-check.md`
- `.trellis/tasks/09-13-ui-polish-samples/research/summarize-evidence.py`
- `.trellis/tasks/09-13-ui-polish-samples/task.json`
- `CHANGELOG.md`
- `README.md`
- `app/src/androidTest/java/com/nordic/mediahub/UiCatalogInteractionTest.kt`
- `app/src/androidTest/java/com/nordic/mediahub/UiCatalogScreenshotTest.kt`
- `app/src/androidTest/java/com/nordic/mediahub/UiTestAssertions.kt`
- `app/src/androidTest/java/com/nordic/mediahub/UiTestDevice.kt`
- `app/src/debug/java/com/nordic/mediahub/ui/UiCatalogSamples.kt`

## B. 前序已有改动（27 个，用户已批准纳入）

这些路径在接续开始时已经是 dirty/WIP。已依据本任务 PRD、diff、测试和产物审查其作用，并在本次用户确认后获得纳入授权；保留分组以记录改动来源，不将既有 WIP 冒充本轮新写。

- `.trellis/tasks/09-13-ui-polish-samples/research/implementation-plan.md`
- `.trellis/tasks/09-13-ui-polish-samples/research/run-ui-checks.py`
- `.trellis/tasks/09-13-ui-polish-samples/research/verify-build-artifacts.py`
- `DESIGN.md`
- `app/build.gradle.kts`
- `app/src/androidTest/java/com/nordic/mediahub/MainSettingsUiTest.kt`
- `app/src/debug/AndroidManifest.xml`
- `app/src/debug/java/com/nordic/mediahub/UiCatalogActivity.kt`
- `app/src/debug/res/drawable-nodpi/ui_sample_cover_a.jpg`
- `app/src/debug/res/drawable-nodpi/ui_sample_cover_b.jpg`
- `app/src/debug/res/drawable-nodpi/ui_sample_cover_c.jpg`
- `app/src/main/java/com/nordic/mediahub/ui/ConfigCards.kt`
- `app/src/main/java/com/nordic/mediahub/ui/MediaPlayerComponents.kt`
- `app/src/main/java/com/nordic/mediahub/ui/MediaPlayerSheets.kt`
- `app/src/main/java/com/nordic/mediahub/ui/MusicHomeSections.kt`
- `app/src/main/java/com/nordic/mediahub/ui/MusicLibraryComponents.kt`
- `app/src/main/java/com/nordic/mediahub/ui/MusicPlayerScreen.kt`
- `app/src/main/java/com/nordic/mediahub/ui/MusicQueueSheet.kt`
- `app/src/main/java/com/nordic/mediahub/ui/MusicScreenV2Pages.kt`
- `app/src/main/java/com/nordic/mediahub/ui/PlaybackDock.kt`
- `app/src/main/java/com/nordic/mediahub/ui/ServerEditorScreen.kt`
- `app/src/main/java/com/nordic/mediahub/ui/SettingsComponents.kt`
- `app/src/main/java/com/nordic/mediahub/ui/SettingsPreferences.kt`
- `app/src/main/java/com/nordic/mediahub/ui/SettingsRowLayout.kt`
- `app/src/test/java/com/nordic/mediahub/ui/NordicDesignContractTest.kt`
- `app/src/test/java/com/nordic/mediahub/ui/QueueItemGeometryTest.kt`
- `app/src/test/java/com/nordic/mediahub/ui/SettingsRowLayoutTest.kt`

## 确认后的执行顺序

1. 重新核对 `git status --porcelain`；出现新路径或变化先复核，不使用 `git add -A` 悄悄扩大范围。
2. 核对源码 SHA-256 `8b4b2b02e8fe355637e368d4320964b379f5ec1f29f9e216fd3164dec642b850` 与 evidence-summary.json；不变时复用最终门禁，变化则重新检查。
3. 按确认的显式文件清单 `git add <paths>`，检查 staged diff 后 `git commit -m "feat(ui): 打磨首轮样板并建立模拟器验收底座"`。
4. 报告 commit hash。只有工作提交实际完成后，才进入归档/日志收尾；用户视觉确认缺口或全量规划不能冒充已完成。

已于 2026-09-14 收到用户“确认”：同意当前样板方向、保留 evidence-index.md 中的明确验证限制，并批准 A+B 纳入上述单个工作提交。执行结果与提交哈希见 task.json 和 commit-plan.json；不 push。
