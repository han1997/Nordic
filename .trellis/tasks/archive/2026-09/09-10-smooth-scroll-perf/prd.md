# 滑动流畅度优化：120Hz 强制、重组修复、Compose 升级、R8

## 背景

120Hz 手机（OPPO 系 ColorOS/OriginOS）上滑动卡顿。排查结论：三因素叠加——

1. Debug 构建天然慢（用户日常用 IDE debug 包测试），真实流畅度必须以 release 为准
2. OPPO 系 ROM"智能刷新率"常把第三方 app 锁 60Hz，app 未强制高刷模式
3. 代码存在确定性掉帧点 + 工具链旧（Compose 1.6 / Kotlin 1.9 / AGP 8.2 / Gradle 8.2）

## 用户决策（已确认）

- 平时用 Debug（IDE 运行）测试；列表滚动和切页都感知卡顿
- 手机为 OPPO/vivo/一加系
- 接受 Compose 升级到 BOM 2024.09（Kotlin 2.0 工具链），并做回归
- **不要 120Hz 设置开关**：直接默认强制高刷，无用户偏好项

## 实施方案（四个独立 commit）

### Phase A：强制 120Hz

- `MainActivity.onCreate` 枚举 `Display.Mode`，同分辨率下选最高刷新率，设 `window.attributes.preferredDisplayModeId`
- 抽纯函数 `resolvePreferredDisplayModeId(modes, currentModeId)` 便于单测
- 无设置项、无开关；权限无需声明

### Phase B：确定性代码修复

| # | 问题 | 修复 | 位置 |
|---|---|---|---|
| B1 | `crossfadeEnabled=false` 时不调用 `crossfade(false)`，回退到全局 loader 160ms crossfade；快速滚动时 RenderThread 堆积 crossfade 动画 | 显式 `crossfade(false)` | AuthedAsyncImage.kt:38 |
| B2 | `rememberPressScale` 返回值在组合期被 `Modifier.scale()` 读取，按压动画每帧重组整张卡片（约 15 处） | 改为 `Modifier.pressScale()` 扩展（内部 `graphicsLayer{}` lambda 读 state），零重组；替换全部调用点 | AnimatedComponents.kt 及调用点 |
| B3 | CoverArt 图片加载成功后渐变背景 brush 仍每帧绘制（网格几十张卡 overdraw） | `showImage` 为真时移除背景 brush | SharedComponents.kt:224 |
| B4 | Tab 切换 `Crossfade` 无状态保持，销毁重建整屏、丢滚动位置 | `rememberSaveableStateHolder` + `SaveableStateProvider` 包裹各 tab 内容 | MainActivity.kt:831 |

### Phase C：工具链升级

```
Gradle wrapper 8.2 → 8.9
AGP 8.2.0 → 8.5.2
Kotlin 1.9.20 → 2.0.21
+ org.jetbrains.kotlin.plugin.compose 2.0.21（移除 kotlinCompilerExtensionVersion）
Compose BOM 2024.01.00 → 2024.09.03（Compose 1.7，lazy list 优化 + strong skipping 默认启用）
material3 pinned 1.2.1 → BOM 托管（1.3.0）；删除 app/build.gradle.kts:50 的过时 pin 注释
```

- 回归重点：MediaLoadingCard（原 pin 注释针对的崩溃点）、material3 1.3.0 废弃 API、compose 1.7 行为差异
- 编译错误/废弃警告逐个修复

### Phase D：Release 工程化

1. R8：release `isMinifyEnabled = true` + `shrinkResources = true` + proguard keep 规则（Gson DTO 反射、Retrofit 接口、Coil、Media3、OkHttp）
2. Baseline Profile：`androidx.profileinstaller:profileinstaller` 依赖，消费 Compose/Coil AAR 内置 profile
3. `assembleRelease` 通过 + 签名校验

## 保留不变

- 全部竞态守卫、缓存语义、播放器行为
- debug 构建不启用 R8
- 无 120Hz 开关（用户明确决策）

## 验证

- 每阶段：`gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug`
- 最终：`assembleRelease` 成功；真机 release 包对比走查（视频网格滚动、音乐列表、切 tab、播放器开合、PiP、深浅色、MediaLoadingCard 渲染）
- 新增单测：`resolvePreferredDisplayModeId` 边界（单模式/多模式/同分辨率多刷新率/60Hz fallback）
