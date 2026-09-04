package com.foundit.app.notifications

import com.foundit.app.firebase.FirebaseManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class FoundItMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        FirebaseManager.initialize(applicationContext)
        FirebaseManager.updateMessagingToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        FirebaseManager.initialize(applicationContext)

        val type = NotificationType.from(
            message.data["notification_type"] ?: message.data["type"] ?: message.data["event"]
        )
        val itemName = message.data["item_name"]
            ?: message.data["thing_name"]
            ?: message.data["item_title"]
            ?: message.data["title_text"]
        val title = message.notification?.title
            ?: message.data["title"]
            ?: NotificationHelper.defaultTitle(applicationContext, type)
        val body = message.notification?.body
            ?: message.data["body"]
            ?: NotificationHelper.defaultBody(applicationContext, type, itemName)
        val deepLink = message.data["deep_link"] ?: message.data["url"]

        NotificationHelper.showRemoteNotification(
            context = applicationContext,
            title = title,
            body = body,
            deepLink = deepLink,
            type = type
        )
    }
}
