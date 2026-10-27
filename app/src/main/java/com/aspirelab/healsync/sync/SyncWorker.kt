package com.aspirelab.healsync.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.aspirelab.healsync.auth.ReauthRequiredException
import com.aspirelab.healsync.data.AppPrefs
import com.aspirelab.healsync.googlehealth.GoogleHealthException
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.time.Duration
import kotlin.coroutines.cancellation.CancellationException

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = AppPrefs(applicationContext)
        // Nothing to do until the user signs in; the periodic job starts with the app.
        if (!prefs.isSignedIn.first()) return Result.success()
        return try {
            val result = SyncRepository(applicationContext).sync()
            prefs.appendLog("Xong: ${result.steps} bản ghi bước chân, ${result.heartRate} nhịp tim, ${result.sleep} giấc ngủ")
            Result.success()
        } catch (e: ReauthRequiredException) {
            prefs.appendLog("Lỗi: ${e.message}")
            Result.failure()
        } catch (e: MissingPermissionException) {
            prefs.appendLog("Lỗi: ${e.message}")
            Result.failure()
        } catch (e: GoogleHealthException) {
            when {
                e.isAccountNotLinked -> {
                    prefs.appendLog("Lỗi: tài khoản Google chưa liên kết với Google Health/Fitbit. Mở app Fitbit để liên kết.")
                    Result.failure()
                }
                e.isRetryable -> {
                    prefs.appendLog("Google Health API tạm lỗi (HTTP ${e.code}), sẽ thử lại")
                    Result.retry()
                }
                else -> {
                    prefs.appendLog("Lỗi API: ${e.message}")
                    Result.failure()
                }
            }
        } catch (e: IOException) {
            prefs.appendLog("Lỗi mạng: ${e.message}, sẽ thử lại")
            Result.retry()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            prefs.appendLog("Lỗi: ${e.javaClass.simpleName}: ${e.message}")
            Result.failure()
        }
    }

    companion object {
        private const val PERIODIC_NAME = "healsync-periodic"
        const val MANUAL_NAME = "healsync-manual"

        private val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(Duration.ofHours(1))
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofMinutes(5))
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun syncNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(MANUAL_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
