package com.deepme.network
import com.deepme.utils.Logger
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    var deepSeekModel = "deepseek-v4-pro"

    data class ModelInfo(val id: String, val name: String, val desc: String, val priceIn: String, val priceOut: String)
    val models = listOf(
        ModelInfo("deepseek-v4-flash", "V4 Flash ⚡", "Быстрый", "0.14", "0.28"),
        ModelInfo("deepseek-v4-pro", "V4 Pro 🧠", "Максимальный", "0.27", "1.10"),
        ModelInfo("deepseek-chat", "V3 Chat", "Устар. 24.07.26", "0.27", "1.10"),
        ModelInfo("deepseek-reasoner", "R1", "Устар. 24.07.26", "0.55", "2.19")
    )

    private val logging = HttpLoggingInterceptor { Logger.log("API: $it") }
        .apply { level = HttpLoggingInterceptor.Level.HEADERS }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .addInterceptor(logging)
        .build()

    val deepSeekApi: DeepSeekApi by lazy {
        Retrofit.Builder().baseUrl("https://api.deepseek.com/").client(client)
            .addConverterFactory(GsonConverterFactory.create()).build().create(DeepSeekApi::class.java)
    }

    val gitHubApi: GitHubApi by lazy {
        Retrofit.Builder().baseUrl("https://api.github.com/").client(client)
            .addConverterFactory(GsonConverterFactory.create()).build().create(GitHubApi::class.java)
    }
}