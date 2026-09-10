# 歌词实现与复盘

## 数据流

Navidrome nullable DTO → 原有 offset/LRC 解析 → normalizeMusicLyrics（时间戳排序、定时文本去重）→ MusicLyricsController（仓库身份+songId+artist+title，取消Job及请求序号保护）→ 单一 MusicLyricsUiState → MusicPlayerLayer → songId 隔离 → MusicLyricsDisplay/displayCues → 同步叶子采样与二分活跃组 → 跟随状态机 → 实测 LazyColumn 对齐。

## 用户决策落实

- 2000ms 从拖动与惯性滚动完全结束计算；暂停时取消恢复，继续播放才恢复，用户仍拖动时不抢。
- showLyrics 留在 ViewModel 生命周期内，关闭播放器不销毁该状态；新会话默认 false，不写持久设置。
- 普通歌词不采集高频位置，不再取 5/7 行；所有正文自然换行，同时间组共同高亮。
- 视口前后 padding 和 item offset 使用 LazyListLayoutInfo 坐标；高于视口的组顶端对齐，其他组居中。跟随按钮预留实测字体高度，避免出现时改变视口。
- 保留父级单击切换/双击 seek；对短歌词、空态和加载态也用区域边界阻止下滑误关播放器。

## 根因类别与防复发

- B（跨层合同）+ D（测试缺口）：旧 onEach 串行请求、三个独立 StateFlow 和整个 Song 去重容易产生串歌/重复请求；现在将加载控制拆为纯 Kotlin 可测状态，并以取消及请求序号双重保护。
- E（隐含假设）：isScrollInProgress 不是用户操作的同义词；自动滚动需独立标记，真实拖动优先，分类和 1999/2000ms 边界均用虚拟时钟测试。
- 测试曾只覆盖生产路径不再使用的 selectVisibleLyricLines；已删除旧窗口辅助函数与对应测试，替换真实规范化/状态/几何/身份隔离用例。
- 集成时曾用文本计数清理导入，误删 by 所需的 Compose getValue/setValue，引发编译错误；已恢复，后续全量验证通过，规范记录不得按词频判断 Kotlin 隐式导入。
- 没有 spec 模板副本需要同步。

## 真机边界

执行期间检测到已连接 OPPO 设备；本轮未覆盖安装、启动/操控手机应用、卸载或清除用户数据。自动化测试不等同于真机手势和视觉验收。
