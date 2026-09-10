# 修复 release 混淆导致 Retrofit 泛型解析崩溃

## 目标

修复 debug 可连接、release 报 `java.lang.Class cannot be cast to java.lang.reflect.ParameterizedType` 的连接失败，保留 R8 混淆和资源收缩，交付可侧载的新版 release APK。

## 已知事实

- 用户要求直接修复；现有版本为 `0.1.1 / 1`，包名 `fun.han1997.nordic`，release 复用本机 debug 签名。
- 三类服务器 API 都通过 Retrofit 2.9 suspend 方法返回 `Response<T>`。
- Retrofit 2.9 `HttpServiceMethod.java:48` 将最后一个参数的反射类型直接转换成 `ParameterizedType`。
- 当前规则只保留 Signature 属性、API/DTO 和 Gson TypeToken，缺少 R8 full mode 下 Continuation/Response/Call 泛型定义的保护。
- ADB 当前没有连接设备，不能承诺已完成用户真机连接测试。

## 方案与边界

- 最小修复：补齐 R8 泛型 keep 规则，不关闭混淆，不修改账号、网络请求、接口协议或签名证书。
- 同步覆盖 Navidrome、AudiobookShelf 和 Emby，防止只修复一个连接入口。
- 按现有版本规则递增为 `0.1.2 / 2`。
- 增加反射/混淆规则回归检查，并核验实际经过 R8 的 release 产物。
- 不在此任务升级 Retrofit/AGP，不以关闭 R8 或大范围保留所有依赖作为修复。

## 验收条件

- [x] 找到旧 release 泛型信息丢失的产物证据，对应源码强转位置。
- [x] 新增回归检查能识别缺少泛型保留规则的情况。
- [x] 编译、单元测试、Lint、release 打包通过。
- [x] 新 release 中 suspend Continuation 与 Response 的嵌套泛型签名保留。
- [x] APK 签名有效，包名不变，版本为 `0.1.2 / 2`。
- [x] CHANGELOG 和 spec 记录根因及 release 专属验证边界。

## 开放问题

无阻塞性产品问题；按上述最小范围执行。真机联网复测由用户安装新包确认，不以 debug 单测替代。

完整证据见 `info.md` 与 `research/diagnosis.md`；工作提交 `49bf0d2` 已完成，真机复测仍待用户反馈。
