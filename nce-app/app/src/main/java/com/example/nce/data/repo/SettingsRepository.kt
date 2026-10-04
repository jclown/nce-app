package com.example.nce.data.repo

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class LanguageMode(val value: Int) { EN(0), EN_CN(1), CN(2);
    companion object { fun from(v: Int) = entries.first { it.value == v } }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val key = intPreferencesKey("language_mode")

    val languageMode: Flow<LanguageMode> = context.dataStore.data.map { prefs ->
        LanguageMode.from(prefs[key] ?: LanguageMode.EN_CN.value)
    }

    suspend fun setLanguageMode(mode: LanguageMode) {
        context.dataStore.edit { it[key] = mode.value }
    }
}
