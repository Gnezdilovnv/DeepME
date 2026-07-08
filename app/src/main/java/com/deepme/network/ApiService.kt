package com.deepme.network
import retrofit2.http.*
interface DeepSeekApi {
    @POST("v1/chat/completions")
    suspend fun chat(@Header("Authorization") a: String, @Body r: DeepSeekRequest): DeepSeekResponse
}
data class DeepSeekRequest(val model: String, val messages: List<Message>, val stream: Boolean = false, val max_tokens: Int = 4096)
data class Message(val role: String, val content: String)
data class DeepSeekResponse(val choices: List<Choice>? = null, val error: DeepSeekError? = null)
data class Choice(val message: Message)
data class DeepSeekError(val message: String)
