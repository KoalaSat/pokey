package com.koalasat.pokey.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface ApplicationDao {
    @Query("SELECT MAX(time) FROM notification")
    fun getLatestNotification(): Long?

    @Query("SELECT EXISTS (SELECT 1 FROM notification WHERE eventId = :eventId)")
    fun existsNotification(eventId: String): Int

    @Query("SELECT * FROM notification WHERE title != '' AND hidden = 0 ORDER BY time DESC")
    fun getNotifications(): List<NotificationEntity>

    @Query("SELECT * FROM notification WHERE accountKexPub = :accountKexPub AND eventId = :eventId AND hidden = 0")
    fun getNotification(accountKexPub: String, eventId: String): NotificationEntity

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertNotification(notificationEntity: NotificationEntity): Long?

    @Update(onConflict = OnConflictStrategy.REPLACE)
    fun updateNotification(notificationEntity: NotificationEntity): Int

    @Query("UPDATE notification SET hidden = :hidden WHERE rootId = :eventId OR eventId = :eventId")
    fun updateMuteThreadNotifications(eventId: String, hidden: Int): Int

    @Query("UPDATE notification SET hidden = :hidden WHERE pubKey = :pubKey")
    fun updateMuteUserNotifications(pubKey: String, hidden: Int): Int

    @Query("UPDATE notification SET hidden = 0 WHERE accountKexPub = :accountKexPub")
    fun clearMutedNotifications(accountKexPub: String): Int

    @Query("SELECT EXISTS (SELECT 1 FROM relay WHERE url = :url AND kind = :kind AND hexPub = :hexPub)")
    fun existsRelay(url: String, kind: Int, hexPub: String): Int

    @Query("SELECT * FROM relay WHERE read = 1 AND hexPub = :hexPub")
    fun getReadRelays(hexPub: String): List<RelayEntity>

    @Query("SELECT * FROM relay where kind = :kind AND hexPub = :hexPub")
    fun getRelaysByKind(kind: Int, hexPub: String): List<RelayEntity>

    @Query("SELECT MAX(createdAt) FROM relay WHERE kind = :kind AND hexPub = :hexPub")
    fun getLatestRelaysByKind(kind: Int, hexPub: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertRelay(notificationEntity: RelayEntity): Long?

    @Query("DELETE FROM relay where kind = :kind AND hexPub = :hexPub")
    fun deleteRelaysByKind(kind: Int, hexPub: String): Int

    @Query("DELETE FROM relay where url = :url and kind = :kind AND hexPub = :hexPub")
    fun deleteRelayByUrl(url: String, kind: Int, hexPub: String): Int

    @Query(
        """
        SELECT DISTINCT r.* FROM relay r
        INNER JOIN user u ON r.hexPub = u.hexPub
        WHERE u.signer = 1
    """,
    )
    fun getRelaysForSignerUsers(): List<RelayEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertMute(muteEntity: MuteEntity): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(muteEntities: List<MuteEntity>)

    @Query("SELECT MAX(createdAt) FROM mute WHERE hexPub = :hexPub")
    fun getMostRecentMuteListDate(hexPub: String): Long?

    @Query("SELECT EXISTS (SELECT 1 FROM mute WHERE entityId = :entityId)")
    fun existsMuteEntity(entityId: String): Int

    @Query("SELECT * FROM mute WHERE kind = :kind AND hexPub = :hexPub AND createdAt = :createdAt")
    fun getMuteList(kind: Int, hexPub: String, createdAt: Long): List<MuteEntity>

    @Query("DELETE FROM mute where kind = :kind AND hexPub = :hexPub")
    fun deleteMuteList(kind: Int, hexPub: String): Int

    @Query("DELETE FROM mute where id = :id")
    fun deleteMuteEntity(id: Int): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertUser(userEntity: UserEntity): Long?

    @Update(onConflict = OnConflictStrategy.REPLACE)
    fun updateUser(userEntity: UserEntity): Int

    @Delete
    fun deleteUser(userEntity: UserEntity): Int

    @Query("SELECT * FROM user WHERE hexPub = :hexPub LIMIT 1")
    fun getUser(hexPub: String): UserEntity?

    @Query("SELECT * FROM user")
    fun getUsers(): List<UserEntity>

    @Query("SELECT * FROM user WHERE signer = 1")
    fun getSignerUsers(): List<UserEntity>
}
