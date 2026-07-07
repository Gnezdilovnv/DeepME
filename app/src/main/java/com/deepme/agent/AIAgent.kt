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
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

object AIAgent {
    private val systemPrompt = """
Ты DeepME AI Agent с полным доступом к устройству. Ты можешь:

1. 💬 ОТВЕЧАТЬ на вопросы
2. 📁 ЧИТАТЬ ФАЙЛЫ — команда: READ_FILE /path/to/file
3. 📝 ПИСАТЬ ФАЙЛЫ — команда: WRITE_FILE /path/to/file
   содержимое...
   END_WRITE
4. 🖥️ ВЫПОЛНЯТЬ КОМАНДЫ — команда: EXEC command
5. 🐙 GITHUB — чтение репозиториев: GITHUB_REPOS
6. 📤 GITHUB — создание файла: GITHUB_CREATE owner/repo/path
   содержимое...
   END_CREATE
7. 📸 АНАЛИЗ ФОТО — опиши что на фото: ANALYZE_IMAGE /path/to/photo.jpg

Отвечай на русском. Если нужно выполнить действие — используй команды.
Не выдумывай содержимое файлов без команды READ_FILE.
""".trimIndent()

    suspend fun processMessage(context: Context, userMessage: String, history: List<Message>): String {
        val dsKey = TokenManager.getDeepSeekKey(context)
        val ghToken = TokenManager.getGitHubToken(context)

        if (dsKey.isEmpty()) return "❌ Настройте DeepSeek API ключ в Настройках"

        val messages = mutableListOf(Message("system", systemPrompt))
        messages.addAll(history)
        messages.add(Message("user", userMessage))

        return try {
            var response = withContext(Dispatchers.IO) {
                ApiClient.deepSeekApi.chat("Bearer $dsKey", DeepSeekRequest(
                    model = ApiClient.deepSeekModel,
                    messages = messages
                ))
            }

            var reply = response.choices?.firstOrNull()?.message?.content ?: "Нет ответа"
            var maxIterations = 5

            while (maxIterations > 0 && containsCommand(reply)) {
                Logger.log("AI requested action: ${extractCommand(reply)}")
                val actionResult = executeCommand(context, reply, ghToken)
                messages.add(Message("assistant", reply))
                messages.add(Message("user", "Результат: $actionResult\nПродолжай или напиши итоговый ответ."))

                response = withContext(Dispatchers.IO) {
                    ApiClient.deepSeekApi.chat("Bearer $dsKey", DeepSeekRequest(
                        model = ApiClient.deepSeekModel,
                        messages = messages
                    ))
                }
                reply = response.choices?.firstOrNull()?.message?.content ?: "Нет ответа"
                maxIterations--
            }

            reply
        } catch (e: Exception) {
            "❌ Ошибка: ${e.message}"
        }
    }

    private fun containsCommand(text: String): Boolean {
        return listOf("READ_FILE", "WRITE_FILE", "EXEC", "GITHUB_REPOS", "GITHUB_CREATE", "ANALYZE_IMAGE").any { text.contains(it) }
    }

    private fun extractCommand(text: String): String {
        return listOf("READ_FILE", "WRITE_FILE", "EXEC", "GITHUB_REPOS", "GITHUB_CREATE", "ANALYZE_IMAGE")
            .firstOrNull { text.contains(it) } ?: "UNKNOWN"
    }

    private suspend fun executeCommand(context: Context, text: String, ghToken: String): String = withContext(Dispatchers.IO) {
        try {
            when {
                text.contains("READ_FILE") -> {
                    val path = text.substringAfter("READ_FILE").trim().lines().first().trim()
                    val file = File(path)
                    if (file.exists() && file.isFile) {
                        file.readText().take(5000) + if (file.readText().length > 5000) "\n... (обрезано)" else ""
                    } else "❌ Файл не найден: $path"
                }
                text.contains("WRITE_FILE") -> {
                    val path = text.substringAfter("WRITE_FILE").trim().lines().first().trim()
                    val content = text.substringAfter("WRITE_FILE").substringAfter("\n").substringBefore("END_WRITE").trim()
                    val file = File(path)
                    file.parentFile?.mkdirs()
                    file.writeText(content)
                    "✅ Файл записан: $path (${content.length} байт)"
                }
                text.contains("EXEC") -> {
                    val cmd = text.substringAfter("EXEC").trim().lines().first().trim()
                    val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
                    val reader = BufferedReader(InputStreamReader(process.inputStream))
                    val errReader = BufferedReader(InputStreamReader(process.errorStream))
                    val output = StringBuilder()
                    reader.forEachLine { output.appendLine(it) }
                    errReader.forEachLine { output.appendLine("ERR: $it") }
                    process.waitFor()
                    output.toString().trim().take(3000)
                }
                text.contains("GITHUB_REPOS") -> {
                    if (ghToken.isEmpty()) return@withContext "❌ GitHub токен не настроен"
                    val repos = ApiClient.gitHubApi.getRepos("Bearer $ghToken", 50)
                    repos.joinToString("\n") { "📁 ${it.full_name}: ${it.description ?: "нет описания"}" }
                }
                text.contains("GITHUB_CREATE") -> {
                    if (ghToken.isEmpty()) return@withContext "❌ GitHub токен не настроен"
                    val repoPath = text.substringAfter("GITHUB_CREATE").trim().lines().first().trim()
                    val parts = repoPath.split("/")
                    if (parts.size < 3) return@withContext "❌ Формат: owner/repo/path/to/file"
                    val owner = parts[0]
                    val repo = parts[1]
                    val path = parts.drop(2).joinToString("/")
                    val content = text.substringAfter("GITHUB_CREATE").substringAfter("\n").substringBefore("END_CREATE").trim()
                    val encoded = java.util.Base64.getEncoder().encodeToString(content.toByteArray())
                    ApiClient.gitHubApi.createFile("Bearer $ghToken", owner, repo, path,
                        GitHubCreateFile("DeepME: $path", encoded))
                    "✅ Файл создан: $owner/$repo/$path"
                }
                text.contains("ANALYZE_IMAGE") -> {
                    val path = text.substringAfter("ANALYZE_IMAGE").trim().lines().first().trim()
                    val file = File(path)
                    if (file.exists()) "📸 Файл найден: ${file.name}, размер: ${file.length() / 1024} KB. Отправь фото в чат для анализа."
                    else "❌ Фото не найдено: $path"
                }
                else -> "Неизвестная команда"
            }
        } catch (e: Exception) {
            "❌ Ошибка: ${e.message}"
        }
    }
}