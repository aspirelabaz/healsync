package com.aspirelab.healsync.googlehealth

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import java.time.Instant
import java.time.ZoneOffset

data class StepsWindow(val start: Instant, val end: Instant, val count: Long)

data class HeartRateSample(val time: Instant, val offset: ZoneOffset?, val bpm: Long)

data class SleepStage(val start: Instant, val end: Instant, val type: String)

data class SleepSession(
    val id: String,
    val start: Instant,
    val startOffset: ZoneOffset?,
    val end: Instant,
    val endOffset: ZoneOffset?,
    val stages: List<SleepStage>,
)

/**
 * Turns Google Health API JSON into plain models. Parsing is lenient: unknown fields are ignored and
 * malformed points are skipped, because parts of the v4 schema are still changing.
 * 64-bit numbers arrive as JSON strings ("count": "40"), so numbers are read from either form.
 */
object GoogleHealthParser {

    fun nextPageToken(body: JsonObject): String? =
        body.string("nextPageToken")?.takeIf { it.isNotEmpty() }

    fun stepsRollup(body: JsonObject): List<StepsWindow> = points(body, "rollupDataPoints").mapNotNull { point ->
        val count = point.obj("steps")?.long("countSum") ?: return@mapNotNull null
        val start = point.instant("startTime") ?: return@mapNotNull null
        val end = point.instant("endTime") ?: return@mapNotNull null
        StepsWindow(start, end, count).takeIf { count > 0 && end.isAfter(start) }
    }

    fun heartRate(body: JsonObject): List<HeartRateSample> = points(body, "dataPoints").mapNotNull { point ->
        val heartRate = point.obj("heartRate") ?: return@mapNotNull null
        val sampleTime = heartRate.obj("sampleTime") ?: return@mapNotNull null
        val time = sampleTime.instant("physicalTime") ?: return@mapNotNull null
        val bpm = heartRate.long("beatsPerMinute") ?: return@mapNotNull null
        HeartRateSample(time, sampleTime.offset("utcOffset"), bpm)
    }

    fun sleep(body: JsonObject): List<SleepSession> = points(body, "dataPoints").mapNotNull { point ->
        val sleep = point.obj("sleep") ?: return@mapNotNull null
        val interval = sleep.obj("interval") ?: return@mapNotNull null
        val start = interval.instant("startTime") ?: return@mapNotNull null
        val end = interval.instant("endTime") ?: return@mapNotNull null
        if (!end.isAfter(start)) return@mapNotNull null
        val stages = (sleep["stages"] as? JsonArray).orEmpty().mapNotNull { element ->
            val stage = element as? JsonObject ?: return@mapNotNull null
            SleepStage(
                start = stage.instant("startTime") ?: return@mapNotNull null,
                end = stage.instant("endTime") ?: return@mapNotNull null,
                type = stage.string("type") ?: "UNKNOWN",
            )
        }
        SleepSession(
            // list returns `name`, reconcile returns `dataPointName`.
            id = (point.string("name") ?: point.string("dataPointName"))?.substringAfterLast('/')
                ?: start.epochSecond.toString(),
            start = start,
            startOffset = interval.offset("startUtcOffset"),
            end = end,
            endOffset = interval.offset("endUtcOffset"),
            stages = stages,
        )
    }

    /** Durations like "-14400s" to a zone offset. */
    fun parseOffset(value: String): ZoneOffset? =
        value.removeSuffix("s").toDoubleOrNull()?.let { ZoneOffset.ofTotalSeconds(it.toInt()) }

    private fun points(body: JsonObject, key: String): List<JsonObject> {
        val array = body[key] as? JsonArray ?: body.values.firstOrNull { it is JsonArray }?.jsonArray
        return array.orEmpty().mapNotNull { it as? JsonObject }
    }

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.long(key: String): Long? = string(key)?.toDoubleOrNull()?.toLong()

    private fun JsonObject.instant(key: String): Instant? = string(key)?.let { runCatching { Instant.parse(it) }.getOrNull() }

    private fun JsonObject.offset(key: String): ZoneOffset? = string(key)?.let(::parseOffset)
}
