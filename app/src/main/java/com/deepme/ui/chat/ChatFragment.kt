package com.deepme.ui.chat

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
import com.deepme.network.ApiClient
import com.deepme.network.DeepSeekRequest
import com.deepme.network.Message
import com.deepme.utils.Logger
import com.deepme.utils.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ChatAdapter
    private lateinit var inputEditText: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var progressBar: ProgressBar
    private lateinit var modelSpinner: Spinner
    private val messages = mutableListOf<ChatMessage>()
    private val models = listOf("deepseek-chat", "deepseek-reasoner", "deepseek-v3")

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

        adapter = ChatAdapter(messages)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter

        val spinnerAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, models)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        modelSpinner.adapter = spinnerAdapter
        modelSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                ApiClient.deepSeekModel = models[pos]
                Logger.log("Model: ${models[pos]}")
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        if (TokenManager.isAuthorized(requireContext())) {
            addMessage("🤖 DeepME готов! Модель: ${ApiClient.deepSeekModel}\\nGitHub: подключен", false)
        } else {
            addMessage("⚠️ Настройте API ключи в разделе Настройки", false)
        }

        sendButton.setOnClickListener {
            val text = inputEditText.text.toString().trim()
            if (text.isNotEmpty()) sendToAI(text)
        }
    }

    private fun sendToAI(text: String) {
        val key = TokenManager.getDeepSeekKey(requireContext())
        if (key.isEmpty()) { addMessage("❌ Не настроен DeepSeek API ключ", false); return }

        inputEditText.setText("")
        addMessage(text, true)
        progressBar.visibility = View.VISIBLE
        sendButton.isEnabled = false

        lifecycleScope.launch {
            try {
                val request = DeepSeekRequest(
                    model = ApiClient.deepSeekModel,
                    messages = listOf(Message("user", text))
                )
                val response = withContext(Dispatchers.IO) {
                    ApiClient.deepSeekApi.chat("Bearer $key", request)
                }
                progressBar.visibility = View.GONE
                sendButton.isEnabled = true
                val reply = response.choices?.firstOrNull()?.message?.content ?: response.error?.message ?: "Пустой ответ"
                addMessage(reply, false)
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