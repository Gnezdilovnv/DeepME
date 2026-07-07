package com.deepme.agent

import android.content.Context
import com.deepme.network.ApiClient
import com.deepme.network.DeepSeekRequest
import com.deepme.network.GitHubCreateFile
import com.deepme.network.Message
import com.deepme.utils.Logger
import com.deepme.utils.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.net.HttpURLConnection
import java.net.URL

object AIAgent {
    private val systemPrompt = """
Ты — DeepME AI Агент, полноценный помощник на русском языке. Ты работаешь на устройстве пользователя и имеешь доступ к:

📁 ФАЙЛЫ УСТРОЙСТВА
• Прочитать файл: скажи "читаю /путь/к/файлу" и укажи путь
• Записать файл: скажи "записываю /путь/к/файлу" и напиши содержимое после двоеточия

🖥️ ТЕРМИНАЛ
• Выполнить команду: скажи "выполняю команда"

🐙 GITHUB
• Список репозиториев: скажи "покажи репозитории"
• Создать файл: скажи "создаю в owner/repo/путь" и напиши содержимое
• Читать файл из репо: скажи "читаю из owner/repo/путь"

📸 РАБОТА С ФОТО
• Анализ фото: скажи "анализирую /путь/к/фото.jpg"

ВАЖНО:
• Отвечай всегда на русском языке
• Если пользователь просит что-то сделать — выполни действие и сообщи результат
• Не придумывай содержимое файлов, всегда читай их перед тем как говорить о них
• Если GitHub токен не настроен — скажи пользователю настроить его в Настройках
• Для работы с GitHub используй точные пути вида "владелец/репозиторий/путь/к/файлу"
""".trimIndent()

