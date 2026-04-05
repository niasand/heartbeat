package com.heartratemonitor.data.sync

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * HTTP client for syncing data to Cloudflare Workers
 */
@Singleton
class SyncApiClient @Inject constructor() {

    private val gson = Gson()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Post sync data to Cloudflare Workers
     */
    suspend fun syncData(request: SyncRequest): SyncResponse = withContext(Dispatchers.IO) {
        val json = gson.toJson(request)
        val body = json.toRequestBody(jsonMediaType)

        val httpRequest = Request.Builder()
            .url(ApiConfig.SYNC_API_URL)
            .post(body)
            .build()

        try {
            val response = httpClient.newCall(httpRequest).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                gson.fromJson(responseBody, SyncResponse::class.java)
            } else {
                SyncResponse(
                    success = false,
                    message = "HTTP ${response.code}: ${responseBody ?: "empty response"}"
                )
            }
        } catch (e: Exception) {
            SyncResponse(
                success = false,
                message = e.message ?: "Network error"
            )
        }
    }

    /**
     * Fetch all data from Cloudflare Workers for restore
     */
    suspend fun fetchData(): FetchResponse = withContext(Dispatchers.IO) {
        val httpRequest = Request.Builder()
            .url(ApiConfig.SYNC_API_URL)
            .get()
            .build()

        try {
            val response = httpClient.newCall(httpRequest).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                gson.fromJson(responseBody, FetchResponse::class.java)
            } else {
                FetchResponse(
                    success = false,
                    message = "HTTP ${response.code}: ${responseBody ?: "empty response"}"
                )
            }
        } catch (e: Exception) {
            FetchResponse(
                success = false,
                message = e.message ?: "Network error"
            )
        }
    }

    /**
     * Delete timer sessions by timestamps from Cloudflare Workers
     */
    suspend fun deleteData(request: DeleteRequest): DeleteResponse = withContext(Dispatchers.IO) {
        val json = gson.toJson(request)
        val body = json.toRequestBody(jsonMediaType)

        val httpRequest = Request.Builder()
            .url(ApiConfig.SYNC_API_URL)
            .delete(body)
            .build()

        try {
            val response = httpClient.newCall(httpRequest).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                gson.fromJson(responseBody, DeleteResponse::class.java)
            } else {
                DeleteResponse(
                    success = false,
                    message = "HTTP ${response.code}: ${responseBody ?: "empty response"}"
                )
            }
        } catch (e: Exception) {
            DeleteResponse(
                success = false,
                message = e.message ?: "Network error"
            )
        }
    }
}
