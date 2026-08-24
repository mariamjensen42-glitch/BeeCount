# 高级统计分析（模块 G）

在图表页的「月度」「年度」两种粒度下追加深度统计区块：消费频次、客单价、中位数、单笔极值、支出波动、星期消费习惯、时间段分布、支出刚性结构（刚性/可选/冲动）、净资产趋势、财务健康评分与年度收支报告书。全部为只读聚合，不新增数据库表。

## 关键决策

1. **两套口径明确区分**：
   - **逐笔维度**（频次/客单价/中位数/方差/标准差）只统计每笔【支出】的原始金额，不冲减退款——「每次消费多少钱」看的是单笔交易本身，退款另算；退款与中性记录不计入笔数。
   - **占比维度**（刚性/可选/冲动）同样按支出原始金额，只在组内分类、不扣退款。
   - **净资产趋势**采用累计口径：净资产 = 累计收入 − 累计支出（退款在支出中冲抵），是「到某天为止的净现金流」，可从任意时间点回溯。

2. **刚性结构按「类别路径的任一分段」匹配**：一级分类（如「居住」「购物」）语义即父分段，子分类（如「居住·房租」）沿父分段的必要性归类。刚性 = 居住/医疗/教育/交通；可变 = 其余类别；冲动 = 可选中偏「想要」的非必需部分（购物/娱乐/人情）。

3. **财务健康评分采用加权打分，避免「刚性高=差」与「冲动高=差」自相矛盾**：刚性占比以约 35% 为健康目标，过高（固定开销吃掉结余）或过低（基本生活无保障）都扣分；冲动则单调越低越好。维度与权重：储蓄率 30 / 支出稳定 25 / 冲动控制 25 / 刚性健康度 10 / 记账覆盖 10（有账天数 ÷ 周期天数）。折为 0..100 并分级（80+ 优秀、65+ 良好、50+ 及格、其余待改善）。

4. **时间段按入库时刻（createdAt）而非账目日期划分**：上午 0-12、下午 12-18、晚上 18-24。因为折线/柱状趋势已按日期统计，时间段分析反映的是「用户几时记账/消费」的行为模式。

5. **实现遵循既有 ADR 0009 模式**：纯函数聚合（`GrowthAggregator`，与 `AnalyticsAggregator` 同构）+ `EntryQuery` 深模块新增 `buildGrowth`/`buildNetAssetTrend` 查询 + `AnalyticsViewModel` 新增 `growthAnalytics`/`netAssetTrend` flow + `ui/analytics/AnalyticsScreen` 新增区块卡。净资产趋势走 `observeAllWithTags` 全量历史，独立于当前粒度。

## 影响面

- 新增：`domain/model/GrowthAnalytics.kt`（SpendingStats/CategoryCount/TimeSlot/WeekdayStats/WeekendVsWeekday/ExpenseRigidity/NetAssetPoint/NetAssetTrend/HealthMetric/FinanceHealthScore/AnnualReport/GrowthAnalytics）、`domain/usecase/GrowthAggregator.kt`、测试 `GrowthAggregatorTest.kt`。
- 变更：`domain/query/EntryQuery.kt`（`buildGrowth`/`buildNetAssetTrend`）、`ui/analytics/AnalyticsViewModel.kt`（两个新 flow）、`ui/analytics/AnalyticsScreen.kt`（深度统计区块）。
- 无数据库迁移，无新表。
