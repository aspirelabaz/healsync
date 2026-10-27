package com.aspirelab.healsync.data

import com.aspirelab.healsync.data.db.HeartRateEntity
import com.aspirelab.healsync.data.db.SleepEntity
import com.aspirelab.healsync.googlehealth.HeartRateSample
import com.aspirelab.healsync.googlehealth.SleepSession
import com.aspirelab.healsync.googlehealth.SleepStage
import com.aspirelab.healsync.googlehealth.StepsWindow
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class HealthAggregatesTest {

    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private fun t(s: String) = Instant.parse(s)

    @Test
    fun `steps rows carry the local date`() {
        val rows = HealthAggregates.steps(
            listOf(StepsWindow(t("2026-10-26T18:00:00Z"), t("2026-10-26T18:15:00Z"), 300)), // 27/10 01:00 local
            zone,
        )
        assertEquals("2026-10-27", rows.single().date)
        assertEquals(300L, rows.single().count)
    }

    @Test
    fun `heart rate samples are aggregated per minute and invalid ones dropped`() {
        val rows = HealthAggregates.heartRateMinutes(
            listOf(
                HeartRateSample(t("2026-10-27T01:10:05Z"), null, 60),
                HeartRateSample(t("2026-10-27T01:10:45Z"), null, 80),
                HeartRateSample(t("2026-10-27T01:11:00Z"), null, 0),
                HeartRateSample(t("2026-10-27T01:12:00Z"), null, 72),
            ),
            zone,
        )
        assertEquals(
            listOf(
                HeartRateEntity(t("2026-10-27T01:10:00Z").epochSecond, 70, 60, 80, "2026-10-27", 8),
                HeartRateEntity(t("2026-10-27T01:12:00Z").epochSecond, 72, 72, 72, "2026-10-27", 8),
            ),
            rows,
        )
    }

    @Test
    fun `sleep minutes are split by stage with unstaged time kept separately`() {
        val session = SleepSession(
            id = "s",
            start = t("2026-10-26T16:00:00Z"),
            startOffset = null,
            end = t("2026-10-26T23:00:00Z"),
            endOffset = null,
            stages = listOf(
                SleepStage(t("2026-10-26T16:00:00Z"), t("2026-10-26T16:20:00Z"), "AWAKE"),
                SleepStage(t("2026-10-26T16:20:00Z"), t("2026-10-26T19:20:00Z"), "LIGHT"),
                SleepStage(t("2026-10-26T19:20:00Z"), t("2026-10-26T20:50:00Z"), "DEEP"),
                SleepStage(t("2026-10-26T20:50:00Z"), t("2026-10-26T22:20:00Z"), "REM"),
            ),
        )
        val sleep = HealthAggregates.sleep(session)
        assertEquals(SleepEntity("s", session.start.toEpochMilli(), session.end.toEpochMilli(), 20, 90, 180, 90, 40), sleep)
        assertEquals(400, sleep.asleepMinutes)
    }
}
