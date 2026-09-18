# r13 覆盖清单追溯矩阵(coverage-traceability)

> r13 收官审计阶段 1 产物。以 `09-13-ui-polish-samples/research/coverage.md` 的 68 条目 + 5 共享影响面为基线,逐项映射 r1–r12 的覆盖轮次与证据。

## 证据口径

| 轮次 | 任务 | 版本 | 设备截图批次 | 交互/单测 | 备注 |
|---|---|---|---|---|---|
| r1 | 09-13-ui-polish-samples | 0.1.9 | ✅ 12 白名单批次 198 张 | 17+17 交互、1 真实设置 | 首轮样板;证据索引 evidence-index.md |
| r2 | 09-14-ui-polish-music | 0.1.10 | ✅ r2-* 多批次(见 build/reports) | 19 音乐交互 + 17 共享回归 + 1 真实设备 | 音乐浏览器域 |
| r3 | 09-15-ui-polish-audiobook | 0.1.11 | ❌ 无设备批次("Instrumentation 截图验收跳过分段") | JVM + 14 单测 | 设备截图缺失 |
| r4 | 09-16-ui-polish-video-browse | 0.1.12 | ❌ 无设备批次("专用模拟器无连接设备") | JVM + AndroidTest 编译 | 设备截图缺失 |
| r5 | 09-16-ui-polish-video-player | 0.1.13 | ❌ 无设备批次("专用模拟器无连接设备") | JVM + AndroidTest 编译 | 设备截图缺失 |
| r6 | 09-16-ui-polish-webdav | 0.1.14 | ❌ 无设备批次("专用模拟器无连接设备") | JVM + AndroidTest 编译 | 设备截图缺失 |
| r7 | 09-16-ui-polish-video-detail | 0.1.15 | ❌ 无设备批次("专用模拟器不可用,r7 截图未产出") | JVM + 2 交互测试编译 | 设备截图缺失 |
| r8 | 09-16-ui-polish-settings | 0.1.16 | ✅ r8 81 张 | 7 交互断言 | 设置域 |
| r9 | 09-17-ui-polish-music-player | 0.1.17 | ✅ r9 64 张 | 5 交互 | 音乐播放器无障碍+弹层 |
| r10 | 09-17-ui-polish-dock | 0.1.18 | ✅ r10 40 张 | 3 交互 + DockStatusTest | dock/modules/songs |
| r11 | 09-17-ui-polish-audiobook-player | 0.1.19 | ✅ r11 48 张 | 1 交互 | 有声书播放器+睡眠+书签 |
| r12 | 09-17-ui-polish-live-server-verification | 0.1.19 | ✅ r12 18 张(真实服务器) | 4 域联测 | Navidrome/ABS/Emby/WebDAV 查验 |

**关键缺口**:r3 有声书域、r4 视频浏览、r5 视频播放器、r6 WebDAV、r7 视频详情 五个轮次在当前版本有代码修改但**没有任何设备截图/交互证据**——stone 状态需在 r13 补测或用既有 instrumentation/JVM 证据佐证。

## 页面与弹层追溯

状态图例:✅ 已验收(有设备截图或真实设备) / ⚠️ 部分(仅编译/单测/样板无设备截图,或有明确边界) / ❌ 未覆盖 / 🔷 保留项(用户确认/环境限制,记理由)

### 音乐浏览
| 条目 | 源码 | 覆盖轮次 | 证据 | 状态 |
|---|---|---|---|---|
| 发现 | MusicScreenV2Pages.kt | r1/r2 | r2-* 批次、19 交互 | ✅ |
| 专辑列表 | MusicScreenV2Pages.kt | r2 | r2-* 批次 | ✅ |
| 歌曲列表 | MusicScreenV2Pages.kt | r1/r2/r10 | r1 样板 + r2 + r10 dock | ✅ |
| 歌手列表 | MusicScreenV2Pages.kt | r2 | r2-* 批次 | ✅ |
| 歌手详情 | MusicScreenV2Pages.kt | r2 | r2-* 批次 | ✅ |
| 专辑详情 | MusicScreenV2Pages.kt | r1/r2 | r1 样板 + r2 | ✅ |
| 音乐搜索 | MusicScreenV2Pages.kt | r2 | r2-search-empty 等 | ✅ |
| 歌单列表 | MusicScreenV2Pages.kt | r2 | r2-* 批次 | ✅ |
| 歌单详情 | MusicScreenV2Pages.kt | r2 | r2-* 批次 | ✅ |

