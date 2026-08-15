package com.cycling.beecount.data.datasource

/**
 * AI 聊天数据源：向模型发送对话并返回响应。
 * Domain 层的解析用例依赖此接口，Data 层通过 Retrofit 调用 DeepSeek 实现。
 */
interface AiChatDataSource {
    suspend fun complete(apiKey: String, systemPrompt: String, userPrompt: String): AiChatResult
}

sealed interface AiChatResult {
    /** 模型正常返回了文本内容 */
    data class Content(val text: String) : AiChatResult

    /** 请求失败，[FailureReason] 区分原因以便 UI 给出针对性提示 */
    data class Failure(val reason: FailureReason) : AiChatResult
}

enum class FailureReason {
    /** 网络不可用或服务器错误（静默重试由调用方决定） */
    NETWORK,

    /** API Key 无效（401） */
    KEY_INVALID,
}
