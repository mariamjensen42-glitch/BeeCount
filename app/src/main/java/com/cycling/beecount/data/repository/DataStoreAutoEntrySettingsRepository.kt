package com.cycling.beecount.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.cycling.beecount.domain.repository.AutoEntrySettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * 自动记账设置仓库（ADR 0014）：DataStore 存总开关与各渠道失败提示节流时间戳。
 */
@Singleton
class DataStoreAutoEntrySettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : AutoEntrySettingsRepository {

    override fun observeEnabled(): Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[ENABLED] ?: false }

    override suspend fun isEnabled(): Boolean =
        dataStore.data.map { prefs -> prefs[ENABLED] ?: false }.first()

    override suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[ENABLED] = enabled }
    }

    override suspend fun lastFailureAt(packageName: String): Long =
        dataStore.data.map { prefs -> prefs[lastFailureKey(packageName)] ?: 0L }.first()

    override suspend fun setLastFailureAt(packageName: String, timeMillis: Long) {
        dataStore.edit { prefs -> prefs[lastFailureKey(packageName)] = timeMillis }
    }

    private fun lastFailureKey(packageName: String) =
        longPreferencesKey("auto_entry_last_failure_$packageName")

    private companion object {
        val ENABLED = booleanPreferencesKey("auto_entry_enabled")
    }
}
