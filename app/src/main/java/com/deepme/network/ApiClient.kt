package com.deepme.network
import com.deepme.utils.Logger
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
object ApiClient{var deepSeekModel="deepseek-v4-pro";data class M(val id:String,val name:String,val desc:String,val pi:String,val po:String);val models=listOf(M("deepseek-v4-flash","V4 Flash","Быстрый","0.14","0.28"),M("deepseek-v4-pro","V4 Pro","Макс.","0.27","1.10"),M("deepseek-chat","V3","Устар.","0.27","1.10"),M("deepseek-reasoner","R1","Устар.","0.55","2.19"))
private fun c(t:String?=null)=OkHttpClient.Builder().connectTimeout(30,java.util.concurrent.TimeUnit.SECONDS).readTimeout(60,java.util.concurrent.TimeUnit.SECONDS).addInterceptor(HttpLoggingInterceptor{Logger.log("API:$it")}.apply{level=HttpLoggingInterceptor.Level.HEADERS}).apply{t?.let{addInterceptor{c->c.proceed(c.request().newBuilder().addHeader("Authorization","Bearer $it").build())}}}.build()
val deepSeekApi:DeepSeekApi by lazy{Retrofit.Builder().baseUrl("https://api.deepseek.com/").client(c()).addConverterFactory(GsonConverterFactory.create()).build().create(DeepSeekApi::class.java)}
val gitHubApi:GitHubApi by lazy{Retrofit.Builder().baseUrl("https://api.github.com/").client(c()).addConverterFactory(GsonConverterFactory.create()).build().create(GitHubApi::class.java)}
fun gh(t:String):GitHubApi=Retrofit.Builder().baseUrl("https://api.github.com/").client(c(t)).addConverterFactory(GsonConverterFactory.create()).build().create(GitHubApi::class.java)}