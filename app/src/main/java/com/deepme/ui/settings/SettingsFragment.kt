package com.deepme.ui.settings
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.deepme.R
import com.deepme.network.*
import com.deepme.utils.*
import kotlinx.coroutines.*

class SettingsFragment : Fragment() {
    private lateinit var ds: EditText
    private lateinit var gh: EditText
    private lateinit var sb: Button
    private lateinit var st: TextView
    private lateinit var bl: TextView
    private lateinit var ms: Spinner

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, b: Bundle?) = i.inflate(R.layout.fragment_settings, c, false)

    override fun onViewCreated(v: View, b: Bundle?) {
        super.onViewCreated(v, b)
        ds = v.findViewById(R.id.api_key_input)
        gh = v.findViewById(R.id.github_token_input)
        sb = v.findViewById(R.id.save_button)
        st = v.findViewById(R.id.status_text)
        bl = v.findViewById(R.id.balance_text)
        ms = v.findViewById(R.id.settings_model_spinner)

        ds.setText(TokenManager.getDeepSeekKey(requireContext()))
        gh.setText(TokenManager.getGitHubToken(requireContext()))

        val mn = ApiClient.models.map { "${it.name} — ${it.desc}" }
        ms.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, mn)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        ms.setSelection(ApiClient.models.indexOfFirst { it.id == ApiClient.deepSeekModel }.coerceAtLeast(0))
        ms.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                ApiClient.deepSeekModel = ApiClient.models[pos].id
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        if (TokenManager.isAuthorized(requireContext())) {
            st.text = "✅ Авторизован"
            st.setTextColor(0xFF238636.toInt())
            checkBalance()
        }

        sb.setOnClickListener {
            val d = ds.text.toString().trim()
            val g = gh.text.toString().trim()
            if (d.isEmpty() || g.isEmpty()) {
                Toast.makeText(context, "Заполните оба поля", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            TokenManager.saveTokens(requireContext(), d, g)
            st.text = "✅ Авторизован"
            st.setTextColor(0xFF238636.toInt())
            checkBalance()
            Toast.makeText(context, "✅ Сохранено!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkBalance() {
        lifecycleScope.launch {
            try {
                val r = withContext(Dispatchers.IO) {
                    ApiClient.ds.chat(
                        "Bearer ${TokenManager.getDeepSeekKey(requireContext())}",
                        DeepSeekRequest(model = ApiClient.deepSeekModel, messages = listOf(Message("user", "ping")), max_tokens = 1)
                    )
                }
                val m = ApiClient.models.first { it.id == ApiClient.deepSeekModel }
                bl.text = "💰 Баланс активен | Модель: ${m.name}\nВход: ${m.priceIn}$ | Выход: ${m.priceOut}$ за 1M токенов"
                bl.setTextColor(0xFF238636.toInt())
            } catch (e: Exception) {
                bl.text = "💰 Ошибка: ${e.message?.take(80)}"
                bl.setTextColor(0xFFf85149.toInt())
            }
        }
    }
}