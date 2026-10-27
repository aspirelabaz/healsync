package com.aspirelab.healsync

import android.app.Application
import com.aspirelab.healsync.sync.SyncWorker

class HealSyncApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SyncWorker.schedulePeriodic(this)
    }
}
