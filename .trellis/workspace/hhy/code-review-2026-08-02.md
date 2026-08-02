# Nordic MediaHub 全面代码审查报告

- 日期: 2026-08-02
- 审查人: hhy (+ AI 并行代理)
- 范围: `app/src/main` 全部 43 个 Kotlin 源文件 + `app/src/test` 18 个测试文件 + AndroidManifest
- 方法: 5 个并行 general 代理按层切分（API/Security、Data/集成契约、Playback、UI/Compose、跨层架构/日志/测试），对齐 `.trellis/spec/backend/*`，汇总去重
- 维度: 架构与分层 / 正确性与 Bug / Compose 性能 / 错误处理与日志 / 集成契约 / 安全 / 测试覆盖

---

## 严重度分布

| 严重度 | 数量 | 主要主题 |
|---|---|---|
| 🔴 Critical | 5 | 明文凭据、MediaController 断连、searchJob 重组、ABS 续听断裂、下载临时文件泄漏 |
| 🟠 High | 15 | 明文传输、无 ViewModel、循环依赖、ui→api 直连、Navidrome 契约缺失、播放音频焦点、共享播放器、播放错误无日志、ABS 类型化错误、playAlbum 版本守卫、章节每 tick 排序、巨型文件、MetaChip 重复、ConfigRepository 无测试 |
| 🟡 Medium | ~34 | 按主题分组见下 |
| ⚪ Low | ~22 | 死代码、格式化函数重复、minor 重组、minor 测试缺口 |

---

## 🔴 Critical（5）

### C1 [security] 明文存储全部服务器凭据
- LOC: `data/ConfigRepository.kt:17,21,27,28`
- Navidrome/AudiobookShelf/Emby 密码 + Emby API key 以 `stringPreferencesKey` 明文写入 DataStore Preferences，root 设备或取证提取即可读取。`allowBackup="false"` 已设（manifest:10），但落盘仍是明文。
- 修复: 迁移到 `EncryptedSharedPreferences`（androidx.security-crypto）或 Tink；保留 `allowBackup="false"`，永不记录这些 key。

### C2 [lifecycle] MediaController 断连后引擎变死桩
- LOC: `playback/MusicPlaybackEngine.kt:136-185`、`playback/AudiobookPlaybackEngine.kt:66-94`
- 监听器是 `Player.Listener`，无 `onDisconnected()`。若 `MusicPlaybackService` 被杀（低内存/用户划掉任务→`onTaskRemoved`→`stopSelf`），缓存的 `controller` 变成静默 no-op 的死引用；`controllerFuture` 已完成故 `init` 不会重跑，无重连路径。
- 修复: 改用 `MediaController.Listener`（含 `onDisconnected`）；断连时置空 `controller`、重新 `MediaController.Builder(...).buildAsync()`、重放缓存的 pending 命令。

### C3 [compose-perf] searchJob 用 mutableStateOf 触发整屏重组
- LOC: `ui/MusicScreenV2.kt:146`（赋值于 :1033）
- `var searchJob by remember { mutableStateOf<Job?>(null) }` — 每次防抖任务替换都会让整个 Music 屏幕重组。`quality-guidelines.md` 已明确将此模式列为反例。
- 修复: 改用 `remember { AtomicReference<Job?>(null) }`。

### C4 [integration] AudiobookShelf 续听功能断裂
- LOC: `api/AudiobookShelfApi.kt:221`、`data/AudiobookShelfRepository.kt:124,260`
- `getLibraryItems` 未带 `include=progress`，精简 DTO 无 `userMediaProgress`，`toSummary()` 不映射进度 → `AudiobookItemSummary.progress` 恒为 null，按 spec 应能派生的"继续收听"无法实现。
- 修复: list 端点加 `@Query("include") include: String = "progress"`，DTO 加 `userMediaProgress`，`toSummary()` 透传。

### C5 [correctness] 下载失败时删错临时文件名
- LOC: `data/MusicDownloadManager.kt:136`
- catch 块删除 `"${song.id}.tmp"`，但真实临时文件名是 `"${song.id}.${extension}.tmp"`（:104），失败时真正临时文件永不清理，磁盘持续累积孤儿文件。
- 修复: 删除 :104 创建的同一个 `tempFile` 引用（或重构 `"${song.id}.${extension}.tmp"`）。

---

## 🟠 High（15）

