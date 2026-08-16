# NLS 候选扫描：manifest permission 必须是 `BIND_NOTIFICATION_LISTENER_SERVICE`

> **⚠️ 已随功能下线（2026-08-16，v0.0.5）**：本 ADR 记录的问题修复随 v0.0.4 发布，但自动记账功能已在 v0.0.5 完全移除（见 ADR 0014 弃用说明），NLS 服务节点与权限声明已从 manifest 删除。本文保留作为「系统列表不显示候选」类问题的排查参考。

ADR 0014 实现 NLS 自动记账后，MIUI V140「通知使用权」列表始终不显示 BeeCount，连续 3 个 commit（f9b7920、daeb90a、f1b6614）都没能解决，根因直到本次烤问通过对比 `Lambada10/SongSync`（同场景、同问题域、可正常显示在列表里）的 manifest 才暴露：`<service>` 节点声明的 permission 写成了 `android.permission.BIND_NOTIFICATION_LISTENER`（**无 `_SERVICE` 后缀**），正确值是 `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE`。这是一个**从未生效的 NLS 候选**：系统的 `Settings$NotificationAccessSettings` 收集候选服务时按 `BIND_NOTIFICATION_LISTENER_SERVICE` 权限名过滤，BeeCount 因权限名错误从 v0.0.3 之前的所有安装包（含 debug v0.0.2 与 release v0.0.3）开始就**根本不在候选集合里**。

**「`cmd package query-services -a android.service.notification.NotificationListenerService` 能解析到本服务」不能证明「系统设置页认为本服务是 NLS 候选」**：前者按 intent-filter 的 action 解析，**不**校验 permission；后者按 permission 名收集。**两个层次的"能解析"和"是候选"完全是两件事**——本次烤问早期也用前者"能解析"的正面结果给自己打气，绕了很久才靠对比参考实现打破这个误判。

**修复是 1 行 manifest 改动**：`android.permission.BIND_NOTIFICATION_LISTENER` → `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE`。修复后实测：MIUI V140「通知使用权」列表直达 BeeCount 详情授权页（`com.android.settings/.Settings$NotificationAccessSettingsActivity`），确认后 `settings get secure enabled_notification_listeners` 出现 `com.cycling.beecount/.../PaymentNotificationListener`，与小米服务框架、弹幕通知并列。

## 三次绕路的回顾（值得记的工程教训）

**f9b7920（2026-08-16 18:36）「自动记账（ADR 0014）」**——**根因产生**：写 `BIND_NOTIFICATION_LISTENER` 而不是 `_SERVICE` 后缀那个。当时是从网络示例/老博客抄来的，注释都没写（commit 引入 9 行、permission 是其中一行）。

**daeb90a（2026-08-16 18:42）「修复自动记账监听服务在系统通知使用权列表不可见：exported 改为 true」**——**第一次绕路**：当时已观察到 NLS 列表不显示，没有回头核对 `pm list permissions | grep BIND_NOTIFICATION_LISTENER` 的实际定义，反而判断为"exported 必须是 true 系统才会扫描"，写出了三段详细注释把 `BIND_NOTIFICATION_LISTENER` 描述为"signature|privileged 权限"，**把错误连同错误解释一起固化进了 manifest 注释**。这次修复距正确根因只差 1 行，但走了 0 行。

**f1b6614（2026-08-16 18:47）「回退通知监听授权跳转到系统列表页：MIUI 未实现详情直达 Activity，改为列表页」**——**第二次绕路**：导出 true 之后设置页仍不显示 BeeCount（因为本来就不在候选集合里），再次没去查 manifest 声明本身，而是判断"MIUI 没实现详情直达 Activity，回退到列表页"。这次改动在「跳转哪个 Action」「哪个 Activity」之间打转，**完全没怀疑 manifest 本身有问题**。

**本次烤问（grill-me session）**——**第三次绕路险些发生**：拿到 `cmd package query-services` 能解析到服务的正面结果后，**用它给自己打气**"系统层认可"，转向"是 MIUI 列表过滤"假设，准备走"文档化 + 引导用户开自启动"路线。直到用户点出"SongSync 在列表里能看到，你看看别人怎么做的"才打破这个循环——对比参考实现 5 分钟内定位根因，5 行代码内修复完成。

**共同模式**：4 个回合（3 个 commit + 1 次烤问）都从「外部路径」（跳转哪个 Activity、跳列表 vs 跳详情、是 MIUI 列表过滤还是系统问题）绕过去，**没人看 manifest 声明本身**。外部路径每绕一步都"显得合理"（有原因、有解释、有 commit message），但**任何合理化都不能取代「打开 manifest 跟一个工作正常的参考实现一行一行 diff」**。

## 遇到 NLS 不显示时的根因排查清单

按"先外部后内部"的顺序，但**内部排查必须做、不可省略**——本次教训。

1. **`pm list permissions | grep BIND_NOTIFICATION_LISTENER`**：列出系统实际声明的两个权限名（应有且仅有 `BIND_NOTIFICATION_LISTENER_SERVICE`），确认本应用 service 节点用的是哪个。无 `_SERVICE` 后缀的直接判定为根因。
2. **`aapt dump xmltree app-release.apk AndroidManifest.xml` 抽 service 节点**：跟一个**已知可在同设备 NLS 列表显示的 app**（参考实现选同 targetSdk、同权限等级的开源 NLS）一行一行 diff，permission / exported / intent-filter action / label 任何一项不一致都可能是根因。
3. **`cmd package query-services -a android.service.notification.NotificationListenerService`**：能列出本服务 ≠ 系统设置页认为本服务是 NLS 候选——**这步只能验证组件可达性，不能验证候选资格**。`pm list permissions` 才是验证候选资格的正确手段。
4. **真实设备走一遍授权路径**：从应用内"打开 NLS 设置"按钮点出去，能进入自己的详情授权页 + 点确认后 `settings get secure enabled_notification_listeners` 出现本包名，才算闭环。
5. **OEM 列表页过滤（MIUI V140 / HyperOS / ColorOS 等）只在第 4 步通过之后才需要查**——本次烤问前期把这个当首要怀疑方向，是路径倒置。

## 对后续类似问题的硬性约定

- 写 service / receiver / provider 节点时，**先找一个能正常工作的同场景参考实现，把它的整段 `<service>` 复制过来再改 class 名**，不要从零写。
- PR review 时对 `<service>` / `<receiver>` 节点引入 diff：reviewer 必须能用 `pm list permissions` 在 PR description 附上系统实际声明的权限名作为佐证。
- 任何"X 在系统某列表里不显示"的报告，第一动作是看 manifest 声明本身，不要先去调"跳转路径" / "入口文案" / "OEM 过滤"。
