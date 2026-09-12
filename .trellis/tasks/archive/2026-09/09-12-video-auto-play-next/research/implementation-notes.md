# 实施调研

- AppPreferencesCodec 先合并默认字段，再解析 JSON；新偏好不加入旧 DataStore 迁移键。
- VideoPlaybackViewModel 持有引擎、来源配置、目录上下文与原会话 repository；适合持有连播协调器。
- MainActivity.VideoPlayerLayer 用 hasEnded 关闭 PiP；ON_STOP 关闭非 PiP 视频，必须保留。
- MainScreen.onPlayVideo / runMediaHandoff 负责原项关闭与切集全屏保持；WebDAV 保存有异步回调，自动请求需要在回调中二次校验，不能只在倒计时结束时检查。
- resolveNextVideoEpisode / resolveVideoPlayerEpisodes 当前未按 sourceId 过滤；WebDAV 当前项缺失会回退最后一项，需在共享解析链路修正并回归手动选集。
- VideoPlayerScreen 现有末 30 秒提示只手动播放，nextPromptDismissed 按 video.id 记忆；新倒计时不能依赖此 UI 局部状态计时，也不能使用控件自动隐藏条件。
- 测试为 JUnit + kotlinx-coroutines-test，已有纯函数交接/PiP/选集测试；不增加 Robolectric 或服务端 API。
