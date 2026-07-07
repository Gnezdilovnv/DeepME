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

    var deepSeekModel = "deepseek-chat"

    private val logging = HttpLoggingInterceptor { Logger.log("HTTP: $it") }
        .apply { level = HttpLoggingInterceptor.Level.BODY }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
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