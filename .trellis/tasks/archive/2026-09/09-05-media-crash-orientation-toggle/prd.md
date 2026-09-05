# 媒体库点击闪退修复 + 视频播放横竖屏切换按钮

## Goal

两个独立问题打包到一个任务：

1. **【Bug】视频 Tab 中，点击视频库上方的"媒体库"选择 chips（VideoLibrarySelector）后应用闪退。**
2. **【Feature】视频播放全屏目前依赖重力感应自动切换横竖屏，用户需要手动横竖屏切换按钮。**

## What I already know

### 问题 1：媒体库点击闪退（根因已由 logcat + 字节码反编译确认）

* **崩溃堆栈**（真机 logcat，点媒体库 chip 触发 loading 卡片）：
  `java.lang.NoSuchMethodError: No virtual method at(Ljava/lang/Object;I)Landroidx/compose/animation/core/KeyframesSpec$KeyframeEntity; in KeyframesSpec$KeyframesSpecConfig` at `material3.ProgressIndicatorKt.LinearProgressIndicator` ← `MediaLoadingCard`（MediaStateComponents.kt:136）。
* **字节码级根因**（javap 证据）：
  * material3 **1.1.2** 的 `LinearProgressIndicator$firstLineHead$1` 调用 `KeyframesSpecConfig.at(Object,int)` 期望协变返回 `KeyframesSpec$KeyframeEntity`（该签名由 animation-core 1.7+ 提供）。
  * 项目解析到 animation-core **1.6.0**，其 `at(Object,int)` 擦除返回 `KeyframeBaseEntity`，没有协变签名 → `NoSuchMethodError`。
  * 即 **material3 1.1.2 与 animation-core 1.6.0 是不兼容组合**。
* **为何解析到坏组合**：本机 Gradle metadata 缓存把 `compose-bom:2024.01.00` 约束到 material3 1.1.2，且该 BOM pom 文件不在磁盘缓存（metadata 索引残留、陈旧/损坏）。真实 BOM 2024.01.00 映射 material3 → 1.2.0（已验证本地 m3 1.2.0 字节码的 `at()` 调用返回 `KeyframeBaseEntity`，与 animation-core 1.6.0 兼容）。
* 触发面：任何渲染 `MediaLoadingCard`/`MediaStateCard` 的 `LinearProgressIndicator`（视频切库 loading、音乐/有声书 loading 卡）都会崩；点媒体库 chip 是最常见入口（videos 清空 + isLoading=true）。
* **修复决策**：在 `app/build.gradle.kts` 显式钉住 `androidx.compose.material3:material3:1.2.1`，覆盖损坏的 BOM 约束。1.2.x 与 compose 1.6.0 / Kotlin 1.9.20 / compose-compiler 1.5.4 兼容。
* 次要发现（同代码路径的正确性 bug，非崩溃）：
  * **ON_RESUME 静默刷新与媒体库切换存在竞态**：`LifecycleEventEffect(ON_RESUME)` 触发的 `refreshVideo(targetLibraryId = selectedLibraryId)` 在调用时捕获旧库 id；用户在其网络往返窗口内点击切库，`refreshVideo` 完成时会回写 `selectedLibraryId = catalog.selectedLibraryId`（旧库）和旧库 items，而 chip 请求结果被自己的 `selectedLibraryId == libraryId` 守卫丢弃 → 用户点击被"吞掉"，显示错库。
  * 仓库层 `getLibraryItems` 无按 id 去重防御：Compose 网格 key 为 `"${libraryId}:${id}"`，服务器返回重复 id 时会抛 `IllegalArgumentException` 闪退（防御性修复，spec 边界校验职责）。
  * 分页循环 `startIndex += pageItems.size` 对"服务器不推进 StartIndex"无防护 → 理论死循环。

### 问题 2：横竖屏切换按钮

* 现状（MainActivity.kt:615-627 单一全屏控制器）：
  * `isFullscreen && showVideoPlayer` → 隐藏系统栏 + `SCREEN_ORIENTATION_SENSOR_LANDSCAPE`（横屏内仍随重力翻转，躺床会 180° 翻面）。
  * 非全屏 → `SCREEN_ORIENTATION_UNSPECIFIED`（整个应用跟随系统/重力旋转）。
  * 全屏按钮（Fullscreen/FullscreenExit 图标）在 `VideoPlayerControls` 行尾；返回键在全屏时先退全屏。
* Manifest 已配置 `configChanges="orientation|screenSize|smallestScreenSize"`，方向切换不会重建 Activity。
* 用户诉求："全屏自动根据重力感应切换，我需要横竖屏切换按钮" → 手动控制优先。
* spec 约束（.trellis/spec/backend/emby-integration.md "Video Playback Display Modes and Fullscreen"）：全屏状态归 app shell 所有；"Fullscreen ... requests sensor landscape" 契约需要随本任务更新。

## Decision (ADR-lite)

