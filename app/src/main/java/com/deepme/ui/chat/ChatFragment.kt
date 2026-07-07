package com.deepme.ui.chat

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.deepme.R
import com.deepme.agent.AIAgent
import com.deepme.network.ApiClient
import com.deepme.network.Message
import com.deepme.utils.Logger
import com.deepme.utils.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ChatFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ChatAdapter
    private lateinit var inputEditText: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var progressBar: ProgressBar
    private lateinit var modelSpinner: Spinner
    private lateinit var projectSpinner: Spinner
    private lateinit var newProjectButton: Button
    private val messages = mutableListOf<ChatMessage>()
    private val models = listOf("deepseek-chat", "deepseek-reasoner", "deepseek-v3")
    private var currentProject = "default"
    private val projects = mutableListOf("default")

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_chat, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerView = view.findViewById(R.id.recycler_view)
        inputEditText = view.findViewById(R.id.input_edit_text)
        sendButton = view.findViewById(R.id.send_button)
        progressBar = view.findViewById(R.id.progress_bar)
        modelSpinner = view.findViewById(R.id.model_spinner)
        projectSpinner = view.findViewById(R.id.project_spinner)
        newProjectButton = view.findViewById(R.id.new_project_button)

        adapter = ChatAdapter(messages)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter

        // Модели
        val modelAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, models)
        modelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        modelSpinner.adapter = modelAdapter
        modelSpinner.setSelection(models.indexOf(ApiClient.deepSeekModel))
        modelSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                ApiClient.deepSeekModel = models[pos]
                Logger.log("Model: ${models[pos]}")
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        // Проекты
        loadProjects()
        updateProjectSpinner()
        newProjectButton.setOnClickListener {
            val name = inputEditText.text.toString().trim()
            if (name.isNotEmpty() && !projects.contains(name)) {
                projects.add(name)
                saveProjects()
                updateProjectSpinner()
                projectSpinner.setSelection(projects.size - 1)
                inputEditText.setText("")
                Toast.makeText(context, "✅ Проект  создан", Toast.LENGTH_SHORT).show()
            }
        }

        loadHistory()

        if (!TokenManager.isAuthorized(requireContext())) {
            addMessage("⚠️ Настройте API ключи в Настройках", false)
        } else {
            addMessage("🤖 DeepME Agent готов! Модель: ${ApiClient.deepSeekModel}\nЯ могу читать/писать файлы, выполнять команды, работать с GitHub.", false)
        }

        sendButton.setOnClickListener {
            val text = inputEditText.text.toString().trim()
            if (text.isNotEmpty()) sendToAI(text)
        }
    }

    private fun sendToAI(text: String) {
        if (!TokenManager.isAuthorized(requireContext())) {
            addMessage("❌ Настройте API ключи в Настройках", false)
            return
        }

        inputEditText.setText("")
        addMessage(text, true)
        saveHistory()
        progressBar.visibility = View.VISIBLE
        sendButton.isEnabled = false

        lifecycleScope.launch {
            try {
                val history = messages.map { Message(if (it.isUser) "user" else "assistant", it.text) }.takeLast(20)
                val reply = withContext(Dispatchers.IO) {
                    AIAgent.processMessage(requireContext(), text, history)
                }
                progressBar.visibility = View.GONE
                sendButton.isEnabled = true
                addMessage(reply, false)
                saveHistory()
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                sendButton.isEnabled = true
                addMessage("❌ ${e.message}", false)
            }
        }
    }

    private fun addMessage(text: String, isUser: Boolean) {
        messages.add(ChatMessage(text, isUser))
        adapter.notifyItemInserted(messages.size - 1)
        recyclerView.scrollToPosition(messages.size - 1)
    }

    // История проектов
    private fun getHistoryFile(): File {
        val dir = File(requireContext().filesDir, "projects")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "$currentProject.json")
    }

    private fun loadHistory() {
        val file = getHistoryFile()
        if (file.exists()) {
            try {
                val json = JSONArray(file.readText())
                messages.clear()
                for (i in 0 until json.length()) {
                    val obj = json.getJSONObject(i)
                    messages.add(ChatMessage(obj.getString("text"), obj.getBoolean("isUser")))
                }
                adapter.notifyDataSetChanged()
                Logger.log("Loaded ${messages.size} messages from $currentProject")
            } catch (e: Exception) {
                Logger.log("History load error: ${e.message}")
            }
        }
    }

    private fun saveHistory() {
        try {
            val json = JSONArray()
            for (msg in messages.takeLast(100)) {
                json.put(JSONObject().put("text", msg.text).put("isUser", msg.isUser))
            }
            getHistoryFile().writeText(json.toString())
        } catch (e: Exception) {
            Logger.log("History save error: ${e.message}")
        }
    }

    private fun loadProjects() {
        val dir = File(requireContext().filesDir, "projects")
        if (dir.exists()) {
            dir.listFiles()?.filter { it.extension == "json" }?.forEach {
                projects.add(it.nameWithoutExtension)
            }
        }
        if (!projects.contains("default")) projects.add(0, "default")
    }

    private fun saveProjects() {
        // Projects are saved as files automatically
    }

    private fun updateProjectSpinner() {
        val pAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, projects)
        pAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        projectSpinner.adapter = pAdapter
        projectSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                saveHistory()
                currentProject = projects[pos]
                messages.clear()
                loadHistory()
                Logger.log("Project: $currentProject")
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    override fun onPause() {
        super.onPause()
        saveHistory()
    }

    data class ChatMessage(val text: String, val isUser: Boolean)
    inner class ChatAdapter(private val messages: List<ChatMessage>) : RecyclerView.Adapter<ChatAdapter.VH>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            return VH(LayoutInflater.from(parent.context).inflate(R.layout.item_message, parent, false))
        }
        override fun onBindViewHolder(holder: VH, pos: Int) {
            val msg = messages[pos]
            holder.textView.text = msg.text
            holder.textView.setBackgroundResource(if (msg.isUser) R.drawable.bg_user_message else R.drawable.bg_assistant_message)
            holder.textView.setTextColor(if (msg.isUser) 0xFFFFFFFF.toInt() else 0xFFE6EDF3.toInt())
        }
        override fun getItemCount() = messages.size
        inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val textView: TextView = itemView.findViewById(R.id.message_text)
        }
    }
}