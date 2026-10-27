package com.aspirelab.healsync.googlehealth

import android.util.Log
import com.aspirelab.healsync.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class GoogleHealthException(val code: Int, val body: String, val path: String = "") :
    IOException("HTTP $code ${path.substringAfter("dataTypes/")}: ${errorMessage(body)}") {
    val isAccountNotLinked: Boolean get() = "ACCOUNT_NOT_LINKED" in body
    val isRetryable: Boolean get() = code == 429 || code >= 500

    private companion object {
        /** The readable `error.message` of a Google API error body, or the raw body if it isn't one. */
        fun errorMessage(body: String): String = runCatching {
            Json.parseToJsonElement(body).jsonObject["error"]!!.jsonObject["message"]!!.jsonPrimitive.content
        }.getOrDefault(body).take(500)
    }
}

/**
 * Thin REST client for https://health.googleapis.com/v4.
 *
 * Reads are restricted to the "google-wearables" source family so data this app writes to
 * Health Connect (which Google Health may import as "google-sources") never comes back in a loop.
 */
class GoogleHealthClient(
    private val accessToken: suspend () -> String,
    private val http: OkHttpClient = defaultHttpClient(),
) {

    suspend fun identity(): JsonObject = get(url("users/me/identity"))

    /** Steps summed into fixed windows; returns `rollupDataPoints[]` with `steps.countSum`. */
    suspend fun rollUpSteps(start: Instant, end: Instant, windowSeconds: Long): JsonObject {
        val body = buildJsonObject {
            putJsonObject("range") {
                put("startTime", start.toString())
                put("endTime", end.toString())
            }
            put("windowSize", "${windowSeconds}s")
            put("dataSourceFamily", WEARABLES)
        }
        return post(url("users/me/dataTypes/steps/dataPoints:rollUp"), body)
    }

    suspend fun listHeartRate(start: Instant, end: Instant, pageToken: String?): JsonObject {
        // list has no startTime/endTime parameters; time ranges go through an AIP-160 filter.
        val field = "heart_rate.sample_time.physical_time"
        val url = url("users/me/dataTypes/heart-rate/dataPoints").newBuilder()
            .addQueryParameter("filter", "$field >= \"$start\" AND $field < \"$end\"")
            .addQueryParameter("dataSourceFamily", WEARABLES)
            .addQueryParameter("pageSize", "10000")
            .apply { pageToken?.let { addQueryParameter("pageToken", it) } }
            .build()
        return get(url)
    }

    /** Sleep sessions ending on or after [fromDate] (local civil time), deduplicated across syncs by the API. */
    suspend fun reconcileSleep(fromDate: LocalDate, pageToken: String?): JsonObject {
        val url = url("users/me/dataTypes/sleep/dataPoints:reconcile").newBuilder()
            .addQueryParameter("dataSourceFamily", WEARABLES)
            .addQueryParameter("filter", "sleep.interval.civil_end_time >= \"${fromDate}T00:00:00\"")
            .addQueryParameter("pageSize", "25")
            .apply { pageToken?.let { addQueryParameter("pageToken", it) } }
            .build()
        return get(url)
    }

    private suspend fun get(url: HttpUrl): JsonObject = execute(Request.Builder().url(url).get())

    private suspend fun post(url: HttpUrl, body: JsonObject): JsonObject =
        execute(Request.Builder().url(url).post(body.toString().toRequestBody(JSON_MEDIA_TYPE)))

    private suspend fun execute(builder: Request.Builder): JsonObject {
        val request = builder
            .header("Authorization", "Bearer ${accessToken()}")
            .header("Accept", "application/json")
            .build()
        return withContext(Dispatchers.IO) {
            http.newCall(request).execute().use { response ->
                val text = response.body.string()
                if (BuildConfig.DEBUG) Log.d(TAG, "${request.method} ${request.url} -> ${response.code}\n${text.take(4000)}")
                if (!response.isSuccessful) throw GoogleHealthException(response.code, text, request.url.encodedPath)
                if (text.isBlank()) JsonObject(emptyMap()) else JSON.parseToJsonElement(text).jsonObject
            }
        }
    }

    private fun url(path: String): HttpUrl = "$BASE_URL/$path".toHttpUrl()

    companion object {
        private const val TAG = "GoogleHealthClient"
        private const val BASE_URL = "https://health.googleapis.com/v4"
        private const val WEARABLES = "users/me/dataSourceFamilies/google-wearables"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        private val JSON = Json { ignoreUnknownKeys = true }

        private fun defaultHttpClient() = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}
