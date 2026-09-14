# 调试样板隔离边界

- `UiCatalogActivity` 仅在 Debug 清单声明，使用当前生产内容 Composable，不调用 MainScreen/业务 VM。
- 音乐播放器拆为原签名宿主 `MusicPlayerScreen` 与 `MusicPlayerContent`；下载管理器/恢复仍只在生产宿主中，取消下载也改为显式回调。
- 服务器编辑宿主仍持有 LocalSourceActions、连接测试、保存、取消请求和未保存返回保护；`ServerEditorContent` 只接收状态与事件。
- 调试筛选与排序直接复用 `filterMusicSongs` / `sortMusicSongs`；本机模拟事件只更新预览内存，不写 ConfigRepository。
- 样板封面裁自仓库已有参考图 `1779754843-896608-img-2333.png`，仅打入 debug drawable，不下载外部图片、不复制其 iOS 布局。歌词为原创测试文案，不是服务返回的真实歌词。
- UI 测试只能经 `adb -s emulator-5580 ... am instrument` 执行，不使用会枚举全部连接设备的 connectedAndroidTest。
- 基准截图必须在视觉修改前运行 screenshot test；交互测试中的大字体设置行用例用于锁定已发现的布局风险，后续精修应使其通过。

## 续跑后的验收补强

- 空播放器的秒/毫秒位置从同一个零值初始化，不把普通样例的 1:16 进度带进空状态；该约束有 instrumentation 回归。
- 正式截图通过真实系统字号、稳定全窗口捕获和明暗双向像素检查；手动目录字号不冒充系统窗口验收。
- 常规/短屏交互采用真实滚动与坐标点击，关键播放和 IME 保存目标必须完整可见；未合成的屏外 lazy item 先滚动定位。
- `settings_rows` 的正常样例本身已含长值/路径，不把重复的 `long` 图片另计覆盖；`disabled` 仅用于确实反映禁用状态的表单/设置组件。
- 原始基线缺少系统字号/源码指纹，尤其不用于大字体 Dialog 严格同条件对照；保留为历史视觉参考。最终截图和交互范围见 `evidence-index.md`。
