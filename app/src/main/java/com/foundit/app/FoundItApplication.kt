package com.foundit.app

import android.app.Application
import com.foundit.app.firebase.FirebaseManager
import com.foundit.app.notifications.NotificationHelper

class FoundItApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
        FirebaseManager.initialize(this)
        FirebaseManager.requestMessagingToken()
    }
}
