package com.deepme.ui.chat

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.method.LinkMovementMethod
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
import com.deepme.model.Environments
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
    private lateinit var rv: RecyclerView
    private lateinit var ad: ChatAdapter
    private lateinit var inp: EditText
    private lateinit var snd: ImageButton
    private lateinit var prg: ProgressBar
    private lateinit var msp: Spinner
    private lateinit var psp: Spinner
    private lateinit var npb: Button
    private lateinit var envBtn: Button
    private lateinit var clb: ImageButton
    private lateinit var inf: TextView
    private val msgs = ArrayList<CM>()
    private var cp = "основной"
    private val prs = ArrayList<String>()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, b: Bundle?) = i.inflate(R.layout.fragment_chat, c, false)

    override fun onViewCreated(v: View, b: Bundle?) {
        super.onViewCreated(v, b)
        rv = v.findViewById(R.id.recycler_view); inp = v.findViewById(R.id.input_edit_text)
        snd = v.findViewById(R.id.send_button); prg = v.findViewById(R.id.progress_bar)
        msp = v.findViewById(R.id.model_spinner); psp = v.findViewById(R.id.project_spinner)
        npb = v.findViewById(R.id.new_project_button); envBtn = v.findViewById(R.id.env_button)
        clb = v.findViewById(R.id.clear_chat_button); inf = v.findViewById(R.id.model_info_text)

        ad = ChatAdapter(msgs) { showMenu(it) }
        rv.layoutManager = LinearLayoutManager(context); rv.adapter = ad

        val mn = ApiClient.models.map { "${it.name}|${it.desc}" }
        msp.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, mn)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        msp.setSelection(ApiClient.models.indexOfFirst { it.id == ApiClient.deepSeekModel }.coerceAtLeast(0))
        msp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                ApiClient.deepSeekModel = ApiClient.models[pos].id
                inf.text = "💰 ${ApiClient.models[pos].pi}/${ApiClient.models[pos].po}/1M"
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        val dm = ApiClient.models.first { it.id == ApiClient.deepSeekModel }
        inf.text = "💰 ${dm.pi}/${dm.po}/1M"

        prs.add("основной"); loadPrj(); updPrj()
        npb.setOnClickListener { newProject() }
        clb.setOnClickListener { clearChat() }
        envBtn.setOnClickListener { showEnvs() }

        if (TokenManager.isAuthorized(requireContext()))
            addMsg("🤖 DeepME готов\n\n🐙 GitHub: репо/файлы\n📁 Файлы: чтение/запись\n🖥️ Терминал\n⚙️ Среды: python/node/go", false)
        else addMsg("⚠️ Настройте ключи в Настройках", false)

        snd.setOnClickListener { val t = inp.text.toString().trim(); if (t.isNotEmpty()) send(t) }
    }

    private fun showEnvs() {
        val items = Environments.list.map { "${it.name} (${it.language})" }.toTypedArray()
        AlertDialog.Builder(requireContext()).setTitle("⚙️ Среды разработки")
            .setItems(items) { _, pos -> inp.setText("установи ${Environments.list[pos].id}") }
            .setPositiveButton("OK", null).show()
    }

    private fun showMenu(m: CM) = AlertDialog.Builder(requireContext())
        .setItems(arrayOf("Копировать", "Поделиться")) { _, w ->
            when (w) {
                0 -> {
                    (requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData.newPlainText("", m.text))
                    Toast.makeText(context, "✅ Скопировано", Toast.LENGTH_SHORT).show()
                }
                1 -> startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"; putExtra(Intent.EXTRA_TEXT, m.text)
                }, "Поделиться"))
            }
        }.show()

    private fun newProject() {
        val et = EditText(requireContext()).apply { hint = "Проект"; setTextColor(0xFFE6EDF3.toInt()) }
        AlertDialog.Builder(requireContext()).setTitle("Новый проект").setView(et)
            .setPositiveButton("Создать") { _, _ ->
                val n = et.text.toString().trim()
                if (n.isNotEmpty() && !prs.contains(n)) {
                    saveHist(); prs.add(n); savePrj(); updPrj()
                    psp.setSelection(prs.indexOf(n)); msgs.clear(); cp = n
                    ad.notifyDataSetChanged(); addMsg("📂 $n", false)
                }
            }.setNegativeButton("Отмена", null).show()
    }

    private fun clearChat() = AlertDialog.Builder(requireContext()).setTitle("Очистить чат?")
        .setPositiveButton("Да") { _, _ -> msgs.clear(); ad.notifyDataSetChanged(); saveHist(); addMsg("🗑️", false) }
        .setNegativeButton("Нет", null).show()

    private fun send(t: String) {
        if (!TokenManager.isAuthorized(requireContext())) { addMsg("❌ Нет ключей", false); return }
        inp.setText(""); addMsg(t, true); saveHist()
        prg.visibility = View.VISIBLE; snd.isEnabled = false
        lifecycleScope.launch {
            try {
                val h = msgs.map { Message(if (it.isUser) "user" else "assistant", it.text) }.takeLast(20)
                val r = withContext(Dispatchers.IO) { AIAgent.processMessage(requireContext(), t, h) }
                prg.visibility = View.GONE; snd.isEnabled = true; addMsg(r, false); saveHist()
            } catch (e: Exception) {
                prg.visibility = View.GONE; snd.isEnabled = true; addMsg("❌ ${e.message}", false)
            }
        }
    }

    private fun addMsg(t: String, u: Boolean) {
        msgs.add(CM(t, u)); ad.notifyItemInserted(msgs.size - 1); rv.scrollToPosition(msgs.size - 1)
    }

    private fun dir() = File(requireContext().filesDir, "projects").also { if (!it.exists()) it.mkdirs() }
    private fun hf(p: String) = File(dir(), "$p.json")
    private fun pf() = File(dir(), "_prj.json")
    private fun loadPrj() { val f = pf(); if (f.exists()) try { prs.clear(); val a = JSONArray(f.readText()); for (i in 0 until a.length()) prs.add(a.getString(i)) } catch (_: Exception) { prs.add("основной") }; if (prs.isEmpty()) prs.add("основной") }
    private fun savePrj() = try { val a = JSONArray(); prs.forEach { a.put(it) }; pf().writeText(a.toString()) } catch (_: Exception) {}
    private fun updPrj() {
        psp.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, prs).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        val i = prs.indexOf(cp); if (i >= 0) psp.setSelection(i)
        psp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) { if (prs[pos] != cp) { saveHist(); cp = prs[pos]; msgs.clear(); loadHist() } }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }
    private fun loadHist() { val f = hf(cp); if (f.exists()) try { msgs.clear(); val a = JSONArray(f.readText()); for (i in 0 until a.length()) { val o = a.getJSONObject(i); msgs.add(CM(o.getString("text"), o.getBoolean("isUser"))) }; ad.notifyDataSetChanged() } catch (_: Exception) {} }
    private fun saveHist() = try { val a = JSONArray(); msgs.takeLast(200).forEach { a.put(JSONObject().put("text", it.text).put("isUser", it.isUser)) }; hf(cp).writeText(a.toString()) } catch (_: Exception) {}
    override fun onPause() { super.onPause(); saveHist() }

    data class CM(val text: String, val isUser: Boolean)

    inner class ChatAdapter(private val ms: List<CM>, private val olc: (CM) -> Unit) : RecyclerView.Adapter<ChatAdapter.VH>() {
        override fun onCreateViewHolder(p: ViewGroup, vt: Int) = VH(LayoutInflater.from(p.context).inflate(R.layout.item_message, p, false))
        override fun onBindViewHolder(h: VH, pos: Int) {
            val m = ms[pos]; h.tv.text = m.text; h.tv.setTextIsSelectable(true)
            h.tv.movementMethod = LinkMovementMethod.getInstance()
            h.tv.setBackgroundResource(if (m.isUser) R.drawable.bg_user_message else R.drawable.bg_assistant_message)
            h.tv.setTextColor(if (m.isUser) 0xFFFFFFFF.toInt() else 0xFFE6EDF3.toInt())
            h.itemView.setOnLongClickListener { olc(m); true }
        }
        override fun getItemCount() = ms.size
        inner class VH(v: View) : RecyclerView.ViewHolder(v) { val tv: TextView = v.findViewById(R.id.message_text) }
    }
}