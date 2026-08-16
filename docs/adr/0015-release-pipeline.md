# 发布流程：手动定版 + tag 触发 CI 自动构建 Draft Release

v0.0.2 的发布是手动 `gh release create` 附 CI artifact，v0.0.3 发布前经 grill-with-docs 打磨，把发布流程定为主流工程实践形态。v0.0.2 的手动流程有真问题：artifact 与发布分离、无校验、无确认点，且「构建发布」这一步本该交给机器。五个核心决策需要记录。

**版本决策留给人，构建发布交给机器（tag push 触发 release.yml）**：版本号是产品决策（这版是修 bug 还是加功能、有无破坏性变更），由人决定版本并打 tag；CI 只负责从 tag 构建出可发布产物并自动创建 Release 草稿。触发方式为 GitHub Actions `on: push: tags: ['v*']`，这是主流项目的标准模式。发布动作收敛为两步：① `release/vX.Y.Z` 分支 bump 版本 → PR 合并 main；② `git tag -a vX.Y.Z` 打在 main 合并提交上 → push。不用 release-please 全自动流派——需要改 Conventional Commits 提交习惯（现有提交是中文描述式），单人项目收益低。

**版本号单一来源是 `app/build.gradle.kts`（versionCode/versionName），release.yml 做 tag ↔ versionName 一致性校验**：Android 的 versionCode 是单调整数，本就该独立手工管理，从 tag 派生 versionName + 另想 versionCode 策略反而把简单事搞复杂。为防止「tag 与代码版本不一致发错版」——这是两处事实来源必然的风险——release.yml 在构建前校验 `v<tag>` 与 `versionName` 必须相等，不一致直接失败。bump 走独立 PR 保留显式记录。

**main 加分支保护：要求 PR + 要求 CI（unit-tests）通过 + 管理员可绕过，不要求分支最新**：保证任何 tag 都从经过单测验证的 main 状态发出。允许管理员绕过（enforce_admins=false）是单人开发的务实取舍——CI 故障时不至于锁死主干；不要求「分支最新」避免单人开发来回 rebase 的摩擦。

**CI 与发布分离成两个 workflow，CI 只跑单测**：`ci.yml`（dev push / PR→main）只跑 `:app:testDebugUnitTest`，PR 门禁从 ~6.5 分钟（原 android-ci.yml 每次构建签名 APK）降到 ~1-2 分钟；`release.yml`（tag push）承担完整的单测 → 签名构建 → apksigner 校验 → SHA-256 → 创建 Release。发布产物只从 release.yml 产出，PR 不再上传 APK artifact——测试版 APK 由发布流程覆盖，避免「PR 构建的 APK 与最终发布不一致」的源头。

**Release 先建 Draft，人工检查后 Publish**：CI 创建 Draft release（用户不可见），附 APK 与 SHA-256.txt，release notes 自动生成自上个 tag 起的提交列表；人在 GitHub 上核对产物、润色 notes 后点 Publish。App 发出去用户就装上了，草稿确认点是标准安全阀；不设 CHANGELOG.md 文件（避免与自动 notes 双份维护），notes 只在 Draft 阶段维护。
