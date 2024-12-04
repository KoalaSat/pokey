package com.koalasat.pokey.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "user",
    indices = [
        Index(
            value = ["hexPub"],
            name = "user_by_hexPub",
        ),
    ],
)
data class UserEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int,
    val hexPub: String,
    var name: String?,
    var avatar: String?,
    var createdAt: Long?,
    var signer: Int = 0,
    var account: Int = 0,
    var notifyReplies: Int = 1,
    var notifyPrivate: Int = 1,
    var notifyZaps: Int = 1,
    var notifyQuotes: Int = 1,
    var notifyReactions: Int = 1,
    var notifyMentions: Int = 1,
    var notifyReposts: Int = 1,
)
