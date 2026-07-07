package com.deepme.network

import com.deepme.utils.Logger
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    private const val DEEPSEEK_URL = "https://api.deepseek.com/"
    private const val GITHUB_URL = "https://api.github.com/"

    // Модели V4 (актуальные)
    var deepSeekModel = "deepseek-v4-pro"
    
    val availableModels = listOf(
        ModelInfo("deepseek-v4-flash", "DeepSeek V4 Flash", "Быстрый", "0.14", "0.28"),
        ModelInfo("deepseek-v4-pro", "DeepSeek V4 Pro", "Максимальный", "0.27", "1.10"),
        ModelInfo("deepseek-chat", "DeepSeek Chat (устар.)", "До 24.07.2026", "0.27", "1.10"),
        ModelInfo("deepseek-reasoner", "DeepSeek Reasoner (устар.)", "До 24.07.2026", "0.55", "2.19")
    )

    data class ModelInfo(val id: String, val name: String, val desc: String, val priceIn: String, val priceOut: String)

    private val logging = HttpLoggingInterceptor { Logger.log("HTTP: $it") }
        .apply { level = HttpLoggingInterceptor.Level.BODY }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .addInterceptor(logging)
        .build()

    val deepSeekApi: DeepSeekApi by lazy {
        Retrofit.Builder().baseUrl(DEEPSEEK_URL).client(client)
            .addConverterFactory(GsonConverterFactory.create()).build().create(DeepSeekApi::class.java)
    }

    val gitHubApi: GitHubApi by lazy {
        Retrofit.Builder().baseUrl(GITHUB_URL).client(client)
            .addConverterFactory(GsonConverterFactory.create()).build().create(GitHubApi::class.java)
    }
}