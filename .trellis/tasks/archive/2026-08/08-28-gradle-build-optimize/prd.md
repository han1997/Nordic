# 优化 Gradle 构建与验证流程

## Goal

每次改动代码后，Trellis 的质量检测（compile + test + lint）耗时过长，主要瓶颈是 spec 要求的验证命令带 `--no-daemon` 串行执行三个 Gradle 任务，等于三次 JVM 冷启动 + 三次插件加载 + 三次编译。本任务目标是在不降低验证质量的前提下，通过 Gradle 配置和验证命令优化，把日常开发循环的验证耗时显著降低。

## Requirements

- **合并验证命令**：将 spec 中三条串行 `--no-daemon` 命令合并为共享 daemon 的单次调用，消除重复 JVM 冷启动
- **去掉 `--no-daemon`**：启用 Gradle daemon 持久化，让后续调用复用已就绪的 JVM + 插件 + 依赖解析
- **`gradle.properties` 性能配置**：在 AGP 8.2 / Kotlin 1.9.20 兼容范围内增加：
  - `org.gradle.caching=true`（构建缓存，跨任务复用输出）
  - `org.gradle.configuration-cache=true`（配置缓存，避免重复解析 build script）
  - `org.gradle.parallel=true`（并行任务执行）
  - 保留现有 `-Xmx2048m` 和其他配置不变
- **spec 验证命令分级**：`.trellis/spec/backend/index.md` 的 Verification 章节改为两级：
  - 快速验证（日常开发循环）：`compileDebugKotlin` + `testDebugUnitTest`
  - 完整验证（提交前）：上述 + `lintDebug`
  - 完整验证使用合并单条命令 `.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug`（无 `--no-daemon`）
- **trellis-check SKILL.md 同步检查**：确认 `.opencode/skills/trellis-check/SKILL.md` 的 Step 3 "Run Project Checks" 是否需要补充项目实际命令引用，使 spec 与 skill 语义一致
- **不引入 lint baseline**，不动 lint 规则（保持现有全量检查语义）

## Acceptance Criteria

- [ ] `gradle.properties` 新增性能配置且与 AGP 8.2 / Kotlin 1.9.20 兼容（编译能跑通）
- [ ] `.trellis/spec/backend/index.md` Verification 章节改为分级验证（快速 / 完整），命令合并为单条无 `--no-daemon`
- [ ] `.opencode/skills/trellis-check/SKILL.md` Step 3 如需对齐则同步更新
- [ ] 优化后 `.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest` 能跑通（绿色）
- [ ] 优化后 `.\gradlew.bat :app:lintDebug` 能跑通（绿色）
- [ ] 首次冷启动后，第二次连续执行验证耗时显著低于优化前（主观确认即可，无需精确测量）

## Definition of Done

- 验证命令变更已写入 spec
- `gradle.properties` 配置变更经过编译/测试验证
- 无回归（现有测试仍通过）
- trellis-check SKILL.md 与 spec 语义一致

## Technical Approach

### 改动文件清单

1. **`gradle.properties`** — 增加性能配置行
2. **`.trellis/spec/backend/index.md`** — 重写 Verification 章节为分级验证
3. **`.opencode/skills/trellis-check/SKILL.md`** — Step 3 补充项目实际命令引用（如确认需要对齐）

### 关键技术决策

- **配置缓存兼容性**：AGP 8.2 官方支持 configuration-cache，本项目无已知不兼容插件（只用了 com.android.application + kotlin.android），可安全启用。首次运行会预热缓存，后续构建显著加速。
- **不引入 lint baseline**：保持 lint 全量检查语义，避免历史警告被 baseline 掩盖新问题。lint 提速只靠 daemon + 缓存。
- **分级验证策略**：日常开发循环（trellis-check 默认）只跑 compile + test；提交前跑完整三项。spec 明确区分两个级别。

## Decision (ADR-lite)

**Context**: 验证流程三次 `--no-daemon` 冷启动导致每次质量检测 60-120s，严重影响开发节奏。

**Decision**: 
1. 合并命令 + 去 `--no-daemon` + 加 Gradle 性能配置
2. 验证分级：日常 compile+test，提交前 +lint
3. 不动 lint 规则，不引入 baseline

**Consequences**: 
- 正面：日常循环省掉 lint（30-60s）+ 省掉 2 次 JVM 冷启动（30-60s），总提速约 60-120s
- 负面：配置缓存首次运行需预热（可能有一次 warning），部分 AGP task 不兼容时会 fallback（不报错）
- 风险：日常循环不跑 lint，可能晚发现 lint 问题——通过"提交前必须跑完整验证"缓解

## Out of Scope

- Compose 运行流畅性重构（拆分 MusicScreenV2、隔离 positionMillis 等）——下一个任务
- 升级 Gradle / AGP / Kotlin / Compose BOM 版本
- 引入 Room / 替换 DataStore 等架构改动
- lint baseline 或 lint 规则定制
- CI 环境配置（当前只优化本地开发循环）

## Technical Notes

- `--no-daemon` 每次冷启动 JVM + 加载 AGP/Kotlin/Compose 插件，仅启动开销 15-30s × 3 次
- `lintDebug` 默认全量扫描所有源 + 依赖 + 所有规则，通常 30-60s
- AGP 8.2 支持配置缓存（`org.gradle.configuration-cache`）
- 构建缓存 `org.gradle.caching=true` 对相同输入的任务输出可跨调用复用
- 三任务合并为单次调用：`.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug`
</content>
