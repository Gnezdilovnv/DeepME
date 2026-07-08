package com.deepme.agent
import android.content.Context
import com.deepme.model.*
import com.deepme.network.*
import com.deepme.utils.*
import kotlinx.coroutines.*
import java.io.*
import java.util.Base64
object AIAgent{private val p="Ты DeepME AI Агент. Команды: 🐙 покажи репозитории|создай репо имя|создай файл owner/repo/путь\\nсодержимое|читай файл owner/repo/путь|обнови файл owner/repo/путь\\nсодержимое|удали файл owner/repo/путь 📁 читаю /путь|записываю /путь\\nтекст|редактирую /путь\\nтекст 🖥️ выполняю команда ⚙️ установи python/node/go/rust|покажи окружения. Отвечай кратко на русском."
suspend fun processMessage(ctx:Context,msg:String,hist:List<Message>):String{val k=TokenManager.getDeepSeekKey(ctx);if(k.isEmpty())return "❌ Нет ключа";val ms=mutableListOf(Message("system",p));hist.takeLast(20).forEach{ms.add(it)};ms.add(Message("user",msg))
try{var r=call(k,ms);var i=0;while(i<5&&hasAct(r)){val a=exec(ctx,r);ms.add(Message("assistant",r.take(2000)));ms.add(Message("user","✅ $a\nДай ответ."));r=call(k,ms);i++};return r}catch(e:Exception){return "❌ ${e.message}"}}
private suspend fun call(k:String,ms:List<Message>)=withContext(Dispatchers.IO){ApiClient.deepSeekApi.chat("Bearer $k",DeepSeekRequest(model=ApiClient.deepSeekModel,messages=ms,max_tokens=4096)).choices?.firstOrNull()?.message?.content?:"Нет ответа"}
private fun hasAct(t:String)=listOf("покажи репозитории","создай репо ","создай файл ","читай файл ","обнови файл ","удали файл ","читаю ","записываю ","редактирую ","выполняю ","установи ","покажи окружения").any{t.lowercase().contains(it)}
private suspend fun exec(ctx:Context,t:String):String=withContext(Dispatchers.IO){val gh=TokenManager.getGitHubToken(ctx);val api=ApiClient.gh(gh)
try{val l=t.lowercase()
if(l.contains("покажи репозитории")){if(gh.isEmpty())"❌ Нет токена" else api.getRepos("token $gh",30).let{if(it.isEmpty())"📭 Пусто" else"📁 Репо:\n"+it.joinToString("\n"){"• ${it.full_name}"}}}
else if(l.contains("создай репо ")){if(gh.isEmpty())"❌ Нет токена" else{val n=t.substringAfter("создай репо ").trim().lines()[0].trim();val r=api.createRepo("token $gh",CreateRepoRequest(name=n,auto_init=true));if(r.isSuccessful)"✅ ${r.body()?.html_url?:n}" else "❌ ${r.code()}"}}
else if(l.contains("создай файл ")){if(gh.isEmpty())"❌ Нет токена" else{val pt=t.substringAfter("создай файл ").trim().lines()[0].trim().split("/");if(pt.size<3)"❌ owner/repo/путь" else{val c=t.substringAfter("создай файл ").substringAfter("\n").trim();if(c.isEmpty())"❌ Нет текста" else{api.createOrUpdateFile("token $gh",pt[0],pt[1],pt.drop(2).joinToString("/"),FileContentRequest("DeepME",Base64.getEncoder().encodeToString(c.toByteArray())));"✅ ${pt[0]}/${pt[1]}"}}}}
else if(l.contains("обнови файл ")){if(gh.isEmpty())"❌ Нет токена" else{val pt=t.substringAfter("обнови файл ").trim().lines()[0].trim().split("/");if(pt.size<3)"❌ owner/repo/путь" else{val c=t.substringAfter("обнови файл ").substringAfter("\n").trim();if(c.isEmpty())"❌ Нет текста" else{val ex=api.getContent("token $gh",pt[0],pt[1],pt.drop(2).joinToString("/"));if(!ex.isSuccessful)"❌ Файл не найден" else{api.createOrUpdateFile("token $gh",pt[0],pt[1],pt.drop(2).joinToString("/"),FileContentRequest("DeepME update",Base64.getEncoder().encodeToString(c.toByteArray()),ex.body()?.sha));"✅ Обновлён"}}}}}
else if(l.contains("читай файл ")){if(gh.isEmpty())"❌ Нет токена" else{val pt=t.substringAfter("читай файл ").trim().lines()[0].trim().split("/");if(pt.size<3)"❌ owner/repo/путь" else{val r=api.getContent("token $gh",pt[0],pt[1],pt.drop(2).joinToString("/"));if(r.isSuccessful){val it=r.body();if(it?.encoding=="base64"&&it.content!=null)Base64.getDecoder().decode(it.content.replace("\n","")).toString(Charsets.UTF_8).take(8000)else"Тип:${it?.type}"}else"❌ ${r.code()}"}}}
else if(l.contains("удали файл ")){if(gh.isEmpty())"❌ Нет токена" else{val pt=t.substringAfter("удали файл ").trim().lines()[0].trim().split("/");if(pt.size<3)"❌ owner/repo/путь" else{val ex=api.getContent("token $gh",pt[0],pt[1],pt.drop(2).joinToString("/"));if(!ex.isSuccessful)"❌ Не найден" else{api.deleteFile("token $gh",pt[0],pt[1],pt.drop(2).joinToString("/"),DeleteFileRequest("DeepME delete",ex.body()?.sha?:""));"✅ Удалён"}}}}
else if(l.contains("читаю ")){val p=t.substringAfter("читаю ").trim().lines()[0].trim();val f=File(p);if(!f.exists())"❌ Нет" else if(f.isDirectory)"📁 Папка" else f.readText().take(8000)}
else if(l.contains("записываю ")){val p=t.substringAfter("записываю ").trim().lines()[0].trim();val c=t.substringAfter("записываю ").substringAfter("\n").trim();if(c.isEmpty())"❌ Пусто" else{File(p).apply{parentFile?.mkdirs()}.writeText(c);"✅ $p"}}
else if(l.contains("редактирую ")){val p=t.substringAfter("редактирую ").trim().lines()[0].trim();val c=t.substringAfter("редактирую ").substringAfter("\n").trim();val f=File(p);if(!f.exists())"❌ Нет" else{f.writeText(c);"✅ $p"}}
else if(l.contains("выполняю ")){val c=t.substringAfter("выполняю ").trim().lines()[0].trim();Runtime.getRuntime().exec(arrayOf("sh","-c","$c 2>&1")).inputStream.bufferedReader().readText().take(4000).ifEmpty{"(ок)"}}
else if(l.contains("установи ")){val e=t.substringAfter("установи ").trim().lines()[0].trim().lowercase();val c=Environments.getSetupCmd(e);if(c=="pkg update -y")"❌ Нет среды: $e\nДоступно: python,node,go,rust,java,kotlin" else"⚙️ Установка $e:\n$c\n\nЗапусти в Терминале"}
else if(l.contains("покажи окружения"))Environments.list.joinToString("\n"){"${it.name} (${it.language})"}+"\nСкажи: установи python"
else"❓ Неизвестно"}
catch(e:Exception){"❌ ${e.message}"}}}