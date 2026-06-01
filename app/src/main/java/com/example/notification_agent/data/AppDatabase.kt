package com.example.notification_agent.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class SourceTypeConverter {
    @TypeConverter
    fun toString(value: SourceType): String = value.name

    @TypeConverter
    fun fromString(value: String): SourceType = SourceType.valueOf(value)
}

@Database(
    entities = [MessageEntity::class, FilterRuleEntity::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(SourceTypeConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun filterRuleDao(): FilterRuleDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE filter_rules ADD COLUMN forwardToWebhook " +
                        "INTEGER NOT NULL DEFAULT 0"
                )
            }
        }
    }
}
