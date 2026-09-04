package com.foundit.app.web

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.webkit.CookieManager
import android.webkit.ServiceWorkerController
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

object WebViewConfigurator {
    fun configure(context: Context, webView: WebView, callbacks: WebViewCallbacks) {
        webView.setLayerType(WebView.LAYER_TYPE_HARDWARE, null)
        webView.isLongClickable = false
        webView.setOnLongClickListener { true }
        webView.isHapticFeedbackEnabled = false
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false
        webView.overScrollMode = WebView.OVER_SCROLL_NEVER
        webView.setBackgroundColor(android.graphics.Color.TRANSPARENT)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadsImagesAutomatically = true
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_NO_CACHE
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            allowFileAccess = false
            allowContentAccess = true
            setSupportMultipleWindows(false)
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            textZoom = 100
            useWideViewPort = true
            loadWithOverviewMode = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                disabledActionModeMenuItems = WebSettings.MENU_ITEM_NONE
            }
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
            flush()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            ServiceWorkerController.getInstance().serviceWorkerWebSettings.cacheMode =
                WebSettings.LOAD_NO_CACHE
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            WebView.startSafeBrowsing(context, null)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                if (!request.isForMainFrame) return false
                val uri = request.url

                if (WebAppConfig.isTrusted(uri)) return false

                if (WebAppConfig.isAuthFlow(uri)) {
                    callbacks.onAuthNavigation(uri)
                } else if (WebAppConfig.isExternalAppScheme(uri)) {
                    callbacks.onExternalNavigation(uri)
                } else {
                    callbacks.onBlockedNavigation(uri)
                }
                return true
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                callbacks.onPageStarted()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                CookieManager.getInstance().flush()
                callbacks.onPageFinished()
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true) callbacks.onRecoverableError()
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?
            ) {
                if (request?.isForMainFrame == true && (errorResponse?.statusCode ?: 0) >= 500) {
                    callbacks.onRecoverableError()
                }
            }

            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler,
                error: android.net.http.SslError?
            ) {
                handler.cancel()
                error?.url?.let { callbacks.onBlockedNavigation(Uri.parse(it)) }
            }
        }
    }
}
