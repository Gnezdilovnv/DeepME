package com.deepme.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.deepme.R
import com.deepme.utils.Logger
import com.deepme.utils.TokenManager

class SettingsFragment : Fragment() {
    private lateinit var deepSeekInput: EditText
    private lateinit var githubInput: EditText
    private lateinit var saveButton: Button
    private lateinit var statusView: TextView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        deepSeekInput = view.findViewById(R.id.api_key_input)
        githubInput = view.findViewById(R.id.github_token_input)
        saveButton = view.findViewById(R.id.save_button)
        statusView = view.findViewById(R.id.status_text)

        deepSeekInput.setText(TokenManager.getDeepSeekKey(requireContext()))
        githubInput.setText(TokenManager.getGitHubToken(requireContext()))

        if (TokenManager.isAuthorized(requireContext())) {
            statusView.text = "✅ Авторизован"
            statusView.setTextColor(0xFF238636.toInt())
        }

        saveButton.setOnClickListener {
            val ds = deepSeekInput.text.toString().trim()
            val gh = githubInput.text.toString().trim()
            if (ds.isEmpty() || gh.isEmpty()) {
                Toast.makeText(context, "Заполните оба поля", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            TokenManager.saveTokens(requireContext(), ds, gh)
            Logger.log("Tokens saved")
            statusView.text = "✅ Авторизован"
            statusView.setTextColor(0xFF238636.toInt())
            Toast.makeText(context, "✅ Сохранено!", Toast.LENGTH_SHORT).show()
        }
    }
}