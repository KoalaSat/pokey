package com.koalasat.pokey.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "mute",
    indices = [
        Index(
            value = ["entityId"],
            name = "mute_by_entityId",
        ),
        Index(
            value = ["hexPub", "entityId", "createdAt"],
            name = "mute_unique_hexPub_entityId_createdAt",
            unique = true,
        ),
    ],
)
data class MuteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int,
    val kind: Int,
    val private: Int,
    val tagType: String,
    val hexPub: String,
    val entityId: String,
    var createdAt: Long = 0L,
)
