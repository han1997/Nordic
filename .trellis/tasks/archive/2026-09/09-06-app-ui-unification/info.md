# 全页面 UI 统一进度

## 本批已实现

- 15 槽原生字体层级、主题语义前景、浅色次要文字对比度修正。
- 页头/返回/搜索统一 48dp 实际操作区域，窄屏与大字体溢出操作进入更多菜单。
- 有声书/视频库及视频类型共用选择控件。
- 音乐导航/歌曲排序/专辑排序共用按真实文字测量的分段控件。
- 音乐/视频搜索共用输入/清除/IME 行为；栏目头与详情动作支持更清晰语义和自然增高。
- 更新共享 UI 合同、设计字体、README 和 CHANGELOG。

## 自动验证

最后一次 Gradle 组合检查 BUILD SUCCESSFUL，1m 6s：

```powershell
.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest --tests 'com.nordic.mediahub.ui.*' --tests 'com.nordic.mediahub.playback.*' --tests 'com.nordic.mediahub.MainActivityTest' --tests 'com.nordic.mediahub.data.EmbyRepositoryTest' :app:lintDebug :app:assembleDebug
```

- 307 用例；失败 0、错误 0、跳过 0。
- lint 0 error/fatal，22 warning；部分既有 ModifierParameter 警告仍需在后续对应组件整理时处理。
- 对比度测试真实抓到浅色 surfaceVariant 上次要文字仅约 4.37:1，修正为 #666676 后通过；没有调低 4.5:1 阈值。
- 这些是当前共享基础/相关逻辑验证，不代表全部页面完成，更不代表真机渲染通过。

## 用户验证安排

- 用户决定直接真机调试；已确认没有残留 SDK 镜像下载进程，镜像未安装。
- 不主动操作或截取用户手机，不创建模拟器。
- APK 位于 app/build/outputs/apk/debug/app-debug.apk；真机重点见 manual-checklist.md。

## 下一批与未完成项

- 音乐专辑/歌手/歌单详情和所有列表/封面/菜单；音乐播放器和附属面板。
- 有声书/视频详情与所有播放面板，配置表单，全局 Dock，公共空/错/加载状态。
- 完整浅/深主题、小屏/横屏/大字体和交互验收继续追踪 research/ui-coverage.md。
- 原有全量配置测试等待尚未解决，不宣称全量单元测试通过。
- 目标和任务保持 active / in_progress，未提交、未归档；用户原有 gradle.properties 保持不动。

## 第二批：音乐浏览 / 集合详情

### 已落地

- MusicCollectionHeader 统一首页集合概览、专辑、歌手、歌单详情；根据宽度/字体并列或堆叠，metadata 换行。
- MusicLibraryRow 统一四类列表的封面、文本、点击反馈与时长排布；通过 CoverArt 统一缺图/失败兜底。
- 横向卡片适配字体，中文单位与未知数据文案统一；导航不重复集合长名称。
- 加载保持集合概览，计数区分未加载/失败/真实空结果，已有错误时不再显示矛盾空态。
- 歌单长简介可展开；管理动作至少 48dp，删除仍先确认；名称按钮/键盘 Done 沿用原接口与忙碌/空白守卫。

### 最终检查

```powershell
.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest --tests 'com.nordic.mediahub.ui.*' --tests 'com.nordic.mediahub.playback.*' --tests 'com.nordic.mediahub.MainActivityTest' --tests 'com.nordic.mediahub.data.EmbyRepositoryTest' --tests 'com.nordic.mediahub.data.NavidromeRepositoryTest' :app:lintDebug :app:assembleDebug
```

- 最后一次 BUILD SUCCESSFUL，1 分钟；19 个套件、369 个用例，失败 0、错误 0、跳过 0。
- lint 0 error/fatal、22 warning；git diff --check 通过。
- 新增 MusicLibraryLayoutTest 七项回归；本批扩大检查至 NavidromeRepositoryTest，而不是以更多测试数量声称全应用验收。
- MusicHomeSections/MusicBrowseComponents/MusicScreenV2Pages 的旧英文缺省文案/单位和独立图片加载分支已核查收敛。
- APK：app/build/outputs/apk/debug/app-debug.apk。

### 下一步与边界

- 用户真机验证见 manual-checklist.md 第二批条目；本会话没有操作或截图手机。
- 音乐播放器、歌词/队列/倍速/均衡器面板，以及有声书/视频详情与面板、配置、Dock、完整状态矩阵继续推进。
- 全量配置测试等待问题仍未解决；全部页面真机验收仍未完成，目标维持 active / in_progress。
- 未提交、未归档；用户原有 gradle.properties 未改动。
