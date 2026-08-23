# 预算系统（周期 × 维度 × 金额）

在类别/标签之上新增「预算」功能：为总支出或某个一级分类设定周期性预算，实时统计当前周期的消费进度、剩余日均、超支预警，并支持上期结余结转与「某天豁免」例外。

## 关键决策

1. **数据模型：一条预算 = 周期 × 维度 × 金额（单表多记录）**：`budgets` 表每条记录含 `cycle`（周/月/季/年/自定义天数）、`categoryName`（`null`=总预算，否则为一级分类叶名）、`amount`、`carryOver`（是否结转上期结余）、`enabled`。总预算与各分类预算是互相独立的行，可同时存在多条。自定义天数周期以创建日所在周期为锚点向前/向后推算（`lengthDays` + `customAnchor`）。

2. **消费口径 = 支出、排除中性与收入**：预算累计只统计 `EXPENSE` 账目，与今日合计/图表口径一致。分类维度按账目类别名快照匹配——总预算命中所有支出；一级分类预算用「一级分类 · 任意」前缀匹配，覆盖该分类及其全部子分类。

3. **结转 = 上期正向结余滚入，超支抹平不反向扣**：当前周期可用额度 = 基础额 + 上期「基础额 − 上期支出」的正向结余（≥0）。上期超支则不反向扣减本期。只结转最近一期，避免结余无限累积。`carryOver=false` 的预算不结转。

4. **预算例外 = 按天豁免**：`budget_exceptions` 表以日期为主键，某天标为例外日后，该日全部账目不计入任何预算消费（适用于旅行、搬家等集中消费日）。

5. **进度与预警（纯函数 `BudgetMath`）**：周期区间、消费、结转、剩余日均全为纯函数便于单测。剩余日均 = (基础额+结转 − 已花) / 剩余天数（含今天），超支时为 0。进度的展示语义：<90% 正常色、≥90% 橙色（接近预警）、≥100% 红色加粗 + 「已超支」提示。

## 迁移（v7 → v8）

新增 `budgets` 表（cycle TEXT、lengthDays INTEGER、customAnchor TEXT 可空、categoryName TEXT 可空、amount REAL、carryOver INTEGER、enabled INTEGER、createdAt INTEGER）与 `budget_exceptions` 表（date TEXT 主键）。均为新表，无损，已有的账目/类别/标签数据不受影响。预算消费统计复用新增的轻量账目查询 `observeLightAll`（只取 date/type/categoryName/amount）。

## 范围界定

- 入口：设置页「管理」新增「管理预算」行；首页「今日 · 日期」概览卡内展示启用预算的进度条区块。
- UI（`ui/budget`）：`BudgetManageScreen` 独立页（环形进度 + 进度条 + 剩余日均 + 结转开关 + 例外日管理）、`AddBudgetDialog`（周期类型分段选择 + 维度选择 + 金额 + 结转）。
- 类别改名/删除后，已建的预算维度保留改名前的一级分类名快照：历史账目匹配不受影响；因新记账不再使用原分类，预算会停留在旧口径。此场景暂不在删除分类时级联处理预算维度（保持简单，后续如需可加）。

## 影响面

- 新增：`domain/model/Budget.kt`（Budget/BudgetCycle/BudgetException/BudgetProgress/BudgetMath）、`BudgetEntity`/`BudgetExceptionEntity`/`BudgetDao`、`BudgetRepository`/`RoomBudgetRepository`、`ManageBudgetUseCase`/`ObserveBudgetProgressUseCase`/`ObserveBudgetsUseCase`、`ui/budget/*`、DB v8 迁移。
- 变更：`EntryDao` 新增 `observeLightAll`；`AssistantUiState`/`AssistantViewModel`/`AssistantScreen`（首页进度区块）；`SettingsScreen`/`BeeCountApp`（预算入口与路由）。