**Context**: 媒体库点击闪退，logcat 定位为 material3 `LinearProgressIndicator` 内 `NoSuchMethodError`；字节码反编译确认 m3 1.1.2 与 animation-core 1.6.0 二进制不兼容；本机 Gradle 缓存把 BOM 2024.01.00 错误约束到 m3 1.1.2。
**Decision**: 显式钉住 material3 1.2.1（与 compose 1.6.0/Kotlin 1.9.20 兼容的最小稳定版），不整体升级 BOM（避免 compose 1.7+ 对 Kotlin/compiler 的连锁升级风险）。横竖屏采用"手动锁定"模型（用户选定）：进全屏默认横锁，退出恢复竖锁，重力不干预播放方向。
**Consequences**: m3 1.2.1 相对 1.1.2 有少量 API 变化（本项目仅用基础组件，风险低）；后续若升级 BOM 需整体对齐 compose/m3 版本；方向锁定状态归 app shell（沿用单一全屏控制器模式）。

## Requirements (evolving)

### R0 依赖修复（闪退根因）

* `app/build.gradle.kts` 显式钉住 `androidx.compose.material3:material3:1.2.1`（覆盖损坏的 BOM 2024.01.00 → 1.1.2 约束），消除 m3 1.1.2 × animation-core 1.6.0 的 `NoSuchMethodError`。
* 验证：`gradlew dependencies` 中 material3 解析为 1.2.1，animation-core 保持 1.6.0；真机点媒体库 chip 不再闪退。

### R1 媒体库健壮性（防御 + 竞态）

* 仓库层 `EmbyRepository.getLibraryItems` 按 `(libraryId, id)` 去重（保持首次出现顺序），杜绝重复 item 进入 UI 层导致 Compose key 冲突。
* 分页防御：同一响应页面与上一页起始 item 重复（服务器未推进 StartIndex）时终止分页，避免死循环。
* 修复库切换竞态：媒体库切换的请求版本（`videoLibraryRequestVersion`）必须同时守卫 `refreshVideo` 对 `selectedLibraryId`/`videos`/缓存的写回，旧库响应不得覆盖新选择。
* 单元测试覆盖：重复 item 去重、分页重叠终止、竞态守卫。

### R2 横竖屏切换按钮（交互模型待确认）

* 播放器控制栏提供手动横竖屏切换入口。
* 全屏仍隐藏系统栏；关闭播放器/退出全屏恢复竖屏与系统栏（沿用现有自愈逻辑）。
* 更新 emby-integration.md 全屏契约（Phase 3.3）。

## Acceptance Criteria (evolving)

* [ ] material3 解析为 1.2.1；真机点击每个媒体库 chip 不闪退（重点验证之前必崩的库）。
* [ ] loading 卡片（LinearProgressIndicator）渲染不再抛 NoSuchMethodError。
* [ ] 同一媒体库响应中重复的 item id 只渲染一次（单测覆盖）。
* [ ] 分页重叠时终止分页（单测覆盖）。
* [ ] 库切换后 ON_RESUME 刷新不会把选择"弹回"旧库。
* [ ] 播放中可通过按钮手动切换横/竖屏，重力不再自动改变播放方向（手动锁定模型）。
* [ ] `.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug` 全绿。
* [ ] 真机回归：全屏进出、返回键、后台切换、锁手势均正常。

## Definition of Done

* 单测覆盖去重/分页/竞态/方向解析纯函数。
* compile + testDebugUnitTest + lintDebug 全绿。
* emby-integration.md 全屏契约段落更新。
* CHANGELOG 条目（中文优先，见 documentation-guidelines.md）。

## Out of Scope (explicit)

* 播放器 UI 重设计、手势变更（现有手势保留）。
* 分页/按需加载大媒体库（性能优化另开任务）。
* 画中画 PiP、字幕/音轨选择。

## Technical Notes

* 关键文件：
  * app/build.gradle.kts（material3 1.2.1 钉版）
  * app/src/main/java/com/nordic/mediahub/data/EmbyRepository.kt（getLibraryItems 分页/去重）
  * app/src/main/java/com/nordic/mediahub/ui/VideoScreen.kt（chip 点击、ON_RESUME 刷新、网格 key）
  * app/src/main/java/com/nordic/mediahub/MainActivity.kt（单一全屏控制器 LaunchedEffect + 方向锁定状态）
  * app/src/main/java/com/nordic/mediahub/ui/VideoPlayerScreen.kt（控制栏方向切换按钮）
  * .trellis/spec/backend/emby-integration.md（全屏契约）
* 崩溃证据（javap）：
  * m3 1.1.2 `firstLineHead$1.invoke` → `at:(Ljava/lang/Object;I)Landroidx/compose/animation/core/KeyframesSpec$KeyframeEntity;`
  * animation-core 1.6.0 `KeyframesSpecBaseConfig.at(T,int)` 擦除返回 `KeyframeBaseEntity`
  * m3 1.2.0 同一 lambda → `at:(Ljava/lang/Object;I)Landroidx/compose/animation/core/KeyframeBaseEntity;`（兼容）
* 现有测试：EmbyRepositoryTest（MockWebServer，分页/去重可挂靠）、VideoScreenTest（纯函数）。
