# Nordic Media Hub

统一的多媒体管理客户端，面向音乐（Navidrome）、有声书（AudiobookShelf）、视频（当前为 Emby）三类自托管媒体服务。

## 特性

- 🎵 **音乐** - 支持 Navidrome 服务器
- 📚 **有声书** - 支持 AudiobookShelf 书库同步、详情浏览、播放会话和进度同步
- 📺 **视频** - 支持 Emby 媒体库浏览、海报、播放和进度同步；Plex、WebDAV 为后续计划
- 🌓 **主题切换** - 日间/深色模式自由切换
- 🔐 **完整认证** - 支持用户名、密码、API Key

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
