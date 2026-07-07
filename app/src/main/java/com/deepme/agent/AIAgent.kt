package com.deepme.agent

import android.content.Context
import com.deepme.network.ApiClient
import com.deepme.network.DeepSeekRequest
import com.deepme.network.Message
import com.deepme.utils.Logger
import com.deepme.utils.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.*

object AIAgent {
    private val systemPrompt = """
Ты — DeepME AI Агент на русском языке. Ты работаешь на Android-устройстве пользователя и можешь:

📁 ФАЙЛЫ УСТРОЙСТВА
• Прочитать файл: скажи "читаю /путь/к/файлу"
• Записать файл: скажи "записываю /путь/к/файлу" и содержимое на следующей строке

🖥️ ТЕРМИНАЛ
• Выполнить команду: скажи "выполняю команда"
• Для Git/GitHub используй: "выполняю gh repo list", "выполняю git clone ...", "выполняю gh repo create ..."

🐙 GITHUB (через Termux)
• Список репо: "выполняю gh repo list"
• Клонировать: "выполняю git clone https://github.com/user/repo"
• Создать репо: "выполняю gh repo create name --public"
• Создать файл: сначала запиши локально, потом "выполняю git add ... && git commit -m ... && git push"

📸 ФОТО
• Анализ: скажи "анализирую /путь/к/фото.jpg"

ПРАВИЛА:
• Отвечай на русском
• Для GitHub используй команды терминала (git/gh)
• Не придумывай содержимое файлов — сначала прочитай их
• Команды выполняются в домашней директории Termux
• Если gh не установлен — скажи пользователю: "pkg install gh"
""".trimIndent()

    suspend fun processMessage(context: Context, userMessage: String, history: List<Message>): String {
        val dsKey = TokenManager.getDeepSeekKey(context)
        if (dsKey.isEmpty()) return "❌ Настройте DeepSeek API ключ в разделе «Настройки»"

        val messages = mutableListOf(Message("system", systemPrompt))
        messages.addAll(history)
        messages.add(Message("user", userMessage))

        return try {
            var response = withContext(Dispatchers.IO) {
                ApiClient.deepSeekApi.chat("Bearer $dsKey", DeepSeekRequest(model = ApiClient.deepSeekModel, messages = messages))
            }
            var reply = response.choices?.firstOrNull()?.message?.content ?: "Нет ответа"
            var iterations = 0

            while (iterations < 5 && needsAction(reply)) {
                Logger.log("Действие AI: ${extractAction(reply)}")
                val result = executeAction(reply)
                messages.add(Message("assistant", reply))
                messages.add(Message("user", "Результат:\n$result\n\nПродолжай или дай итоговый ответ на русском."))

                response = withContext(Dispatchers.IO) {
                    ApiClient.deepSeekApi.chat("Bearer $dsKey", DeepSeekRequest(model = ApiClient.deepSeekModel, messages = messages))
                }
                reply = response.choices?.firstOrNull()?.message?.content ?: "Нет ответа"
                iterations++
            }
            reply
        } catch (e: Exception) {
            "❌ Ошибка: ${e.message}"
        }
    }

    private fun needsAction(text: String): Boolean {
        return listOf("читаю ", "записываю ", "выполняю ", "анализирую ").any { text.lowercase().contains(it) }
    }

    private fun extractAction(text: String): String {
        val lower = text.lowercase()
        return when {
            lower.contains("читаю ") -> "READ"
            lower.contains("записываю ") -> "WRITE"
            lower.contains("выполняю ") -> "EXEC"
            lower.contains("анализирую ") -> "ANALYZE"
            else -> "UNKNOWN"
        }
    }

    private suspend fun executeAction(text: String): String = withContext(Dispatchers.IO) {
        try {
            val lower = text.lowercase()
            when {
                lower.contains("читаю ") -> {
                    val path = extractPath(text, "читаю")
                    val file = File(path)
                    if (file.exists() && file.isFile) {
                        file.readText().let { if (it.length > 8000) it.take(8000) + "\n... (обрезано)" else it }
                    } else "❌ Файл не найден: $path"
                }
                lower.contains("записываю ") -> {
                    val path = extractPath(text, "записываю")
                    val content = extractContent(text)
                    File(path).apply { parentFile?.mkdirs() }.writeText(content)
                    "✅ Записано: $path (${content.length} символов)"
                }
                lower.contains("выполняю ") -> {
                    val cmd = text.substringAfter("выполняю").trim().lines().first().trim()
                    execCommand(cmd)
                }
                lower.contains("анализирую ") -> {
                    val path = extractPath(text, "анализирую")
                    val file = File(path)
                    if (file.exists()) "📸 Файл «${file.name}» (${file.length()/1024} КБ). Отправьте фото в чат для анализа."
                    else "❌ Файл не найден: $path"
                }
                else -> "Неизвестное действие"
            }
        } catch (e: Exception) {
            "❌ Ошибка: ${e.message}"
        }
    }

    private fun extractPath(text: String, keyword: String): String {
        return text.substringAfter(keyword).trim().lines().first().trim().replace("«","").replace("»","")
    }

    private fun extractContent(text: String): String {
        val lines = text.lines()
        val idx = lines.indexOfFirst { it.lowercase().contains("записываю") }
        return if (idx >= 0) lines.drop(idx + 1).joinToString("\n").trim() else ""
    }

    private fun execCommand(cmd: String): String {
        return try {
            val pb = ProcessBuilder("sh", "-c", "cd ~ && $cmd 2>&1")
            pb.redirectErrorStream(true)
            val process = pb.start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            output.ifEmpty { "(команда выполнена)" }.take(5000)
        } catch (e: Exception) {
            "❌ ${e.message}"
        }
    }
}