### H1 [security] 全局允许明文流量 + normalizer 默认 http://
- LOC: `AndroidManifest.xml:13`（`usesCleartextTraffic="true"`）；`data/NavidromeAuth.kt:27`、`AudiobookShelfAuth.kt:13`、`VideoServerAuth.kt:13`
- 裸主机名被默认补成 `http://`，凭据（ABS/Emby 密码在 body、Navidrome MD5 token+salt 在 URL）可明文传输。与 C1 叠加风险极高。
- 修复: 引入 `network_security_config.xml` 默认拒绝明文、仅显式放行 LAN 主机；normalizer 默认 `https://`，仅当用户显式输入 `http://` 时保留。

### H2 [architecture] 无 ViewModel；状态全在 composable + MainActivity 巨石
- LOC: `MainActivity.kt:237-803`（803 行 `MainScreen`）；全仓零 `ViewModel/Hilt/@Inject`
- `MainScreen` 持有：tab 路由、3 个播放器可见性、全屏、队列 sheet、有声书错误态、3 个播放引擎、ConfigRepository+3 派生 repo、歌词态+fetch `LaunchedEffect`、有声书 30s 同步循环(:516-550)、视频 30s 同步循环(:552-586)、底部 dock 滚动显隐、全屏系统栏/方向、3 个关闭编排函数(:396-483)、主题切换、4 个 BackHandler。配置变更/进程死亡状态全丢。
- 修复: 抽 `MusicPlaybackViewModel`/`AudiobookPlaybackViewModel`/`VideoPlaybackViewModel`（持引擎、repo、同步循环、关闭编排），`MainScreen` 仅剩路由/脚手架。

### H3 [architecture] MainActivity ↔ MusicPlaybackService 循环依赖
- LOC: `MainActivity.kt:41-43` + `playback/MusicPlaybackService.kt:21,139`
- `MainActivity` import `playback.*`；`MusicPlaybackService` 反向 import `com.nordic.mediahub.MainActivity` 以构建 sessionActivity PendingIntent。app-shell→playback→app-shell 真实环。
- 修复: 倒置为 session-activity 契约 — 在 manifest 声明 `<media-session-activity>` 或经 `data/` 的 `SessionActivityProvider`，service 不直接命名 `MainActivity`。

### H4 [architecture] ui 直接 import api DTO；无领域模型
- LOC: `ui/MusicHomeSections.kt:39-41`、`MusicPlayerScreen.kt:47`、`MusicScreenV2.kt:62-65`、`MusicQueueSheet.kt:44`、`PlaybackDock.kt:26`；根因 `api/NavidromeApi.kt:68-128`（NavidromeAlbum/Song/Artist/Playlist 当领域模型用）
- 5 个 Compose 文件直接渲染 `api.NavidromeSong` 等 DTO（含 `streamUrl`/`coverArt`），违反 `directory-structure.md`："UI should call repository methods, not Retrofit APIs directly"。`playback/MusicPlaybackEngine.kt:12`、`MusicMediaItems.kt:8` 同样持原始 DTO。
- 修复: 在 `data/` 引入领域模型（`MusicTrack`/`AlbumSummary` 等），在仓库边界映射；UI/引擎只吃领域类型。

### H5 [integration] Navidrome 大量契约端点缺失
- LOC: `data/NavidromeRepository.kt`（整文件）/ `api/NavidromeApi.kt`
- `navidrome-integration.md` 要求的 `star`/`unstar`/`getStarred2`/`createPlaylist`/`updatePlaylist`/`deletePlaylist`/`getSimilarSongs`/`scrobble` 在 API 接口与仓库中**完全不存在**。
- 修复: 按 spec 签名与校验矩阵补齐 Retrofit 端点 + 仓库方法。

### H6 [lifecycle] 视频 ExoPlayer 忽略音频焦点与"音频变噪"
- LOC: `playback/VideoPlaybackEngine.kt:61`
- `ExoPlayer.Builder(appContext).build()` 未设 `AudioAttributes`、未 `setHandleAudioBecomingNoisy(true)`（对比 `MusicPlaybackService.kt:93-100` 有）。拔耳机视频音频继续外放，且抢占其它 app 焦点。
- 修复: 配 `AudioAttributes`(USAGE_MEDIA, AUDIO_CONTENT_TYPE_MOVIE, `handleAudioFocus=true`) + `setHandleAudioBecomingNoisy(true)`。

