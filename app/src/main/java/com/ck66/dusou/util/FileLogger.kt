package com.ck66.dusou.util

import android.content.Context
import android.os.Build
import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 文件日志工具，输出到 Download/dusou_crash/ 目录。
 * 用于读屏搜题等关键路径的运行时诊断。
 */
object FileLogger {

    private const val DIR_NAME = "dusou_crash"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private var logFile: File? = null
    private val lock = Any()

    fun init(context: Context) {
        synchronized(lock) {
            if (logFile != null) return
            val dir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), DIR_NAME)
            } else {
                File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), DIR_NAME)
            }
            if (!dir.exists()) dir.mkdirs()

            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            logFile = File(dir, "dusou_log_$today.txt")
        }
    }

    fun log(tag: String, message: String, throwable: Throwable? = null) {
        synchronized(lock) {
            val file = logFile ?: return
            val timestamp = dateFormat.format(Date())
            val line = "$timestamp [$tag] $message"
            try {
                file.appendText(line + "\n")
                throwable?.let {
                    file.appendText(it.stackTraceToString() + "\n")
                }
            } catch (_: Exception) {}
        }
    }

    fun i(tag: String, message: String) = log(tag, message, null)
    fun e(tag: String, message: String, throwable: Throwable? = null) = log(tag, "ERROR: $message", throwable)
    fun w(tag: String, message: String) = log(tag, "WARN: $message", null)
}
