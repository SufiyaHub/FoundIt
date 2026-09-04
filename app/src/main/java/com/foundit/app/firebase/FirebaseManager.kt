package com.foundit.app.firebase

import android.content.Context
import android.os.Bundle
import com.foundit.app.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.messaging.FirebaseMessaging

object FirebaseManager {
    private var initialized = false

    val analytics: FirebaseAnalytics by lazy { FirebaseAnalytics.getInstance(appContext) }
    val crashlytics: FirebaseCrashlytics by lazy { FirebaseCrashlytics.getInstance() }

    private lateinit var appContext: Context

    fun initialize(context: Context) {
        if (initialized) return
        appContext = context.applicationContext
        FirebaseApp.initializeApp(appContext)
        crashlytics.setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)
        initialized = true
    }

    fun requestMessagingToken() {
        runCatching {
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { updateMessagingToken(it) }
                .addOnFailureListener { crashlytics.recordException(it) }
        }.onFailure { crashlytics.recordException(it) }
    }

    fun updateMessagingToken(token: String) {
        if (token.isBlank()) return
        logEvent(
            "messaging_token_refreshed",
            Bundle().apply {
                putString("platform", "android")
                putString("app_version", BuildConfig.VERSION_NAME)
            }
        )
    }

    fun bindSupabaseUser(userId: String?) {
        if (userId.isNullOrBlank()) {
            analytics.setUserId(null)
            crashlytics.setUserId("")
        } else {
            analytics.setUserId(userId)
            crashlytics.setUserId(userId)
        }
    }

    fun logScreen(screenName: String) {
        runCatching {
            analytics.logEvent(
                FirebaseAnalytics.Event.SCREEN_VIEW,
                Bundle().apply {
                    putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
                    putString(FirebaseAnalytics.Param.SCREEN_CLASS, screenName)
                }
            )
        }
    }

    fun logEvent(name: String, params: Bundle = Bundle()) {
        runCatching { analytics.logEvent(name, params) }
    }

    fun recordNonFatal(throwable: Throwable) {
        runCatching { crashlytics.recordException(throwable) }
    }

}
