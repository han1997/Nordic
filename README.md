# Nordic Media Hub

一个三合一的 Android 媒体应用，整合音乐、有声书和视频功能。

## 功能

- **音乐播放器**：连接 Navidrome 服务，播放音乐库
- **有声书**：连接 AudiobookShelf 服务，管理有声书和电子书
- **视频播放器**：支持 Emby、Plex、WebDav 视频服务
- **设置**：配置各服务器连接信息

## 技术栈

- **语言**：Kotlin
- **最低 SDK**：26 (Android 8.0)
- **目标 SDK**：34 (Android 14)
- **UI 框架**：Material Design 3
- **架构**：Fragment + RecyclerView

## 项目结构

```
app/src/main/
├── java/com/nordic/mediahub/
│   ├── MainActivity.kt           # 主入口
│   ├── MusicFragment.kt          # 音乐模块
│   ├── AudiobookFragment.kt      # 有声书模块
│   ├── VideoFragment.kt          # 视频模块
│   ├── SettingsFragment.kt       # 设置模块
│   ├── MusicAdapter.kt           # 音乐列表适配器
│   ├── AudiobookAdapter.kt       # 有声书列表适配器
│   ├── VideoAdapter.kt           # 视频列表适配器
│   ├── Track.kt                  # 音乐数据模型
│   ├── Audiobook.kt              # 有声书数据模型
│   ├── Video.kt                  # 视频数据模型
│   ├── ServerConfig.kt           # 服务器配置模型
│   └── ConfigManager.kt          # 配置管理器
└── res/
    ├── layout/
    │   ├── activity_main.xml     # 主界面布局
    │   ├── fragment_music.xml    # 音乐界面
    │   ├── fragment_audiobook.xml # 有声书界面
    │   ├── fragment_video.xml    # 视频界面
    │   ├── fragment_settings.xml # 设置界面
    │   ├── item_music.xml        # 音乐列表项
    │   ├── item_audiobook.xml    # 有声书列表项
    │   └── item_video.xml        # 视频列表项
    ├── values/
    │   ├── colors.xml            # 颜色定义
    │   ├── strings.xml           # 字符串资源
    │   └── themes.xml            # 主题样式
    └── menu/
        └── bottom_nav.xml        # 底部导航菜单
```

## 构建步骤

1. 使用 Android Studio 打开项目
2. 等待 Gradle 同步完成
3. 连接 Android 设备或启动模拟器
4. 点击 Run 按钮运行应用

## 设计特点

- **毛玻璃半透明背景**：卡片使用半透明白色背景
- **圆角卡片设计**：16dp 圆角，2dp 阴影
- **底部迷你播放器**：固定显示当前播放内容
- **Material 3 风格**：遵循最新 Material Design 规范
- **柔和配色**：紫色主题 (oklch(0.55 0.18 270))

## 后续开发

- [ ] 实现 Navidrome API 集成
- [ ] 实现 AudiobookShelf API 集成
- [ ] 实现 Emby/Plex API 集成
- [ ] 添加 WebDav 支持
- [ ] 实现真实音频播放功能
- [ ] 添加设置页面（服务器配置）
- [ ] 实现登录认证
- [ ] 添加搜索功能
- [ ] 实现播放历史记录
- [ ] 添加离线下载功能

## 许可证

MIT License
