package com.foundit.app.web

import android.net.Uri

object WebAppConfig {
    const val HOME_URL = "https://find-it-copy-e35baad5.base44.app"
    private const val HOME_HOST = "find-it-copy-e35baad5.base44.app"
    private const val SUPABASE_HOST = "byrkzjnnlledsuvikfwd.supabase.co"
    const val USE_CUSTOM_TABS_FOR_GOOGLE_AUTH = false

    fun isTrusted(uri: Uri): Boolean {
        return uri.scheme == "https" && uri.host.equals(HOME_HOST, ignoreCase = true)
    }

    fun isAuthFlow(uri: Uri): Boolean {
        val host = uri.host?.lowercase() ?: return false
        return uri.scheme == "https" &&
            (host == "base44.app" ||
                host.endsWith(".base44.app") ||
                host == SUPABASE_HOST)
    }

    fun isExternalAppScheme(uri: Uri): Boolean {
        return uri.scheme in setOf("mailto", "tel", "sms", "geo", "intent", "market")
    }
}
