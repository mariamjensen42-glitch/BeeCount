package com.cycling.beecount.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.cycling.beecount.domain.model.EntryType
import com.cycling.beecount.domain.model.PendingDraft
import java.time.LocalDate
import kotlinx.serialization.json.Json

/**
 * 待确认草稿表（ADR 0014）：通知记账解析成功的草稿先落队列，确认/拒绝后移除。
 * [tagsJson] 为标签名的 JSON 数组（kotlinx.serialization 序列化），
 * 草稿确认时走与文字输入相同的 ConfirmEntryUseCase（库外新标签自动注册）。
 */
@Entity(tableName = "pending_drafts")
data class PendingDraftEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val type: EntryType,
    val amount: Double,
    val amountRaw: String,
    val categoryName: String,
    val date: LocalDate,
    val tagsJson: String,
    val note: String?,
    val originalText: String,
    val createdAt: Long,
)

fun PendingDraftEntity.toDomain(): PendingDraft = PendingDraft(
    id = id,
    type = type,
    amount = amount,
    amountRaw = amountRaw,
    categoryName = categoryName,
    date = date,
    tags = runCatching { Json.decodeFromString<List<String>>(tagsJson) }.getOrDefault(emptyList()),
    note = note,
    originalText = originalText,
    createdAt = createdAt,
)

fun PendingDraft.toEntity(): PendingDraftEntity = PendingDraftEntity(
    id = id,
    type = type,
    amount = amount,
    amountRaw = amountRaw,
    categoryName = categoryName,
    date = date,
    tagsJson = Json.encodeToString(tags),
    note = note,
    originalText = originalText,
    createdAt = createdAt,
)
