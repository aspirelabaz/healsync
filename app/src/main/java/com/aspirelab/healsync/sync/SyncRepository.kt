package com.aspirelab.healsync.sync

import android.content.Context
import com.aspirelab.healsync.auth.AuthManager
import com.aspirelab.healsync.data.AppPrefs
import com.aspirelab.healsync.data.HealthStore
import com.aspirelab.healsync.googlehealth.GoogleHealthClient
import com.aspirelab.healsync.googlehealth.GoogleHealthParser
import com.aspirelab.healsync.googlehealth.HeartRateSample
import com.aspirelab.healsync.googlehealth.SleepSession
import com.aspirelab.healsync.healthconnect.HealthConnectWriter
import com.aspirelab.healsync.healthconnect.RecordFactory
import net.openid.appauth.AuthorizationService
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class MissingPermissionException(message: String) : Exception(message)

data class SyncResult(val steps: Int, val heartRate: Int, val sleep: Int)

/** Pulls data from the Google Health API and writes it to Health Connect, where Samsung Health reads it. */
class SyncRepository(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = AppPrefs(appContext)
    private val auth = AuthManager(prefs)
    private val writer = HealthConnectWriter(appContext)
    private val store = HealthStore(appContext)

    suspend fun sync(): SyncResult {
        if (!writer.isAvailable) throw MissingPermissionException("Health Connect chưa sẵn sàng trên máy")
        if (!writer.hasAllPermissions()) throw MissingPermissionException("Chưa cấp đủ quyền ghi Health Connect")

        val service = AuthorizationService(appContext)
        try {
            val api = GoogleHealthClient(accessToken = { auth.freshAccessToken(service) })
            val zone = ZoneId.systemDefault()
            val until = Instant.now()
            val from = syncStart(until, zone)
            val records = RecordFactory(version = until.toEpochMilli(), zone = zone)
            prefs.appendLog("Bắt đầu đồng bộ từ ${from.atZone(zone).toLocalDate()}")

            val steps = fetchSteps(api, from, until)
            val stepRecords = records.steps(steps)
            writer.insert(stepRecords)

            val heartRate = fetchHeartRate(api, from, until)
            val heartRateRecords = records.heartRate(heartRate)
            writer.insert(heartRateRecords)

            val sleep = fetchSleep(api, from.minus(Duration.ofDays(1)))
            val sleepRecords = records.sleep(sleep)
            writer.insert(sleepRecords)

            store.save(from, until, zone, steps, heartRate, sleep)
            prefs.saveLastSync(until)
            return SyncResult(stepRecords.size, heartRateRecords.size, sleepRecords.size)
        } finally {
            service.dispose()
        }
    }

    /**
     * Re-reads the last 24h before the previous sync: the band uploads late and Fitbit reprocesses
     * sleep, and stable clientRecordIds make the overlap harmless. Starts at local midnight so the
     * daily step totals in the database are always complete, and backfills when the database is empty.
     */
    private suspend fun syncStart(until: Instant, zone: ZoneId): Instant {
        val earliest = until.minus(MAX_LOOKBACK)
        val lastSync = prefs.lastSyncNow()?.takeIf { store.hasData() }
        val wanted = lastSync?.minus(OVERLAP) ?: until.minus(INITIAL_BACKFILL)
        return maxOf(wanted, earliest).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()
    }

    private suspend fun fetchSteps(api: GoogleHealthClient, from: Instant, until: Instant) =
        GoogleHealthParser.stepsRollup(api.rollUpSteps(from, until, STEPS_WINDOW.seconds))

    private suspend fun fetchHeartRate(api: GoogleHealthClient, from: Instant, until: Instant): List<HeartRateSample> {
        val samples = mutableListOf<HeartRateSample>()
        var dayStart = from
        while (dayStart.isBefore(until)) {
            val dayEnd = minOf(dayStart.plus(Duration.ofDays(1)), until)
            var pageToken: String? = null
            do {
                val page = api.listHeartRate(dayStart, dayEnd, pageToken)
                samples += GoogleHealthParser.heartRate(page)
                pageToken = GoogleHealthParser.nextPageToken(page)
            } while (pageToken != null)
            dayStart = dayEnd
        }
        return samples
    }

    private suspend fun fetchSleep(api: GoogleHealthClient, from: Instant): List<SleepSession> {
        val sessions = mutableListOf<SleepSession>()
        val fromDate = from.atZone(ZoneId.systemDefault()).toLocalDate()
        var pageToken: String? = null
        do {
            val page = api.reconcileSleep(fromDate, pageToken)
            sessions += GoogleHealthParser.sleep(page)
            pageToken = GoogleHealthParser.nextPageToken(page)
        } while (pageToken != null)
        return sessions
    }

    private companion object {
        val INITIAL_BACKFILL: Duration = Duration.ofDays(7)
        val OVERLAP: Duration = Duration.ofHours(24)
        val MAX_LOOKBACK: Duration = Duration.ofDays(30)
        val STEPS_WINDOW: Duration = Duration.ofMinutes(15)
    }
}