### H7 [architecture] 单一共享 ExoPlayer 同时服务音乐与有声书
- LOC: `playback/MusicPlaybackService.kt:103`；`AudiobookPlaybackEngine.kt:54-58`、`MusicPlaybackEngine.kt:123-127`
- 两个引擎连同一个 `MusicPlaybackService` 会话（仅一个 `ExoPlayer`），互斥仅靠 `MainActivity` 的 UI 编排（`playbackEngine.stop()` 后再 `audiobookEngine.play()`）；任一引擎的 `setMediaItems` 会静默覆盖另一域播放列表。
- 修复: 给 service 两个 player/两个 session id 按域区分，或在引擎层强制单域所有权（token/lease）。

### H8 [error-handling] 三引擎 onPlayerError 吞异常无 Log.e
- LOC: `MusicPlaybackEngine.kt:174-184`、`AudiobookPlaybackEngine.kt:84-93`、`VideoPlaybackEngine.kt:80-89`
- 三引擎都把 `error.localizedMessage ?: error.errorCodeName` 推入 state 但从不记录 throwable。`logging-guidelines.md` 要求 `Log.e` 用于"操作失败且异常对象对调试有用"。全 `playback/` 零 `Log.e`。
- 修复: 各 `onPlayerError` 加 `Log.e("<Tag>", "Playback failed", error)`，并在 spec 注册播放子系统 tag。

### H9 [error-handling] ABS sync/close 缺类型化 catch
- LOC: `data/AudiobookShelfRepository.kt:214-258`
- `syncProgress`/`closeSession`/`syncAndCloseSession` 无 catch-rethrow；原始 `IOException` 直达播放引擎而非 `AudiobookShelfApiException`，有未处理崩溃风险。
- 修复: 包 `catch (AudiobookShelfApiException){throw e} catch (Exception){throw AudiobookShelfApiException(...)}` 或走类型化请求 helper。

### H10 [error-handling] ABS bearerToken 登录未捕获 EOFException
- LOC: `data/AudiobookShelfRepository.kt:80`
- `bearerToken()` 调 `api.login(...)` 未包 `EOFException`，空响应体抛未类型化 `EOFException` 而非 `AudiobookShelfApiException(Kind.API/AUTH)`，破坏类型化错误契约。
- 修复: 与 `requireResponseBody` 同模式包 `EOFException`。

### H11 [correctness] playAlbum 缺版本守卫 → 换号后播错账号歌曲
- LOC: `ui/MusicScreenV2.kt:350-373`
- `playAlbum` 无 `musicConfigStateVersion` 守卫，`resetMusicStateAfterConfigChange()` 不重置 `loadingAlbumId`，切换账户时进行中的请求会在配置变更后用**旧账户**歌曲回调 `onSongSelected`。（`openAlbumDetail`/`openArtistDetail`/`openPlaylistDetail` 都有版本守卫，唯 `playAlbum` 漏。）
- 修复: `playAlbum` 加 `requestVersion` 守卫；配置重置时清 `loadingAlbumId`。

### H12 [compose-perf] 有声书章节每 tick 重新排序
- LOC: `ui/AudiobookPlayerScreen.kt:151,453-455`
- `resolveCurrentAudiobookChapter` 在组合中每秒位置更新时被调用，每次 `chapters.sortedBy {...}.lastOrNull{...}` 做 O(n log n) 重算，无 `remember`。
- 修复: `val sorted = remember(chapters){ chapters.sortedBy { it.startSeconds } }`，再派生当前章节。

### H13 [architecture] 巨型 UI 文件
- LOC: `ui/MusicScreenV2.kt`（2092 行）、`ui/VideoScreen.kt`（1246 行）
- MusicScreenV2: `LazyColumn` 内 `when(libraryPage){ 9 个分支 }` + 8+ 私有子组件 + 所有编排 suspend。VideoScreen: 浏览网格/详情/聚焦/剧集/控件/缩略图/骨架/纯辅助全在一个文件。
- 修复: 按 `MusicLibraryPage.*` 分支拆独立 Composable；列表行/头部/排序控件移 `MusicBrowseComponents.kt`；状态持有者移 `MusicViewModel`/`rememberMusicScreenState()`。VideoScreen 拆 `VideoDetailScreen.kt`+`VideoBrowseComponents.kt`+`VideoScreenLogic.kt`。

