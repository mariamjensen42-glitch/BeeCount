package com.cycling.beecount.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.cycling.beecount.domain.repository.AiKeyRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * API Key 仓库：DataStore 明文存储（见 ADR-0001，个人自用场景的风险边界）。
 */
@Singleton
class DataStoreAiKeyRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : AiKeyRepository {

    override fun observeKey(): Flow<String?> =
        dataStore.data.map { prefs -> prefs[KEY] }

    override suspend fun getKey(): String? = dataStore.data.map { prefs -> prefs[KEY] }.first()
    override suspend fun saveKey(key: String) {
        dataStore.edit { prefs -> prefs[KEY] = key.trim() }
    }

    private companion object {
        val KEY = stringPreferencesKey("deepseek_api_key")
    }
}
