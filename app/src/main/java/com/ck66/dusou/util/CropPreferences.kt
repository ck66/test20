package com.ck66.dusou.util

import android.content.Context

object CropPreferences {
    private const val PREFS_NAME = "crop_prefs"
    private const val KEY_CENTER_X = "crop_center_x"
    private const val KEY_CENTER_Y = "crop_center_y"
    private const val KEY_WIDTH_RATIO = "crop_width_ratio"
    private const val KEY_HEIGHT_RATIO = "crop_height_ratio"

    fun getLastCropRatio(context: Context): CropRatio {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return CropRatio(
            centerX = prefs.getFloat(KEY_CENTER_X, 0.5f),
            centerY = prefs.getFloat(KEY_CENTER_Y, 0.5f),
            widthRatio = prefs.getFloat(KEY_WIDTH_RATIO, 0.8f),
            heightRatio = prefs.getFloat(KEY_HEIGHT_RATIO, 0.6f)
        )
    }

    fun saveCropRatio(context: Context, ratio: CropRatio) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_CENTER_X, ratio.centerX)
            .putFloat(KEY_CENTER_Y, ratio.centerY)
            .putFloat(KEY_WIDTH_RATIO, ratio.widthRatio)
            .putFloat(KEY_HEIGHT_RATIO, ratio.heightRatio)
            .apply()
    }
}

data class CropRatio(
    val centerX: Float,
    val centerY: Float,
    val widthRatio: Float,
    val heightRatio: Float
)

data class CropRect(val x: Int, val y: Int, val width: Int, val height: Int)