### H14 [reuse] 6 个近似 MetaChip Composable 重复
- LOC: `MusicHomeSections.kt:629`、`AudiobookScreen.kt:734`、`AudiobookPlayerScreen.kt:347`、`MusicPlayerScreen.kt:577&253`、`VideoScreen.kt:836`
- `MusicMetaChip`/`AudiobookMetaChip`/`AudiobookPlayerMetaChip`/`PlayerMetaChip`/`PlayerStatusChip`/`VideoDetailMetaChip` 均为 `Surface { Text(padding, 11-12sp, rounded) }`，违反 spec "Shared Compose components must be internal"。
- 修复: 共享文件定义 1-2 个 `internal` MetaChip 变体（plain/colored-tone）。

### H15 [tests] ConfigRepository 零测试
- LOC: `data/ConfigRepository.kt`（无对应测试）
- 唯一持有全部持久化凭据、`toVideoServerType` 解析、`lastAudiobookItemId` 的类零测试，键映射/默认回退/就绪回归无防护。
- 修复: 加 `ConfigRepositoryTest`（Robolectric/假 DataStore），覆盖键往返、`toVideoServerType` 各枚举/null/blank、`lastAudiobookItemId` blank 合并。

---

## 🟡 Medium（按主题分组，~34）

### M-安全
- **令牌嵌入媒体 URL 落盘**: `EmbyRepository.kt:264,276`、`NavidromeRepository.kt:87`、`AudiobookShelfRepository.kt:337` — `api_key=`/`t,s=`/`token=` 嵌入 URL 交予 ExoPlayer/Coil，其磁盘缓存会跨会话持久化令牌。修复: 对 authed URL 关磁盘缓存或本地代理注入令牌。

### M-架构/重复
- **MainActivity 内重复**: `closeAudiobookPlayback`(:396-426) 与 `closeAudiobookPlaybackAfterSync`(:428-462) 近重复；有声书同步循环(:516-550) 与视频同步循环(:552-586) 结构同构。修复: 抽 `closeAudiobook(reopenOnFailure)` 与泛型 `periodicProgressSyncer`。
- **MusicPlaybackService 命名误导**: `playback/MusicPlaybackService.kt:27`（亦服务有声书）。修复: 改名 `MediaPlaybackService` 或按 H7 拆域。

### M-播放/服务
- **视频同步吞失败**: `MainActivity.kt:576-584` `runCatching{syncPlaybackProgress}.onSuccess{...}` 无 `onFailure`。对比有声书 :544-548 有上报。修复: 加 `onFailure{Log.e/上报}`。
- **closeVideoPlayback 吞最终进度保存错误**: `MainActivity.kt:476-480`。修复: `onFailure{Log.e}`。
- **SimpleCache 构造未守**: `MusicPlaybackService.kt:48-55` — 锁/损坏 index 时 `IOException` 抛出，`onCreate` 中断、`mediaSession` 不建。修复: try/catch，失败则降级无缓存。
- **audioSessionId 不随重建刷新**: `MusicPlaybackService.kt:32-34,108` — `MusicEqualizerSheet` 读陈旧值。修复: `onDestroy` 重置 0 或暴露 `StateFlow`。
- **togglePlayPause 断连时从 0 重播**: `MusicPlaybackEngine.kt:411-418` — `pendingSong` 不带位置，重连后 `seekTo(0)`。修复: 随 `pendingSong` 存位置或此分支 no-op。
- **MediaSession 无 Callback 校验调用方**: `MusicPlaybackService.kt:103-106`（service 已 exported）。修复: 设 `MediaSession.Callback` 校验 `controllerInfo.packageName`。
- **共享 player 缓存失效不一致**: `MusicPlaybackEngine.kt:315-319` `playQueue` 未置 `cachedTimelineGeneration=-1`（`play` 在 :251 有）。修复: 同步置 -1。

### M-ABS 仓库
- **DTO 非空字段 NPE**: `AudiobookShelfApi.kt:60-65,93,162-177` — `media`/`metadata`/session 顶层字段非空，Gson 对缺省赋 null 致 `toDetail()` NPE。修复: 改 `? = null` + 仓库校验。
- **SubsonicError 字段非空**: `NavidromeApi.kt:42-45` — `code: Int`/`message: String` 非空，缺省时 `body.response.error?.let{"[${it.code}] ${it.message}"}` NPE。修复: 改可空 + null-safe 格式化。
- **syncAndCloseSession 非原子**: `AudiobookShelfRepository.kt:255-258` — `syncProgress` 抛则 `closeSession` 被跳过，会话孤儿。修复: try/finally 保证 close。
- **bearerToken 无 401 重认证**: `AudiobookShelfRepository.kt:77-103` — 缓存永不过期，令牌过期后每次调用 `Kind.HTTP` 失败。修复: 观测 401 清缓存重试一次。
- **toAbsoluteAudioUrl 未编码 token**: `AudiobookShelfRepository.kt:343-344` — string 拼接 `token=$token` 未 URL-encode。修复: `Uri.encode(token)` 或 `HttpUrl.Builder`。

