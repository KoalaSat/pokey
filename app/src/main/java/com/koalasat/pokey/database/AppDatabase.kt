package com.koalasat.pokey.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.vitorpamplona.ammolite.relays.COMMON_FEED_TYPES
import com.vitorpamplona.ammolite.relays.RelaySetupInfo

val MIGRATION_5_6 =
    object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `relay` ADD COLUMN `read` INT NOT NULL DEFAULT TRUE")
            db.execSQL("ALTER TABLE `relay` ADD COLUMN `write` INT NOT NULL DEFAULT TRUE")
        }
    }
val MIGRATION_6_7 =
    object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `mute` (\n" +
                    "    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,\n" +
                    "    `kind` INTEGER NOT NULL,\n" +
                    "    `private` INTEGER NOT NULL,\n" +
                    "    `tagType` TEXT NOT NULL,\n" +
                    "    `entityId` TEXT NOT NULL\n" +
                    ");",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `mute_by_entityId` ON `mute` (`entityId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `notification_by_eventId` ON `notification` (`eventId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `relay_by_url` ON `relay` (`url`)")
        }
    }

val MIGRATION_7_8 =
    object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `user` (\n" +
                    "    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,\n" +
                    "    `hexPub` TEXT NOT NULL,\n" +
                    "    `name` TEXT,\n" +
                    "    `avatar` TEXT,\n" +
                    "    `createdAt` INTEGER,\n" +
                    "    `notifyReplies` INTEGER NOT NULL DEFAULT 1,\n" +
                    "    `notifyPrivate` INTEGER NOT NULL DEFAULT 1,\n" +
                    "    `notifyZaps` INTEGER NOT NULL DEFAULT 1,\n" +
                    "    `notifyQuotes` INTEGER NOT NULL DEFAULT 1,\n" +
                    "    `notifyReactions` INTEGER NOT NULL DEFAULT 1,\n" +
                    "    `notifyMentions` INTEGER NOT NULL DEFAULT 1,\n" +
                    "    `notifyReposts` INTEGER NOT NULL DEFAULT 1\n" +
                    ");",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `user_by_hexPub` ON `user` (`hexPub`)")
        }
    }

val MIGRATION_8_9 =
    object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE user ADD COLUMN signer INTEGER NOT NULL DEFAULT 0;")
            db.execSQL("ALTER TABLE user ADD COLUMN account INTEGER NOT NULL DEFAULT 0;")
            db.execSQL("ALTER TABLE relay ADD COLUMN `hexPub` TEXT NOT NULL;")
            db.execSQL("ALTER TABLE mute ADD COLUMN `hexPub` TEXT NOT NULL;")
        }
    }

val MIGRATION_9_10 =
    object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE notification ADD COLUMN title TEXT;")
            db.execSQL("ALTER TABLE notification ADD COLUMN text TEXT;")
            db.execSQL("ALTER TABLE notification ADD COLUMN avatarUrl TEXT;")
            db.execSQL("ALTER TABLE notification ADD COLUMN accountKexPub TEXT NOT NULL DEFAULT '';")
            db.execSQL("ALTER TABLE notification ADD COLUMN nip32 TEXT NOT NULL DEFAULT '';")
        }
    }

val MIGRATION_10_11 =
    object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE mute")
            db.execSQL(
                """
                CREATE TABLE mute (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    kind INTEGER NOT NULL,
                    private INTEGER NOT NULL,
                    tagType TEXT NOT NULL,
                    hexPub TEXT NOT NULL,
                    entityId TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    UNIQUE(hexPub, entityId, createdAt) ON CONFLICT REPLACE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX mute_by_entityId ON mute(entityId)")
            db.execSQL("CREATE UNIQUE INDEX mute_unique_hexPub_entityId_createdAt ON mute(hexPub, entityId, createdAt)")

            db.execSQL("DROP TABLE notification")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `notification` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `eventId` TEXT NOT NULL,
                    `time` INTEGER NOT NULL,
                    `pubKey` TEXT NOT NULL,
                    `accountKexPub` TEXT NOT NULL,
                    `nip32` TEXT NOT NULL DEFAULT '',
                    `rootId` TEXT NOT NULL DEFAULT '',
                    `hidden` INTEGER NOT NULL DEFAULT 0,
                    `title` TEXT NOT NULL DEFAULT '',
                    `text` TEXT NOT NULL DEFAULT '',
                    `avatarUrl` TEXT NOT NULL DEFAULT ''
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `notification_by_eventId` ON `notification` (`eventId`)
                """.trimIndent(),
            )
        }
    }

@Database(
    entities = [
        NotificationEntity::class,
        RelayEntity::class,
        MuteEntity::class,
        UserEntity::class,
    ],
    version = 11,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun applicationDao(): ApplicationDao

    companion object {
        fun getDatabase(
            context: Context,
            pubKey: String,
        ): AppDatabase {
            return synchronized(this) {
                val instance =
                    Room.databaseBuilder(
                        context,
                        AppDatabase::class.java,
                        "pokey_db_$pubKey",
                    )
                        .addMigrations(MIGRATION_5_6)
                        .addMigrations(MIGRATION_6_7)
                        .addMigrations(MIGRATION_7_8)
                        .addMigrations(MIGRATION_8_9)
                        .addMigrations(MIGRATION_9_10)
                        .addMigrations(MIGRATION_10_11)
                        .build()
                instance
            }
        }
    }
}

class Converters {
    @TypeConverter
    fun fromString(stringListString: String): List<RelaySetupInfo> {
        if (stringListString.isBlank()) {
            return emptyList()
        }
        return stringListString.split(",").map {
            RelaySetupInfo(it, read = true, write = true, feedTypes = COMMON_FEED_TYPES)
        }
    }

    @TypeConverter
    fun toString(relays: List<RelaySetupInfo>): String {
        return relays.joinToString(separator = ",") {
            it.url
        }
    }
}
