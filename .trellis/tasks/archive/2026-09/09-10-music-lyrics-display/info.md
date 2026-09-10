# 音乐歌词优化验收记录

## 用户确认的行为

- 完整显示普通/同步歌词与长句，同时间文本组成共同高亮句组。
- 播放中停止手动拖动及惯性滚动后 2 秒恢复跟随；暂停时保留浏览，继续播放恢复；正在拖动不抢占。
- 歌词/封面选择在 ViewModel 会话内保留，切歌、收起重开不重置，新会话默认封面。
- 保留单击切换、双击快进退；歌词区域拖动不误关播放器。

## 实现与质量

- 领域规范化、请求控制、跟随计时/滚动分类/几何逻辑均有独立单测；单一 MusicLyricsUiState、Job 取消及请求序号防止串歌和迟到覆盖。
- 已去除生产路径不用的窗口截取函数和相关旧测试；原有 nullable DTO、毫秒与 offset 测试继续通过。
- 首轮领域定向测试及原歌词解析测试通过（1 分 35 秒）。提取组件时曾误删隐式 getValue/setValue 导入，已恢复；之后完整 compile/test/lint 通过（1 分 53 秒）。
- 最终执行 `gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease --console=plain`，BUILD SUCCESSFUL（3 分 25 秒），包括实际 R8 重建。
- 最终 609 项单元测试、41 个 suite，失败/错误/跳过均为 0；Lint 0 error、22 项既有 Warning、18 项 Information。
- UTF-8、文档相对链接、7 段合同、任务 JSONL 及 git diff --check 均通过；没有新增警告抑制、依赖、持久歌词缓存或新歌词来源。

## APK 验证

- Debug/release 均为 `fun.han1997.nordic`、versionName `0.1.3`、versionCode `3`，v2 签名有效且沿用旧证书。
- Release 不包含 debug 预览 Activity，未开启 debuggable；启动类仍为 `com.nordic.mediahub.MainActivity`。
- 真实 release DEX 中 Navidrome 19、有声书 9、Emby 8 个 suspend 方法均保留泛型；Continuation/Response/Call 类型定义也保持正常。
- 交付文件：`app/build/distributions/Nordic-0.1.3-release.apk`
- 大小：4359432 bytes
- SHA-256：`49288341706c56c4ad5106c2b30a185763afb3cec49d878193806e62c14d824d`
- 工作文件 SHA-256 与详细产物数据保存于 `research/verification.json`。

## 真机待验收（未伪装为自动测试通过）

执行期间检测到连接的 OPPO 设备，但没有自动覆盖安装或操作播放，也没有卸载/清数据。

- [ ] 普通歌词可滚动至末尾；长句、同时间句组显示完整。
- [ ] 首尾句居中，超高句顶部对齐；播放中手动浏览停止 2 秒后恢复。
- [ ] 暂停浏览不自动拉回，恢复播放/回到当前行为正确。
- [ ] 切歌与收起重开保留视图选择，错误重试及快速切歌不串词。
- [ ] 浅深主题、窄屏、大字体、单/双击与下滑关闭手势无冲突。

## 工作提交与收尾

用户已确认提交，工作提交为 `2faadbc`（优化歌词显示、同步跟随与加载状态），包含以下 21 个工作文件。实现、自动质量门、规范同步和工作提交已完成；归档状态以 task.json 为准，会话只记录工作提交。本轮没有推送远端或操作手机；上述真机验收项仍未执行。

- `.trellis/spec/backend/error-handling.md`
- `.trellis/spec/backend/index.md`
- `.trellis/spec/backend/music-lyrics.md`
- `.trellis/spec/backend/quality-guidelines.md`
- `.trellis/spec/backend/ui-consistency.md`
- `CHANGELOG.md`
- `README.md`
- `app/build.gradle.kts`
- `app/src/main/java/com/nordic/mediahub/MainActivity.kt`
- `app/src/main/java/com/nordic/mediahub/data/MusicLyrics.kt`
- `app/src/main/java/com/nordic/mediahub/data/NavidromeRepository.kt`
- `app/src/main/java/com/nordic/mediahub/playback/MusicLyricsController.kt`
- `app/src/main/java/com/nordic/mediahub/playback/MusicPlaybackViewModel.kt`
- `app/src/main/java/com/nordic/mediahub/ui/MusicLyricsDisplay.kt`
- `app/src/main/java/com/nordic/mediahub/ui/MusicLyricsFollowController.kt`
- `app/src/main/java/com/nordic/mediahub/ui/MusicPlayerScreen.kt`
- `app/src/test/java/com/nordic/mediahub/data/MusicLyricsTest.kt`
- `app/src/test/java/com/nordic/mediahub/data/NavidromeRepositoryTest.kt`
- `app/src/test/java/com/nordic/mediahub/playback/MusicLyricsControllerTest.kt`
- `app/src/test/java/com/nordic/mediahub/ui/MusicLyricsFollowControllerTest.kt`
- `app/src/test/java/com/nordic/mediahub/ui/MusicPlayerScreenTest.kt`