### M-下载/缓存/历史
- **renameTo 忽略返回值**: `MusicDownloadManager.kt:128-131` — 重命名失败仍标 DOWNLOADED，真实文件留 `.tmp`，重启 `restoreDownloadState()` 丢失。修复: 检查布尔或 copy-then-delete。
- **失败路径未关 response**: `MusicDownloadManager.kt:89-97` — `!response.isSuccessful`/`body==null` 早返回未 `close()`，泄漏 OkHttp 连接。修复: 早返回前 `response.close()`。
- **下载去重非原子**: `MusicDownloadManager.kt:66` — check-then-launch 竞态可双开。修复: `putIfAbsent` 或同步。
- **扩展名映射错误**: `MusicDownloadManager.kt:219-230` — `audio/m4b`、`audio/mp4` 落 else→`mp3`。修复: 加 `m4b`/`mp4` 分支。
- **PlayHistory 非原子 load/save**: `PlayHistoryRepository.kt:33-44` — 并发 `recordPlay` 互相覆盖。修复: `context.dataStore.edit{}` 内 read-modify-write。
- **空库不缓存**: `NavidromeMusicCacheRepository.kt:33` — `load()` 全空返 null，合法空库每次联网刷新。修复: 按 config 缓存空但新鲜结果。
- **MusicLyrics copy() 可解同步**: `MusicLyrics.kt:9-10` — `syncedLines`/`plainLines` 为构造参数，`copy()` 可致派生字段失同步。修复: 改计算 `val`。

### M-配置持久化
- **toVideoServerType 未知→EMBY**: `ConfigRepository.kt:93-97` — PLEX/WEBDAV/未知存值回读成 EMBY，用户看到错配的"未就绪"。修复: 存枚举名，未知值保 nullable 或告警。
- **无 schema 版本**: `ConfigRepository.kt:12` — 改/删 key 留孤儿值。修复: `config_schema_version` 或文档化迁移。

### M-Compose 性能
- **visiblePosition 顶层读取**: `MusicPlayerScreen.kt:78,159`、`AudiobookPlayerScreen.kt:63` — 位置在屏顶读 → 整个播放器屏每 tick 重组（顶栏/封面无需位置）。修复: 以 `State<Int>`/lambda 下传，仅滑块/状态行读。
- **剧集 eager 组合**: `VideoScreen.kt:749` — `episodes.forEach{VideoEpisodeRow}` 在 `verticalScroll` 内全量组合。修复: 固定高 `LazyColumn` 或单 `LazyColumn` 跨项。
- **状态 pill 字符串相等**: `VideoPlayerScreen.kt:285` — `if(text=="Buffering")` 选色，文案改动即崩外观。修复: 传类型化 `VideoStatusTone` 枚举。
- **保存与 LaunchedEffect 竞态**: `MusicScreenV2.kt:616`、`AudiobookScreen.kt:307`、`VideoScreen.kt:281` — 手动 `refreshXxx` 紧跟 `LaunchedEffect(savedConfig)`，重置递增版本可中断进行中保存、双取闪烁。修复: 保存只持久化、让 effect 刷新；或版本协调。
- **均衡器 LazyRow 无 key**: `MusicEqualizerSheet.kt:141` — 预设改/增时组合不稳。修复: `itemsIndexed(presetNames, key={i,_->i})`。
- **均衡器吞异常**: `MusicEqualizerSheet.kt:62,162,231` — 空 `catch(_:Exception){}` 静默吞 Equalizer 构造/预设/频段错误。修复: 至少 Log 或频段失败内联提示。
- **频段滑块全行重组**: `MusicEqualizerSheet.kt:196-251` — `bandLevels.toMutableList()` 致全行重组。修复: 每频段本地态（通常 ≤5 频段可接受）。

