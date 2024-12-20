package com.koalasat.pokey.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notification",
    indices = [
        Index(
            value = ["eventId"],
            name = "notification_by_eventId",
        ),
    ],
)
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true)
    var id: Long,
    val eventId: String,
    val accountKexPub: String,
    val time: Long,
    var title: String? = "",
    var text: String? = "",
    var avatarUrl: String? = "",
    var nip32: String? = "",
)
