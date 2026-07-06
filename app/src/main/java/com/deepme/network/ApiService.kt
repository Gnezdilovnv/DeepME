package com.deepme.network

import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.Call

interface ApiService {
    @POST("api/chat")
    suspend fun sendMessage(@Body request: ChatRequest): ChatResponse
}

data class ChatRequest(val message: String)
data class ChatResponse(val response: String?)