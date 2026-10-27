package com.aspirelab.healsync.healthconnect

import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import com.aspirelab.healsync.googlehealth.HeartRateSample
import com.aspirelab.healsync.googlehealth.SleepSession
import com.aspirelab.healsync.googlehealth.StepsWindow
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/**
 * Builds Health Connect records. Every record gets a stable clientRecordId, so syncing the same
 * period again replaces the earlier record (newer clientRecordVersion) instead of duplicating it.
 */
class RecordFactory(
    private val version: Long,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    private val device = Device(type = Device.TYPE_WATCH, manufacturer = "Fitbit", model = "Charge 5")

    fun steps(windows: List<StepsWindow>): List<StepsRecord> = windows
        .filter { it.count in 1..1_000_000 }
        .map { window ->
            StepsRecord(
                startTime = window.start,
                startZoneOffset = offsetAt(window.start),
                endTime = window.end,
                endZoneOffset = offsetAt(window.end),
                count = window.count,
                metadata = metadata("hs-steps-${window.start.epochSecond}"),
            )
        }

    /** One record per clock hour; re-syncing an hour replaces it with the fuller sample list. */
    fun heartRate(samples: List<HeartRateSample>): List<HeartRateRecord> = samples
        .filter { it.bpm in 1..300 }
        .distinctBy { it.time }
        .sortedBy { it.time }
        .groupBy { it.time.truncatedTo(ChronoUnit.HOURS) }
        .map { (hour, hourSamples) ->
            val first = hourSamples.first()
            val endTime = hourSamples.last().time.plusSeconds(1)
            HeartRateRecord(
                startTime = first.time,
                startZoneOffset = first.offset ?: offsetAt(first.time),
                endTime = endTime,
                endZoneOffset = hourSamples.last().offset ?: offsetAt(endTime),
                samples = hourSamples.map { HeartRateRecord.Sample(time = it.time, beatsPerMinute = it.bpm) },
                metadata = metadata("hs-hr-${hour.epochSecond}"),
            )
        }

    fun sleep(sessions: List<SleepSession>): List<SleepSessionRecord> = sessions.map { session ->
        SleepSessionRecord(
            startTime = session.start,
            startZoneOffset = session.startOffset ?: offsetAt(session.start),
            endTime = session.end,
            endZoneOffset = session.endOffset ?: offsetAt(session.end),
            metadata = metadata("hs-sleep-${session.id}"),
            stages = validStages(session),
        )
    }

    /** Health Connect rejects stages outside the session or overlapping each other. */
    private fun validStages(session: SleepSession): List<SleepSessionRecord.Stage> {
        val result = mutableListOf<SleepSessionRecord.Stage>()
        var cursor = session.start
        for (stage in session.stages.sortedBy { it.start }) {
            val start = maxOf(stage.start, cursor)
            val end = minOf(stage.end, session.end)
            if (!end.isAfter(start)) continue
            result += SleepSessionRecord.Stage(startTime = start, endTime = end, stage = stageType(stage.type))
            cursor = end
        }
        return result
    }

    private fun metadata(clientRecordId: String) =
        Metadata.autoRecorded(device = device, clientRecordId = clientRecordId, clientRecordVersion = version)

    private fun offsetAt(instant: Instant): ZoneOffset = zone.rules.getOffset(instant)

    companion object {
        fun stageType(type: String): Int = when (type.uppercase()) {
            "AWAKE", "WAKE" -> SleepSessionRecord.STAGE_TYPE_AWAKE
            "LIGHT" -> SleepSessionRecord.STAGE_TYPE_LIGHT
            "DEEP" -> SleepSessionRecord.STAGE_TYPE_DEEP
            "REM" -> SleepSessionRecord.STAGE_TYPE_REM
            "ASLEEP" -> SleepSessionRecord.STAGE_TYPE_SLEEPING
            "RESTLESS" -> SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED
            else -> SleepSessionRecord.STAGE_TYPE_UNKNOWN
        }
    }
}
