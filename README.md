# Nordic Media Hub

统一的多媒体管理客户端，面向音乐（Navidrome）、有声书（AudiobookShelf）、视频（当前为 Emby）三类自托管媒体服务。

## 特性

- 🎵 **音乐** - 支持 Navidrome 服务器
- 📚 **有声书** - 支持 AudiobookShelf 书库同步、详情浏览、播放会话和进度同步
- 📺 **视频** - 支持 Emby 媒体库浏览、海报、沉浸播放、播放器内选集、字幕/音轨选择、画中画和进度同步；新增 WebDAV 文件夹点播与本机续播；Plex 尚未接入
- 🌓 **主题切换** - 日间/深色模式自由切换
- 🔐 **完整认证** - 支持用户名、密码、API Key

## 界面操作

- 音乐播放页点击封面/歌词可切换显示，左右半区双击可快退/快进。普通歌词可完整滚动，长句自动换行；同步歌词按当前句组居中跟随。
- 播放中手动翻看歌词后，停止拖动及惯性滚动 2 秒恢复跟随，也可点击“回到当前”；暂停时保留浏览位置，继续播放再恢复。歌词/封面选择在本次使用期间保留，切歌和收起重开不重置，应用重启默认封面。加载失败可重试，不再与“暂无歌词”混淆。
- 页面顶部的返回、搜索及其他操作采用统一触控尺寸；窄屏或大字体下，部分操作会进入“更多页面操作”菜单，功能并未移除。
- 音乐分段导航与排序在空间不足时横向滚动；音乐/视频搜索统一清除入口，键盘“搜索”键可收起键盘。
- 浅/深主题的共享字体和按钮文字已统一。全应用仍在按页面精细化完善，阶段性改动及验证范围见 CHANGELOG 和当前任务记录。

## 服务器配置

### Navidrome

- 服务器地址（如 `https://music.example.com`）
- 用户名
- 密码

### AudiobookShelf

- 服务器地址（如 `https://audiobook.example.com`）
- 用户名
- 密码

当前版本使用用户名/密码登录 AudiobookShelf，支持加载 audiobook library、查看条目详情、启动播放会话，并在播放过程中同步/关闭会话进度。

### 视频服务器

**Emby:**

- 服务器地址
- 用户名、密码
- API Key（可选）

视频支持 Emby 媒体库与 WebDAV 文件夹点播。Emby 使用服务器进度，WebDAV 使用本机进度；Plex 尚未接入，不作为可用服务显示。

### 视频播放器

- 非全屏锁定竖屏、全屏锁定横屏，退出播放器恢复系统方向；播放控制闲置 4 秒后隐藏。
- 点按画面唤起控制，支持后退 10 秒/前进 30 秒、拖动或点按进度条、倍速与画面比例；长按临时 2 倍速，左右侧滑动调整亮度/音量。
- “选集”按季展示已载入的同剧集数据，定位当前集并显示已看/续播状态；不会自动补取全库。电影不显示选集，无法播放的集禁用。
- 切换剧集保持全屏并保存原集进度；片尾提示仍可手动点击。新增可选「自动连播」，默认关闭，可在「设置 → 视频播放」或播放器设置中开启；完整播完后倒计时 5 秒，支持取消或立即播放，下一项有有效进度时继续播放。
- 自动连播仅在应用前台生效：Emby 使用已载入的同来源、同剧集顺序，WebDAV 使用同来源、同目录文件名自然顺序；不补取全集、不跨目录、不循环，电影与最后一项不连播。关闭下一集提示、打开面板或倒计时期间手动选集/定位/重播会取消本次连播，不改变全局开关。
- 进入后台或画中画会取消本次倒计时，返回前台不会重新计时。手势锁仅禁用画面手势，播放与取消按钮仍可操作。
- 更多、倍速和选集在宽横屏使用侧面板，其余情况使用底部面板。设置中可选择字幕/音轨，播放倍速会持久化保存；Emby 支持清晰度选择与服务器转码，WebDAV 仅原画播放；投屏尚未提供。
- 画中画默认开启，可在播放器设置中关闭并保存。支持 PiP 且系统允许的设备上，播放中按 Home/上滑返回桌面会以小窗继续播放，缓冲中也可进入；暂停或错误状态不会自动进入。
- 点小窗返回应用会继续原视频；关闭小窗或小窗内播放结束会停止并上报进度，不自动连播。关闭画中画开关、系统禁止或设备不支持 PiP 时，视频进入后台即停止。音乐、有声书的后台播放不受影响。

## 多来源与设置中心（0.1.4）

