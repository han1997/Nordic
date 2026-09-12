# 代码边界调研

- MainActivity.selectedTab 目前混合媒体 0/1/2 与设置 3；LocalSourceActions.manage 通过 openServersRequest/selectedTab=3 跳转。
- PlaybackDock 将空的正在播放区域、分隔线与固定四标签一起绘制；必须独立判定导航与播放内容，父级布局不能订阅逐秒播放状态。
- MediaPageHeader 是四种媒体根页共用头部，HeaderActionGroup 提供溢出菜单；固定齿轮不能在窄屏丢进菜单。
- SettingsScreen 自有 page/search/editor 状态，SettingsNavigationGuard 保护草稿；打开请求要有序号，重建不能反复重置子页。
- data.MediaDomain 与 playback.MediaDomain 同名但职责不同，跨层引用显式 alias。
- MusicPlaybackEngine.stop 与 AudiobookPlaybackEngine.stop 当前无所有权检查；PlaybackDomain.activeDomain 标识共享 Media3 服务的当前所属域。
- AudiobookPlaybackViewModel.startPlayback 尚未保存 Job，不能只停止现有引擎；取消需及时回调释放主界面交接门闩，并拒绝迟到会话。
- 已下载音乐有独立播放入口；隐藏音乐后保留管理但不允许该入口绕过显示开关。
