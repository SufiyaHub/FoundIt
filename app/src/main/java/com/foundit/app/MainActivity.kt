package com.foundit.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.foundit.app.firebase.FirebaseManager
import com.foundit.app.network.NetworkMonitor
import com.foundit.app.web.DownloadHandler
import com.foundit.app.web.FileUploadManager
import com.foundit.app.web.FoundItWebChromeClient
import com.foundit.app.web.WebAppConfig
import com.foundit.app.web.WebViewCallbacks
import com.foundit.app.web.WebViewConfigurator
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar

class MainActivity : AppCompatActivity(), WebViewCallbacks {
    private lateinit var root: View
    private lateinit var webView: WebView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var loadingOverlay: View
    private lateinit var offlineOverlay: View
    private lateinit var splashOverlay: View
    private lateinit var splashLogo: ImageView
    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var fullscreenContainer: FrameLayout
    private lateinit var fileUploadManager: FileUploadManager
    private lateinit var networkMonitor: NetworkMonitor

    private var lastBackPressedAt = 0L
    private var pendingStartupUrl: String? = null
    private var initialNavigationHandled = false
    private val loadTimeoutHandler = Handler(Looper.getMainLooper())
    private val loadTimeoutRunnable = Runnable {
        if (loadingOverlay.visibility == View.VISIBLE && webView.progress < 100) {
            swipeRefresh.isRefreshing = false
            loadingOverlay.visibility = View.GONE
            if (!networkMonitor.isOnline()) {
                showOffline()
            } else {
                showMessage("Taking too long. Check your connection and retry.")
            }
        }
    }

    private val fileChooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            fileUploadManager.handleResult(result.resultCode, result.data)
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            FirebaseManager.logEvent(
                if (granted) "notification_permission_granted" else "notification_permission_denied"
            )
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        configureWindow()
        configureRuntimeServices()
        configureWebView()
        configureInteractions()
        animateSplash()