    suspend fun processMessage(context: Context, userMessage: String, history: List<Message>): String {
        val dsKey = TokenManager.getDeepSeekKey(context)
        val ghToken = TokenManager.getGitHubToken(context)

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
                val result = executeAction(context, reply, ghToken)
                messages.add(Message("assistant", reply))
                messages.add(Message("user", "Результат действия:\n$result\n\nПродолжай диалог или напиши итоговый ответ на русском языке."))

                response = withContext(Dispatchers.IO) {
                    ApiClient.deepSeekApi.chat("Bearer $dsKey", DeepSeekRequest(model = ApiClient.deepSeekModel, messages = messages))
                }
                reply = response.choices?.firstOrNull()?.message?.content ?: "Нет ответа"
                iterations++
            }
            reply
        } catch (e: Exception) {
            "❌ Ошибка связи с DeepSeek: ${e.message}"
        }
    }

    private fun needsAction(text: String): Boolean {
        val triggers = listOf("читаю ", "записываю ", "выполняю ", "покажи репозитории", "создаю в ", "читаю из ", "анализирую ")
        return triggers.any { text.lowercase().contains(it) }
    }

    private fun extractAction(text: String): String {
        val lower = text.lowercase()
        return when {
            lower.contains("читаю из ") && lower.contains("/") -> "GITHUB_READ"
            lower.contains("создаю в ") && lower.contains("/") -> "GITHUB_CREATE"
            lower.contains("покажи репозитории") -> "GITHUB_REPOS"
            lower.contains("читаю ") -> "READ_FILE"
            lower.contains("записываю ") -> "WRITE_FILE"
            lower.contains("выполняю ") -> "EXEC"
            lower.contains("анализирую ") -> "ANALYZE"
            else -> "UNKNOWN"
        }
    }

    private suspend fun executeAction(context: Context, text: String, ghToken: String): String = withContext(Dispatchers.IO) {
        try {
            val lower = text.lowercase()
            when {
                lower.contains("читаю ") && !lower.contains("читаю из ") -> {
                    val path = extractPath(text, "читаю")
                    val file = File(path)
                    if (file.exists() && file.isFile) {
                        val content = file.readText()
                        if (content.length > 10000) content.take(10000) + "\n... (файл большой, показано начало)"
                        else content
                    } else "❌ Файл не найден: $path"
                }
                lower.contains("записываю ") -> {
                    val path = extractPath(text, "записываю")
                    val content = extractContent(text)
                    val file = File(path)
                    file.parentFile?.mkdirs()
                    file.writeText(content)
                    "✅ Записано в $path (${content.length} символов)"
                }
                lower.contains("выполняю ") -> {
                    val cmd = text.substringAfter("выполняю").trim().lines().first().trim()
                    val pb = ProcessBuilder("sh", "-c", cmd)
                    pb.redirectErrorStream(true)
                    val process = pb.start()
                    val output = process.inputStream.bufferedReader().readText()
                    process.waitFor()
                    output.ifEmpty { "(команда выполнена, нет вывода)" }.take(5000)
                }
                lower.contains("покажи репозитории") -> {
                    if (ghToken.isEmpty()) return@withContext "❌ GitHub токен не настроен. Добавьте его в разделе «Настройки»"
                    val repos = ApiClient.gitHubApi.getRepos("token $ghToken", 50)
                    if (repos.isEmpty()) "У вас нет репозиториев"
                    else repos.joinToString("\n") { "📁 ${it.full_name}: ${it.description ?: "нет описания"}" }
                }
                lower.contains("создаю в ") -> {
                    if (ghToken.isEmpty()) return@withContext "❌ GitHub токен не настроен"
                    val fullPath = text.substringAfter("создаю в").trim().lines().first().trim()
                    val parts = fullPath.split("/")
                    if (parts.size < 3) return@withContext "❌ Укажите путь: владелец/репозиторий/путь/файл"
                    val owner = parts[0]; val repo = parts[1]; val path = parts.drop(2).joinToString("/")
                    val content = extractContent(text)
                    val encoded = java.util.Base64.getEncoder().encodeToString(content.toByteArray())
                    ApiClient.gitHubApi.createFile("token $ghToken", owner, repo, path, GitHubCreateFile("DeepME: создание $path", encoded))
                    "✅ Файл создан в GitHub: $owner/$repo/$path"
                }
                lower.contains("читаю из ") -> {
                    if (ghToken.isEmpty()) return@withContext "❌ GitHub токен не настроен"
                    val fullPath = text.substringAfter("читаю из").trim().lines().first().trim()
                    val parts = fullPath.split("/")
                    if (parts.size < 3) return@withContext "❌ Укажите путь: владелец/репозиторий/путь"
                    val owner = parts[0]; val repo = parts[1]; val path = parts.drop(2).joinToString("/")
                    val file = ApiClient.gitHubApi.getFile("token $ghToken", owner, repo, path)
                    val content = file.content?.let { java.util.Base64.getDecoder().decode(it) }?.toString(Charsets.UTF_8) ?: "Не удалось прочитать"
                    if (content.length > 10000) content.take(10000) + "\n... (файл большой)" else content
                }
                lower.contains("анализирую ") -> {
                    val path = extractPath(text, "анализирую")
                    val file = File(path)
                    if (file.exists()) "📸 Файл «${file.name}» найден (${file.length() / 1024} КБ). Отправьте его как вложение в чат для детального анализа."
                    else "❌ Файл не найден: $path"
                }
                else -> "Неизвестное действие"
            }
        } catch (e: Exception) {
            "❌ Ошибка: ${e.message}"
        }
    }

    private fun extractPath(text: String, keyword: String): String {
        return text.substringAfter(keyword).trim().lines().first().trim().replace("«", "").replace("»", "")
    }

    private fun extractContent(text: String): String {
        val lines = text.lines()
        val startIdx = lines.indexOfFirst { it.contains("записываю") || it.contains("создаю в") }
        if (startIdx < 0) return ""
        return lines.drop(startIdx + 1).joinToString("\n").trim()
    }
}