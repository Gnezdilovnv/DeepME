package com.deepme.ui.settings
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.deepme.R
import com.deepme.network.*
import com.deepme.utils.*
import kotlinx.coroutines.*

class SettingsFragment : Fragment() {
    private lateinit var dsInput: EditText
    private lateinit var ghInput: EditText
    private lateinit var saveBtn: Button
    private lateinit var statusText: TextView
    private lateinit var balanceText: TextView
    private lateinit var modelSpinner: Spinner

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dsInput = view.findViewById(R.id.api_key_input)
        ghInput = view.findViewById(R.id.github_token_input)
        saveBtn = view.findViewById(R.id.save_button)
        statusText = view.findViewById(R.id.status_text)
        balanceText = view.findViewById(R.id.balance_text)
        modelSpinner = view.findViewById(R.id.settings_model_spinner)

        dsInput.setText(TokenManager.getDeepSeekKey(requireContext()))
        ghInput.setText(TokenManager.getGitHubToken(requireContext()))

        val mn = ApiClient.models.map { "${it.name} - ${it.desc}" }
        modelSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, mn)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        modelSpinner.setSelection(ApiClient.models.indexOfFirst { it.id == ApiClient.deepSeekModel }.coerceAtLeast(0))
        modelSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                ApiClient.deepSeekModel = ApiClient.models[pos].id
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        if (TokenManager.isAuthorized(requireContext())) {
            statusText.text = "✅ Авторизован"; statusText.setTextColor(0xFF238636.toInt()); checkBalance()
        }

        saveBtn.setOnClickListener {
            val ds = dsInput.text.toString().trim(); val gh = ghInput.text.toString().trim()
            if (ds.isEmpty() || gh.isEmpty()) { Toast.makeText(context, "Заполните поля", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            TokenManager.saveTokens(requireContext(), ds, gh)
            statusText.text = "✅ Авторизован"; statusText.setTextColor(0xFF238636.toInt()); checkBalance()
            Toast.makeText(context, "✅ Сохранено!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkBalance() {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    ApiClient.deepSeekApi.chat("Bearer ${TokenManager.getDeepSeekKey(requireContext())}",
                        DeepSeekRequest(model = ApiClient.deepSeekModel, messages = listOf(Message("user", "ping")), max_tokens = 1))
                }
                val m = ApiClient.models.first { it.id == ApiClient.deepSeekModel }
                balanceText.text = "💰 Активен | ${m.name}\nВход: ${m.pi}$ | Выход: ${m.po}$"
                balanceText.setTextColor(0xFF238636.toInt())
            } catch (e: Exception) {
                balanceText.text = "💰 ${e.message?.take(60)}"; balanceText.setTextColor(0xFFf85149.toInt())
            }
        }
    }
}