### M-UI 重用
- **封面图框 ×7+**: `AudiobookScreen.kt:699`、`MusicHomeSections.kt:574`、`VideoScreen.kt:960`、`MusicScreenV2.kt:1426/1933/2004`、`PlaybackDock.kt:185` — "渐变 Box+AsyncImage+后备图标"复制。修复: 抽 `internal CoverArt(shape,fallbackGlyph,size)`。
- **返回/关闭按钮 ×4**: `AudiobookScreen.kt:461`、`MusicScreenV2.kt:1566`、`VideoScreen.kt:621`、`AudiobookPlayerScreen.kt:316` — 抽 `internal ScreenBackButton(glyph,onClick)`。
- **主操作按钮 ×4**: `MusicScreenV2.kt:2054&1542`、`AudiobookScreen.kt:618`、`VideoScreen.kt:856` — 抽 `internal PrimaryActionButton`。
- **ConfigCards 重复认证字段**: `ConfigCards.kt:129-150` — EMBY/PLEX 与 WEBDAV 同渲染 user+pass，仅 API-key 不同。修复: 抽共享字段，仅类型字段动画。

### M-UI 正确性
- **全屏无 BackHandler**: `VideoPlayerScreen.kt`（全屏时） — 系统返回在全屏可能直接退出 app（取决于调用者）。修复: `isFullscreen` 时声明先 `onToggleFullscreen` 的 BackHandler。
- **专辑加载守卫不一致**: `MusicScreenV2.kt:375-399` vs `350-358` — `openAlbumDetail` 设 `isLoadingAlbumDetail` 不设 `loadingAlbumId`，`playAlbum` 反之。修复: 统一。

### M-测试
- **纯逻辑 sortMusicSongs 无测试**: `MusicScreenV2.kt:1730-1756`（6 排序模式）。修复: 单元测试覆盖各模式+平局。
- **零插桩 Compose UI 测试**: 全 `ui/` 无 `createAndroidComposeRule`，重组稳定性/BackHandler 优先级未测。修复: 至少覆盖 BackHandler 顺序 + 各屏 loading/error/empty。
- **NavidromeMusicCacheRepository 无测试**: spec 要求 cache-key 正确性 + schema 失效。修复: 断言 `cacheKey()` 含 config 身份+版本、bumped 版本失效旧缓存。
- **PlayHistoryRepository 无测试**: spec 要求 malformed-JSON→empty + replay 前移/计数。修复: 覆盖 malformed、replay 递增、上限、顺序。
- **MusicMediaItems 往返无测试**: `playback/MusicMediaItems.kt:19-64` — extras-bundle key 改动会静默崩队列重建。修复: `MusicMediaItemsTest` 覆盖 id/title/artist/album/duration/coverArt/streamUrl/created 往返 + `localFilePath→file://` 覆盖 + `mediaId` 空回退。
- **MusicLyrics 无测试**: spec 要求 structured/null/blank 行 + LRC offset 解析。修复: 仓库级 MockWebServer 测试 `NavidromeRepository.getLyrics`（最高风险未测）。
- **NavidromeAuth 无测试**: `NavidromeAuth.kt:35`（MD5 token+salt、auth query、http 默认）— 与已测 `AudiobookShelfAuth` 不一致。修复: `NavidromeAuthTest`。

---

## ⚪ Low（精简清单，~22）

