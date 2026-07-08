package com.deepme.agent
import android.content.Context
import com.deepme.model.*
import com.deepme.network.*
import com.deepme.utils.*
import kotlinx.coroutines.*
import java.io.*
import java.util.Base64

object AIAgent {
    private val prompt = "Ты DeepME AI Агент. Команды: покажи репозитории | создай репо ИМЯ | создай файл owner/repo/путь\\nСОДЕРЖИМОЕ | читай файл owner/repo/путь | обнови файл owner/repo/путь\\nСОДЕРЖИМОЕ | удали файл owner/repo/путь | читаю /путь | записываю /путь\\nТЕКСТ | редактирую /путь\\nТЕКСТ | выполняю КОМАНДА | установи python/node/go/rust/java/kotlin | покажи окружения. Отвечай кратко на русском."

    suspend fun processMessage(ctx: Context, msg: String, hist: List<Message>): String {
        val k = TokenManager.getDeepSeekKey(ctx)
        if (k.isEmpty()) return "❌ Нет API ключа"
        val ms = mutableListOf(Message("system", prompt))
        hist.takeLast(20).forEach { ms.add(it) }
        ms.add(Message("user", msg))
        try {
            var r = callAI(k, ms)
            var i = 0
            while (i < 5 && hasAct(r)) {
                val a = exec(ctx, r)
                ms.add(Message("assistant", r.take(2000)))
                ms.add(Message("user", "✅ $a\nДай ответ."))
                r = callAI(k, ms)
                i++
            }
            return r
        } catch (e: Exception) { return "❌ ${e.message}" }
    }

    private suspend fun callAI(k: String, ms: List<Message>) = withContext(Dispatchers.IO) {
        ApiClient.deepSeekApi.chat("Bearer $k", DeepSeekRequest(model = ApiClient.deepSeekModel, messages = ms, max_tokens = 4096))
            .choices?.firstOrNull()?.message?.content ?: "Нет ответа"
    }

    private fun hasAct(t: String) = listOf("покажи репозитории","создай репо","создай файл","читай файл","обнови файл","удали файл","читаю","записываю","редактирую","выполняю","установи","покажи окружения").any { t.lowercase().contains(it) }

