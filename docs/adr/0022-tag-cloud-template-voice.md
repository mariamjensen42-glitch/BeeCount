# 标签云、快捷模板与离线语音记账

在既有记账与图表能力上新增三块：图表页「标签云」统计视图、今日页「快捷模板」一键填入、今日页「离线语音」记账。全部只读聚合或本地功能，无新增数据库表；语音识别走 Android 设备端（离线），快捷模板与份额均不新增网络依赖。

## 关键决策

1. **标签云按支出原始金额聚合，字体越大=消费越多**：只统计支出、按标签原始金额求和（不冲减退款，口径与模块 G 逐笔维度一致），取前 40 个高消费标签金额降序。字号 12..28sp 按相对最大金额对数缩放，颜色取标签自身色。空周期不展示该卡。实现复用既有的 `GrowthAnalytics` 聚合（新增 `tagCloud` 字段），无需改 `EntryQuery`——月/年入口的 `observeBetween` 已带标签。

2. **快捷模板绕过 AI 解析，直接构建确认卡片**：模板保存为 DataStore 一段 JSON，字段含标题/类别/金额/备注/收支类型。点击即在今日页把模板转成一条 `AiParseResult` 并弹出预填的确认卡，复用 `EntryIntake.confirm` 入库。天然支持「早餐=豆浆油条+5元」这类不上 AI 的高频记账。内置默认模板，设置页「管理快捷模板」可增删改。

3. **离线语音用设备端识别，走既有解析路径**：新增 `SpeechToText` domain seam（与 `OcrTextRecognizer` 同构），Android adapter 优先用 `SpeechRecognizer.createOnDeviceSpeechRecognizer`（API 31+，`minSdk=31`）做本地识别，配合 `EXTRA_PREFER_OFFLINE` 不依赖网络。`isOnDeviceRecognitionAvailable` 要求设备已下载离线模型、很多设备恒为 `false`，故可用性判断放宽为「存在任意语音识别服务」（`isRecognitionAvailable`），设备端不可用时改用标准识别器（默认服务，仍偏好离线）而非拦截。识别文本送入与文字输入相同的 `EntryIntake.parse` → 确认卡流程。首次点击请求 `RECORD_AUDIO` 权限，拒绝或设备不支持时以助手消息提示。

4. **语音/OCR/模板三种入口共用同一确认与入库链**：确认卡、`EntryIntake.confirm`、标签颜色注册等只保留一份。新增入口只负责「产出可解析输入或直接产出已确认结果」，不新增入库分支。

5. **演示数据挂标签以支撑标签云**：`FillDemoDataUseCase` 原先用 `replaceAll` 只写 `entries` 表，`Entry.toEntity()` 丢弃标签（标签存于独立的 `entry_tags` 关联表），导致 `tagCloud` 恒空。修复为仓库新增 `replaceAllWithTagIds`（同事务写账目 + 标签关联），演示数据按备注关键词确定性派生标签并写入关联，让标签云与标签筛选有可见数据。

## 影响面

- 新增：`domain/model/QuickTemplate.kt`、`domain/repository/QuickTemplateRepository.kt`、`data/repository/DataStoreQuickTemplateRepository.kt`、`domain/usecase/ManageQuickTemplateUseCase.kt`、`domain/usecase/SpeechToText.kt`、`domain/usecase/OnDeviceSpeechToText.kt`、`ui/settings/ManageQuickTemplatesViewModel.kt`、`ui/settings/ManageQuickTemplatesScreen.kt`、测试 `QuickTemplateTest.kt`、`GrowthAggregatorTest` 标签云用例。
- 变更：`domain/model/GrowthAnalytics.kt`（`tagCloud` 字段）、`domain/usecase/GrowthAggregator.kt`（`tagCloud` 聚合）、`domain/query/EntryQuery.kt`（`observeQuickTemplates`）、`di/RepositoryModule.kt`（两个绑定）、`ui/analytics/AnalyticsScreen.kt`（标签云卡）、`ui/assistant/AssistantViewModel.kt`（模板/语音事件）、`ui/assistant/AssistantUiState.kt`、`ui/assistant/AssistantScreen.kt`（模板条 + 麦克风钮）、`ui/settings/SettingsScreen.kt`（管理入口）、`ui/BeeCountApp.kt`（`template-manage` 路由）、`AndroidManifest.xml`（`RECORD_AUDIO`）、`strings.xml`、`domain/repository/EntryRepository.kt`（`replaceAllWithTagIds`）、`data/local/BeeCountDao.kt`（`replaceAllWithTags` + `EntryWithTagIds`）、`domain/usecase/FillDemoDataUseCase.kt`（标签派生与写入）。
- 无数据库迁移，无新表（演示数据标签写入复用既有 `tags`/`entry_tags` 表）。
