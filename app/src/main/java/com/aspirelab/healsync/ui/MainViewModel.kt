package com.aspirelab.healsync.ui

import android.app.Application
import android.content.Intent
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.aspirelab.healsync.auth.AuthManager
import com.aspirelab.healsync.data.AppPrefs
import com.aspirelab.healsync.data.HealthStore
import com.aspirelab.healsync.data.HealthSummary
import com.aspirelab.healsync.googlehealth.GoogleHealthClient
import com.aspirelab.healsync.googlehealth.GoogleHealthException
import com.aspirelab.healsync.healthconnect.HealthConnectWriter
import com.aspirelab.healsync.sync.SyncWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.openid.appauth.AuthorizationService
import java.time.Instant

enum class HealthConnectState { Available, NeedsUpdate, NotInstalled }

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = AppPrefs(app)
    val auth = AuthManager(prefs)
    private val writer = HealthConnectWriter(app)
    private val store = HealthStore(app)

    /** null until DataStore has been read, so the UI doesn't flash the login screen on start. */
    val isSignedIn: StateFlow<Boolean?> = prefs.isSignedIn.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val lastSync: StateFlow<Instant?> = prefs.lastSync.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val summary: StateFlow<HealthSummary> = store.summary().stateIn(viewModelScope, SharingStarted.Eagerly, HealthSummary())
    val log: StateFlow<List<String>> = prefs.log.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val isSyncing: StateFlow<Boolean> = WorkManager.getInstance(app)
        .getWorkInfosForUniqueWorkFlow(SyncWorker.MANUAL_NAME)
        .map { infos -> infos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _healthConnect = MutableStateFlow(HealthConnectState.NotInstalled)
    val healthConnect: StateFlow<HealthConnectState> = _healthConnect.asStateFlow()

    private val _hcGranted = MutableStateFlow(true)
    val hcGranted: StateFlow<Boolean> = _hcGranted.asStateFlow()

    private val _signingIn = MutableStateFlow(false)
    val signingIn: StateFlow<Boolean> = _signingIn.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    fun refreshHealthConnect() {
        _healthConnect.value = when (HealthConnectWriter.sdkStatus(getApplication())) {
            HealthConnectClient.SDK_AVAILABLE -> HealthConnectState.Available
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthConnectState.NeedsUpdate
            else -> HealthConnectState.NotInstalled
        }
        if (_healthConnect.value == HealthConnectState.Available) {
            viewModelScope.launch { _hcGranted.value = writer.hasAllPermissions() }
        }
    }

    fun onSignInStarted() {
        _authError.value = null
        _signingIn.value = true
    }

    fun onAuthResult(service: AuthorizationService, data: Intent?) {
        viewModelScope.launch {
            try {
                auth.completeAuthorization(service, data)
                prefs.appendLog("Đã đăng nhập Google")
                GoogleHealthClient(accessToken = { auth.freshAccessToken(service) }).identity()
                prefs.appendLog("Tài khoản đã liên kết với Google Health")
                SyncWorker.syncNow(getApplication())
            } catch (e: GoogleHealthException) {
                val message = if (e.isAccountNotLinked) {
                    "Tài khoản chưa liên kết Google Health. Mở app Fitbit, đăng nhập bằng tài khoản Google này rồi thử lại."
                } else {
                    "Không kiểm tra được tài khoản: ${e.message}"
                }
                prefs.appendLog(message)
                _authError.value = message
                auth.signOut()
            } catch (e: Exception) {
                prefs.appendLog("Lỗi đăng nhập: ${e.message}")
                _authError.value = e.message
            } finally {
                _signingIn.value = false
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            auth.signOut()
            prefs.appendLog("Đã đăng xuất")
        }
    }

    fun syncNow() = SyncWorker.syncNow(getApplication())
}
