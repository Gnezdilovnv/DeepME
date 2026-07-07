package com.deepme.ui.files

import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.deepme.R
import com.deepme.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class FilesFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var pathView: TextView
    private lateinit var adapter: FileAdapter
    private var currentDir = Environment.getExternalStorageDirectory()
    private val files = mutableListOf<FileItem>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_files, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerView = view.findViewById(R.id.recycler_view_files)
        pathView = view.findViewById(R.id.file_path)

        adapter = FileAdapter(files) { item ->
            if (item.isDir) {
                currentDir = File(item.path)
                loadFiles()
            } else {
                Toast.makeText(context, "📄 ${item.name} (${item.size / 1024} KB)", Toast.LENGTH_SHORT).show()
            }
        }
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter
        loadFiles()
    }

    private fun loadFiles() {
        lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) {
                val result = mutableListOf<FileItem>()
                if (currentDir.parentFile != null) {
                    result.add(FileItem("📁 ..", "", 0, true, currentDir.parent!!))
                }
                currentDir.listFiles()?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name })?.forEach { f ->
                    result.add(FileItem(
                        if (f.isDirectory) "📁 ${f.name}" else "📄 ${f.name}",
                        f.name, f.length(), f.isDirectory, f.absolutePath
                    ))
                }
                result
            }
            pathView.text = currentDir.absolutePath
            files.clear()
            files.addAll(list)
            adapter.notifyDataSetChanged()
            Logger.log("Loaded ${files.size} files from ${currentDir.absolutePath}")
        }
    }

    data class FileItem(val displayName: String, val name: String, val size: Long, val isDir: Boolean, val path: String)

    inner class FileAdapter(
        private val items: List<FileItem>,
        private val onClick: (FileItem) -> Unit
    ) : RecyclerView.Adapter<FileAdapter.VH>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            return VH(LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false))
        }
        override fun onBindViewHolder(holder: VH, pos: Int) {
            val item = items[pos]
            holder.textView.text = item.displayName
            holder.itemView.setOnClickListener { onClick(item) }
        }
        override fun getItemCount() = items.size
        inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val textView: TextView = itemView.findViewById(R.id.file_name)
        }
    }
}