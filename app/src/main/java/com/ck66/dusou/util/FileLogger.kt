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
 * 支持总开关 + 按分类过滤，通过 SharedPreferences 持久化。
 */
object FileLogger {

    private const val DIR_NAME = "dusou_crash"
    private const val PREFS_NAME = "dusou_log_prefs"
    private const val KEY_ENABLED = "log_enabled"
    private const val KEY_CATEGORIES = "log_categories"

    /** 所有可记录的日志分类 */
    val ALL_CATEGORIES = listOf(
        "FloatingBall",
        "ScreenCapture",
        "ScreenSearchVM",
        "TextMatcher",
        "OCR",
        "Search",
        "Match",
    )

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private var logFile: File? = null
    private val lock = Any()
    private var prefs: android.content.SharedPreferences? = null

    private var _enabled = false
    private var _enabledCategories: Set<String> = emptySet()

    @Volatile
    var isEnabled: Boolean
        get() = _enabled
        set(value) {
            _enabled = value
            prefs?.edit()?.putBoolean(KEY_ENABLED, value)?.apply()
        }

    val enabledCategories: Set<String>
        get() = _enabledCategories

    val allCategories: List<String>
        get() = ALL_CATEGORIES

    fun setCategoryEnabled(category: String, enabled: Boolean) {
        synchronized(lock) {
            _enabledCategories = if (enabled) {
                _enabledCategories + category
            } else {
                _enabledCategories - category
            }
            prefs?.edit()?.putStringSet(KEY_CATEGORIES, _enabledCategories)?.apply()
        }
    }

    fun isCategoryEnabled(category: String): Boolean {
        return category in _enabledCategories
    }

    fun init(context: Context) {
        synchronized(lock) {
            if (logFile != null) return

            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            _enabled = prefs!!.getBoolean(KEY_ENABLED, false)
            _enabledCategories = prefs!!.getStringSet(KEY_CATEGORIES, emptySet())?.toSet() ?: ALL_CATEGORIES.toSet()

            // 首次使用，默认全部分类启用
            if (_enabledCategories.isEmpty()) {
                _enabledCategories = ALL_CATEGORIES.toSet()
            }

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
        if (!_enabled || tag !in _enabledCategories) return
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
