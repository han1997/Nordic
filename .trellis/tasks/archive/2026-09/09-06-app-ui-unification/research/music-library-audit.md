# 音乐浏览 / 集合详情审查与改动

## 审查证据

- 原 AlbumDetailHeader、PlaylistDetailHeader 固定 120dp 左封面与窄文字区，metadata 不换行；ArtistDetailPage 则只有播放按钮和列表，没有同等概览。
- 四类列表重复 surface、封面与文字实现；Album/Playlist 绕开 CoverArt 的错误兜底。
- 列表与首页存在 Unknown artist / tracks / albums，和同页中文单位不一致。
- 歌单管理入口为 34/36dp，详情加载时头部消失；失败与空态可能同时出现。

## 本批落实

- 新增 MusicLibraryComponents.kt / MusicLibraryLayout.kt；列表和集合概览复用同一实现。
- 首页概览、专辑/歌手/歌单详情、列表和横向推荐卡片按宽度/字体适配。
- 收敛中文缺省文案、单位、时长与封面兜底；导航不重复集合长标题。
- 上层错误可见性传递到三个详情页，避免显示矛盾空态；API、缓存和播放队列回调不改写。
- 歌单管理按钮、长简介展开和对话框输入/确认语义统一；键盘提交沿用原忙碌/空白守卫。

## 验证范围

- 新增七个布局/计数/文案测试；回归包含音乐 UI、播放器、主路由、Emby 与 Navidrome 相关协议测试。
- 真机由用户完成；没有截取或操作设备，不能据此宣布音乐模块或全应用全部验收。
