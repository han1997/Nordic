# Design System

## Color Strategy
**Committed - 深色/日间双主题，紫色系渐变**

### Dark Theme
- **Background**: `#0A0A0F` - 深黑紫
- **Surface**: `#1A1A24` (87%透明度)
- **Surface Variant**: `#252530`
- **Primary**: `#B098FF` - 明亮紫色
- **Secondary**: `#7DD3FC` - 青蓝色
- **Text Primary**: `#E8E8EE`
- **Text Secondary**: `#9898A8`

### Light Theme
- **Background**: `#F5F4F8` - 浅紫灰
- **Surface**: `#FFFEFF` (87%透明度)
- **Surface Variant**: `#EBEAF0`
- **Primary**: `#7B5FD3` - 深紫色
- **Secondary**: `#0EA5E9` - 明蓝色
- **Text Primary**: `#1A1A20`
- **Text Secondary**: `#6B6B7B`

## Typography
- **Font**: 系统默认 (Roboto/思源黑体)
- **Scale**: 
  - Title: 20sp / Medium
  - Body: 15sp / Regular
  - Caption: 13sp / Regular

## Layout
- **Spacing scale**: 4dp, 8dp, 12dp, 16dp, 24dp
- **Border radius**: 12dp (卡片), 8dp (小元素)
- **Content max-width**: 无限制（移动端全宽）

## Components

### Cards
- 半透明背景 + 轻微模糊（毛玻璃效果）
- 12dp圆角
- 微妙阴影

### Bottom Navigation
- 固定底部，半透明背景
- 图标 + 文字标签

### Now Playing Bar
- 浮动在底部导航上方
- 显示封面、标题、播放控制

## Motion
- 页面切换：300ms ease-out
- 元素出现：200ms ease-out
- 避免弹性动画
