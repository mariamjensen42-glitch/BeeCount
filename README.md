# BeeCount

个人记账 Android 应用。核心体验是一个 **AI 记账助手**：用一句自然语言描述一笔收支（如「昨天打车花了 30 块」），本地调用大模型解析为结构化账目，确认后即可入库。在此之上逐步扩展出 OCR 记账、离线语音记账、快捷模板、预算、深度统计、微信账单导入与桌面小组件。

品牌视觉为 **终端黑客风**（暗色独占 + 荧光青主色 + 等宽字体），见 [ADR 0019](docs/adr/0019-terminal-theme.md)。

---

## 功能特性

| 模块 | 说明 |
| --- | --- |
| **AI 记账助手** | 自然语言 → JSON 结构化解析 → 确认卡片（金额/类别可编辑）→ 入库；闲聊与查询不生成卡片 |
| **OCR 记账** | 相册选取支付宝/微信/云闪付截图，ML Kit 本地文字识别后走同一解析流程（v1 仅相册，无相机） |
| **离线语音记账** | 端侧 `SpeechRecognizer` 本地识别，不依赖网络，走同一解析流程 |
| **快捷模板** | 自定义一键记账模板（DataStore 持久化），点击即弹出预填确认卡片；内置常用默认模板 |
| **类别与标签** | 二级类别层级 + Emoji/颜色；横向打标的标签系统（标签色全局生效） |
| **预算系统** | 周期（周/月/季/年/自定义）/分类预算、预算结转、例外日、剩余日均、超支预警 |
| **账本页** | 全部账目历史，标签交集筛选 + 综合筛选面板（关键词/日期/分类/金额/对方），多条件「与」组合 |
| **日历页** | 周一起的月视图，支持历史/补记；点击日期打开当天流水抽屉 |
| **图表与分析** | 月度/年度报表、趋势图、年度热力图、**深度统计**（支出分布/消费习惯/刚性结构/净资产趋势/财务健康评分）、标签云、增长分析 |
| **退款与报销** | 退款类型（统计时冲抵支出）、支出报销标记 |
| **微信账单导入** | 离线关键词映射分类、去重、导入确认汇总、来源引用与「微信」标签 |
| **桌面小组件** | Glance 实现的收支速览小组件（2×1 / 4×2） |
| **数据管理** | CSV 导出、清空 |

> 完整的领域术语与口径定义见仓库根目录 [`CONTEXT.md`](CONTEXT.md)；架构决策见 [`docs/adr/`](docs/adr/)（ADR 0001–0022）。

---

## 技术栈

| 类别 | 选型 |
| --- | --- |
| 语言 | Kotlin 2.2.10（`kotlinx-serialization`、协程） |
| UI | Jetpack Compose（BOM 2026.02.01）+ Material 3 |
| 架构 | 单向数据流（Compose + ViewModel + UiState），Clean 分层 |
| 依赖注入 | Hilt 2.60.1 |
| 本地存储 | Room 2.8.4（账目/类别/标签/预算）、DataStore 1.2.1（快捷模板等偏好） |
| 网络 | Retrofit 3.0.0（对接大模型 API） |
| OCR | ML Kit 文字识别（中文）16.0.1 |
| 相机 | CameraX 1.4.0 |
| 桌面小组件 | Glance 1.1.1（appwidget + material3） |
| 构建 | AGP 9.3.1，Gradle Kotlin DSL，KSP 2.3.11；编译需 **JDK 17** |
| 测试 | JUnit 4、kotlinx-coroutines-test、Compose UI test |

**平台要求**：`minSdk 31`（Android 12）、`targetSdk / compileSdk 37`。应用固定深色外观（暗色独占），不跟随系统明暗。

---

## 架构

三层 Clean 架构，依赖方向由外向内：

```
ui/           Compose 界面与状态（assistant / ledger / calendar / analytics /
              budget / settings / widget / navigation / theme / components / common）
   ↑
domain/       领域模型与业务规则（model / ai / query / repository 接口 / usecase）
   ↑           - EntryIntake：解析→确认→入库的写入规则聚合
               - EntryQuery：日期/区间/粒度 → 聚合结果的只读查询聚合
data/         Room（Dao / Database / Entity）、Repository 实现、DataStore、
              Hilt DI（RepositoryModule）
```

- **领域语义与 UI 解耦**：组件只消费语义角色（primary/surface/error）与领域语义色（支出红、收入绿），不直接引用具体色值；圆角/间距/尺寸集中在 `ui/theme` 的 token（见 `CONTEXT.md` 设计语言章节）。
- **输入源统一**：手打文字、OCR 输入、语音识别、通知文本都归约到「可记账输入 → 解析 → 确认卡片」同一条管线。

---

## 快速开始

### 环境

- Android SDK（含 `compileSdk 37` 平台与构建工具）
- JDK 17
- 可选：签名用的 keystore（仅发布 Release 需要）

### 构建与运行

```bash
# 调试包
./gradlew :app:assembleDebug

# 或直接在 Android Studio 中打开本仓库并运行 :app
```

### 配置 AI 记账

AI 助手需要大模型 API Key，在应用内 **设置页 → API Key 管理** 中填写（密钥仅存于本机，不发往第三方）。

### Release 签名（CI 用）

本地或 CI 构建签名 Release 时通过环境变量注入 keystore，不会写入仓库：

```bash
BEECOUNT_KEYSTORE_PATH=... \
BEECOUNT_STORE_PASSWORD=... \
BEECOUNT_KEY_ALIAS=... \
BEECOUNT_KEY_PASSWORD=... \
./gradlew :app:assembleRelease
```

---

## 发布流程

仓库采用 **标签触发自动发布**：

1. 在 `app/build.gradle.kts` 中提升 `versionCode` / `versionName`（如 `0.0.6`）。
2. 提交并打注解标签 `vX.Y.Z`（`git tag -a v0.0.6 -m "..."`）。
3. 推送标签 `git push origin v0.0.6` → 触发 **Release** 工作流：
   - 校验 tag 与 `versionName` 一致；
   - 运行单元测试；
   - 构建签名并 R8 优化的 Release APK；
   - 校验 APK 签名、计算 `SHA-256`；
   - 创建 GitHub Release，附 `app-release.apk` 与 `SHA-256.txt`。
4. 发布前复核 Release 说明（见 [`CHANGELOG.md`](CHANGELOG.md)）。

> CI 工作流（`dev` 推送、向 `main` 的 PR）会运行单元测试作为准入门槛（分支保护要求 `unit-tests` 通过）。

---

## 文档与决策

- [`CONTEXT.md`](CONTEXT.md) — 单一语境的领域术语表与口径定义（权威来源）。
- [`docs/adr/`](docs/adr/) — 22 篇架构决策记录（ADR 0001–0022），覆盖解析范围、暗色策略、标签系统、预算、OCR、日历导航、微信导入、桌面小组件、终端主题、退款报销、深度统计、标签云与语音等。
- [`CHANGELOG.md`](CHANGELOG.md) — 用户可见的版本变更记录。

---

## 贡献与反馈

- Issue 统一使用 GitHub Issues（`mariamjensen42-glitch/BeeCount`），推荐用 `gh` CLI 操作。
- 五大分诊标签：`needs-triage` / `needs-info` / `ready-for-agent` / `ready-for-human` / `wontfix`。
- 提交信息建议遵循仓库既有风格（功能 `feat:`、重构 `refactor:`、文档 `docs:`、发布 `release:`）。

---

## License

见仓库 [`LICENSE`](LICENSE)。
