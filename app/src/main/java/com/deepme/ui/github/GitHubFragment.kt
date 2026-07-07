package com.deepme.ui.github
import android.os.Bundle;android.view.*;android.widget.*
import androidx.fragment.app.Fragment;androidx.lifecycle.lifecycleScope;androidx.recyclerview.widget.*
import com.deepme.R;import com.deepme.network.ApiClient;import com.deepme.network.GitHubRepo
import com.deepme.utils.*;kotlinx.coroutines.*

class GitHubFragment : Fragment() {
    private lateinit var rv: RecyclerView; private lateinit var prg: ProgressBar; private lateinit var err: TextView
    private val repos = ArrayList<GitHubRepo>(); private lateinit var ad: RepoAdapter

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, b: Bundle?) = i.inflate(R.layout.fragment_github, c, false)

    override fun onViewCreated(v: View, b: Bundle?) {
        super.onViewCreated(v, b)
        rv = v.findViewById(R.id.recycler_view_github); prg = v.findViewById(R.id.github_progress); err = v.findViewById(R.id.github_error)
        ad = RepoAdapter(repos) { Toast.makeText(context, "📁 ${it.full_name}", Toast.LENGTH_SHORT).show() }
        rv.layoutManager = LinearLayoutManager(context); rv.adapter = ad
        loadRepos()
    }

    private fun loadRepos() {
        val token = TokenManager.getGitHubToken(requireContext())
        if (token.isEmpty()) { err.visibility = View.VISIBLE; err.text = "❌ Введите GitHub Token в Настройках"; return }
        prg.visibility = View.VISIBLE; err.visibility = View.GONE
        lifecycleScope.launch {
            try {
                val list = withContext(Dispatchers.IO) { ApiClient.gitHubApi.getRepos("token $token", 50) }
                repos.clear(); repos.addAll(list); ad.notifyDataSetChanged(); prg.visibility = View.GONE
                if (repos.isEmpty()) { err.visibility = View.VISIBLE; err.text = "📭 Нет репозиториев" }
            } catch (e: Exception) { prg.visibility = View.GONE; err.visibility = View.VISIBLE; err.text = "❌ ${e.message}" }
        }
    }

    inner class RepoAdapter(private val items: List<GitHubRepo>, private val onClick: (GitHubRepo) -> Unit) : RecyclerView.Adapter<RepoAdapter.VH>() {
        override fun onCreateViewHolder(p: ViewGroup, vt: Int) = VH(LayoutInflater.from(p.context).inflate(R.layout.item_repo, p, false))
        override fun onBindViewHolder(h: VH, pos: Int) {
            val r = items[pos]; h.name.text = "📁 ${r.full_name}"; h.desc.text = r.description ?: "Нет описания"; h.itemView.setOnClickListener { onClick(r) }
        }
        override fun getItemCount() = items.size
        inner class VH(v: View) : RecyclerView.ViewHolder(v) { val name: TextView = v.findViewById(R.id.repo_name); val desc: TextView = v.findViewById(R.id.repo_desc) }
    }
}