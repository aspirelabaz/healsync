package com.aspirelab.healsync.googlehealth

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class GoogleHealthParserTest {

    private fun json(text: String) = Json.parseToJsonElement(text).jsonObject

    @Test
    fun `steps rollup reads string countSum and skips empty windows`() {
        val body = json(
            """
            {"rollupDataPoints": [
              {"startTime": "2026-03-04T07:00:00Z", "endTime": "2026-03-04T07:15:00Z", "steps": {"countSum": "412"}},
              {"startTime": "2026-03-04T07:15:00Z", "endTime": "2026-03-04T07:30:00Z", "steps": {"countSum": "0"}},
              {"startTime": "2026-03-04T07:30:00Z", "endTime": "2026-03-04T07:45:00Z"}
            ]}
            """,
        )
        val windows = GoogleHealthParser.stepsRollup(body)
        assertEquals(
            listOf(StepsWindow(Instant.parse("2026-03-04T07:00:00Z"), Instant.parse("2026-03-04T07:15:00Z"), 412)),
            windows,
        )
    }

    @Test
    fun `heart rate parses documented sample shape`() {
        val body = json(
            """
            {"dataPoints": [{
              "name": "users/me/dataTypes/heart-rate/dataPoints/hr-123456789",
              "dataSource": {"recordingMethod": "ACTIVELY_MEASURED"},
              "heartRate": {
                "sampleTime": {"physicalTime": "2026-04-20T08:00:00Z", "utcOffset": "25200s"},
                "beatsPerMinute": "72",
                "metadata": {"motionContext": "SEDENTARY", "sensorLocation": "WRIST"}
              }
            }], "nextPageToken": "abc"}
            """,
        )
        assertEquals(
            listOf(HeartRateSample(Instant.parse("2026-04-20T08:00:00Z"), ZoneOffset.ofHours(7), 72)),
            GoogleHealthParser.heartRate(body),
        )
        assertEquals("abc", GoogleHealthParser.nextPageToken(body))
    }

    @Test
    fun `sleep parses interval, offsets and stages`() {
        val body = json(
            """
            {"dataPoints": [{
              "name": "users/me/dataTypes/sleep/dataPoints/s-42",
              "sleep": {
                "interval": {"startTime": "2026-03-03T15:30:00Z", "startUtcOffset": "25200s",
                             "endTime": "2026-03-03T22:45:00Z", "endUtcOffset": "25200s"},
                "type": "STAGES",
                "stages": [
                  {"startTime": "2026-03-03T15:30:00Z", "endTime": "2026-03-03T15:40:00Z", "type": "AWAKE"},
                  {"startTime": "2026-03-03T15:40:00Z", "endTime": "2026-03-03T17:00:00Z", "type": "LIGHT"}
                ]
              }
            }]}
            """,
        )
        val session = GoogleHealthParser.sleep(body).single()
        assertEquals("s-42", session.id)
        assertEquals(ZoneOffset.ofHours(7), session.startOffset)
        assertEquals(listOf("AWAKE", "LIGHT"), session.stages.map { it.type })
    }

    @Test
    fun `reconciled sleep takes its id from dataPointName`() {
        val body = json(
            """
            {"dataPoints": [{
              "dataPointName": "users/me/dataTypes/sleep/dataPoints/s-77",
              "sleep": {"interval": {"startTime": "2026-03-03T15:30:00Z", "endTime": "2026-03-03T22:45:00Z"}}
            }]}
            """,
        )
        assertEquals("s-77", GoogleHealthParser.sleep(body).single().id)
    }

    @Test
    fun `offset parsing handles negative and invalid values`() {
        assertEquals(ZoneOffset.ofHours(-4), GoogleHealthParser.parseOffset("-14400s"))
        assertEquals(ZoneOffset.UTC, GoogleHealthParser.parseOffset("0s"))
        assertNull(GoogleHealthParser.parseOffset("abc"))
    }

    @Test
    fun `empty next page token means last page`() {
        assertNull(GoogleHealthParser.nextPageToken(json("""{"dataPoints": [], "nextPageToken": ""}""")))
    }
}
