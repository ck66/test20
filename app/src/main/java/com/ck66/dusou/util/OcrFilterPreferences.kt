package com.ck66.dusou.util

import android.content.Context
import org.json.JSONArray

/**
 * OCR 过滤词配置管理。
 *
 * 存储结构：
 * - filter_enabled: Boolean，总开关
 * - user_filters: List<String>，用户自定义过滤词
 * - disabled_default_filters: List<String>，用户禁用的系统默认过滤词
 */
object OcrFilterPreferences {

    private const val PREFS_NAME = "ocr_filter_prefs"
    private const val KEY_FILTER_ENABLED = "filter_enabled"
    private const val KEY_USER_FILTERS = "user_filters"
    private const val KEY_DISABLED_DEFAULTS = "disabled_default_filters"

    /** 系统默认过滤词（正则表达式） */
    val DEFAULT_FILTERS = listOf(
        // 题型标签
        """^\s*单选题\s*$""",
        """^\s*多选题\s*$""",
        """^\s*判断题\s*$""",
        """^\s*填空题\s*$""",
        """^\s*简答题\s*$""",
        // 进度指示
        """^\s*第\s*\d+\s*/\s*\d+\s*题\s*$""",
        // 统计信息
        """^\s*\d*正确\d*错误\s*$""",
        """^\s*O正\s*$""",
        // 图标符号
        """^\s*☆\s*$""",
        // UI 文字
        """^\s*屏幕共享\s*$""",
        """^\s*共享中\s*$""",
        """^\s*顺序练习\s*$""",
        """^\s*随机练习\s*$""",
        // 文件名
        """^\s*题库\d*\.txt\s*$""",
        // 答案行
        """^\s*答案[:：]\s*[A-Da-d]+\s*$""",
        // 单独选项行残留
        """^\s*[A-Da-d][.、)．]\s*.+$"""
    )

    /** 用户自定义过滤词的显示名称（友好展示） */
    private val DISPLAY_NAMES = mapOf(
        """^\s*单选题\s*$""" to "单选题",
        """^\s*多选题\s*$""" to "多选题",
        """^\s*判断题\s*$""" to "判断题",
        """^\s*填空题\s*$""" to "填空题",
        """^\s*简答题\s*$""" to "简答题",
        """^\s*第\s*\d+\s*/\s*\d+\s*题\s*$""" to "第X/Y题",
        """^\s*\d*正确\d*错误\s*$""" to "X正确X错误",
        """^\s*O正\s*$""" to "O正",
        """^\s*☆\s*$""" to "☆",
        """^\s*屏幕共享\s*$""" to "屏幕共享",
        """^\s*共享中\s*$""" to "共享中",
        """^\s*顺序练习\s*$""" to "顺序练习",
        """^\s*随机练习\s*$""" to "随机练习",
        """^\s*题库\d*\.txt\s*$""" to "题库.txt",
        """^\s*答案[:：]\s*[A-Da-d]+\s*$""" to "答案：X",
        """^\s*[A-Da-d][.、)．]\s*.+$""" to "选项行残留"
    )

    fun isFilterEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_FILTER_ENABLED, true)
    }

    fun setFilterEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_FILTER_ENABLED, enabled).apply()
    }

    fun getUserFilters(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_USER_FILTERS, "[]") ?: "[]"
        return parseJsonArray(json)
    }

    fun setUserFilters(context: Context, filters: List<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_USER_FILTERS, toJsonArray(filters)).apply()
    }

    fun getDisabledDefaults(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_DISABLED_DEFAULTS, "[]") ?: "[]"
        return parseJsonArray(json)
    }

    fun setDisabledDefaults(context: Context, filters: List<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_DISABLED_DEFAULTS, toJsonArray(filters)).apply()
    }

    /**
     * 获取最终生效的过滤词列表（正则表达式）。
     * = (系统默认 - 用户禁用) + 用户自定义
     */
    fun getEffectiveFilters(context: Context): List<String> {
        val disabledDefaults = getDisabledDefaults(context).toSet()
        val userFilters = getUserFilters(context)

        val effectiveDefaults = DEFAULT_FILTERS.filter { it !in disabledDefaults }
        return effectiveDefaults + userFilters
    }

    /**
     * 获取系统默认过滤词的友好显示名称。
     */
    fun getDisplayName(regex: String): String {
        return DISPLAY_NAMES[regex] ?: regex
    }

    private fun parseJsonArray(json: String): List<String> {
        val arr = JSONArray(json)
        val list = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            list.add(arr.getString(i))
        }
        return list
    }

    private fun toJsonArray(list: List<String>): String {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        return arr.toString()
    }
}
