package com.foundit.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.foundit.app.MainActivity
import com.foundit.app.R
import kotlin.random.Random

enum class NotificationType {
    General,
    FinderConfirmation,
    ItemReturned;

    companion object {
        fun from(rawType: String?): NotificationType {
            return when (rawType?.trim()?.lowercase()?.replace("-", "_")) {
                "finder_confirmation",
                "found_confirmation",
                "confirmation",
                "confirmation_received",
                "item_confirmed" -> FinderConfirmation

                "item_returned",
                "returned",
                "return_confirmed" -> ItemReturned

                else -> General
            }
        }
    }
}

object NotificationHelper {
    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val generalChannel = NotificationChannel(
            context.getString(R.string.default_notification_channel_id),
            context.getString(R.string.default_notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.default_notification_channel_description)
            enableVibration(true)
        }

        val itemStatusChannel = NotificationChannel(
            context.getString(R.string.item_status_notification_channel_id),
            context.getString(R.string.item_status_notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.item_status_notification_channel_description)
            enableVibration(true)
        }

        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannels(listOf(generalChannel, itemStatusChannel))
    }

    fun defaultTitle(context: Context, type: NotificationType): String {
        return when (type) {
            NotificationType.FinderConfirmation -> context.getString(R.string.notification_confirmation_title)
            NotificationType.ItemReturned -> context.getString(R.string.notification_returned_title)
            NotificationType.General -> context.getString(R.string.app_name)
        }
    }

    fun defaultBody(context: Context, type: NotificationType, itemName: String?): String {
        val cleanItemName = itemName?.trim()?.takeIf { it.isNotEmpty() }
        return when (type) {
            NotificationType.FinderConfirmation -> cleanItemName?.let {
                context.getString(R.string.notification_confirmation_body_with_item, it)
            } ?: context.getString(R.string.notification_confirmation_body)

            NotificationType.ItemReturned -> cleanItemName?.let {
                context.getString(R.string.notification_returned_body_with_item, it)
            } ?: context.getString(R.string.notification_returned_body)

            NotificationType.General -> context.getString(R.string.default_notification_channel_description)
        }
    }

    fun showRemoteNotification(
        context: Context,
        title: String,
        body: String,
        deepLink: String? = null,
        type: NotificationType = NotificationType.General
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            deepLink?.let { putExtra(MainActivity.EXTRA_DEEP_LINK, it) }
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(
            context,
            channelIdFor(context, type)
        )
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(context.getColor(R.color.foundit_primary))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        runCatching {
            NotificationManagerCompat.from(context)
                .notify(Random.nextInt(1000, 999999), notification)
        }
    }

    private fun channelIdFor(context: Context, type: NotificationType): String {
        return when (type) {
            NotificationType.FinderConfirmation,
            NotificationType.ItemReturned -> context.getString(R.string.item_status_notification_channel_id)

            NotificationType.General -> context.getString(R.string.default_notification_channel_id)
        }
    }
}
