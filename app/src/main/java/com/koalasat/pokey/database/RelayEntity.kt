package com.koalasat.pokey.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "relay",
    indices = [
        Index(
            value = ["url"],
            name = "relay_by_url",
        ),
    ],
)
data class RelayEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int,
    val url: String,
    val kind: Int,
    val createdAt: Long,
    val read: Int,
    val write: Int,
)