- 死代码: `MusicHomeSections.kt:192-263,266-314`（`AlbumShelfCard` 空 onClick、`ArtistRoundCard` 未调用）；`theme/Color.kt:11,21`（`DarkAccent`/`LightAccent` 未引用）；`api/AudiobookShelfApi.kt:20`（`userDefaultLibraryId` 未读）。
- 格式化重复: `VideoScreen.kt:1174` vs `MusicFormatters.kt:3`（`formatDuration` M:SS 与 `formatVideoDuration` Xh Ym 并存，视频 slider 用前者、卡片用后者）。
- minor 重组: `MusicPlayerScreen.kt:163,175-180`（toggle/滑块 lambda 每 tick 重建）；`VideoPlayerScreen.kt:135`、`VideoScreen.kt:506/1062`（`metaText()` 每组合 `buildList` 分配）；`MusicHomeSections.kt:122`（`AsyncImage` 应 `matchParentSize()`）；`MusicQueueSheet.kt:219`（硬编码 64dp 行高拖拽映射）。
- minor 正确性: `MusicScreenV2.kt:266`（`albums` 变量遮蔽外层）；`VideoScreen.kt:698,769`（禁用按钮无"无播放地址"提示）；`AudiobookBookmarkRepository.kt:40`（blank itemId 返回全表与 happy path 不一致）。
- minor 测试: `MusicFormatters.kt:3`/`AudiobookPlayerScreen.kt:437`/`VideoScreen.kt:1174`/`MusicScreenV2.kt:2078`（纯格式化函数无测试）；`VideoServerAuth.kt:3`、`AudiobookShelfAuthTest.kt:7`（仅 URL 归一化、`isReadyForAudiobookSync` 未测）。
- 日志: `NavidromeRepository.kt:274`（`Log.d` 写 server origin）；`EmbyRepository`/`AudiobookShelfRepository` 失败边界无 `Log.e`（仅 NavidromeRepo 有，不一致）；`ui/MusicHomeSections.kt` 无 `MusicArtwork` tag 日志。
- 播放 minor: `MusicPlaybackEngine.kt:322-330`（`seekToNext` 同步发陈旧位置）；`AudiobookPlaybackEngine.kt:129-137`（单 `pendingSession` 覆盖）；`MusicPlaybackService.kt:58`（`cache!!` 非空断言）。

---

## 测试覆盖速查（高风险未测）

| 文件 | 已测? | 风险 |
|---|:---:|---|
| `MainActivity` `MainScreen`（803 行巨石） | 仅 4 个 internal helper 测 | **高** |
| `data/ConfigRepository.kt` | 否 | **高** |
| `playback/MusicPlaybackService.kt` | 否 | **高** |
| `data/NavidromeAuth.kt` | 否 | 高（`AudiobookShelfAuth` 已测，不一致） |
| `data/VideoServerAuth.kt` | 否 | 中 |
| `data/NavidromeMusicCacheRepository.kt` | 否 | 中 |
| `data/PlayHistoryRepository.kt` | 否 | 中 |
| `playback/MusicMediaItems.kt` | 否 | 中 |
| `ui/MusicHomeSections.kt`/`AudiobookPlayerScreen.kt`/`ConfigCards.kt`/`MusicQueueSheet.kt`/`MusicEqualizerSheet.kt` | 否 | 低-中 |
| `androidTest/` 全空 | — | 中（无插桩 UI 测试） |

深测代表: `NavidromeRepositoryTest`（MockWebServer ~30 用例，null/缺数组边界、歌词偏移）。

---

## 建议任务拆分

按"内聚修复主题"而非"单条 finding"组织，建议优先级如下。每条任务建好后走 Trellis 流程（brainstorm prd → jsonl → start → 实现）。

| # | 任务主题 | 覆盖 findings | 优先级 |
|---|---|---|---|
| T1 | 凭据安全加固（加密存储 + network_security_config + 令牌不入盘缓存） | C1, H1, M-令牌落盘 | P0 |
| T2 | Navidrome 契约补齐（star/unstar/getStarled2/playlist CRUD/相似/scrobble） | H5 | P0 |
| T3 | 播放健壮性（MediaController 断连恢复 + 视频音频焦点 + 共享 player 所有权 + 播放错误日志 + service 生命周期） | C2, H6, H7, H8, M-播放/服务 | P0 |
| T4 | ViewModel + 领域模型架构（抽 VM、领域模型、破 MainActivity 巨石、破 ui→api、破 MainActivity↔Service 环） | H2, H3, H4, H13, M-MainActivity 重复 | P1 |
| T5 | ABS 续听 + DTO null 安全 + 类型化错误 | C4, H9, H10, M-ABS 仓库 | P1 |
| T6 | Compose 性能快赢（searchJob 修复 + 章节预排序 + 位置隔离 + 剧集懒加载 + 均衡器 key/catch） | C3, H12, M-Compose 性能 | P1 |
| T7 | UI 拆分 + 共享组件提取（MetaChip/CoverArt/BackButton/PrimaryActionButton） | H14, M-UI 重用 | P2 |
| T8 | MusicDownloadManager 修复（临时文件清理 + renameTo + response 关闭 + 去重 + 扩展名） | C5, M-下载 | P2 |
| T9 | 配置持久化正确性 + 关键单元测试补齐（ConfigRepository/NavidromeAuth/VideoServerAuth/MusicMediaItems/PlayHistory/NavidromeMusicCache/sortMusicSongs/插桩 UI） | M-配置持久化, H15, M-测试 | P2 |
