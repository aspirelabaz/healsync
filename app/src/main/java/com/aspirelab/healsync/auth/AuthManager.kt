package com.aspirelab.healsync.auth

import android.content.Intent
import android.net.Uri
import com.aspirelab.healsync.BuildConfig
import com.aspirelab.healsync.data.AppPrefs
import kotlinx.coroutines.suspendCancellableCoroutine
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenResponse
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Raised when there is no usable Google sign-in and the user has to sign in again. */
class ReauthRequiredException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Google OAuth 2.0 (authorization code + PKCE) through a Custom Tab, as Google requires for mobile apps. */
class AuthManager(private val prefs: AppPrefs) {

    val isConfigured: Boolean get() = BuildConfig.GOOGLE_CLIENT_ID.isNotBlank()

    fun authorizationIntent(service: AuthorizationService): Intent {
        val request = AuthorizationRequest.Builder(
            SERVICE_CONFIG,
            BuildConfig.GOOGLE_CLIENT_ID,
            ResponseTypeValues.CODE,
            REDIRECT_URI,
        )
            .setScopes(SCOPES)
            .setPrompt("consent")
            .build()
        return service.getAuthorizationRequestIntent(request)
    }

    /** Exchanges the authorization code from the Custom Tab result for tokens and stores them. */
    suspend fun completeAuthorization(service: AuthorizationService, data: Intent?) {
        if (data == null) throw ReauthRequiredException("Đã huỷ đăng nhập")
        val response = AuthorizationResponse.fromIntent(data)
        val error = AuthorizationException.fromIntent(data)
        if (response == null) throw ReauthRequiredException("Đăng nhập thất bại: ${error?.errorDescription ?: error?.error}", error)

        val state = AuthState(response, error)
        val tokenResponse = suspendCancellableCoroutine<TokenResponse> { cont ->
            service.performTokenRequest(response.createTokenExchangeRequest()) { token, ex ->
                if (token != null) cont.resume(token)
                else cont.resumeWithException(ReauthRequiredException("Không đổi được mã lấy token: ${ex?.errorDescription ?: ex?.error}", ex))
            }
        }
        state.update(tokenResponse, null)
        prefs.saveAuthState(state.jsonSerializeString())
    }

    /** Returns a valid access token, refreshing it with the stored refresh token when needed. */
    suspend fun freshAccessToken(service: AuthorizationService): String {
        val json = prefs.authStateJson() ?: throw ReauthRequiredException("Chưa đăng nhập Google")
        val state = AuthState.jsonDeserialize(json)
        val token = suspendCancellableCoroutine<String> { cont ->
            state.performActionWithFreshTokens(service) { accessToken, _, ex ->
                when {
                    accessToken != null -> cont.resume(accessToken)
                    // invalid_grant: refresh token revoked or expired (7 days while the OAuth app is in Testing).
                    ex?.type == AuthorizationException.TYPE_OAUTH_TOKEN_ERROR ->
                        cont.resumeWithException(ReauthRequiredException("Phiên đăng nhập đã hết hạn, hãy đăng nhập lại", ex))
                    else -> cont.resumeWithException(ex ?: IllegalStateException("Không có access token"))
                }
            }
        }
        prefs.saveAuthState(state.jsonSerializeString())
        return token
    }

    suspend fun signOut() = prefs.saveAuthState(null)

    companion object {
        private val SERVICE_CONFIG = AuthorizationServiceConfiguration(
            Uri.parse("https://accounts.google.com/o/oauth2/v2/auth"),
            Uri.parse("https://oauth2.googleapis.com/token"),
        )
        private val REDIRECT_URI: Uri = Uri.parse("com.aspirelab.healsync:/oauth2redirect")
        private const val SCOPE_PREFIX = "https://www.googleapis.com/auth/googlehealth"
        val SCOPES = listOf(
            "$SCOPE_PREFIX.activity_and_fitness.readonly",
            "$SCOPE_PREFIX.health_metrics_and_measurements.readonly",
            "$SCOPE_PREFIX.sleep.readonly",
        )
    }
}
