package com.example.notification_agent.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages ORDER BY timestamp DESC LIMIT 500")
    fun observeRecent(): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: MessageEntity): Long

    @Query("DELETE FROM messages WHERE timestamp < :cutoffTimestamp")
    suspend fun deleteOlderThan(cutoffTimestamp: Long): Int

    @Query("DELETE FROM messages")
    suspend fun clear()
}

@Dao
interface FilterRuleDao {
    @Query("SELECT * FROM filter_rules WHERE sourceType = :type ORDER BY sourceLabel COLLATE NOCASE")
    fun observeByType(type: SourceType): Flow<List<FilterRuleEntity>>

    @Query("SELECT * FROM filter_rules WHERE sourceType = :type")
    suspend fun listByType(type: SourceType): List<FilterRuleEntity>

    @Query("SELECT COUNT(*) FROM filter_rules WHERE sourceType = :type")
    suspend fun countByType(type: SourceType): Int

    @Query("SELECT * FROM filter_rules WHERE sourceType = :type AND sourceKey = :key LIMIT 1")
    suspend fun findRule(type: SourceType, key: String): FilterRuleEntity?

    @Query("SELECT enabled FROM filter_rules WHERE sourceType = :type AND sourceKey = :key LIMIT 1")
    suspend fun isEnabled(type: SourceType, key: String): Boolean?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: FilterRuleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rules: List<FilterRuleEntity>)

    @Query("DELETE FROM filter_rules WHERE sourceType = :type AND sourceKey = :key")
    suspend fun delete(type: SourceType, key: String)
}

@Dao
interface CrashLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: CrashLogEntity): Long

    @Query("SELECT * FROM crash_logs WHERE sent = 0 ORDER BY occurredAt ASC LIMIT 20")
    suspend fun pendingLogs(): List<CrashLogEntity>

    @Query("UPDATE crash_logs SET sent = 1 WHERE id IN (:ids)")
    suspend fun markSent(ids: List<Long>)

    @Query("DELETE FROM crash_logs WHERE sent = 1 AND occurredAt < :cutoff")
    suspend fun deleteSentBefore(cutoff: Long)

    @Query("DELETE FROM crash_logs WHERE occurredAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}
