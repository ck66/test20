package com.ck66.dusou.ui.practice

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "favorites")

private val FAVORITE_IDS_KEY = stringSetPreferencesKey("favorite_ids")

object FavoriteManager {

    /** 切换收藏状态，返回新的收藏状态 (true=已收藏) */
    suspend fun toggle(context: Context, questionId: Long): Boolean {
        var isNowFavorite = false
        context.dataStore.edit { prefs ->
            val currentIds = prefs[FAVORITE_IDS_KEY] ?: emptySet()
            if (questionId.toString() in currentIds) {
                prefs[FAVORITE_IDS_KEY] = currentIds - questionId.toString()
                isNowFavorite = false
            } else {
                prefs[FAVORITE_IDS_KEY] = currentIds + questionId.toString()
                isNowFavorite = true
            }
        }
        return isNowFavorite
    }

    suspend fun isFavorite(context: Context, questionId: Long): Boolean {
        val ids = context.dataStore.data
            .map { prefs -> prefs[FAVORITE_IDS_KEY] ?: emptySet() }
            .first()
        return questionId.toString() in ids
    }

    suspend fun getAllFavorites(context: Context): List<Long> {
        val ids = context.dataStore.data
            .map { prefs -> prefs[FAVORITE_IDS_KEY] ?: emptySet() }
            .first()
        return ids.mapNotNull { it.toLongOrNull() }
    }
}
