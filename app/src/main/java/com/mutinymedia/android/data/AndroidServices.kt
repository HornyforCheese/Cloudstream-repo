package com.mutinymedia.android.data

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import com.mutinymedia.android.domain.InstalledGame

class AndroidServices(private val context: Context) {
    // Older games may expose the legacy flag without setting an app category.
    @Suppress("DEPRECATION")
    fun installedGames(): List<InstalledGame> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = context.packageManager.queryIntentActivities(intent, 0)
        return apps.filter { ri ->
            ri.activityInfo?.applicationInfo?.let { app ->
                app.category == ApplicationInfo.CATEGORY_GAME || (app.flags and ApplicationInfo.FLAG_IS_GAME) != 0
            } == true
        }.map { InstalledGame(it.loadLabel(context.packageManager).toString(), it.activityInfo.packageName) }
            .distinctBy { it.packageName }.sortedBy { it.name }
    }
    fun launchGame(game: InstalledGame): Boolean {
        val launch = context.packageManager.getLaunchIntentForPackage(game.packageName) ?: return false
        return runCatching { context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }.getOrDefault(false)
    }
    fun downloadFile(url: String, name: String): Long {
        require(url.startsWith("https://", true)) { "Use a direct HTTPS download URL" }
        val safeName = name.trim().replace(Regex("[^a-zA-Z0-9._-]"), "_").take(90).takeUnless { it.isBlank() || it == "." || it == ".." } ?: "download"
        val req = DownloadManager.Request(Uri.parse(Network.validateHttps(url)))
            .setTitle(safeName)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, safeName)
        return (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
    }
}
