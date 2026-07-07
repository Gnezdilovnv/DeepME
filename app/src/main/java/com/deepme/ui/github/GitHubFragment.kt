package com.deepme.ui.github

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
import com.deepme.network.GitHubRepo
import com.deepme.utils.Logger
import com.deepme.utils.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GitHubFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var errorText: TextView
    private val repos = mutableListOf<GitHubRepo>()
    private lateinit var adapter: RepoAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_github, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerView = view.findViewById(R.id.recycler_view_github)
        progressBar = view.findViewById(R.id.github_progress)
        errorText = view.findViewById(R.id.github_error)

        adapter = RepoAdapter(repos) { repo ->
            Toast.makeText(context, "Открываю ${repo.full_name}", Toast.LENGTH_SHORT).show()
        }
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter
        loadRepos()
    }

    private fun loadRepos() {
        val token = TokenManager.getGitHubToken(requireContext())
        if (token.isEmpty()) {
            errorText.visibility = View.VISIBLE
            errorText.text = "❌ Введите GitHub Token в Настройках"
            return
        }

        progressBar.visibility = View.VISIBLE
        errorText.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val user = withContext(Dispatchers.IO) {
                    ApiClient.gitHubApi.getUser("Bearer $token")
                }
                Logger.log("GitHub user: ${user.login}")

                val repoList = withContext(Dispatchers.IO) {
                    ApiClient.gitHubApi.getRepos("Bearer $token", 100)
                }
                repos.clear()
                repos.addAll(repoList)
                adapter.notifyDataSetChanged()
                progressBar.visibility = View.GONE

                if (repos.isEmpty()) {
                    errorText.visibility = View.VISIBLE
                    errorText.text = "📭 Нет репозиториев"
                }
            } catch (e: Exception) {
                Logger.log("GitHub error: ${e.message}")
                progressBar.visibility = View.GONE
                errorText.visibility = View.VISIBLE
                errorText.text = "❌ ${e.message}"
            }
        }
    }

    inner class RepoAdapter(
        private val items: List<GitHubRepo>,
        private val onClick: (GitHubRepo) -> Unit
    ) : RecyclerView.Adapter<RepoAdapter.VH>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_repo, parent, false)
            return VH(view)
        }
        override fun onBindViewHolder(holder: VH, pos: Int) {
            val repo = items[pos]
            holder.nameView.text = "📁 ${repo.full_name}"
            holder.descView.text = repo.description ?: "Нет описания"
            holder.itemView.setOnClickListener { onClick(repo) }
        }
        override fun getItemCount() = items.size
        inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val nameView: TextView = itemView.findViewById(R.id.repo_name)
            val descView: TextView = itemView.findViewById(R.id.repo_desc)
        }
    }
}