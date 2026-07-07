package com.deepme.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.deepme.R
import com.deepme.network.ApiClient
import com.deepme.network.ChatRequest
import com.deepme.utils.Logger
import kotlinx.coroutines.launch
import java.net.ConnectException
import java.net.SocketTimeoutException

class ChatFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ChatAdapter
    private lateinit var inputEditText: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var progressBar: ProgressBar
    private val messages = mutableListOf<ChatMessage>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        Logger.log("ChatFragment onCreateView")
        return inflater.inflate(R.layout.fragment_chat, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Logger.log("ChatFragment onViewCreated")
        
        recyclerView = view.findViewById(R.id.recycler_view)
        inputEditText = view.findViewById(R.id.input_edit_text)
        sendButton = view.findViewById(R.id.send_button)
        progressBar = view.findViewById(R.id.progress_bar)

        adapter = ChatAdapter(messages)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter

        val welcomeMsg = ChatMessage("👋 Привет! Я DeepME\\n\\n⚠️ Сервер не подключен.\\nНастройте сервер в Настройках.", false)
        messages.add(welcomeMsg)
        adapter.notifyDataSetChanged()
        Logger.log("ChatFragment initialized with ${messages.size} messages")

        sendButton.setOnClickListener {
            val text = inputEditText.text.toString().trim()
            if (text.isNotEmpty()) sendMessage(text)
        }
    }

    private fun sendMessage(text: String) {
        Logger.log("Sending message: $text")
        inputEditText.setText("")
        messages.add(ChatMessage(text, true))
        adapter.notifyItemInserted(messages.size - 1)
        recyclerView.scrollToPosition(messages.size - 1)

        progressBar.visibility = View.VISIBLE
        sendButton.isEnabled = false

        lifecycleScope.launch {
            try {
                Logger.log("Calling API...")
                val response = ApiClient.apiService.sendMessage(ChatRequest(text))
                progressBar.visibility = View.GONE
                sendButton.isEnabled = true
                val reply = response.response ?: "❌ Пустой ответ"
                Logger.log("API response: $reply")
                messages.add(ChatMessage(reply, false))
                adapter.notifyItemInserted(messages.size - 1)
                recyclerView.scrollToPosition(messages.size - 1)
            } catch (e: ConnectException) {
                handleError("Сервер недоступен. Проверьте подключение.")
            } catch (e: SocketTimeoutException) {
                handleError("Таймаут соединения. Сервер не отвечает.")
            } catch (e: Exception) {
                Logger.log("Error: ${e.message}")
                handleError("❌ ${e.message}")
            }
        }
    }

    private fun handleError(error: String) {
        Logger.log("ERROR: $error")
        progressBar.visibility = View.GONE
        sendButton.isEnabled = true
        messages.add(ChatMessage(error, false))
        adapter.notifyItemInserted(messages.size - 1)
        recyclerView.scrollToPosition(messages.size - 1)
        Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
    }

    data class ChatMessage(val text: String, val isUser: Boolean)

    inner class ChatAdapter(private val messages: List<ChatMessage>) :
        RecyclerView.Adapter<ChatAdapter.ViewHolder>() {
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val message = messages[position]
            holder.textView.text = message.text
            if (message.isUser) {
                holder.textView.setBackgroundResource(R.drawable.bg_user_message)
                holder.textView.setTextColor(0xFFFFFFFF.toInt())
            } else {
                holder.textView.setBackgroundResource(R.drawable.bg_assistant_message)
                holder.textView.setTextColor(0xFFE6EDF3.toInt())
            }
        }

        override fun getItemCount(): Int = messages.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val textView: TextView = itemView.findViewById(R.id.message_text)
        }
    }
}