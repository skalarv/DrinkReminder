package com.drinkreminder.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DrinkLogDao {

    @Insert
    suspend fun insert(drinkLog: DrinkLog): Long

    @Delete
    suspend fun delete(drinkLog: DrinkLog)

    @Query("SELECT * FROM drink_logs WHERE timestamp >= :startOfDay AND timestamp < :endOfDay ORDER BY timestamp DESC")
    fun getLogsForDate(startOfDay: Long, endOfDay: Long): Flow<List<DrinkLog>>

    @Query("SELECT COALESCE(SUM(amountMl), 0) FROM drink_logs WHERE timestamp >= :startOfDay AND timestamp < :endOfDay")
    fun getTotalForDate(startOfDay: Long, endOfDay: Long): Flow<Int>

    @Query("SELECT COALESCE(SUM(amountMl), 0) FROM drink_logs WHERE timestamp >= :startOfDay AND timestamp < :endOfDay")
    suspend fun getTotalForDateOnce(startOfDay: Long, endOfDay: Long): Int

    @Query("SELECT * FROM drink_logs WHERE timestamp >= :startDate AND timestamp < :endDate ORDER BY timestamp DESC")
    suspend fun getLogsBetweenDates(startDate: Long, endDate: Long): List<DrinkLog>

    @Query("SELECT DISTINCT(timestamp / 86400000) as day FROM drink_logs WHERE timestamp >= :startDate AND timestamp < :endDate")
    suspend fun getDaysWithLogs(startDate: Long, endDate: Long): List<Long>
}
