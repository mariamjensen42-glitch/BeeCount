package com.cycling.beecount.domain.usecase

/**
 * 智能分类建议：基于交易对方与备注预判类别候选（端侧 ML，离线隐私）。
 * 具体实现（[com.cycling.beecount.data.ml.TfliteCategoryClassifier]）加载 assets/ml 下的
 * TF Lite 模型；无模型或线索不足时返回空列表，不阻断记账流程。
 */
interface CategoryClassifier {
    /** 返回按相关性降序的类别候选（最多 3 个）；无建议时返回空列表 */
    suspend fun suggest(counterparty: String?, note: String?): List<String>
}
