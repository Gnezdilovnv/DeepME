package com.deepme.agent

import android.content.Context
import com.deepme.network.*
import com.deepme.utils.*
import kotlinx.coroutines.*
import java.io.*
import java.util.Base64

object AIAgent {
    private val prompt = """
Ты DeepME AI Агент. Ты РЕАЛЬНО выполняешь действия:

📁 ФАЙЛЫ:
- "читаю /путь" — читаю файл
- "записываю /путь" — записываю (содержимое на следующей строке)

🖥️ ТЕРМИНАЛ:
- "выполняю команда" — выполняю в терминале

🐙 GITHUB (реальное API):
- "покажи репозитории" — список репо
- "создай файл owner/repo/путь" — создаю файл (содержимое на следующей строке)
- "читай файл owner/repo/путь" — читаю файл из репо

📸 АНАЛИЗ:
- "анализирую /путь" — информация о файле

Отвечай на русском кратко. Когда просят действие — я его ВЫПОЛНЯЮ.
""".trimIndent()

    suspend fun processMessage(ctx: Context, msg: String, hist: List<Message>): String {
        val key = TokenManager.getDeepSeekKey(ctx)
        if (key.isEmpty()) return "❌ Настройте API ключ в Настройках"
        val ms = mutableListOf(Message("system", prompt))
        hist.takeLast(20).forEach { ms.add(it) }
        ms.add(Message("user", msg))
        try {
            var reply = callAI(key, ms)
            var i = 0
            while (i < 5 && hasAction(reply)) {
                val result = executeAction(ctx, reply)
                ms.add(Message("assistant", reply.take(2000)))
                ms.add(Message("user", "✅ ВЫПОЛНЕНО:\n$result\n\nПродолжай."))
                reply = callAI(key, ms)
                i++
            }
            return reply
        } catch (e: Exception) { return "❌ ${e.message}" }
    }

    private suspend fun callAI(key: String, ms: List<Message>) = withContext(Dispatchers.IO) {
        ApiClient.deepSeekApi.chat("Bearer $key", DeepSeekRequest(model = ApiClient.deepSeekModel, messages = ms, max_tokens = 4096))
            .choices?.firstOrNull()?.message?.content ?: "Нет ответа"
    }

    private fun hasAction(t: String) = listOf("читаю ","записываю ","выполняю ","покажи репозитории","создай файл ","читай файл ","анализирую ").any { t.lowercase().contains(it) }

    private suspend fun executeAction(ctx: Context, text: String) = withContext(Dispatchers.IO) {
        val gh = TokenManager.getGitHubToken(ctx)
        try {
            when {
                text.lowercase().contains("покажи репозитории") -> {
                    if (gh.isEmpty()) "❌ GitHub токен не настроен"
                    else ApiClient.gitHubApi.getRepos("token $gh", 30).let { if(it.isEmpty()) "📭 Нет репо" else "📁 ${it.size} репо:\n"+it.joinToString("\n") { "• ${it.full_name}" } }
                }
                text.lowercase().contains("создай файл ") -> {
                    if (gh.isEmpty()) "❌ Нет токена GitHub"
                    else {
                        val p = text.substringAfter("создай файл ").trim().lines().first().trim().split("/")
                        if (p.size < 3) "❌ Формат: owner/repo/путь"
                        else {
                            val c = text.substringAfter("создай файл ").substringAfter("\n").trim()
                            if (c.isEmpty()) "❌ Напиши содержимое"
                            else {
                                ApiClient.gitHubApi.createFile("token $gh", p[0], p[1], p.drop(2).joinToString("/"),
                                    GitHubCreateFile("DeepME", Base64.getEncoder().encodeToString(c.toByteArray())))
                                "✅ Файл создан в ${p[0]}/${p[1]}"
                            }
                        }
                    }
                }
                text.lowercase().contains("читай файл ") -> {
                    if (gh.isEmpty()) "❌ Нет токена"
                    else {
                        val p = text.substringAfter("читай файл ").trim().lines().first().trim().split("/")
                        if (p.size < 3) "❌ Формат: owner/repo/путь"
                        else {
                            val f = ApiClient.gitHubApi.getFile("token $gh", p[0], p[1], p.drop(2).joinToString("/"))
                            Base64.getDecoder().decode(f.content?.replace("\n","") ?: "").toString(Charsets.UTF_8).take(8000)
                        }
                    }
                }
                text.lowercase().contains("читаю ") -> File(text.substringAfter("читаю ").trim().lines().first().trim()).let {
                    if(!it.exists()) "❌ Нет файла" else if(it.isDirectory) "📁 Папка" else it.readText().take(8000) }
                text.lowercase().contains("записываю ") -> {
                    val path = text.substringAfter("записываю ").trim().lines().first().trim()
                    val c = text.substringAfter("записываю ").substringAfter("\n").trim()
                    if(c.isEmpty()) "❌ Пусто" else { File(path).apply { parentFile?.mkdirs() }.writeText(c); "✅ $path" }
                }
                text.lowercase().contains("выполняю ") -> {
                    val cmd = text.substringAfter("выполняю ").trim().lines().first().trim()
                    Runtime.getRuntime().exec(arrayOf("sh","-c","$cmd 2>&1")).inputStream.bufferedReader().readText().take(4000).ifEmpty{"(ок)"}
                }
                text.lowercase().contains("анализирую ") -> File(text.substringAfter("анализирую ").trim().lines().first().trim()).let {
                    if(it.exists()) "📸 ${it.name} | ${it.length()/1024}KB" else "❌ Нет" }
                else -> "?"
            }
        } catch (e: Exception) { "❌ ${e.message}" }
    }
}