### 音乐播放与弹层
| 条目 | 源码 | 覆盖轮次 | 证据 | 状态 |
|---|---|---|---|---|
| 完整音乐播放器 | MusicPlayerScreen.kt | r1/r9/r12 | r1 样板 + r9 64 张 + r12 真实播放 | ✅ |
| 完整/同步歌词 | MusicLyricsDisplay.kt | r1/r9 | r1 样板 + r9 lyrics | ✅ |
| 播放队列与更多菜单 | MusicQueueSheet.kt | r1/r2/r9 | r1 + r2 queue-empty + r9 queue | ✅ |
| 音乐倍速 | MusicPlayerScreen.kt | r1/r2/r9 | r1 + r2 + r9 speed | ✅ |
| 音乐均衡器 | MusicEqualizerSheet.kt | r2 | r2-eq-* 批次 | ✅ (仅内容展示,无生产调用方) |
| 收藏/下载操作层 | MusicPlayerScreen.kt | r2 | r2-final-actions、下载/收藏交互 | ⚠️ 交互回归通过,未单独截图视检 |
| 新建歌单 | MusicScreenV2.kt | r2 | r2-dialog-* 批次 | ✅ |
| 重命名歌单 | MusicScreenV2.kt | r2 | r2-dialog-* 批次 | ✅ |
| 删除歌单确认 | MusicScreenV2.kt | r2 | r2-dialog-* 批次 | ✅ |

### 有声书(⚠️ r3 无设备截图)
| 条目 | 源码 | 覆盖轮次 | 证据 | 状态 |
|---|---|---|---|---|
| 书库/来源选择/筛选 | AudiobookScreen.kt | r3/r12 | r3 无设备批次 + r12 真实晒图 | ⚠️ 代码已改、r12 真实浏览;交互矩阵缺 |
| 书籍详情/章节列表 | AudiobookScreen.kt | r3/r12 | r3 无设备批次 + r12 详情 | ⚠️ 同上 |
| 有声书播放器 | AudiobookPlayerScreen.kt | r3/r11/r12 | r11 48 张 + r12 真实播放 | ✅ |
| 播放器章节面板 | AudiobookPlayerScreen.kt | r3/r11 | r11 ab_chapters | ⚠️ r11 覆盖;interaction 覆盖有限 |
| 有声书倍速 | AudiobookPlayerScreen.kt | r3 | r3 无设备批次 | ⚠️ 无设备截图 |
| 睡眠定时 | AudiobookPlayerScreen.kt | r3/r11 | r11 ab_sleep 48 张内 | ✅ |
| 书签与笔记 | AudiobookPlayerScreen.kt | r3/r11 | r11 ab_bookmarks | ✅ |

### 视频(⚠️ r4/r5/r7 无设备截图)
| 条目 | 源码 | 覆盖轮次 | 证据 | 状态 |
|---|---|---|---|---|
| 视频媒体库/筛选/排序 | VideoScreen.kt | r4/r12 | r4 无设备批次 + r12 真实 brom | ⚠️ 无设备截图矩阵 |
| 视频搜索 | VideoScreen.kt | r4 | r4 无设备批次 | ⚠️ 无设备截图;r8 记录 flaky |
| 继续观看 | VideoScreen.kt | r4/r12 | r12 续看 19m 真实屏 | ⚠️ 真实浏览;矩阵缺 |
| 视频详情/分季选集 | VideoDetailScreen.kt | r4/r7/r12 | r7 无设备批次 + r12 详情 | ⚠️ 无设备截图 |
| 视频播放器(横/竖屏) | VideoPlayerScreen.kt | r5/r12 | r5 无设备批次 + r12 ⚠️失败 | ⚠️ Emby 环境性播放失败;WebDAV 播放通过 |
| 画中画 | VideoPlayerScreen.kt | r5(代码) | 无 | ❌ 无任何验收证据 |
| 连播倒计时/立即播放/取消 | VideoPlayerScreen.kt | r5/r12 | r12 WebDAV 自动连播 3 屏 | ⚠️ 真实自动连播通过;倒计时面板交互缺 |

### 视频面板(⚠️ r5 无设备截图)
| 条目 | 源码 | 覆盖轮次 | 证据 | 状态 |
|---|---|---|---|---|
| 播放设置 | VideoPlayerPanels.kt | r5 | 无设备批次 | ⚠️ 无设备截图 |
| 播放速度 | VideoPlayerPanels.kt | r5 | 无设备批次 | ⚠️ 无设备截图 |
| 影片信息 | VideoPlayerPanels.kt | r5/r12 | r12 详情 seen | ⚠️ 无设备截图 |
| 选集 | VideoPlayerPanels.kt | r5 | 无设备批次 | ⚠️ 无设备截图 |
| 字幕与音轨 | VideoPlayerPanels.kt | r5 | 无设备批次 | ⚠️ 无设备截图;嵌套滚动修复未截图 |
| 章节 | VideoPlayerPanels.kt | r5 | 无设备批次 | ⚠️ 无设备截图 |
| 清晰度 | VideoPlayerPanels.kt | r5 | 无设备批次 | ⚠️ 无设备截图 |

### WebDAV(⚠️ r6 无设备截图)
| 条目 | 源码 | 覆盖轮次 | 证据 | 状态 |
|---|---|---|---|---|
| 网盘目录/路径 | WebDavScreen.kt | r6/r12 | r12 目录多层导航 | ⚠️ 真实浏览;矩阵缺 |
| 收藏文件夹 | WebDavScreen.kt | r6 | 无设备批次 | ⚠️ 无设备截图 |
| 文件信息/播放操作 | WebDavScreen.kt | r6/r12 | r12 文件信息 + 播放 | ⚠️ 真实部分;矩阵缺 |
| 网盘筛选/排序 | WebDavScreen.kt | r6 | 无设备批次 | ⚠️ 无设备截图 |

