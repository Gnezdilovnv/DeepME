package com.deepme.ui.termux

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.deepme.R
import com.deepme.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class TermuxFragment : Fragment() {
    private lateinit var inputEditText: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var outputTextView: TextView
    private lateinit var scrollView: ScrollView
    private val outputBuffer = StringBuilder()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_termux, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        inputEditText = view.findViewById(R.id.termux_input)
        sendButton = view.findViewById(R.id.termux_send)
        outputTextView = view.findViewById(R.id.termux_output)
        scrollView = view.findViewById(R.id.termux_scroll)

        outputBuffer.append("🖥️ DeepME Terminal\n")
        outputBuffer.append("Текущая папка: ${File(".").absolutePath}\n\n")
        outputTextView.text = outputBuffer.toString()

        sendButton.setOnClickListener {
            val command = inputEditText.text.toString().trim()
            if (command.isNotEmpty()) executeCommand(command)
        }
    }

    private fun executeCommand(command: String) {
        outputBuffer.append("$ $command\n")
        outputTextView.text = outputBuffer.toString()
        inputEditText.setText("")
        Logger.log("Termux: $command")

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
                    val reader = BufferedReader(InputStreamReader(process.inputStream))
                    val errReader = BufferedReader(InputStreamReader(process.errorStream))
                    val output = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        output.append(line).append("\n")
                    }
                    while (errReader.readLine().also { line = it } != null) {
                        output.append("ERR: ").append(line).append("\n")
                    }
                    process.waitFor()
                    if (output.isEmpty()) "(нет вывода)" else output.toString().trimEnd()
                } catch (e: Exception) {
                    "❌ ${e.message}"
                }
            }
            outputBuffer.append(result).append("\n\n")
            outputTextView.text = outputBuffer.toString()
            scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
        }
    }
}