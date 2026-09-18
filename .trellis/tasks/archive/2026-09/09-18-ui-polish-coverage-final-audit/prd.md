# UI 精修 r13 覆盖清单收官审计

## Goal

全应用 UI 精修（r1–r12）收官：以首轮 `09-13-ui-polish-samples/research/coverage.md` 的 **68 个页面/弹层条目 + 5 项共享影响面** 为基线，建立逐项追溯矩阵，对缺口做补测或定论，更新清单为终态，正式宣告 UI 精修收官或明确列出最终保留项。

## Background

- r1（09-13）只验收了 10 个样板条目，其余 58 项标注"后续,未逐页验收"
- r2–r11 逐域推进覆盖各页面，r12 为真实服务器联测；**从未回写总清单**，清单仍停留在 r1 时刻
- 当前版本 `0.1.19`（versionCode 19），工作区干净，无活动任务

## Requirements

### 阶段 1 — 追溯矩阵（纯文档）

- 以 `09-13-ui-polish-samples/research/coverage.md` 的 68 条目 + 5 项共享影响面 + 各轮 device-verification 证据为基线
- 逐项扫描 r2–r12 各任务的 `prd.md`、`research/device-verification-r*.md`、截图批次 manifest
- 产出 `research/coverage-traceability.md`：每条目 → 覆盖轮次、证据路径、状态（✅ 已验收 / ⚠️ 部分 / ❌ 未覆盖）

### 阶段 2 — 缺口补测（`r13-*` 批次）

- 以阶段 1 实际缺口为准，预计对象：画中画、连播倒计时交互、横屏播放器矩阵、收藏/下载操作层单独视检、跨域进度隔离等
- 沿用既有基建：`UiCatalogScreenshotTest` 参数流 + `run-ui-checks.py` + `summarize-evidence.py`，批次命名 `r13-*`，不覆盖历史批次，失败批次保留但不计通过
- 补测发现 UI 缺陷 → 修复 + 回归，版本按惯例可升 0.1.20

### 阶段 3 — 收官文档

- 更新 `09-13-ui-polish-samples/research/coverage.md` 为终态（每条款项填实）
- 产出 `research/coverage-final-report.md`：最终通过率、明确保留项及理由
- 有新约定 → `trellis-update-spec`；Phase 3.4 提交

## Acceptance Criteria

- [ ] `research/coverage-traceability.md`：全部 68 条目 + 5 共享项都有明确状态与证据链接
- [ ] 每条目终态为以下之一：✅ 有验收证据 / ⚠️ 明确的部分覆盖理由 / ❌ 用户确认的保留项（附理由）
- [ ] 无"状态未知"条目
- [ ] 可测缺口完成 `r13-*` 截图/交互批次，证据经过指纹守卫
- [ ] JVM 单测 / Lint / Debug+Release 构建全绿
- [ ] `coverage.md` 更新为终态；`coverage-final-report.md` 定论

## Definition of Done

- Tests 更新/通过（含既有门禁）
- Lint / typecheck / build 全绿
- Docs 更新（coverage.md 终态 + 收官报告）
- 提交 + 归档

## Out of Scope

- 非 UI 类优化（功能/性能/新特性评审）
- Emby 流媒体播放的环境性失败（r12 已判定 environmental，除非用户要求在本轮重试）
- 不重新验收 r1–r11 已封存的批次（引用现有证据）

## Technical Notes

- 基线清单：`.trellis/tasks/archive/2026-09/09-13-ui-polish-samples/research/coverage.md`
- 各轮证据：`.trellis/tasks/archive/2026-09/*/research/device-verification-r*.md`、`app/build/reports/ui-polish/r{7..12}/`
- 基建脚本：`09-13`/`09-14` 各有一份 `run-ui-checks.py`（取最新版）
- 当前版本：0.1.19 / 19