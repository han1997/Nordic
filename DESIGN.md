# Design System

## Color Strategy
**Restrained with soft pastels**

### Palette
- **Background**: `oklch(95% 0.008 280)` - 柔和的淡紫灰
- **Surface**: `oklch(97% 0.006 280 / 0.8)` - 半透明白色表面
- **Primary**: `oklch(65% 0.15 280)` - 柔和紫色
- **Secondary**: `oklch(70% 0.12 200)` - 柔和蓝色
- **Text Primary**: `oklch(25% 0.01 280)`
- **Text Secondary**: `oklch(50% 0.01 280)`

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
