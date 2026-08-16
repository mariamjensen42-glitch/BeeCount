package com.cycling.beecount.data.remote

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface DeepSeekApi {

    @POST("chat/completions")
    suspend fun chatCompletion(
        @Header("Authorization") authorization: String,
        @Body request: ChatCompletionRequest,
    ): ChatCompletionResponse

    companion object {
        const val BASE_URL = "https://api.deepseek.com/"
        const val MODEL = "deepseek-v4-flash"
    }
}
