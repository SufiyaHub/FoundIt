package com.foundit.app.web

import android.net.Uri

interface WebViewCallbacks {
    fun onPageStarted()
    fun onPageFinished()
    fun onProgress(progress: Int)
    fun onRecoverableError()
    fun onBlockedNavigation(uri: Uri)
    fun onAuthNavigation(uri: Uri)
    fun onExternalNavigation(uri: Uri)
}
