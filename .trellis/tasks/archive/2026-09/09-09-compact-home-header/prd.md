# 重设计三个媒体首页头部与文案

## 背景

音乐、有声书、视频三个首页的头部使用 displaySmall 大字标题（「音乐库」「有声书」「视频」），与底部 dock 的页签重复，占一整行却零信息量。副标题充斥"教学式"文案（"点一下直接播放""先从这里开始""点开查看章节和续播进度""点击海报播放"），像永不消失的新手引导。音乐首页每个 section 还各带一句废话副标题。

## 用户决策（已确认）

1. **头部方案 A：压缩头部** — 首页不再用 displaySmall 大字，改为单行紧凑头部：小号标题 + 右侧对齐的状态信息，内容区上移。
2. **Section 副标题：全部删除** — 音乐首页 4 个 section 只留标题 +「全部」action。

## 设计定案

```
┌──────────────────────────────────────┐
│ 有声书                   共 128 本   │  ← titleLarge 左 / 状态信息右对齐
│ ────────────────────────────────────│
│ [封面卡片] [封面卡片] [封面卡片] ...  │  ← 内容上移约 40dp
```

副标题文案原则：
- 只保留**状态语义**：正在刷新 / 本地缓存 X 前 / 错误信息 / 条目数
- 删除所有**操作教学**文案
- 空库引导文案保留（"连接 Navidrome 后…"）

## 改动清单

1. **SharedComponents.kt:61 `MediaPageHeader`**
   - 首页模式（`showBack=false`）：单行布局，标题 `titleLarge` SemiBold 左对齐，副标题变右对齐状态文本（`bodySmall`、onSurfaceVariant、maxLines 1、weight(1f) 反转）
   - 详情页（`showBack=true`）：完全不变（保持 headlineMedium 两行结构）

2. **MusicScreenV2.kt**
   - L820 Home: "最近添加按曲目展示，点一下直接播放" → 空字符串
   - L829 Songs: "共 X 首，点一下直接播放" → "共 X 首"

3. **MusicScreenV2Pages.kt**
   - L105 "最新进入曲库的专辑，先从这里开始" → 删除
   - L122 "新同步到曲库的曲目，点一下直接播放" → 删除
   - L155 "按最近添加展示，进入全部后可切换排序" → 删除
   - L182 "从熟悉的声音继续展开" → 删除
   - `MusicSectionHeader`（MusicHomeSections.kt:77）`subtitle` 改为可选参数，无副标题时不渲染该行

4. **AudiobookScreen.kt:409** — "共 X 本，点开查看章节和续播进度" → "共 X 本"
5. **VideoScreen.kt:372-373** — "共 X 个条目，点击海报播放" → "共 X 个条目"；"已连接 Emby，选择媒体库浏览内容" → "已连接 Emby"

## 保留不动

- 空库引导文案（"连接 Navidrome 后，这里会自动同步你的内容" 等）
- 错误 / 正在刷新 / 缓存年龄等状态语义文案
- 详情页头部结构
- 底部 dock

## 验证

- 现有测试无文案依赖（已 grep 确认）
- `gradlew :app:compileDebugKotlin` 编译通过
- 相关 screen 测试（AudiobookScreenTest / VideoScreenTest）通过
