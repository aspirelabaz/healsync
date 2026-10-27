package com.aspirelab.healsync.healthconnect

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord

class HealthConnectWriter(private val context: Context) {

    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    val isAvailable: Boolean
        get() = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    suspend fun hasAllPermissions(): Boolean =
        client.permissionController.getGrantedPermissions().containsAll(PERMISSIONS)

    suspend fun insert(records: List<Record>) {
        records.chunked(BATCH_SIZE).forEach { client.insertRecords(it) }
    }

    companion object {
        private const val BATCH_SIZE = 500

        val PERMISSIONS = setOf(
            HealthPermission.getWritePermission(StepsRecord::class),
            HealthPermission.getWritePermission(HeartRateRecord::class),
            HealthPermission.getWritePermission(SleepSessionRecord::class),
        )

        fun sdkStatus(context: Context): Int = HealthConnectClient.getSdkStatus(context)
    }
}
