package com.eqv.visualizer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import com.eqv.visualizer.R
import com.eqv.visualizer.ServiceMode
import com.eqv.visualizer.ui.MainActivity

object Notifications {
    const val CHANNEL_SERVICE = "eqv.service"
    const val CHANNEL_ALERTS = "eqv.alerts"
    const val ID_SERVICE = 1
    const val ID_RESUME = 2

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SERVICE, context.getString(R.string.channel_service), NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, context.getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    /** The FGS notification doubles as the control surface: on/off + preset switch. */
    fun service(context: Context, mode: ServiceMode, presetName: String, detail: String): Notification {
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = servicePi(context, VisualizerService.ACTION_STOP, 1)
        val next = servicePi(context, VisualizerService.ACTION_NEXT_PRESET, 2)
        val title = when (mode) {
            ServiceMode.ACTIVE -> context.getString(R.string.notif_active, presetName)
            else -> context.getString(R.string.notif_armed, presetName)
        }
        return Notification.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_stat_eqv)
            .setContentTitle(title)
            .setContentText(detail)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(
                Notification.Action.Builder(Icon.createWithResource(context, R.drawable.ic_stat_eqv), context.getString(R.string.action_next_preset), next).build(),
            )
            .addAction(
                Notification.Action.Builder(Icon.createWithResource(context, R.drawable.ic_stat_eqv), context.getString(R.string.action_turn_off), stop).build(),
            )
            .build()
    }

    private fun servicePi(context: Context, action: String, code: Int): PendingIntent =
        PendingIntent.getService(
            context, code, Intent(context, VisualizerService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
