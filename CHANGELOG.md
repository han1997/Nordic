# 更新日志

所有项目的重要更改都会记录在此文件中。

## [1.1.0] - 2026-06-11

### 新增
- 添加设置界面（SettingsFragment）
- 实现服务器配置功能
  - Navidrome 音乐服务器配置（URL、用户名、密码）
  - AudiobookShelf 有声书服务器配置（URL、Token）
  - 视频服务配置（Emby/Plex/WebDav，URL、用户名、密码）
- 创建 ServerConfig 数据模型
- 创建 ConfigManager 本地存储（SharedPreferences）
- 底部导航新增"设置"标签

### 技术实现
- 使用 Material TextInputLayout 表单组件
- Spinner 选择视频服务类型
- SharedPreferences 持久化配置

## [1.0.0] - 2026-06-11

### 新增
- 创建基础 Android 项目结构
- 实现 Material Design 3 主题
- 添加底部导航栏（音乐、有声书、视频三个标签）
- 实现音乐模块 UI
  - 音乐列表（RecyclerView）
  - 底部迷你播放器
  - 卡片式音乐条目（序号、标题、歌手、时长）
- 实现有声书模块 UI
  - 有声书列表（RecyclerView）
  - 卡片式书籍条目（封面、标题、作者、进度）
- 实现视频模块 UI
  - 视频网格布局（2列 GridLayoutManager）
  - 卡片式视频条目（海报、标题、类型、时长）
- 配色方案
  - Surface: `oklch(0.95 0.008 270)` - 浅灰微紫调
  - Primary: `oklch(0.55 0.18 270)` - 柔和紫
  - 半透明卡片背景
- 添加项目文档（README.md、CHANGELOG.md）
- 添加设计文档（DESIGN.md、PRODUCT.md）

### 技术实现
- Kotlin 语言
- AndroidX 库
- Material 3 组件
- Fragment 架构
- RecyclerView 列表
- 所有文件使用 UTF-8 编码

### 设计特点
- 16dp 圆角卡片
- 2dp/8dp 阴影层次
- 毛玻璃半透明效果
- 统一的视觉语言
