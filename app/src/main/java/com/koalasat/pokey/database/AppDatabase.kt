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

@Database(
    entities = [
        NotificationEntity::class,
        RelayEntity::class,
        MuteEntity::class,
    ],
    version = 7,
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
