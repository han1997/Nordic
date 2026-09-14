# 已批准计划与代码边界

- 现有 Kotlin tokens 为基准；DESIGN.md 的 spacing/rounded YAML 仍有过期值，先校准而非重新选风格。
- MusicSongsPage / MusicAlbumDetailPage 已接收状态和回调，可供调试宿主直接复用；完整音乐播放器本身接收播放状态，但宿主依赖/附属面板需逐项隔离检查。
- ModuleVisibilityPage 已可复用；ServerEditorScreen 自带 ConfigRepository，需抽取共享表单内容层，保持生产验证/保存/草稿行为不变。
- 现有 VideoPlayerPreviewActivity 提供 debug-only 合成状态范式；新增 UI 目录沿用该隔离方式，Release 必须排除。
- 当前没有 androidTest；添加与现有 Compose BOM 对齐的 UI instrumentation 依赖及 AndroidJUnitRunner，使用语义断言与截图，不引入第二套截图框架。
- 基准截图必须在实际视觉修改前获得。先完成调试宿主/最小内容层抽取，再改样板视觉；若发现宿主会写真实偏好或启动服务，先切断副作用再运行。
- 明确区分状态预览与实际生产数据流；模拟器全应用设置/导航冒烟单独验证。个人手机不安装、不启动、不清数据。
