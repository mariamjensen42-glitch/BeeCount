package com.cycling.beecount.data.remote

import com.cycling.beecount.data.datasource.AiChatDataSource
import com.cycling.beecount.data.datasource.AiChatResult
import com.cycling.beecount.data.datasource.FailureReason
import javax.inject.Inject
import retrofit2.HttpException

/**
 * DeepSeek 聊天数据源实现：通过 Retrofit 调用，错误映射为 [AiChatResult.Failure]。
 * 401 映射为 [FailureReason.KEY_INVALID]，其余网络/服务器错误映射为 [FailureReason.NETWORK]。
 */
class DeepSeekAiChatDataSource @Inject constructor(
    private val api: DeepSeekApi,
) : AiChatDataSource {

    override suspend fun complete(
        apiKey: String,
        systemPrompt: String,
        userPrompt: String,
    ): AiChatResult = try {
        val response = api.chatCompletion(
            authorization = "Bearer $apiKey",
            request = ChatCompletionRequest(
                model = DeepSeekApi.MODEL,
                messages = listOf(
                    ChatMessage(role = "system", content = systemPrompt),
                    ChatMessage(role = "user", content = userPrompt),
                ),
            )
        )
        val content = response.choices.firstOrNull()?.message?.content
        if (content.isNullOrBlank()) {
            // DeepSeek JSON Output 有概率返回空 content，按网络问题处理以触发重试
            AiChatResult.Failure(FailureReason.NETWORK)
        } else {
            AiChatResult.Content(content)
        }
    } catch (e: HttpException) {
        if (e.code() == 401) {
            AiChatResult.Failure(FailureReason.KEY_INVALID)
        } else {
            AiChatResult.Failure(FailureReason.NETWORK)
        }
    } catch (e: Exception) {
        AiChatResult.Failure(FailureReason.NETWORK)
    }
}