底部「配置」现为「设置」，包含媒体服务器、外观与启动、音乐播放、有声书播放、视频播放、存储与下载、隐私与数据、关于与帮助八个分类。顶部搜索可直达主题、字幕、倍速、缓存等具体选项。

- 音乐、AudiobookShelf、视频分别保存多个连接，在媒体页标题旁切换来源，不合并媒体库。首个连接自动激活，之后新增不自动切换。
- 连接表单的「测试连接」不会保存或切换账号；保存后才写入加密存储。编辑中的密码仅在内存，未保存退出会提示。
- 切换正在播放的同类来源前先确认并结束旧会话；其他媒体不受影响。历史、书签、目录缓存和下载按来源隔离。
- 旧单服务器配置自动迁为首个来源；无法确定归属的旧记录和下载可在「隐私与数据 → 待归属旧数据」手动关联。删除连接默认保留下载，不会删除服务器内容。
- 主题支持跟随系统、浅色和深色；启动页及播放偏好持久化。快进/后退可调整，字幕和音轨语言偏好在下次播放生效。
- 音乐播放器右上角操作菜单可下载当前曲目；「存储与下载」按来源查看、播放及删除下载。清图片/目录缓存不会删除下载或观看进度。

## 模块显示与设置入口（0.1.7）

- 设置移出底部导航，各媒体首页右上角固定齿轮进入；未配置、加载或错误状态也可进入。设置内「管理服务器」直达媒体服务器页，返回保留搜索、子页与原媒体，未保存的编辑会提示。
- 音乐、有声书、视频可在「设置 → 模块显示」分别显示/隐藏，默认全部显示且至少保留一个；隐藏模块会隐藏对应播放设置与搜索项，服务器、存储、隐私与模块显示入口不受影响，数据与下载任务保留。
- 两个或三个模块时底部显示媒体导航，仅一个模块时不显示导航与把手，正在播放条独立保留；设置页面不显示媒体导航。
- 隐藏正在播放/暂停/准备中的模块需确认，按现有进度关闭策略停止该模块并取消准备与视频连播，不误停其他音频；关闭或偏好提交失败保持模块可见，已停止的播放不自动重启，隐藏模块不能从下载或迟到回调重新开播。
- 启动页与上次媒体页只指向可见模块，隐藏后按音乐→有声书→视频回退；重新显示不自动切页或播放。

### WebDAV 连接与播放

1. 打开「设置 → 媒体服务器 → 添加服务器 → WebDAV」。
2. 输入完整 WebDAV 根地址，例如 `https://example.com/dav/`。AList/OpenList 通常使用 `/dav/`；NAS 和 Nextcloud 请使用其实际 WebDAV 地址与端口。
3. 填写用户名、密码；匿名访问时同时留空。可指定相对于根地址的起始目录，中文路径直接输入，不需手工编码。
4. 测试并保存，在视频页选择该来源。可浏览目录、搜索当前目录、按名称/时间/大小排序及收藏文件夹。
5. 点击视频使用内置播放器播放。同目录同名 `.srt`、`.ass`、`.vtt` 可作为字幕，例如 `电影.mkv` 对应 `电影.zh.srt`。

WebDAV 手动选集与自动连播使用开播时准备好的同目录视频，保留同目录字幕和本机续播信息；目录搜索不会截断播放队列。

WebDAV 当前支持 Basic 与匿名认证，不支持仅提供 Digest 的服务。优先使用 HTTPS；HTTP 需对该连接明确确认，不提供忽略证书校验的开关。网盘临时地址仅在播放请求中使用，跨域重定向不会携带原服务器认证。

续播和拖动要求服务器/直链支持 HTTP Range；不支持时可选择从头播放。WebDAV 不转码，视频编码、字幕效果和解码能力取决于 Media3 与设备。不提供 WebDAV 远程修改、影视刮削、视频离线下载、投屏或云同步。

## 技术栈

- Jetpack Compose - 现代化 UI 框架
- Material 3 - 设计系统
- Kotlin - 开发语言
- Coil - 图片加载
- Retrofit / OkHttp - 自托管服务 API 接入
- Media3 - 音频播放、MediaSession 和缓存基础设施

## 构建

在 Android Studio 中打开项目，点击运行按钮即可。

命令行构建使用 JDK 17，并在本机 `local.properties` 中配置 Android SDK 的 `sdk.dir`。

仅打包已签名的 Release APK：

```powershell
.\gradlew.bat :app:assembleRelease
```

需要同时执行编译、单元测试和 Lint 时：

```powershell
.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug :app:assembleRelease
```

