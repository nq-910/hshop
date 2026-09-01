package me.erista.hshop.thor.updater

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class AppUpdateInfo(
    val hasUpdate: Boolean,
    val latestVersion: String,
    val currentVersion: String,
    val releaseName: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val htmlUrl: String
)

class AppUpdater(
    private val context: Context,
    private val repoOwner: String = "yggdrasil-seed",
    private val repoName: String = "hshop"
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun checkForUpdates(): AppUpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val currentVersionName = try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.1-beta"
            } catch (e: Exception) {
                "0.0.1-beta"
            }

            val request = Request.Builder()
                .url("https://api.github.com/repos/$repoOwner/$repoName/releases/latest")
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "hShop-Thor-Updater")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                // If no "latest" official release yet (e.g. only pre-releases exist), check all releases
                val allReleasesRequest = Request.Builder()
                    .url("https://api.github.com/repos/$repoOwner/$repoName/releases")
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("User-Agent", "hShop-Thor-Updater")
                    .build()
                val allResponse = client.newCall(allReleasesRequest).execute()
                if (!allResponse.isSuccessful) return@withContext null
                val body = allResponse.body?.string() ?: return@withContext null
                val releasesArray = json.parseToJsonElement(body).jsonArray
                if (releasesArray.isEmpty()) return@withContext null
                return@withContext parseReleaseObject(releasesArray[0].jsonObject, currentVersionName)
            }

            val body = response.body?.string() ?: return@withContext null
            val releaseObj = json.parseToJsonElement(body).jsonObject
            return@withContext parseReleaseObject(releaseObj, currentVersionName)
        } catch (e: Exception) {
            Log.w("AppUpdater", "Update check failed: ${e.message}")
            null
        }
    }

    private fun parseReleaseObject(releaseObj: JsonObject, currentVersionName: String): AppUpdateInfo {
        val rawTag = releaseObj["tag_name"]?.jsonPrimitive?.content.orEmpty()
        val latestVersion = rawTag.removePrefix("v").trim()
        val releaseName = releaseObj["name"]?.jsonPrimitive?.content ?: rawTag
        val releaseNotes = releaseObj["body"]?.jsonPrimitive?.content.orEmpty()
        val htmlUrl = releaseObj["html_url"]?.jsonPrimitive?.content.orEmpty()

        var downloadUrl = htmlUrl
        val assets = releaseObj["assets"]?.jsonArray
        if (assets != null) {
            for (asset in assets) {
                val assetObj = asset.jsonObject
                val name = assetObj["name"]?.jsonPrimitive?.content.orEmpty()
                if (name.endsWith(".apk", ignoreCase = true)) {
                    downloadUrl = assetObj["browser_download_url"]?.jsonPrimitive?.content ?: downloadUrl
                    break
                }
            }
        }

        val hasUpdate = isNewerVersion(latestVersion, currentVersionName)

        return AppUpdateInfo(
            hasUpdate = hasUpdate,
            latestVersion = latestVersion,
            currentVersion = currentVersionName,
            releaseName = releaseName,
            releaseNotes = releaseNotes,
            downloadUrl = downloadUrl,
            htmlUrl = htmlUrl
        )
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        val cleanLatest = latest.removePrefix("v").split("-")[0]
        val cleanCurrent = current.removePrefix("v").split("-")[0]

        val latestParts = cleanLatest.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = cleanCurrent.split(".").mapNotNull { it.toIntOrNull() }

        val length = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until length) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }
}
