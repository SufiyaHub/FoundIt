package com.foundit.app.web

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebView
import com.foundit.app.R

class DownloadHandler(
    private val context: Context,
    private val onMessage: (String) -> Unit,
    private val onError: (Throwable) -> Unit
) {
    fun attach(webView: WebView) {
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            val uri = Uri.parse(url)
            if (uri.scheme != "https") return@setDownloadListener

            runCatching {
                val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
                val request = DownloadManager.Request(uri).apply {
                    setMimeType(mimeType)
                    addRequestHeader("User-Agent", userAgent)
                    CookieManager.getInstance().getCookie(url)?.let {
                        addRequestHeader("Cookie", it)
                    }
                    setTitle(fileName)
                    setDescription(context.getString(R.string.app_name))
                    setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                    )
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                    setAllowedOverMetered(true)
                    setAllowedOverRoaming(false)
                }

                val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                manager.enqueue(request)
                onMessage(context.getString(R.string.download_started))
            }.onFailure(onError)
        }
    }
}
