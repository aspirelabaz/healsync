package com.aspirelab.healsync.ui

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.getValue
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aspirelab.healsync.healthconnect.HealthConnectWriter
import com.aspirelab.healsync.ui.glass.GlassScaffold
import net.openid.appauth.AuthorizationService

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private lateinit var authService: AuthorizationService

    private val authLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        vm.onAuthResult(authService, it.data)
    }

    private val hcPermissionLauncher =
        registerForActivityResult(PermissionController.createRequestPermissionResultContract()) {
            vm.refreshHealthConnect()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authService = AuthorizationService(this)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            HealSyncTheme {
                GlassScaffold {
                    val signedIn by vm.isSignedIn.collectAsStateWithLifecycle()
                    AnimatedContent(
                        targetState = signedIn,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "screen",
                    ) { state ->
                        when (state) {
                            null -> Unit
                            false -> {
                                val signingIn by vm.signingIn.collectAsStateWithLifecycle()
                                val error by vm.authError.collectAsStateWithLifecycle()
                                LoginScreen(
                                    configured = vm.auth.isConfigured,
                                    signingIn = signingIn,
                                    error = error,
                                    onSignIn = {
                                        vm.onSignInStarted()
                                        authLauncher.launch(vm.auth.authorizationIntent(authService))
                                    },
                                )
                            }
                            true -> {
                                val summary by vm.summary.collectAsStateWithLifecycle()
                                val lastSync by vm.lastSync.collectAsStateWithLifecycle()
                                val syncing by vm.isSyncing.collectAsStateWithLifecycle()
                                val hcState by vm.healthConnect.collectAsStateWithLifecycle()
                                val hcGranted by vm.hcGranted.collectAsStateWithLifecycle()
                                val log by vm.log.collectAsStateWithLifecycle()
                                HomeScreen(
                                    summary = summary,
                                    lastSync = lastSync,
                                    syncing = syncing,
                                    healthConnect = hcState,
                                    hcGranted = hcGranted,
                                    log = log,
                                    onSync = vm::syncNow,
                                    onSignOut = vm::signOut,
                                    onGrantHealthConnect = { hcPermissionLauncher.launch(HealthConnectWriter.PERMISSIONS) },
                                    onInstallHealthConnect = ::openHealthConnectInStore,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshHealthConnect()
    }

    override fun onDestroy() {
        authService.dispose()
        super.onDestroy()
    }

    private fun openHealthConnectInStore() {
        val uri = Uri.parse("market://details?id=com.google.android.apps.healthdata&url=healthconnect%3A%2F%2Fonboarding")
        startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage("com.android.vending"))
    }
}
