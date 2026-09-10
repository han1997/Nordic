# 音乐歌词显示与加载合同

## 1. 范围 / 触发条件

适用于 Navidrome 歌词解析、MusicLyrics 领域逻辑、MusicLyricsController、MusicLyricsDisplay 和播放器手势。保留当前视觉、服务器协议与歌词来源，不添加逐字、双语选择、点词跳播或持久歌词缓存。

## 2. 入口与签名

```kotlin
suspend fun NavidromeRepository.getLyrics(song: NavidromeSong): MusicLyrics?
internal fun normalizeMusicLyrics(lines: List<MusicLyricsLine>, synced: Boolean): MusicLyrics?
internal fun MusicLyrics.displayCues(): List<MusicLyricsLine>
internal fun resolveActiveMusicLyricIndex(cues: List<MusicLyricsLine>, positionMillis: Long): Int?
internal fun lyricAlignmentDelta(itemOffset: Int, itemSize: Int, viewportStart: Int, viewportEnd: Int): Float
```

- `MusicLyricsUiState` 是单一状态：Idle / Loading / Content / Empty / Error，每个歌曲状态带 songId，Content 另带 requestId。Error 明确 message/canRetry。
- `MusicPlaybackViewModel.lyricsState`、`showLyrics`、`lyricsSeekRevision` 对接 MusicPlayerLayer；`toggleLyricsDisplay()`、`retryLyrics()` 与显式 seek 动作由 ViewModel 驱动。
- `MusicLyricsController<Source>` 接收 CoroutineScope 和挂起加载函数，独立于 Android 运行环境，拥有 select/retry/close 与会话内视图选择；选择、重试和结果发布都在同一作用域线程（ViewModel 主线程）执行。
- `MusicLyricsFollowController` 处理手动开始/结束、播放状态、立即返回和销毁；`lyricScrollActivity` 区分 User / Automatic / Idle。

## 3. 实现合同

### 内容与时间轴

- 保留原毫秒单位、LRC metadata 过滤、正 offset 提前（减法）和负 offset 延后；调整用 Long 运算，结果钳制到 0..Int.MAX_VALUE，非有限的结构化数值不作为有效时间。
- 普通歌词保留所有非空正文及重复段落。同步歌词稳定排序，仅去重相同时间/相同文本，未计时正文置于末尾且不参与同步。
- displayCues 将同时间的不同文本合成一个换行句组；活跃索引对规范化后的时间轴二分查找，首句之前为 null，负位置按 0 处理。
- 普通和同步内容都用完整 LazyColumn，正文不取前 N 行，不设置两行省略；长句自然换行，当前句组共同高亮。

### 加载与视图记忆

- 随当前歌曲加载；请求按仓库身份、songId、artist、title 去重。收藏等无关 Song 字段变化不重取；配置切换即使 songId 相同也重取。
- 新请求立即取消旧 Job 并发布 Loading；请求序号和取消状态双重检查，非协作的迟到结果也不能覆盖新歌。UI 用 songId 再隔离切歌瞬间的旧 Content。
- 歌曲 ID 优先，失败/无可用歌词才按歌手标题兜底；取消立即重抛，不做兜底。最终请求失败为 Error，成功无内容为 Empty，未配置服务器为不可重试 Error；错误不得中断音频播放。
- 重试只作用于当前可重试 Error，Loading 期间重复点击不产生新请求。
- 歌词/封面选择属于 ViewModel 会话内状态，切歌与收起重开保留，进程重建默认封面；不写入 DataStore/SavedStateHandle。重开歌词重新跟随当前句，不恢复旧浏览位置。

### 跟随与手势