Release 产物自动命名为 `app/build/outputs/apk/release/nordic-<versionName>.apk`。例如 `versionName` 为 `0.1.5` 时生成 `nordic-0.1.5.apk`；以后修改 `app/build.gradle.kts` 中的 `versionName`，文件名会随之更新，不需要手动重命名。Debug 仍生成 `app-debug.apk`。

Release 已签名并启用 R8 混淆与资源收缩，可用于侧载。请以本次构建的 `output-metadata.json` 中 `elements[].outputFile` 为准，避免误用输出目录中可能残留的旧 `app-release.apk` 或 `app-release-unsigned.apk`。

### 应用标识、版本与侧载签名

- 设备上的应用包名为 `fun.han1997.nordic`；Kotlin/资源 namespace 仍为 `com.nordic.mediahub`，两者有意分离。
- 包名变更后会作为新应用安装，不会覆盖旧 `com.nordic.mediahub` 应用，也不会自动迁移旧应用的数据；卸载旧应用会删除其本地数据。
- 版本从 `0.1.1`（versionCode `1`）开始；每个修改代码的工作提交将 patch 位和 versionCode 各加 1。minor/major 仅按用户明确要求增加；纯文档、任务归档和会话日志不触发版本递增。当前值以 `app/build.gradle.kts` 为准。
- 按当前侧载决策，release 复用本机 `~/.android/debug.keystore`。覆盖安装必须沿用相同证书，请妥善备份该文件；不要提交到仓库，也不要在换电脑后直接用新生成的证书替换。此方案不适用于应用商店发布，正式发布需另行规划签名和已有安装的升级方式。

构建产物的签名、包名和版本验证命令见[构建身份、版本与签名规范](.trellis/spec/backend/build-release.md)。签名校验成功不代表已完成真机安装测试，设备仍需允许对应来源的安装权限。

## 要求

- Android 8.0 (API 26) 或更高版本
- Android Studio Hedgehog 或更高版本

### 离线播放器布局验证（仅 debug）

Debug 构建包含 `VideoPlayerPreviewActivity`，使用合成画面与剧集，不连接服务器或更改观看记录：

```powershell
adb shell am start -n fun.han1997.nordic/com.nordic.mediahub.VideoPlayerPreviewActivity
adb shell am start -n fun.han1997.nordic/com.nordic.mediahub.VideoPlayerPreviewActivity --ez fullscreen true
```

可传 `--es scenario movie|unknown|buffering|error|end|empty|autoplay`（选择一个值）验证状态。`autoplay` 固定显示 5 秒连播提示，用于检查布局、取消和立即播放，不模拟真实计时。该入口不进入 release 构建；预览验证不能替代真实 Emby 播放/上报测试。


### 全页面精修样板目录（仅 Debug）

首轮样板保留现有紫青品牌，覆盖歌曲列表、专辑详情、音乐播放器／歌词／队列／倍速、模块显示与服务器表单。首轮样板方向已确认；其他页面仍需继续逐页打磨，不能把方向确认或共享组件通过测试当作整应用验收完成。

`UiCatalogActivity` 使用本地合成状态，复用正式界面组件；目录中可以切换深浅主题、字号和状态。它不连接账号、不写入设置、不启动媒体服务。封面仅来自仓库既有参考图的调试裁图，Release 不包含此入口和样例资源。

在已安装 Debug 包的专用模拟器上打开目录（示例序列号仅用于本任务 AVD，先确认对应设备）：

```powershell
adb -s emulator-5580 emu avd name
adb -s emulator-5580 shell am start -n fun.han1997.nordic/com.nordic.mediahub.UiCatalogActivity
```

可直接选择页面：`--es screen songs|album|player|lyrics|queue|speed|modules|server|server_emby|server_webdav|settings_rows`；状态使用 `--es state normal|long|empty|loading|error|no_art|disabled`，主题使用 `--ez dark true`，字体使用 `--ef font_scale 2.0`。每项选择单个值，只有适用页面会体现对应状态。

手动字号选项用于内容预览；包含 Dialog／系统栏的正式验收还必须切换模拟器系统字号，不能只传 `font_scale`。任务的 `research/run-ui-checks.py` 会设置并恢复系统字号、分辨率和密度，并封存 APK／源码／截图哈希；详见 `.trellis/spec/backend/ui-catalog-verification.md`。

自动化 APK 通过 `:app:assembleDebugAndroidTest` 构建，再用明确的模拟器 `-s` 运行 instrumentation；不要使用会把个人手机也纳入的无差别设备执行。原始截图／报告位于 `app/build/reports/ui-polish/`，截图、交互、真实应用设置数据流和服务端播放分别记录验收范围。
