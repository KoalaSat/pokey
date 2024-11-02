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

@Database(
    entities = [
        NotificationEntity::class,
        RelayEntity::class,
    ],
    version = 6,
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
