# Nordic Media Hub

统一的多媒体管理客户端，整合音乐（Navidrome）、有声书（AudiobookShelf）、视频（Emby/Plex/WebDAV）三种媒体服务。

## 特性

- 🎵 **音乐** - 支持 Navidrome 服务器
- 📚 **有声书** - 支持 AudiobookShelf 服务器
- 📺 **视频** - 支持 Emby、Plex、WebDAV
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

### 视频服务器
**Emby:**
- 服务器地址
- 用户名、密码
- API Key（可选）

**Plex:**
- 服务器地址
- 用户名、密码

**WebDAV:**
- 服务器地址
- 用户名、密码

## 技术栈

- Jetpack Compose - 现代化 UI 框架
- Material 3 - 设计系统
- Kotlin - 开发语言
- Coil - 图片加载

## 构建

在 Android Studio 中打开项目，点击运行按钮即可。

## 要求

- Android 8.0 (API 26) 或更高版本
- Android Studio Hedgehog 或更高版本
