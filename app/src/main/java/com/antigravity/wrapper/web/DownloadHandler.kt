package com.antigravity.wrapper.web

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.URLUtil
import androidx.core.content.ContextCompat
import com.antigravity.wrapper.R

/**
 * Wires WebView download requests into Android's [DownloadManager], forwarding session cookies
 * and the sanitized User-Agent so authenticated downloads from Antigravity succeed.
 */
class DownloadHandler(
    private val context: Context,
    private val onMessage: (String) -> Unit
) : DownloadListener {

    override fun onDownloadStart(
        url: String?,
        userAgent: String?,
        contentDisposition: String?,
        mimetype: String?,
        contentLength: Long
    ) {
        if (url.isNullOrBlank()) {
            onMessage(context.getString(R.string.download_failed))
            return
        }

        val uri = try {
            Uri.parse(url)
        } catch (_: Exception) {
            onMessage(context.getString(R.string.download_failed))
            return
        }

        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            onMessage(context.getString(R.string.download_unsupported_scheme))
            return
        }

        val rawFileName = URLUtil.guessFileName(url, contentDisposition, mimetype)
        val safeFileName = rawFileName
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .ifBlank { "antigravity_download" }

        try {
            val request = DownloadManager.Request(uri).apply {
                setTitle(safeFileName)
                setDescription(context.getString(R.string.app_name))
                if (!mimetype.isNullOrBlank()) {
                    setMimeType(mimetype)
                }
                setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )

                val cookies = CookieManager.getInstance().getCookie(url)
                if (!cookies.isNullOrBlank()) {
                    addRequestHeader("Cookie", cookies)
                }
                if (!userAgent.isNullOrBlank()) {
                    addRequestHeader("User-Agent", userAgent)
                }

                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)

                if (canWriteToPublicDownloads()) {
                    setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_DOWNLOADS,
                        safeFileName
                    )
                } else {
                    setDestinationInExternalFilesDir(
                        context,
                        Environment.DIRECTORY_DOWNLOADS,
                        safeFileName
                    )
                }
            }

            val downloadManager =
                context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            if (downloadManager == null) {
                onMessage(context.getString(R.string.download_failed))
                return
            }

            downloadManager.enqueue(request)
            onMessage(context.getString(R.string.download_started, safeFileName))
        } catch (_: Exception) {
            onMessage(context.getString(R.string.download_failed))
        }
    }

    private fun canWriteToPublicDownloads(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return true
        }
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }
}
