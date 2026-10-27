package com.aspirelab.healsync.data.db

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Steps in one rollup window (15 minutes). Times are epoch seconds; [date] is the local day. */
@Entity(tableName = "steps", indices = [Index("date")])
data class StepsEntity(
    @PrimaryKey val startTime: Long,
    val endTime: Long,
    val count: Long,
    val date: String,
)

/** Heart rate aggregated per minute, which keeps a day at ~1,400 rows instead of tens of thousands of samples. */
@Entity(tableName = "heart_rate", indices = [Index("date")])
data class HeartRateEntity(
    @PrimaryKey val minute: Long,
    val avgBpm: Int,
    val minBpm: Int,
    val maxBpm: Int,
    val date: String,
    val hour: Int,
)

/** One sleep session with its minutes per stage. Times are epoch milliseconds. */
@Entity(tableName = "sleep")
data class SleepEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "start_time") val start: Long,
    @ColumnInfo(name = "end_time") val end: Long,
    val awakeMinutes: Int,
    val remMinutes: Int,
    val lightMinutes: Int,
    val deepMinutes: Int,
    /** Asleep time Fitbit did not split into stages (classic sleep). */
    val otherMinutes: Int,
) {
    val asleepMinutes: Int get() = remMinutes + lightMinutes + deepMinutes + otherMinutes
}

data class DailySteps(val date: String, val steps: Long)

data class HourlyHeartRate(val hour: Int, val avg: Int, val min: Int, val max: Int)

@Dao
interface HealthDao {

    @Query("DELETE FROM steps WHERE startTime >= :from AND startTime < :until")
    suspend fun deleteSteps(from: Long, until: Long)

    @Upsert
    suspend fun upsertSteps(rows: List<StepsEntity>)

    @Query("DELETE FROM heart_rate WHERE minute >= :from AND minute < :until")
    suspend fun deleteHeartRate(from: Long, until: Long)

    @Upsert
    suspend fun upsertHeartRate(rows: List<HeartRateEntity>)

    @Upsert
    suspend fun upsertSleep(rows: List<SleepEntity>)

    @Query("DELETE FROM steps WHERE startTime < :before")
    suspend fun pruneSteps(before: Long)

    @Query("DELETE FROM heart_rate WHERE minute < :before")
    suspend fun pruneHeartRate(before: Long)

    @Query("DELETE FROM sleep WHERE end_time < :beforeMillis")
    suspend fun pruneSleep(beforeMillis: Long)

    /**
     * Replaces everything in [from, until) (epoch seconds) with freshly fetched rows, so data the
     * API no longer reports for that range doesn't linger.
     */
    @Transaction
    suspend fun replaceRange(
        from: Long,
        until: Long,
        steps: List<StepsEntity>,
        heartRate: List<HeartRateEntity>,
        sleep: List<SleepEntity>,
        pruneBefore: Long,
    ) {
        deleteSteps(from, until)
        upsertSteps(steps)
        deleteHeartRate(from, until)
        upsertHeartRate(heartRate)
        upsertSleep(sleep)
        pruneSteps(pruneBefore)
        pruneHeartRate(pruneBefore)
        pruneSleep(pruneBefore * 1000)
    }

    @Query("SELECT EXISTS(SELECT 1 FROM steps) OR EXISTS(SELECT 1 FROM heart_rate) OR EXISTS(SELECT 1 FROM sleep)")
    suspend fun hasData(): Boolean

    @Query("SELECT date, SUM(count) AS steps FROM steps WHERE date >= :fromDate GROUP BY date")
    fun dailySteps(fromDate: String): Flow<List<DailySteps>>

    @Query(
        "SELECT hour, CAST(AVG(avgBpm) AS INTEGER) AS `avg`, MIN(minBpm) AS `min`, MAX(maxBpm) AS `max` " +
            "FROM heart_rate WHERE date = :date GROUP BY hour ORDER BY hour",
    )
    fun hourlyHeartRate(date: String): Flow<List<HourlyHeartRate>>

    @Query("SELECT * FROM heart_rate ORDER BY minute DESC LIMIT 1")
    fun latestHeartRate(): Flow<HeartRateEntity?>

    @Query("SELECT * FROM sleep ORDER BY end_time DESC LIMIT 1")
    fun latestSleep(): Flow<SleepEntity?>
}

@Database(
    entities = [StepsEntity::class, HeartRateEntity::class, SleepEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class HealthDatabase : RoomDatabase() {
    abstract fun dao(): HealthDao

    companion object {
        @Volatile
        private var instance: HealthDatabase? = null

        fun get(context: Context): HealthDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, HealthDatabase::class.java, "healsync.db")
                // Everything here can be re-downloaded by a sync, so a schema change may simply start over.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
                .also { instance = it }
        }
    }
}
