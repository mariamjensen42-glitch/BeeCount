package com.cycling.beecount.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.cycling.beecount.domain.model.Entry
import com.cycling.beecount.domain.model.EntryType
import java.time.LocalDate

@Entity(tableName = "entries")
data class EntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val type: EntryType,
    val amount: Double,
    val amountRaw: String,
    val categoryName: String,
    val date: LocalDate,
    val note: String,
    val createdAt: Long,
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
)
