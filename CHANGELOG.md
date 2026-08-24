# BeeCount 更新日志

本项目所有用户可见的变更都会记录在此文件。格式参考 [Keep a Changelog](https://keepachangelog.com/)，版本号遵循语义化版本。

## [0.0.7] - 2026-08-24

### ✨ 新功能
- **AI 自然语言查询**：在助手页用一句问句（如"上月餐饮花多少"）即可查询账目。AI 只识别意图，真实数字由数据库确定性计算，再经本地模板生成中文答案——绝不编造金额（`AnswerQueryUseCase` / `QueryIntent` / `AiQueryIntentDecoder`）。
- **AI 月度财务报告**：生成中文月报，所有数字来自数据库；无 API Key 或网络失败时自动降级为本地模板报告，保证离线可用（`AiMonthlyReportUseCase`）。
- **异常消费预警**：新支出落库后对比历史基线（单笔或单日累计偏离均值 + K·标准差即触发），本地通知提醒（`AnomalyDetector` / `AnomalyNotifier` / `Notifier`）。
- **预算执行预测**：按当前日均线性外推周期末支出，预判是否将超支（`BudgetForecastUseCase`）。
- **端侧智能分类建议**：用 TF Lite 离线模型，按交易对方与备注预判类别候选（无模型时安全降级，不阻断记账）；训练脚本见 `scripts/train_category_classifier.py`（`CategoryClassifier` / `TfliteCategoryClassifier`）。
- **相似交易检测**：微信账单重复导入时，按「金额 + 日期窗口 + 交易对方」找出疑似重复，与单号精确去重互补（`SimilarityDetector`）。

### 🔧 改进
- 新增通知权限 `POST_NOTIFICATIONS` 声明。

## [0.0.6] - 2026-08-24

### ✨ 新功能
- **快捷模板**：支持保存常用记账模板，一键快速记账（DataStore 存储 + 独立管理界面）。
- **语音记账**：支持端侧语音转文字，说话即可完成记账（`SpeechToText` / `OnDeviceSpeechToText`）。
- **增长分析**：新增增长趋势分析，查看收支随时间的变化（`GrowthAnalytics` / `GrowthAggregator`）。
- **退款与报销**：支持退款、报销类型的记账（`EntryType` 扩展与领域层联调）。
- **高级统计与标签云**：Analytics 增强，支持高级统计视图与标签云。

### 🔧 改进
- **分类层级与预算系统重构**：重构记账核心，分类层级与预算系统结构更清晰、更易于扩展。
- **终端黑客主题**：UI 升级为等宽字体 + 终端色板风格（见 ADR 0019）。

### 📚 文档
- 补充项目代理文档。
- 忽略工具目录（`.idea` / `.zcode` / `.reasonix`）。

## [0.0.5] - 历史版本
- 早期稳定版本，详见 Git 历史。