- 播放中停止用户拖动及惯性滚动后等待 2000ms；1999ms 不恢复。再次操作取消旧倒计时并从新的停止时刻开始。
- 暂停取消倒计时并保留浏览，继续播放恢复跟随；若仍在拖动，不抢占，等停止后按 2 秒规则恢复。回到当前按钮可在暂停时立即恢复定位。
- 程序滚动不能进入手动模式；真实拖动优先于自动滚动标记。歌词组件销毁取消恢复 Job。
- 自动跟随使用 LazyListLayoutInfo 实测偏移/行高/视口，首尾留白；大于视口的句组顶部对齐。正常换句平滑，初次显示/倒退/显式 seek/远距离定位直接到目标，不穿越整首歌词。
- 跟随按钮预留按真实字号和宽度测得的空间，出现/消失不改变视口；滚动条保留绘制阶段实现，不新增拖动功能。
- 保留单击切换、左右半区双击快退/快进；按钮消耗自己的点击。歌词区域（包括短内容与空态）不能触发父播放器下滑关闭，其他区域仍保留关闭手势。
- 100ms positionMillis 只在同步歌词叶子收集，用 derivedStateOf 限制活跃句更新；普通歌词不订阅高频进度。颜色可动画，字号/行高不随高亮缩放。

## 4. 验证与错误矩阵

| 输入 / 操作 | 预期 |
|---|---|
| 两个同时间不同文本 / 同时间同文本 | 一个多行高亮句组 / 去重 |
| 只有普通歌词、30 行以上、单句超两行 | 全部可滚动阅读，不截断 |
| 标记 synced 但没有有效时间、后续候选有时间 | 优先后续真正同步的候选 |
| 首句 10500ms，进度 10499 / 10500 | 无高亮 / 第一组高亮 |
| 拖动或惯性仍进行 / 停止后 1999 / 2000ms | 不恢复 / 不恢复 / 恢复 |
| 浏览时暂停 / 恢复播放但仍拖动 | 不自动拉回 / 不抢手势 |
| 快速换歌、同 ID 切账号、旧请求迟到 | 新 Loading/Content 独立，旧响应不覆盖 |
| 成功空响应 / 最终 HTTP 错误 / 取消 | Empty / 可重试 Error / 传播取消且无兜底 |
| 未配置服务 / Loading 时重复重试 | 无重试动作 / 不重复请求 |

## 5. 正反案例

- 好：首句/末句按实际高度居中，长句超过视口时从顶端展示；大字体按钮不被固定 48dp 高度截断。
- 基础：普通歌词始终完整可读，暂停翻看不会被定时器打断。
- 坏：按整个 Song 判断是否重取歌词；顺序等待旧歌曲完成；把网络错误吞成 null；把所有 isScrollInProgress 都视为用户滚动；测试仍只覆盖已不用的窗口裁剪辅助函数。

## 6. 测试与验收

- MusicLyricsTest：完整文本、稳定排序、句组/去重、未计时正文、空内容、毫秒边界、前后 seek。
- MusicLyricsControllerTest：原子状态、取消与迟到、配置隔离、元数据去重、重试去重、会话视图记忆、关闭后迟到结果。
- MusicLyricsFollowControllerTest：虚拟时钟 1999/2000ms、拖动/惯性、重复停止、暂停/恢复、立即返回、销毁、滚动分类与几何偏移。
- NavidromeRepositoryTest：保留全部 nullable/offset/LRC 用例，增加有效候选选择、兜底成功、最终失败、取消不兜底及溢出保护。
- MusicPlayerScreenTest：保留进度条/模式标签测试，增加歌曲身份隔离及歌词区域拖动防误关闭；不恢复旧的 selectVisibleLyricLines 窗口测试。
- 全量编译、单测、Lint、debug/release 打包、签名与版本检查。真机另验浅/深色、窄屏/大字体、长句、手势、2 秒恢复、暂停浏览、收起重开；自动检查不能宣称真机交互通过。

## 7. 错误与正确写法

```kotlin
// 错误：取消、请求失败和真正无歌词无法区分。
runCatching { repo.getLyrics(song) }.getOrNull()

// 正确：取消传播，不伪装成空结果；Controller 再按请求序号发布 Error/Empty/Content。
val lyrics = try {
    repo.getLyrics(song)
} catch (cancelled: CancellationException) {
    throw cancelled
}
coroutineContext.ensureActive()
```

移除 Kotlin 导入不能只统计文本出现次数：`getValue`/`setValue` 被 `by` 隐式使用。提取 Compose 组件后应保留委托运算符导入并通过编译确认。
