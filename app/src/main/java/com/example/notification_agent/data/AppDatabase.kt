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
    entities = [MessageEntity::class, FilterRuleEntity::class, CrashLogEntity::class],
    version = 5,
    exportSchema = false
)
@TypeConverters(SourceTypeConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun filterRuleDao(): FilterRuleDao
    abstract fun crashLogDao(): CrashLogDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE filter_rules ADD COLUMN forwardToWebhook " +
                        "INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN dedupeKey TEXT")

                db.execSQL(
                    """
                    UPDATE messages
                    SET dedupeKey =
                        sourceType || '|' ||
                        sourceKey || '|' ||
                        lower(trim(replace(replace(replace(ifnull(title, ''), char(10), ' '), char(13), ' '), char(9), ' '))) || '|' ||
                        lower(trim(replace(replace(replace(ifnull(text, ''), char(10), ' '), char(13), ' '), char(9), ' ')))
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    DELETE FROM messages
                    WHERE id NOT IN (
                        SELECT MIN(id)
                        FROM messages
                        GROUP BY dedupeKey
                    )
                    """.trimIndent()
                )

                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_messages_dedupeKey ON messages(dedupeKey)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS crash_logs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        level TEXT NOT NULL,
                        tag TEXT NOT NULL,
                        thread TEXT NOT NULL,
                        exceptionClass TEXT NOT NULL,
                        message TEXT,
                        stackTrace TEXT NOT NULL,
                        occurredAt INTEGER NOT NULL,
                        appVersion TEXT NOT NULL,
                        sent INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS messages_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        sourceType TEXT NOT NULL,
                        sourceKey TEXT NOT NULL,
                        sourceLabel TEXT,
                        title TEXT,
                        text TEXT,
                        timestamp INTEGER NOT NULL,
                        dedupeKey TEXT NOT NULL DEFAULT ''
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    INSERT INTO messages_new(id, sourceType, sourceKey, sourceLabel, title, text, timestamp, dedupeKey)
                    SELECT
                        MIN(id) AS id,
                        sourceType,
                        sourceKey,
                        sourceLabel,
                        title,
                        text,
                        timestamp,
                        CASE
                            WHEN ifnull(trim(dedupeKey), '') = '' THEN
                                sourceType || '|' ||
                                sourceKey || '|' ||
                                lower(trim(replace(replace(replace(ifnull(title, ''), char(10), ' '), char(13), ' '), char(9), ' '))) || '|' ||
                                lower(trim(replace(replace(replace(ifnull(text, ''), char(10), ' '), char(13), ' '), char(9), ' ')))
                            ELSE dedupeKey
                        END AS normalizedDedupeKey
                    FROM messages
                    GROUP BY normalizedDedupeKey
                    """.trimIndent()
                )

                db.execSQL("DROP TABLE messages")
                db.execSQL("ALTER TABLE messages_new RENAME TO messages")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_messages_dedupeKey ON messages(dedupeKey)")
            }
        }
    }
}