    private suspend fun exec(ctx: Context, t: String): String = withContext(Dispatchers.IO) {
        val gh = TokenManager.getGitHubToken(ctx)
        val api = ApiClient.gh(gh)
        try {
            val l = t.lowercase()
            when {
                l.contains("покажи репозитории") -> {
                    if (gh.isEmpty()) "❌ GitHub токен не настроен"
                    else {
                        val repos = api.getRepos("token $gh", 30)
                        if (repos.isEmpty()) "📭 Репозиториев нет"
                        else "📁 Найдено ${repos.size}:\n" + repos.joinToString("\n") { "• ${it.full_name}" }
                    }
                }
                l.contains("создай репо") -> {
                    if (gh.isEmpty()) "❌ Нет GitHub токена"
                    else {
                        val n = t.substringAfter("создай репо").trim().lines()[0].trim()
                        val r = api.createRepo("token $gh", CreateRepoRequest(name = n, auto_init = true))
                        if (r.isSuccessful) "✅ Репозиторий $n создан: ${r.body()?.html_url}" else "❌ ${r.code()}"
                    }
                }
                l.contains("создай файл") -> {
                    if (gh.isEmpty()) "❌ Нет токена"
                    else {
                        val pt = t.substringAfter("создай файл").trim().lines()[0].trim().split("/")
                        if (pt.size < 3) "❌ Формат: owner/repo/путь"
                        else {
                            val c = t.substringAfter("создай файл").substringAfter("\n").trim()
                            if (c.isEmpty()) "❌ Напиши содержимое"
                            else {
                                api.createOrUpdateFile("token $gh", pt[0], pt[1], pt.drop(2).joinToString("/"),
                                    FileContentRequest("DeepME", Base64.getEncoder().encodeToString(c.toByteArray())))
                                "✅ Файл создан в ${pt[0]}/${pt[1]}"
                            }
                        }
                    }
                }
                l.contains("обнови файл") -> {
                    if (gh.isEmpty()) "❌ Нет токена"
                    else {
                        val pt = t.substringAfter("обнови файл").trim().lines()[0].trim().split("/")
                        if (pt.size < 3) "❌ owner/repo/путь"
                        else {
                            val c = t.substringAfter("обнови файл").substringAfter("\n").trim()
                            if (c.isEmpty()) "❌ Напиши содержимое"
                            else {
                                val ex = api.getContent("token $gh", pt[0], pt[1], pt.drop(2).joinToString("/"))
                                if (!ex.isSuccessful) "❌ Файл не найден"
                                else {
                                    api.createOrUpdateFile("token $gh", pt[0], pt[1], pt.drop(2).joinToString("/"),
                                        FileContentRequest("DeepME update", Base64.getEncoder().encodeToString(c.toByteArray()), ex.body()?.sha))
                                    "✅ Файл обновлён"
                                }
                            }
                        }
                    }
                }
                l.contains("читай файл") -> {
                    if (gh.isEmpty()) "❌ Нет токена"
                    else {
                        val pt = t.substringAfter("читай файл").trim().lines()[0].trim().split("/")
                        if (pt.size < 3) "❌ owner/repo/путь"
                        else {
                            val r = api.getContent("token $gh", pt[0], pt[1], pt.drop(2).joinToString("/"))
                            if (r.isSuccessful) {
                                val it = r.body()
                                if (it?.encoding == "base64" && it.content != null)
                                    Base64.getDecoder().decode(it.content.replace("\n", "")).toString(Charsets.UTF_8).take(8000)
                                else "Тип: ${it?.type}"
                            } else "❌ ${r.code()}"
                        }
                    }
                }
                l.contains("удали файл") -> {
                    if (gh.isEmpty()) "❌ Нет токена"
                    else {
                        val pt = t.substringAfter("удали файл").trim().lines()[0].trim().split("/")
                        if (pt.size < 3) "❌ owner/repo/путь"
                        else {
                            val ex = api.getContent("token $gh", pt[0], pt[1], pt.drop(2).joinToString("/"))
                            if (!ex.isSuccessful) "❌ Не найден"
                            else {
                                api.deleteFile("token $gh", pt[0], pt[1], pt.drop(2).joinToString("/"),
                                    DeleteFileRequest("DeepME delete", ex.body()?.sha ?: ""))
                                "✅ Удалён"
                            }
                        }
                    }
                }
                l.contains("читаю") && !l.contains("читай файл") -> {
                    val p = t.substringAfter("читаю").trim().lines()[0].trim()
                    val f = File(p)
                    if (!f.exists()) "❌ Нет файла" else f.readText().take(8000)
                }
                l.contains("записываю") -> {
                    val p = t.substringAfter("записываю").trim().lines()[0].trim()
                    val c = t.substringAfter("записываю").substringAfter("\n").trim()
                    if (c.isEmpty()) "❌ Пусто" else { File(p).apply { parentFile?.mkdirs() }.writeText(c); "✅ $p" }
                }
                l.contains("редактирую") -> {
                    val p = t.substringAfter("редактирую").trim().lines()[0].trim()
                    val c = t.substringAfter("редактирую").substringAfter("\n").trim()
                    val f = File(p)
                    if (!f.exists()) "❌ Нет файла" else { f.writeText(c); "✅ $p" }
                }
                l.contains("выполняю") -> {
                    val c = t.substringAfter("выполняю").trim().lines()[0].trim()
                    Runtime.getRuntime().exec(arrayOf("sh", "-c", "$c 2>&1")).inputStream.bufferedReader().readText().take(4000).ifEmpty { "(ок)" }
                }
                l.contains("установи") -> {
                    val e = t.substringAfter("установи").trim().lines()[0].trim().lowercase()
                    val c = Environments.getSetupCmd(e)
                    if (c == "pkg update -y") "❌ Нет среды: $e\nДоступно: python, node, go, rust, java, kotlin"
                    else "⚙️ Установка $e:\n$c\n\nВыполни в Терминале"
                }
                l.contains("покажи окружения") -> {
                    Environments.list.joinToString("\n") { "${it.name} (${it.language})" } + "\nСкажи: установи python"
                }
                else -> "❓ Неизвестная команда"
            }
        } catch (e: Exception) { "❌ ${e.message}" }
    }
}
