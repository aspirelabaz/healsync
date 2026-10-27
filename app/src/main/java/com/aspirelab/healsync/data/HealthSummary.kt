package com.aspirelab.healsync.data

import android.content.Context
import com.aspirelab.healsync.data.db.HealthDatabase
import com.aspirelab.healsync.data.db.HeartRateEntity
import com.aspirelab.healsync.data.db.HourlyHeartRate
import com.aspirelab.healsync.data.db.SleepEntity
import com.aspirelab.healsync.data.db.StepsEntity
import com.aspirelab.healsync.googlehealth.HeartRateSample
import com.aspirelab.healsync.googlehealth.SleepSession
import com.aspirelab.healsync.googlehealth.StepsWindow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** What the home screen shows, read from the local database so it is there before any sync. */
data class HealthSummary(
    /** ISO date to step count for the last [KEEP_DAYS] days. */
    val dailySteps: Map<String, Long> = emptyMap(),
    val heartRateByHour: List<HourlyHeartRate> = emptyList(),
    val latestHeartRate: Int? = null,
    val latestHeartRateAt: Long? = null,
    val lastSleep: SleepEntity? = null,
) {
    fun stepsOn(date: LocalDate): Long = dailySteps[date.toString()] ?: 0

    companion object {
        const val KEEP_DAYS = 14L
    }
}

/** Saves synced data to Room and exposes it as a [HealthSummary] flow. */
class HealthStore(context: Context) {
    private val dao = HealthDatabase.get(context).dao()

    suspend fun hasData(): Boolean = dao.hasData()

    suspend fun save(
        from: Instant,
        until: Instant,
        zone: ZoneId,
        steps: List<StepsWindow>,
        heartRate: List<HeartRateSample>,
        sleep: List<SleepSession>,
    ) {
        dao.replaceRange(
            from = from.epochSecond,
            until = until.epochSecond,
            steps = HealthAggregates.steps(steps, zone),
            heartRate = HealthAggregates.heartRateMinutes(heartRate, zone),
            sleep = sleep.map(HealthAggregates::sleep),
            pruneBefore = until.minus(RETENTION).epochSecond,
        )
    }

    fun summary(zone: ZoneId = ZoneId.systemDefault()): Flow<HealthSummary> {
        val today = LocalDate.now(zone)
        return combine(
            dao.dailySteps(today.minusDays(HealthSummary.KEEP_DAYS - 1).toString()),
            dao.hourlyHeartRate(today.toString()),
            dao.latestHeartRate(),
            dao.latestSleep(),
        ) { steps, hourly, latest, sleep ->
            HealthSummary(
                dailySteps = steps.associate { it.date to it.steps },
                heartRateByHour = hourly,
                latestHeartRate = latest?.avgBpm,
                latestHeartRateAt = latest?.minute?.times(1000),
                lastSleep = sleep,
            )
        }
    }

    private companion object {
        val RETENTION: Duration = Duration.ofDays(90)
    }
}

/** Pure conversions from API models to database rows. */
object HealthAggregates {

    fun steps(windows: List<StepsWindow>, zone: ZoneId): List<StepsEntity> = windows.map {
        StepsEntity(
            startTime = it.start.epochSecond,
            endTime = it.end.epochSecond,
            count = it.count,
            date = it.start.atZone(zone).toLocalDate().toString(),
        )
    }

    fun heartRateMinutes(samples: List<HeartRateSample>, zone: ZoneId): List<HeartRateEntity> = samples
        .filter { it.bpm in 1..300 }
        .groupBy { it.time.epochSecond / 60 * 60 }
        .map { (minute, inMinute) ->
            val local = Instant.ofEpochSecond(minute).atZone(zone)
            HeartRateEntity(
                minute = minute,
                avgBpm = inMinute.map { it.bpm }.average().toInt(),
                minBpm = inMinute.minOf { it.bpm }.toInt(),
                maxBpm = inMinute.maxOf { it.bpm }.toInt(),
                date = local.toLocalDate().toString(),
                hour = local.hour,
            )
        }

    fun sleep(session: SleepSession): SleepEntity {
        val minutes = mutableMapOf<String, Long>()
        for (stage in session.stages) {
            val start = maxOf(stage.start, session.start)
            val end = minOf(stage.end, session.end)
            if (end.isAfter(start)) minutes.merge(stage.type.uppercase(), Duration.between(start, end).toMinutes(), Long::plus)
        }
        val total = Duration.between(session.start, session.end).toMinutes()
        val awake = (minutes["AWAKE"] ?: 0) + (minutes["WAKE"] ?: 0) + (minutes["RESTLESS"] ?: 0)
        val rem = minutes["REM"] ?: 0
        val light = minutes["LIGHT"] ?: 0
        val deep = minutes["DEEP"] ?: 0
        return SleepEntity(
            id = session.id,
            start = session.start.toEpochMilli(),
            end = session.end.toEpochMilli(),
            awakeMinutes = awake.toInt(),
            remMinutes = rem.toInt(),
            lightMinutes = light.toInt(),
            deepMinutes = deep.toInt(),
            otherMinutes = (total - awake - rem - light - deep).coerceAtLeast(0).toInt(),
        )
    }
}
