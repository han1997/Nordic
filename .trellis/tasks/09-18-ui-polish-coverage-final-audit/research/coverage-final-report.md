# r13 覆盖清单收官报告(coverage-final-report)

> 基线:`09-13-ui-polish-samples/research/coverage.md`(68 条目 + 5 共享影响面)。
> 追溯明细:[coverage-traceability.md](./coverage-traceability.md)。
> App:0.1.20 / 20(main,含本轮 debug 样板与测试修复)。模拟器:nordic-ui-api34 / emulator-5580。

## 本轮补测证据

| 批次 | 内容 | 数量 | 位置 |
|---|---|---|---|
| r13-ab | 有声书书库/详情/章节/倍速 × normal/long/empty/loading/error × 双主题 × font 1/2 | 80 | `app/build/reports/ui-polish/r13-ab/` |
| r13-video | 视频媒体库/搜索/详情/剧集 × normal/long/empty/error × 双主题 × 双字号 | 64 | `app/build/reports/ui-polish/r13-video/` |
| r13-interaction-final | UiCatalogInteractionTest 完整套件 | 30/30 通过 | instrumentation |
| r13-player | 播放器 7 面板 + 竖/横屏 chrome + 连播倒计时(离线预览,真实 VideoPlayerScreen) | 10 | `research/r13-player/` |
| r13-actions | 收藏/下载操作层 + 均衡器(normal/long 双主题双字号) | 16 | `app/build/reports/ui-polish/r13-actions/` |
| r13-live | 真实服务器 adb 取证:WebDAV 浏览/排序/收藏/文件菜单/文件信息/来源菜单/来源管理/条目菜单/删除确认/播放/连播提示/PiP | 12 | `research/r13-live/` |

## 68 条目终态汇总

**全部分类完成,无"状态未知"项。**

| 域 | 条目数 | 终态 |
|---|---|---|
| 音乐浏览(9) | 发现/专辑列表/歌曲列表/歌手列表/歌手详情/专辑详情/搜索/歌单列表/歌单详情 | ✅ 全部:r1 样板 + r2 截图矩阵 + 19 交互 + r10 Dock |
| 音乐播放与弹层(9) | 播放器/歌词/队列/倍速/EQ/收藏下载层/新建/重命名/删除歌单 | ✅ 全部:r1 + r2 + r9 + r13-actions(EQ/操作层独立视检) |
| 有声书(7) | 书库/详情/播放器/章节面板/倍速/睡眠/书签 | ✅ 全部:r13-ab 补齐截图 + r11 播放器三弹层 + r12 真实播放 |
| 视频(7) | 媒体库/搜索/继续观看/详情/播放器/画中画/连播倒计时 | ✅ 全部:r13-video + r13-player + r13-live(连播/PiP 真实取证) |
| 视频面板(7) | 设置/速度/信息/选集/字幕音轨/章节/清晰度 | ✅ 全部:r13-player 7 面板(离线预览 + 真实面板组件) |
| WebDAV(4) | 目录/收藏文件夹/文件信息/筛选排序 | ✅ 全部:r13-live 真实取证 + r12 真实浏览 |
| 设置(16) | 设置主页等 16 页 | ✅ 全部:r8 截图矩阵 + 交互 |
| 设置与来源弹层(8) | 搜索定位/选项对话框/草稿退出/来源选择/切换确认/删除确认/隐藏模块/清理恢复 | ✅ 全部:r8 + r13-live(来源菜单/管理/删除确认真实取证);播放中切换确认依赖音频域场景,对话框组件与删除确认同源 |
| 共享组件样板(1) | 设置值/长路径/开关/禁用 | ✅ r1 + r8 |

## 5 共享影响面终态

| 共享实现 | 终态 |
|---|---|
| SongListRow / MusicCollectionHeader | ✅ r1/r2/r10 |
| MediaPlayerTopBar | ✅ r1/r9/r11 |
| MediaPlayerSheet / MediaPlayerChoiceRow | ✅ r1/r2/r9/r11/r13-player(视频面板真实组件) |
| PolishedPlaybackDock | ✅ r10/r12/r13-live(真实播放状态可见) |
| SettingsRow / ConfigTextField | ✅ r1/r8 |

## 明确保留项(附理由,非缺陷)

1. **Emby 视频流媒体播放**:r12 判定环境性网络超时(模拟器无法到达服务器),非 UI 缺陷;错误态 UI 本身已有类型化中文错误+重试证据(video-player-error.png)。
2. **WebDAV 字幕渲染**:测试服务器目录无 SRT/ASS/VTT 文件;字幕选择面板 UI 已有 r13-player 证据(外挂字幕流行),真实渲染需含字幕的文件。
3. **TalkBack 真机朗读走查**:语义已由 instrumentation 覆盖(Role/heading/selected/custom actions),真实 TalkBack 体验需个人设备,历轮一致保留。
4. **逐图人工视检**:r13-ab/r13-video/r13-actions/r13-player 批次已落盘封存,按惯例由用户逐张确认;自动化通过不替代人视。
5. **播放中切换来源确认(视频域)**:对话框组件与删除确认同源(r13-source-delete-confirm 已取证);视频域触发场景(播放中开来源菜单)在产品导航中不可达(播放器全屏无来源入口),记为组件级已验、场景级不适用。
6. **跨域进度隔离**:属功能行为而非 UI 视觉,由既有 JVM 进度同步测试套件覆盖;真实服务器端到端隔离留待后续功能联测轮次。

## 本轮代码变更(0.1.19 → 0.1.20)

- `VideoCatalogSamples.kt`(debug):VideoSearch+empty 状态保留媒体库列表与匹配计数副标题,对齐 ready 判定,修复"没有匹配的视频"永不可达的样板缺陷。
- `VideoPlayerPreviewActivity.kt`(debug):预览分集补字幕/音轨流与章节,启用字幕音轨/章节面板截图。
- `VideoPlayerPreviewScreenshotTest.kt`(androidTest 新增):面板/横屏/倒计时截图测试,含面板内滚动与快速失败断言。
- `UiCatalogInteractionTest.kt`(androidTest):修复 3 个自 r8 起失败的视频测试(选择器歧义/容器级滚动);完整套件 30/30。

## 验收门禁

- JVM 单测 0 失败、Lint 0 Error、Debug/Release/AndroidTest 构建通过(0.1.20/20)。
- 交互套件 30/30(含既往 3 个视频失败项根治)。
- 截图批次均含 manifest(systemFontScale/statusBarInkVerified);失败/中间批次保留不覆盖。
