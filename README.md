# Nordic Media Hub

统一的多媒体管理客户端，面向音乐（Navidrome）、有声书（AudiobookShelf）、视频（当前为 Emby）三类自托管媒体服务。

## 特性

- 🎵 **音乐** - 支持 Navidrome 服务器
- 📚 **有声书** - 支持 AudiobookShelf 书库同步、详情浏览、播放会话和进度同步
- 📺 **视频** - 支持 Emby 媒体库浏览、海报、沉浸播放、播放器内选集和进度同步；Plex、WebDAV 为后续计划
- 🌓 **主题切换** - 日间/深色模式自由切换
- 🔐 **完整认证** - 支持用户名、密码、API Key

## 界面操作

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
- 更多、倍速和选集在宽横屏使用侧面板，其余情况使用底部面板。字幕/音轨切换、清晰度、投屏与画中画尚未提供。

## 技术栈

- Jetpack Compose - 现代化 UI 框架
- Material 3 - 设计系统
- Kotlin - 开发语言
- Coil - 图片加载
- Retrofit / OkHttp - 自托管服务 API 接入
- Media3 - 音频播放、MediaSession 和缓存基础设施

## 构建

在 Android Studio 中打开项目，点击运行按钮即可。

## 要求

- Android 8.0 (API 26) 或更高版本
- Android Studio Hedgehog 或更高版本

### 离线播放器布局验证（仅 debug）

Debug 构建包含 `VideoPlayerPreviewActivity`，使用合成画面与剧集，不连接服务器或更改观看记录：

```powershell
adb shell am start -n com.nordic.mediahub/.VideoPlayerPreviewActivity
adb shell am start -n com.nordic.mediahub/.VideoPlayerPreviewActivity --ez fullscreen true
```

可传 `--es scenario movie|unknown|buffering|error|end|empty`（选择一个值）验证状态。该入口不进入 release 构建；预览验证不能替代真实 Emby 播放/上报测试。
