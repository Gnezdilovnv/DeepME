package com.deepme.utils

import android.os.Environment
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Logger {
    private const val TAG = "DeepME"
    private var logFile: File? = null

    fun init() {
        try {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "DeepME")
            if (!dir.exists()) dir.mkdirs()
            logFile = File(dir, "deepme_log.txt")
            log("=== DeepME Started ===")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Cannot create log file", e)
        }
    }

    fun log(message: String) {
        android.util.Log.d(TAG, message)
        try {
            logFile?.let {
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                FileWriter(it, true).use { fw ->
                    fw.write("[${sdf.format(Date())}] $message\n")
                    fw.flush()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Write log error", e)
        }
    }

    fun getLogPath(): String = logFile?.absolutePath ?: "N/A"
}