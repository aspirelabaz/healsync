package com.aspirelab.healsync.healthconnect

import androidx.health.connect.client.records.SleepSessionRecord
import com.aspirelab.healsync.googlehealth.HeartRateSample
import com.aspirelab.healsync.googlehealth.SleepSession
import com.aspirelab.healsync.googlehealth.SleepStage
import com.aspirelab.healsync.googlehealth.StepsWindow
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class RecordFactoryTest {

    private val factory = RecordFactory(version = 1L, zone = ZoneId.of("Asia/Ho_Chi_Minh"))
    private fun t(s: String) = Instant.parse(s)

    @Test
    fun `steps get stable client ids`() {
        val record = factory.steps(listOf(StepsWindow(t("2026-10-27T01:00:00Z"), t("2026-10-27T01:15:00Z"), 120))).single()
        assertEquals("hs-steps-${t("2026-10-27T01:00:00Z").epochSecond}", record.metadata.clientRecordId)
        assertEquals(120L, record.count)
    }

    @Test
    fun `heart rate is grouped per hour and drops invalid samples`() {
        val samples = listOf(
            HeartRateSample(t("2026-10-27T01:05:00Z"), null, 70),
            HeartRateSample(t("2026-10-27T01:50:00Z"), null, 0),
            HeartRateSample(t("2026-10-27T01:55:00Z"), null, 75),
            HeartRateSample(t("2026-10-27T02:01:00Z"), null, 80),
        )
        val records = factory.heartRate(samples)
        assertEquals(2, records.size)
        assertEquals(listOf(70L, 75L), records[0].samples.map { it.beatsPerMinute })
        assertEquals(t("2026-10-27T01:55:01Z"), records[0].endTime)
    }

    @Test
    fun `sleep stages are clamped to the session and never overlap`() {
        val session = SleepSession(
            id = "s-1",
            start = t("2026-10-26T15:00:00Z"),
            startOffset = null,
            end = t("2026-10-26T23:00:00Z"),
            endOffset = null,
            stages = listOf(
                SleepStage(t("2026-10-26T14:50:00Z"), t("2026-10-26T15:10:00Z"), "AWAKE"),
                SleepStage(t("2026-10-26T15:05:00Z"), t("2026-10-26T16:00:00Z"), "LIGHT"),
                SleepStage(t("2026-10-26T22:30:00Z"), t("2026-10-26T23:30:00Z"), "REM"),
            ),
        )
        val stages = factory.sleep(listOf(session)).single().stages
        assertEquals(
            listOf(
                Triple(t("2026-10-26T15:00:00Z"), t("2026-10-26T15:10:00Z"), SleepSessionRecord.STAGE_TYPE_AWAKE),
                Triple(t("2026-10-26T15:10:00Z"), t("2026-10-26T16:00:00Z"), SleepSessionRecord.STAGE_TYPE_LIGHT),
                Triple(t("2026-10-26T22:30:00Z"), t("2026-10-26T23:00:00Z"), SleepSessionRecord.STAGE_TYPE_REM),
            ),
            stages.map { Triple(it.startTime, it.endTime, it.stage) },
        )
    }
}
