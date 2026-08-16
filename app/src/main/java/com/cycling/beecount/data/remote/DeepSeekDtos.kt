package com.cycling.beecount.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
    val role: String,
    val content: String,
)

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    @SerialName("response_format")
    val responseFormat: ResponseFormat = ResponseFormat(type = "json_object"),
    @SerialName("max_tokens")
    val maxTokens: Int = 2048,
)

@Serializable
data class ResponseFormat(
    val type: String,
)

@Serializable
data class ChatCompletionResponse(
    val choices: List<Choice>,
)

@Serializable
data class Choice(
    val message: ChatMessage,
)
