# Research: 主流播放页对标（音流 / audiobookshelf / Hills-Yamby）与 Batch F 特性清单

- **Query**: 参考主流软件完善音乐（音流）、有声书（audiobookshelf）、视频（Hills/Yamby）播放页面
- **Scope**: external conventions + internal code audit
- **Date**: 2026-09-05

## 现状盘点（代码审计结论）

### 音乐播放页 MusicPlayerScreen.kt
- 已有: 下滑关闭手势、封面/歌词切换、收藏、队列 sheet 入口、共享薄滑杆、错误 pill
- 缺: 播放速度、双击 seek、顶栏右侧功能位空置（只有占位 Spacer）

### 有声书播放页 AudiobookPlayerScreen.kt
- 已有: 书签 sheet、睡眠定时器 sheet、速度循环点按（MetaChip）、±30s、章节 prev/next、下滑关闭
- 缺: 章节列表直接跳转（目前只能在详情页看章节列表，且行不可点）、速度面板化

### 视频播放页 VideoPlayerScreen.kt
- 已有: 缓冲进度条(bufferedPosition)、剩余时间、双击 seek 反馈 chip、横向拖动 scrub、捏合切比例、信息面板、宽高比循环、全屏
- 缺: 亮度/音量手势、手势锁、播放速度

## 主流参考特性（Hills/Yamby/Infuse/Jellyfin/audiobookshelf/音流通用范式）

1. **视频亮度/音量手势**: 左半屏上下滑 = 亮度 (WindowManager.LayoutParams.screenBrightness 0~1)，右半屏 = 音量 (AudioManager STREAM_MUSIC, step ±1/15)；拖动时中央显示竖向指示条（图标+进度）。[Hills/Yamby/Infuse/Jellyfin 均有]
2. **手势锁**: 锁定后忽略所有手势仅保留解锁按钮，防口袋误触。[Jellyfin/Infuse/audiobookshelf]
3. **播放速度**: 视频 0.5–2.0 步进 0.25/0.5 菜单；音乐 0.5–2.0；有声书面板化选择。[全平台]
4. **双击 seek**: 全播放器统一 ±10s（视频）/±30s（有声书）+ 视觉反馈。
5. **章节列表**: audiobookshelf 播放页直接内嵌章节抽屉，点击跳转 + 当前章节高亮。
6. **音乐速度**: 音流支持倍速播放（变速不变调，Media3 PlaybackParameters 原生支持）。

## Batch F 实施清单（纯客户端，无新 server API 依赖）

### F1 音乐播放页
- `MusicPlaybackEngine`: 新增 `setPlaybackSpeed(speed: Float)` → `controller.playbackParameters = controller.playbackParameters.buildUpon().setSpeed(speed).build()`；state 增加 `playbackSpeed: Float`，在 `publishPlayerState()` 读取 `activeController.playbackParameters.speed`。
- `MusicPlayerScreen`: 顶栏右侧 Spacer 换成速度按钮（显示当前倍速文本，点击弹 sheet）；新增 `MusicPlaybackSpeedSheet`（0.5/0.75/1.0/1.25/1.5/2.0）；双击封面/歌词区左右半屏 ±10s seek + 复用 `SeekFeedback` 风格 overlay（浅色主题适配）。
- `MainActivity.MusicPlayerLayer`: 接线 `onSetPlaybackSpeed`。

### F2 有声书播放页
- `AudiobookPlayerScreen`: 新增章节列表 ModalBottomSheet（复用 `sortedChapters`，行显示 序号+标题+时长区间，当前章节高亮 primary，点击 `onSeek(chapter.startSeconds)` 并关 sheet）；MetaChip 速度 chip 改为打开速度 sheet（0.5–3.0 步进 0.25，audiobookshelf 范式），保留长按循环可删除——只改点击行为。
- Engine 无需改动（`seekTo` 已支持绝对秒）。

### F3 视频播放页
- `VideoPlayerGestures`: 扩展为左半屏竖滑=亮度、右半屏竖滑=音量（竖向位移超过阈值即进入对应手势模式，与横向 scrub 互斥：先判定主方向）；中央 overlay 显示图标+竖向进度。
- `VideoPlaybackEngine`: 新增 `setPlaybackSpeed(speed: Float)`；`VideoPlaybackState` 增加 `playbackSpeed: Float`（publishPlayerState 同步）。
- `VideoPlayerScreen`: 控制行加速度按钮（文本按钮显示 "1.0x"），弹速度菜单 sheet（0.5–2.0 步进 0.25）；顶栏信息按钮旁加手势锁按钮（锁定时仅显示解锁按钮+忽略其它手势，含 BackHandler 不关闭）。
- 亮度用 `LocalContext` activity window attributes；音量用 `AudioManager.setStreamVolume`。

### 测试要求
- 纯函数单元测试: `resolvePlayerThinSliderPosition` 已有覆盖模式可循；新增 `resolvePlaybackSpeedLabel`（三处共用）、视频手势方向判定 `resolveVideoGestureAxis`（dx/dy 主方向判定）、速度列表 `resolveNextPlaybackSpeed`。
- 涉及文件: MusicPlayerScreen.kt / AudiobookPlayerScreen.kt / VideoPlayerScreen.kt / VideoPlayerGestures.kt / MusicPlaybackEngine.kt / VideoPlaybackEngine.kt / MainActivity.kt + 对应 test 文件。

## Sources

- 音流: gitlab.com/gitlab-gzsubstreamer/substreamer-android (release notes 描述播放页功能)；实际以通用 Subsonic 客户端播放页范式为准
- audiobookshelf: github.com/advplyr/audiobookshelf-android (player UI: chapters drawer, sleep timer, speed panel, bookmarks)
- Hills/Yamby: 无公开文档，参照 Infuse / Jellyfin Android / Yatse 的成熟 Emby 客户端手势范式推断，标记为 [inferred]
