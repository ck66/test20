package com.ck66.dusou

import android.os.Build
import android.os.Environment
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashHandler {

    private var previousHandler: Thread.UncaughtExceptionHandler? = null

    fun install() {
        previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            writeCrashLog(thread, throwable)
            // 交给系统默认处理（让 APP 正常崩溃）
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun writeCrashLog(thread: Thread, throwable: Throwable) {
        try {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "dusou_crash"
            )
            if (!dir.exists()) dir.mkdirs()

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(Date())
            val logFile = File(dir, "crash_$timestamp.txt")

            val sw = StringWriter()
            val pw = PrintWriter(sw)
            throwable.printStackTrace(pw)
            val stackTrace = sw.toString()

            val content = buildString {
                appendLine("========== 读屏搜题 崩溃日志 ==========")
                appendLine("时间: ${Date()}")
                appendLine("线程: ${thread.name} (id=${thread.id})")
                appendLine("设备: ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
                appendLine("APP版本: 1.0.0")
                appendLine("========================================")
                appendLine()
                appendLine(stackTrace)
            }

            logFile.writeText(content)
        } catch (_: Exception) {
            // 崩溃处理器本身出错，不能再次抛出异常
        }
    }
}
