package com.mutinymedia.android.data

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment

class AndroidServices(private val context: Context) {
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
