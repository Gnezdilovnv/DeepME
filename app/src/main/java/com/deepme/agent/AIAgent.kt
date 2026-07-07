package com.deepme.agent
import android.content.Context
import com.deepme.network.*
import com.deepme.utils.*
import kotlinx.coroutines.*
import java.io.*

object AIAgent {
    private val prompt = "Ты DeepME AI Агент (DeepSeek V4 Pro). Команды: читаю /путь | записываю /путь\\nтекст\\nКОНЕЦ | выполняю cmd | анализирую /путь. Отвечай на русском."

    suspend fun processMessage(ctx: Context, msg: String, hist: List<Message>): String {
        val key = TokenManager.getDeepSeekKey(ctx)
        if (key.isEmpty()) return "❌ Настройте API ключ в Настройках"

        val ms = mutableListOf(Message("system", prompt))
        hist.takeLast(20).forEach { ms.add(it) }
        ms.add(Message("user", msg))

        try {
            var reply = call(key, ms)
            var i = 0
            while (i < 5 && reply.lowercase().let { it.contains("читаю ") || it.contains("записываю ") || it.contains("выполняю ") || it.contains("анализирую ") }) {
                val result = doAction(reply)
                ms.add(Message("assistant", reply.take(2000)))
                ms.add(Message("user", "Результат:\n$result\n\nПродолжай или дай ответ."))
                reply = call(key, ms)
                i++
            }
            return reply
        } catch (e: Exception) { return "❌ ${e.message}" }
    }

    private suspend fun call(key: String, ms: List<Message>): String = withContext(Dispatchers.IO) {
        val r = ApiClient.deepSeekApi.chat("Bearer $key", DeepSeekRequest(model = ApiClient.deepSeekModel, messages = ms, max_tokens = 4096))
        r.choices?.firstOrNull()?.message?.content ?: r.error?.message ?: "Нет ответа"
    }

    private suspend fun doAction(text: String): String = withContext(Dispatchers.IO) {
        try {
            val lower = text.lowercase()
            when {
                lower.contains("читаю ") -> {
                    val path = text.substringAfter("читаю").trim().replace("«","").replace("»","").replace("\"","").lines().first().trim()
                    val file = File(path)
                    when {
                        !file.exists() -> "❌ Файл не найден: $path"
                        file.isDirectory -> "📁 $path\n${file.listFiles()?.take(15)?.joinToString("\n") { "${if(it.isDirectory)"📁" else "📄"} ${it.name}" } ?: "пусто"}"
                        file.length() > 50000 -> "📄 ${file.name} (${file.length()/1024} КБ)\n${file.readText().take(10000)}\n... (обрезано)"
                        else -> file.readText()
                    }
                }
                lower.contains("записываю ") -> {
                    val path = text.substringAfter("записываю").trim().replace("«","").replace("»","").replace("\"","").lines().first().trim()
                    val content = text.substringAfter("записываю").substringAfter("\n").substringBefore("КОНЕЦ").trim()
                    if (content.isEmpty()) return@withContext "❌ Пусто. Используй: записываю /путь\\nтекст\\nКОНЕЦ"
                    val file = File(path)
                    file.parentFile?.mkdirs()
                    if (file.exists()) file.copyTo(File("$path.bak"), true)
                    file.writeText(content)
                    "✅ Записано: $path (${content.length} символов)"
                }
                lower.contains("выполняю ") -> {
                    val cmd = text.substringAfter("выполняю").trim().lines().first().trim()
                    val p = Runtime.getRuntime().exec(arrayOf("sh", "-c", "cd ~ && $cmd 2>&1"))
                    val out = p.inputStream.bufferedReader().readText()
                    p.waitFor()
                    out.ifEmpty { "(выполнено)" }.take(4000)
                }
                lower.contains("анализирую ") -> {
                    val path = text.substringAfter("анализирую").trim().replace("«","").replace("»","").replace("\"","").lines().first().trim()
                    val file = File(path)
                    if (file.exists()) {
                        val date = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm").format(java.util.Date(file.lastModified()))
                        "📸 ${file.name} | ${file.length()/1024} КБ | $date"
                    } else "❌ Файл не найден: $path"
                }
                else -> "Неизвестное действие"
            }
        } catch (e: Exception) { "❌ ${e.message}" }
    }
}