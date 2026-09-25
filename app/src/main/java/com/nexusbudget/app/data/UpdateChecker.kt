package com.nexusbudget.app.data

import com.nexusbudget.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

data class AvailableUpdate(val version: String, val downloadUrl: String)

/**
 * Looks for a newer release on the project's GitHub Releases page. Only runs when the user taps
 * "Check for updates"; nothing about the user or their data is sent.
 */
class UpdateChecker(private val http: OkHttpClient) {

    /** The newest release if it's newer than this build, or null when up to date. Throws [IOException] when offline. */
    suspend fun check(): AvailableUpdate? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/${BuildConfig.UPDATE_REPOSITORY}/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .build()
        http.newCall(request).execute().use { response ->
            // No releases published yet.
            if (response.code == 404) return@withContext null
            if (!response.isSuccessful) throw IOException("GitHub returned ${response.code}")
            val release = JSONObject(response.body?.string().orEmpty())
            val version = release.getString("tag_name").removePrefix("v")
            val assets = release.optJSONArray("assets")
            val apk = (0 until (assets?.length() ?: 0))
                .map { assets!!.getJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk") }
                ?.optString("browser_download_url")
            if (isNewer(version, BuildConfig.VERSION_NAME)) AvailableUpdate(version, apk ?: release.getString("html_url")) else null
        }
    }

    companion object {
        /** Compares dotted version numbers, ignoring suffixes like "-debug". */
        fun isNewer(candidate: String, current: String): Boolean {
            fun parts(version: String) = version.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
            val a = parts(candidate)
            val b = parts(current)
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }
    }
}
