package com.foundit.app.web

import android.view.View
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity

class FoundItWebChromeClient(
    private val activity: AppCompatActivity,
    private val fileUploadManager: FileUploadManager,
    private val fullscreenContainer: FrameLayout,
    private val onProgressChanged: (Int) -> Unit
) : WebChromeClient() {
    private var fullscreenView: View? = null
    private var fullscreenCallback: CustomViewCallback? = null

    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        onProgressChanged(newProgress)
    }

    override fun onShowFileChooser(
        webView: WebView?,
        filePathCallback: ValueCallback<Array<android.net.Uri>>,
        fileChooserParams: FileChooserParams
    ): Boolean {
        return fileUploadManager.openFileChooser(filePathCallback, fileChooserParams)
    }

    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
        if (fullscreenView != null) {
            callback.onCustomViewHidden()
            return
        }

        fullscreenView = view
        fullscreenCallback = callback
        fullscreenContainer.visibility = View.VISIBLE
        fullscreenContainer.addView(
            view,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        activity.window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }

    override fun onHideCustomView() {
        fullscreenView?.let { fullscreenContainer.removeView(it) }
        fullscreenView = null
        fullscreenContainer.visibility = View.GONE
        fullscreenCallback?.onCustomViewHidden()
        fullscreenCallback = null
        activity.window.decorView.systemUiVisibility = 0
    }

    override fun onPermissionRequest(request: PermissionRequest) {
        val trusted = WebAppConfig.isTrusted(request.origin)
        val grantedResources = request.resources.filter {
            it == PermissionRequest.RESOURCE_VIDEO_CAPTURE ||
                it == PermissionRequest.RESOURCE_AUDIO_CAPTURE
        }.toTypedArray()

        activity.runOnUiThread {
            if (trusted && grantedResources.isNotEmpty()) {
                request.grant(grantedResources)
            } else {
                request.deny()
            }
        }
    }
}
