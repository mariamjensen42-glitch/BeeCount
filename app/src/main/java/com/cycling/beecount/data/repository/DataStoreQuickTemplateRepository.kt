package com.cycling.beecount.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.cycling.beecount.domain.model.QuickTemplate
import com.cycling.beecount.domain.repository.QuickTemplateRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * 快捷模板仓库：DataStore 存储。首次读取时播种内置默认模板（早餐等高频场景），
 * 之后由用户在「管理模板」页增删改。模板列表作为一段 JSON 存于单键下（个人自用场景，风险边界同 ADR 0001）。
 */
@Singleton
class DataStoreQuickTemplateRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val json: Json,
) : QuickTemplateRepository {

    override fun observeAll(): Flow<List<QuickTemplate>> =
        dataStore.data.map { prefs -> read(prefs[TEMPLATES_KEY]) }

    override suspend fun add(template: QuickTemplate): Long {
        val current = readCurrent()
        val id = (current.maxOfOrNull { it.id } ?: 0L) + 1
        val created = template.copy(id = id)
        persist(current + created)
        return id
    }

    override suspend fun update(template: QuickTemplate) {
        val current = readCurrent()
        persist(current.map { if (it.id == template.id) template else it })
    }

    override suspend fun delete(id: Long) {
        val current = readCurrent()
        persist(current.filter { it.id != id })
    }

    /** 读取当前模板；首次（键不存在）时播种默认模板并返回。 */
    private suspend fun readCurrent(): List<QuickTemplate> {
        val raw = dataStore.data.map { it[TEMPLATES_KEY] }.first()
        if (raw != null) return decode(raw)
        val defaults = defaultTemplates()
        dataStore.edit { prefs -> prefs[TEMPLATES_KEY] = encode(defaults) }
        return defaults
    }

    private fun read(raw: String?): List<QuickTemplate> =
        if (raw == null) defaultTemplates() else decode(raw)

    private fun decode(raw: String): List<QuickTemplate> =
        runCatching { json.decodeFromString(ListSerializer(QuickTemplate.serializer()), raw) }
            .getOrDefault(defaultTemplates())

    private suspend fun persist(latest: List<QuickTemplate>) {
        dataStore.edit { prefs -> prefs[TEMPLATES_KEY] = encode(latest) }
    }

    private fun encode(list: List<QuickTemplate>): String =
        json.encodeToString(ListSerializer(QuickTemplate.serializer()), list)

    private fun defaultTemplates(): List<QuickTemplate> = listOf(
        QuickTemplate(1L, "早餐", "餐饮", 5.0, amountRaw = "5元", note = "豆浆油条"),
        QuickTemplate(2L, "午餐", "餐饮", 25.0, amountRaw = "25元", note = "午餐"),
        QuickTemplate(3L, "下午茶", "餐饮", 20.0, amountRaw = "20元", note = "奶茶咖啡"),
        QuickTemplate(4L, "地铁", "交通", 4.0, amountRaw = "4元", note = "地铁"),
        QuickTemplate(5L, "打车", "交通", 30.0, amountRaw = "30元", note = "打车"),
    )

    private companion object {
        val TEMPLATES_KEY = stringPreferencesKey("quick_templates")
    }
}