### 设置(16 页)
| 条目 | 源码 | 覆盖轮次 | 证据 | 状态 |
|---|---|---|---|---|
| 设置 | SettingsScreen.kt | r8 | r8 81 张内 settings_home | ✅ |
| 媒体服务器 | SettingsScreen.kt | r8 | r8 servers | ✅ |
| 外观与启动 | SettingsScreen.kt | r8 | r8 prefs | ✅ |
| 模块显示 | SettingsScreen.kt | r1/r8/r10 | r1 样板 + r8 + r10 modules | ✅ |
| 音乐播放 | SettingsScreen.kt | r8 | r8 prefs | ✅ |
| 有声书播放 | SettingsScreen.kt | r8 | r8 prefs(设置页) | ✅ |
| 视频播放 | SettingsScreen.kt | r8 | r8 prefs | ✅ |
| 存储与下载 | SettingsScreen.kt | r8 | r8 data | ✅ |
| 隐私与数据 | SettingsScreen.kt | r8 | r8 data | ✅ |
| 关于与帮助 | SettingsScreen.kt | r8 | r8 内 | ✅ |
| 添加服务器 | SettingsScreen.kt | r8 | r8 serverForm | ✅ |
| 编辑服务器 | SettingsScreen.kt | r1/r8 | r1 表单 + r8 交互 | ✅ |
| 已下载音乐 | SettingsScreen.kt | r8 | r8 data | ✅ |
| 待归属旧数据 | SettingsScreen.kt | r8 | r8 data | ✅ |
| 连接帮助 | SettingsScreen.kt | r8 | r8 内 | ✅ |
| 开源声明 | SettingsScreen.kt | r8 | r8 内 | ✅ |

### 设置与来源弹层
| 条目 | 源码 | 覆盖轮次 | 证据 | 状态 |
|---|---|---|---|---|
| 设置搜索及定位 | SettingsScreen.kt | r8 | r8 交互 | ✅ |
| 设置选项对话框 | SettingsComponents.kt | r8/r1 | r1 设置行 + r8 | ✅ |
| 未保存草稿退出确认 | ServerEditorScreen.kt | r8 | r8 交互 | ✅ |
| 来源选择/管理菜单 | MediaSourceControls.kt | r8 | r8 | ⚠️ 交互有;截图矩阵视检待确认 |
| 播放中切换来源确认 | MediaSourceControls.kt | r8 | r8 | ⚠️ 同上 |
| 删除来源确认 | MediaSourceControls.kt | r8 | r8 | ⚠️ 同上 |
| 隐藏正在播放模块确认 | SettingsPreferences.kt | r8/r10 | r8 + r10 | ✅ |
| 清理/恢复/旧数据归属确认 | SettingsDataPages.kt | r8 | r8 | ✅ |

### 共享组件样板
| 条目 | 覆盖轮次 | 证据 | 状态 |
|---|---|---|---|
| 设置值/长路径/开关与禁用项 | r1/r8 | r1 settings_rows + r8 | ✅ |

## 共享实现影响面(不计作逐页完成)

| 共享实现 | 影响范围 | 证据边界 | 状态 |
|---|---|---|---|
| SongListRow / MusicCollectionHeader | 音乐集合与列表 | r1/r2 音乐浏览器批次 | ✅ 音乐域已覆盖 |
| MediaPlayerTopBar | 音乐/有声书播放器 | r1/r9 音乐;r11 有声书 | ✅ |
| MediaPlayerSheet / MediaPlayerChoiceRow | 音频弹层/视频选择 | r1/r9/r11 | ⚠️ 视频 panel 宿主因 r5 无截图,复用证据不足 |
| PolishedPlaybackDock | 根导航 + 迷你条 | r10 Dock + r1/r2 | ⚠️ 真实三域播放切换仅 r12 部分(WebDAV 播放) |
| SettingsRow / ConfigTextField | 设置/服务器表单 | r1/r8 | ✅ |

## 缺口汇总(待补测候选)

### A. 无设备截图轮次(r3/r4/r5/r6/r7)——需 r13-* 批次或交互补测
1. 有声书书库/详情矩阵(r3)
2. 有声书倍速(r3)
3. 视频 Home 搜索/筛选/无匹配(r4)
4. 视频继续观看卡字号适配(r4)
5. 视频详情 Hero/分集(r7)
6. 视频播放器面板全家桶:设置/速度/信息/选集/字幕音轨/章节/清晰度(r5)
7. WebDAV 收藏/排序/文件信息长路径(r6)

### B. 完全无证据项
8. 画中画 PiP(r5 代码有,无任何验收)
9. 连播倒计时 / 立即播放 / 取消 面板交互

### C. 明确保留项(环境限制)
- Emby 视频流媒体 r12 环境性失败——非 UI 缺陷,保留
- 真机 PiP / TalkBack 真机朗读——需个人设备,保留

### D. 交互回归通过但未单独截图
- 收藏/下载操作层、来源选择/删除/切换确认