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
)
