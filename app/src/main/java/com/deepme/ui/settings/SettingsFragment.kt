package com.deepme.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.deepme.R
import com.deepme.network.ApiClient
import com.deepme.network.Message
import com.deepme.utils.Logger
import com.deepme.utils.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsFragment : Fragment() {
    private lateinit var deepSeekInput: EditText
    private lateinit var githubInput: EditText
    private lateinit var saveButton: Button
    private lateinit var statusView: TextView
    private lateinit var balanceView: TextView
    private lateinit var modelSpinner: Spinner
    private val models = listOf("deepseek-chat", "deepseek-reasoner", "deepseek-v3")

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        deepSeekInput = view.findViewById(R.id.api_key_input)
        githubInput = view.findViewById(R.id.github_token_input)
        saveButton = view.findViewById(R.id.save_button)
        statusView = view.findViewById(R.id.status_text)
        balanceView = view.findViewById(R.id.balance_text)
        modelSpinner = view.findViewById(R.id.settings_model_spinner)

        deepSeekInput.setText(TokenManager.getDeepSeekKey(requireContext()))
        githubInput.setText(TokenManager.getGitHubToken(requireContext()))

        // Модель
        val mAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, models)
        mAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        modelSpinner.adapter = mAdapter
        modelSpinner.setSelection(models.indexOf(ApiClient.deepSeekModel))
        modelSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                ApiClient.deepSeekModel = models[pos]
                Logger.log("Default model: ${models[pos]}")
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        if (TokenManager.isAuthorized(requireContext())) {
            statusView.text = "✅ Авторизован"
            statusView.setTextColor(0xFF238636.toInt())
            checkBalance()
        }

        saveButton.setOnClickListener {
            val ds = deepSeekInput.text.toString().trim()
            val gh = githubInput.text.toString().trim()
            if (ds.isEmpty() || gh.isEmpty()) {
                Toast.makeText(context, "Заполните оба поля", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            TokenManager.saveTokens(requireContext(), ds, gh)
            statusView.text = "✅ Авторизован"
            statusView.setTextColor(0xFF238636.toInt())
            checkBalance()
            Toast.makeText(context, "✅ Сохранено!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkBalance() {
        val key = TokenManager.getDeepSeekKey(requireContext())
        if (key.isEmpty()) return

        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    ApiClient.deepSeekApi.chat("Bearer $key",
                        com.deepme.network.DeepSeekRequest(
                            model = ApiClient.deepSeekModel,
                            messages = listOf(Message("user", "hi")),
                            max_tokens = 1
                        ))
                }
                balanceView.text = "💰 Баланс: активен | Модель: ${ApiClient.deepSeekModel}"
                balanceView.setTextColor(0xFF238636.toInt())
            } catch (e: Exception) {
                balanceView.text = "💰 Баланс: ошибка проверки\n${e.message?.take(100)}"
                balanceView.setTextColor(0xFFf85149.toInt())
            }
        }
    }
}