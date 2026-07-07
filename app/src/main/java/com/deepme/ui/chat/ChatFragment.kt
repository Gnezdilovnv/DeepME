package com.deepme.ui.chat

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.*
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
    private lateinit var clearChatButton: ImageButton
    private val messages = mutableListOf<ChatMessage>()
    private val models = listOf("deepseek-chat", "deepseek-reasoner", "deepseek-v3")
    private var currentProject = "основной"
    private val projects = mutableListOf("основной")

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
        clearChatButton = view.findViewById(R.id.clear_chat_button)

        adapter = ChatAdapter(messages) { msg ->
            showCopyMenu(msg)
        }
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
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        // Проекты
        loadProjects()
        updateProjectSpinner()

        newProjectButton.setOnClickListener { showNewProjectDialog() }
        clearChatButton.setOnClickListener { clearCurrentChat() }

        if (!TokenManager.isAuthorized(requireContext())) {
            addMessage("⚠️ Настройте API ключи в разделе «Настройки»", false)
        } else {
            addMessage("🤖 DeepME готов к работе\nМодель: ${ApiClient.deepSeekModel}\nЯ умею читать и создавать файлы, выполнять команды и работать с GitHub.", false)
        }

        sendButton.setOnClickListener {
            val text = inputEditText.text.toString().trim()
            if (text.isNotEmpty()) sendToAI(text)
        }
    }

    private fun showCopyMenu(msg: ChatMessage) {
        val options = arrayOf("Копировать текст", "Поделиться")
        AlertDialog.Builder(requireContext())
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("DeepME", msg.text))
                        Toast.makeText(context, "✅ Скопировано", Toast.LENGTH_SHORT).show()
                    }
                    1 -> {
                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND)
                        intent.type = "text/plain"
                        intent.putExtra(android.content.Intent.EXTRA_TEXT, msg.text)
                        startActivity(android.content.Intent.createChooser(intent, "Поделиться"))
                    }
                }
            }
            .show()
    }

    private fun showNewProjectDialog() {
        val input = EditText(requireContext())
        input.hint = "Название проекта"
        input.setTextColor(0xFFE6EDF3.toInt())
        AlertDialog.Builder(requireContext())
            .setTitle("Новый проект")
            .setView(input)
            .setPositiveButton("Создать") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty() && !projects.contains(name)) {
                    saveHistory()
                    projects.add(name)
                    saveProjectsList()
                    updateProjectSpinner()
                    projectSpinner.setSelection(projects.indexOf(name))
                    messages.clear()
                    currentProject = name
                    adapter.notifyDataSetChanged()
                    addMessage("📂 Проект «$name» создан", false)
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun clearCurrentChat() {
        AlertDialog.Builder(requireContext())
            .setTitle("Очистить чат?")
            .setMessage("Все сообщения в проекте «$currentProject» будут удалены")
            .setPositiveButton("Очистить") { _, _ ->
                messages.clear()
                adapter.notifyDataSetChanged()
                saveHistory()
                addMessage("🗑️ Чат очищен", false)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun sendToAI(text: String) {
        if (!TokenManager.isAuthorized(requireContext())) {
            addMessage("❌ Настройте API ключи в разделе «Настройки»", false)
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

    // История
    private fun getProjectDir(): File {
        val dir = File(requireContext().filesDir, "projects")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun getHistoryFile(project: String): File = File(getProjectDir(), "$project.json")
    private fun getProjectsFile(): File = File(getProjectDir(), "_projects.json")

    private fun loadProjects() {
        val file = getProjectsFile()
        if (file.exists()) {
            try {
                val arr = JSONArray(file.readText())
                projects.clear()
                for (i in 0 until arr.length()) projects.add(arr.getString(i))
            } catch (e: Exception) { projects.add("основной") }
        }
        if (projects.isEmpty()) projects.add("основной")
    }

    private fun saveProjectsList() {
        try {
            val arr = JSONArray()
            projects.forEach { arr.put(it) }
            getProjectsFile().writeText(arr.toString())
        } catch (e: Exception) { Logger.log("Ошибка сохранения проектов: ${e.message}") }
    }

    private fun updateProjectSpinner() {
        val pAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, projects)
        pAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        projectSpinner.adapter = pAdapter

        val currentIdx = projects.indexOf(currentProject)
        if (currentIdx >= 0) projectSpinner.setSelection(currentIdx)

        projectSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (projects[pos] != currentProject) {
                    saveHistory()
                    currentProject = projects[pos]
                    messages.clear()
                    loadHistory()
                    Logger.log("Проект: $currentProject")
                }
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    private fun loadHistory() {
        val file = getHistoryFile(currentProject)
        if (file.exists()) {
            try {
                val arr = JSONArray(file.readText())
                messages.clear()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    messages.add(ChatMessage(obj.getString("text"), obj.getBoolean("isUser")))
                }
                adapter.notifyDataSetChanged()
                if (messages.isNotEmpty()) recyclerView.scrollToPosition(messages.size - 1)
            } catch (e: Exception) { Logger.log("Ошибка истории: ${e.message}") }
        }
    }

    private fun saveHistory() {
        try {
            val arr = JSONArray()
            messages.takeLast(200).forEach { msg ->
                arr.put(JSONObject().put("text", msg.text).put("isUser", msg.isUser))
            }
            getHistoryFile(currentProject).writeText(arr.toString())
        } catch (e: Exception) { Logger.log("Ошибка сохранения: ${e.message}") }
    }

    override fun onPause() { super.onPause(); saveHistory() }
    override fun onDestroy() { super.onDestroy(); saveHistory() }

    data class ChatMessage(val text: String, val isUser: Boolean)

    inner class ChatAdapter(
        private val messages: List<ChatMessage>,
        private val onLongClick: (ChatMessage) -> Unit
    ) : RecyclerView.Adapter<ChatAdapter.VH>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            return VH(LayoutInflater.from(parent.context).inflate(R.layout.item_message, parent, false))
        }
        override fun onBindViewHolder(holder: VH, pos: Int) {
            val msg = messages[pos]
            holder.textView.text = msg.text
            holder.textView.setTextIsSelectable(true)
            holder.textView.movementMethod = LinkMovementMethod.getInstance()
            holder.textView.setBackgroundResource(if (msg.isUser) R.drawable.bg_user_message else R.drawable.bg_assistant_message)
            holder.textView.setTextColor(if (msg.isUser) 0xFFFFFFFF.toInt() else 0xFFE6EDF3.toInt())
            holder.itemView.setOnLongClickListener {
                onLongClick(msg)
                true
            }
        }
        override fun getItemCount() = messages.size
        inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val textView: TextView = itemView.findViewById(R.id.message_text)
        }
    }
}