        FirebaseManager.logScreen("main")
        pendingStartupUrl = resolveStartupUrl(intent)
    }

    override fun onStart() {
        super.onStart()
        networkMonitor.start()
    }

    override fun onStop() {
        networkMonitor.stop()
        android.webkit.CookieManager.getInstance().flush()
        super.onStop()
    }

    override fun onDestroy() {
        loadTimeoutHandler.removeCallbacks(loadTimeoutRunnable)
        fileUploadManager.cancelPending()
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        resolveStartupUrl(intent)?.let { loadTrustedUrl(it) }
    }

    private fun bindViews() {
        root = findViewById(R.id.root)
        webView = findViewById(R.id.webView)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        loadingOverlay = findViewById(R.id.loadingOverlay)
        offlineOverlay = findViewById(R.id.offlineOverlay)
        splashOverlay = findViewById(R.id.splashOverlay)
        splashLogo = findViewById(R.id.splashLogo)
        progressBar = findViewById(R.id.progressBar)
        fullscreenContainer = findViewById(R.id.fullscreenContainer)
    }

    private fun configureWindow() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = ContextCompat.getColor(this, R.color.foundit_black)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.foundit_black)
        WindowInsetsControllerCompat(window, root).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(bottom = bars.bottom)
            insets
        }
    }

    private fun configureRuntimeServices() {
        fileUploadManager = FileUploadManager(this, fileChooserLauncher)
        networkMonitor = NetworkMonitor(this) { isOnline ->
            runOnUiThread { handleConnectionChange(isOnline) }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val state = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            if (state != PermissionChecker.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        WebView.setWebContentsDebuggingEnabled(BuildConfig.WEBVIEW_DEBUGGING)
        WebViewConfigurator.configure(this, webView, this)
        clearOldWebViewState()

        webView.webChromeClient = FoundItWebChromeClient(
            activity = this,
            fileUploadManager = fileUploadManager,
            fullscreenContainer = fullscreenContainer,
            onProgressChanged = ::onProgress
        )

        DownloadHandler(
            context = this,
            onMessage = ::showMessage,
            onError = {
                FirebaseManager.recordNonFatal(it)
                showMessage("Download could not be started")
            }
        ).attach(webView)
    }

    private fun configureInteractions() {
        swipeRefresh.isEnabled = false
        swipeRefresh.setColorSchemeResources(R.color.foundit_primary, R.color.foundit_accent)
        swipeRefresh.setOnRefreshListener {
            root.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            reloadCurrent()
        }

        findViewById<MaterialButton>(R.id.retryButton).setOnClickListener {
            root.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            reloadCurrent()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackPress()
            }
        })
    }

    private fun animateSplash() {
        splashLogo.alpha = 0f
        splashLogo.scaleX = 0.9f
        splashLogo.scaleY = 0.9f
        splashLogo.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(650L)
            .withEndAction {
                splashOverlay.postDelayed({
                    splashOverlay.animate()
                        .alpha(0f)
                        .setDuration(350L)
                        .withEndAction { splashOverlay.visibility = View.GONE }
                        .start()
                }, 1000L)
            }
            .start()
    }

    private fun handleConnectionChange(isOnline: Boolean) {
        if (isOnline) {
            val wasOfflineVisible = offlineOverlay.visibility == View.VISIBLE
            offlineOverlay.visibility = View.GONE
            val target = pendingStartupUrl ?: webView.url ?: WebAppConfig.HOME_URL
            pendingStartupUrl = null
            if (!initialNavigationHandled || webView.url.isNullOrBlank() || wasOfflineVisible) {
                initialNavigationHandled = true
                loadTrustedUrl(target)
            }
        } else if (webView.url.isNullOrBlank()) {
            showOffline()
        } else {
            showMessage("Offline mode")
        }
    }

    private fun reloadCurrent() {
        if (!networkMonitor.isOnline()) {
            if (webView.url.isNullOrBlank()) showOffline() else showMessage("Offline mode")
            swipeRefresh.isRefreshing = false
            return
        }

        offlineOverlay.visibility = View.GONE
        webView.settings.cacheMode = WebSettings.LOAD_NO_CACHE
        val target = webView.url ?: pendingStartupUrl ?: WebAppConfig.HOME_URL
        loadTrustedUrl(target)
    }

    private fun loadTrustedUrl(url: String) {
        val uri = Uri.parse(url)
        if (!WebAppConfig.isTrusted(uri)) {
            openExternal(uri)
            return
        }

        loadingOverlay.visibility = View.VISIBLE
        progressBar.progress = 0
        loadTimeoutHandler.removeCallbacks(loadTimeoutRunnable)
        loadTimeoutHandler.postDelayed(loadTimeoutRunnable, PAGE_LOAD_TIMEOUT_MS)
        webView.settings.cacheMode = WebSettings.LOAD_NO_CACHE
        webView.loadUrl(url, NO_CACHE_HEADERS)
    }

    private fun resolveStartupUrl(intent: Intent?): String? {
        val explicit = intent?.getStringExtra(EXTRA_DEEP_LINK)
        val dataUrl = intent?.dataString
        return explicit ?: dataUrl ?: WebAppConfig.HOME_URL
    }

    private fun showOffline() {
        swipeRefresh.isRefreshing = false
        loadingOverlay.visibility = View.GONE
        offlineOverlay.visibility = View.VISIBLE
    }

    private fun showMessage(message: String) {
        Snackbar.make(root, message, Snackbar.LENGTH_SHORT)
            .setBackgroundTint(ContextCompat.getColor(this, R.color.foundit_surface_high))
            .setTextColor(ContextCompat.getColor(this, R.color.foundit_text))
            .show()
    }

    private fun handleBackPress() {
        if (fullscreenContainer.visibility == View.VISIBLE) {
            (webView.webChromeClient as? FoundItWebChromeClient)?.onHideCustomView()
            return
        }

        if (webView.canGoBack()) {
            webView.goBack()
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastBackPressedAt < EXIT_CONFIRMATION_WINDOW_MS) {
            finish()
        } else {
            lastBackPressedAt = now
            root.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            showMessage("Press back again to exit")
        }
    }

    override fun onPageStarted() {
        swipeRefresh.isRefreshing = false
        loadingOverlay.visibility = View.VISIBLE
        progressBar.progress = 8
    }

    override fun onPageFinished() {
        loadTimeoutHandler.removeCallbacks(loadTimeoutRunnable)
        swipeRefresh.isRefreshing = false
        progressBar.progress = 100
        loadingOverlay.animate()
            .alpha(0f)
            .setDuration(220L)
            .withEndAction {
                loadingOverlay.visibility = View.GONE
                loadingOverlay.alpha = 1f
            }
            .start()
        offlineOverlay.visibility = View.GONE
    }

    override fun onProgress(progress: Int) {
        progressBar.setProgressCompat(progress.coerceIn(0, 100), true)
    }

    override fun onRecoverableError() {
        loadTimeoutHandler.removeCallbacks(loadTimeoutRunnable)
        swipeRefresh.isRefreshing = false
        if (!networkMonitor.isOnline()) {
            showOffline()
        } else {
            loadingOverlay.visibility = View.GONE
            showMessage("Something went wrong. Please retry.")
        }
    }

    override fun onBlockedNavigation(uri: Uri) {
        val params = Bundle().apply {
            putString("uri_scheme", uri.scheme)
            putString("uri_host", uri.host)
        }
        FirebaseManager.logEvent("blocked_navigation", params)
        showMessage("This link was blocked")
    }

    override fun onAuthNavigation(uri: Uri) {
        if (WebAppConfig.USE_CUSTOM_TABS_FOR_GOOGLE_AUTH) {
            showMessage("Opening secure sign-in")
            openExternal(uri)
        } else {
            webView.loadUrl(uri.toString(), NO_CACHE_HEADERS)
        }
    }

    override fun onExternalNavigation(uri: Uri) {
        openExternal(uri)
    }

    private fun openExternal(uri: Uri) {
        runCatching {
            when (uri.scheme) {
                "intent" -> startActivity(Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME))
                "https" -> CustomTabsIntent.Builder()
                    .setShowTitle(true)
                    .build()
                    .launchUrl(this, uri)
                else -> startActivity(Intent(Intent.ACTION_VIEW, uri))
            }
        }.onFailure {
            if (it !is ActivityNotFoundException) FirebaseManager.recordNonFatal(it)
            showMessage("Cannot open this link")
        }
    }

    private fun clearOldWebViewState() {
        webView.clearCache(true)
        webView.clearHistory()
        webView.clearFormData()
    }

    companion object {
        const val EXTRA_DEEP_LINK = "extra_deep_link"
        private const val EXIT_CONFIRMATION_WINDOW_MS = 2000L
        private const val PAGE_LOAD_TIMEOUT_MS = 20000L
        private val NO_CACHE_HEADERS = mapOf(
            "Cache-Control" to "no-cache, no-store, must-revalidate",
            "Pragma" to "no-cache"
        )
    }
}
