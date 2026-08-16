# 修复点击下一首后封面无法切换歌词

## Goal

修复音乐播放页面在切换到下一首歌曲后，点击封面无法切换到歌词视图的 bug。

## Bug 根因分析

**复现路径**：进入播放页 → 点封面切到歌词 → 点「下一首」→ 停在封面页 → 再点封面想看歌词但没反应

**根因**：`MusicPlayerScreen.kt:437-440` 的 `PlayerPrimaryDisplay` 中：

```kotlin
Box(
    modifier = modifier.pointerInput(Unit) {
        detectTapGestures(onTap = { onToggleDisplay() })
    }
)
```

`pointerInput(Unit)` 的 key 是 `Unit`，永不重启。`detectTapGestures` 内部捕获的 `onToggleDisplay` lambda 引用了 `MusicPlayerScreen` 作用域的 `showLyrics` MutableState。

当点击「下一首」时 `song?.id` 改变 → `rememberSaveable(song?.id)` 创建了一个**新的** `MutableState`（重置为 `false`）。但 `pointerInput(Unit)` 不会重启，旧的 gesture detector 仍然持有旧的 `onToggleDisplay` lambda，该 lambda 引用的是**旧的** `MutableState`（已被丢弃）。点击封面时写入的是旧 state，UI 读的是新 state，所以点击无效。

## Requirements

- 点击下一首后，点击封面能正常切换到歌词视图
- 不引入额外的重组开销（避免 `pointerInput(onToggleDisplay)` 这种每次重组都重启的写法）

## Acceptance Criteria

- [ ] 进入播放页 → 切歌词 → 下一首 → 点封面能切到歌词
- [ ] 进入播放页 → 切歌词 → 下一首 → 上一首 → 点封面能切到歌词
- [ ] 正常切换（不切歌）仍然工作

## Technical Approach

使用 Compose 标准模式 `rememberUpdatedState` 修复：

```kotlin
val currentToggle by rememberUpdatedState(onToggleDisplay)
Box(
    modifier = modifier.pointerInput(Unit) {
        detectTapGestures(onTap = { currentToggle() })
    }
)
```

`pointerInput(Unit)` 保持不重启（性能好），`currentToggle` 通过 State 始终读取最新的 lambda。

## Out of Scope

- 其他手势/交互逻辑的改动
- 歌词加载逻辑
- UI 视觉调整

## Technical Notes

- 文件：`app/src/main/java/com/nordic/mediahub/ui/MusicPlayerScreen.kt`
- 相关行：437-440（`PlayerPrimaryDisplay` 的 `pointerInput`）
- 需新增 import：`androidx.compose.runtime.rememberUpdatedState`
