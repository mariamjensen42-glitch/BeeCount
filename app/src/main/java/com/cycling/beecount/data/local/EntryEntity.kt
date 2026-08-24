package com.cycling.beecount.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.cycling.beecount.domain.model.Entry
import com.cycling.beecount.domain.model.EntryType
import java.time.LocalDate

/**
 * 账目表。[sourceRef] 为来源引用（ADR 0012）：微信导入的账目记录交易单号，
 * 可空唯一索引（SQLite 对多个 NULL 不冲突），导入去重与批量撤销都依赖它。
 */
@Entity(
    tableName = "entries",
    indices = [Index(value = ["sourceRef"], unique = true)],
)
data class EntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val type: EntryType,
    val amount: Double,
    val amountRaw: String,
    val categoryName: String,
    val date: LocalDate,
    val note: String,
    val createdAt: Long,
    val sourceRef: String? = null,
    /** 交易对方，微信导入来自账单行，其余记账可识别时写入，否则 null */
    val counterparty: String? = null,
    /** 报销标记：仅支出有效，标识该笔支出是否已报销 */
    val isReimbursed: Boolean = false,
)

fun EntryEntity.toDomain(): Entry = Entry(
    id = id,
    type = type,
    amount = amount,
    amountRaw = amountRaw,
    categoryName = categoryName,
    date = date,
    note = note,
    createdAt = createdAt,
    sourceRef = sourceRef,
    counterparty = counterparty,
    isReimbursed = isReimbursed,
)

fun Entry.toEntity(): EntryEntity = EntryEntity(
    id = id,
    type = type,
    amount = amount,
    amountRaw = amountRaw,
    categoryName = categoryName,
    date = date,
    note = note,
    createdAt = createdAt,
    sourceRef = sourceRef,
    counterparty = counterparty,
    isReimbursed = isReimbursed,
)
