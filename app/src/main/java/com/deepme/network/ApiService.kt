package com.deepme.network

import retrofit2.http.*

interface DeepSeekApi {
    @POST("v1/chat/completions")
    suspend fun chat(@Header("Authorization") auth: String, @Body request: DeepSeekRequest): DeepSeekResponse
}

interface GitHubApi {
    @GET("user")
    suspend fun getUser(@Header("Authorization") auth: String): GitHubUser

    @GET("user/repos")
    suspend fun getRepos(@Header("Authorization") auth: String, @Query("per_page") perPage: Int = 50): List<GitHubRepo>

    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun getFile(@Header("Authorization") auth: String, @Path("owner") owner: String, @Path("repo") repo: String, @Path("path") path: String): GitHubContent

    @PUT("repos/{owner}/{repo}/contents/{path}")
    suspend fun createFile(@Header("Authorization") auth: String, @Path("owner") owner: String, @Path("repo") repo: String, @Path("path") path: String, @Body body: GitHubCreateFile): GitHubCreateResponse
}

data class DeepSeekRequest(
    val model: String = "deepseek-chat",
    val messages: List<Message>,
    val stream: Boolean = false,
    val max_tokens: Int = 4096
)
data class Message(val role: String, val content: String)
data class DeepSeekResponse(val choices: List<Choice>? = null, val error: DeepSeekError? = null)
data class Choice(val message: Message)
data class DeepSeekError(val message: String)

data class GitHubUser(val login: String, val avatar_url: String?, val name: String?)
data class GitHubRepo(val name: String, val full_name: String, val html_url: String, val description: String?)
data class GitHubContent(val name: String, val path: String, val sha: String, val content: String?, val encoding: String?)
data class GitHubCreateFile(val message: String, val content: String, val sha: String? = null)
data class GitHubCreateResponse(val content: GitHubContent?)