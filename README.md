# Nordic Media Hub

统一的多媒体管理客户端，面向音乐（Navidrome）、有声书（AudiobookShelf）、视频（当前为 Emby）三类自托管媒体服务。

## 特性

- 🎵 **音乐** - 支持 Navidrome 服务器
- 📚 **有声书** - 支持 AudiobookShelf 书库同步、详情浏览、播放会话和进度同步
- 📺 **视频** - 支持 Emby 媒体库浏览、海报、沉浸播放、播放器内选集、字幕/音轨选择、画中画和进度同步；Plex、WebDAV 为后续计划
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

当前版本的视频同步、浏览和播放以 Emby 为主。Plex 和 WebDAV 入口保留为后续扩展方向，尚未作为可用视频服务接入。

### 视频播放器

- 非全屏锁定竖屏、全屏锁定横屏，退出播放器恢复系统方向；播放控制闲置 4 秒后隐藏。
- 点按画面唤起控制，支持后退 10 秒/前进 30 秒、拖动或点按进度条、倍速与画面比例；长按临时 2 倍速，左右侧滑动调整亮度/音量。
- “选集”按季展示已载入的同剧集数据，定位当前集并显示已看/续播状态；不会自动补取全库。电影不显示选集，无法播放的集禁用。
- 切换剧集保持全屏并保存原集进度；片尾提示需要手动点击，不自动连播。手势锁仅禁用画面手势，播放按钮仍可操作。
- 更多、倍速和选集在宽横屏使用侧面板，其余情况使用底部面板。设置中可选择字幕/音轨，播放倍速会持久化保存；清晰度切换与投屏尚未提供。
- 画中画默认开启，可在播放器设置中关闭并保存。支持 PiP 且系统允许的设备上，播放中按 Home/上滑返回桌面会以小窗继续播放，缓冲中也可进入；暂停或错误状态不会自动进入。
- 点小窗返回应用会继续原视频；关闭小窗或小窗内播放结束会停止并上报进度，不自动连播。关闭画中画开关、系统禁止或设备不支持 PiP 时，视频进入后台即停止。音乐、有声书的后台播放不受影响。

## 技术栈

- Jetpack Compose - 现代化 UI 框架
- Material 3 - 设计系统
- Kotlin - 开发语言
- Coil - 图片加载
- Retrofit / OkHttp - 自托管服务 API 接入
- Media3 - 音频播放、MediaSession 和缓存基础设施

## 构建

在 Android Studio 中打开项目，点击运行按钮即可。

命令行构建使用 JDK 17，并在本机 `local.properties` 中配置 Android SDK 的 `sdk.dir`：

```powershell
.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug :app:assembleRelease
```

Release 产物位于 `app/build/outputs/apk/release/app-release.apk`，已签名并启用 R8 混淆与资源收缩，可用于侧载；不要安装旧的 `app-release-unsigned.apk`。

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

可传 `--es scenario movie|unknown|buffering|error|end|empty`（选择一个值）验证状态。该入口不进入 release 构建；预览验证不能替代真实 Emby 播放/上报测试。
