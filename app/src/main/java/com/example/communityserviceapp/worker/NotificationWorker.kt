package com.example.communityserviceapp.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.communityserviceapp.R
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.text.SimpleDateFormat
import java.util.*

/**
 * A worker class that is used to:
 * 1) Receive scheduled notification data
 * 2) Determine if the received notification data should be shown,
 * 3) Generate the notification and show it to user (if condition is met)
 */
@HiltWorker
class NotificationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val CHANNEL_ID = "1002"
    }

    init { createNotificationChannel() }

    override suspend fun doWork(): Result {
        val simpleDateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT)
        val scheduleNotificationDate = inputData.getString("DATE_TO_SHOW_NOTIFICATION")!!.toLong()
        val dateToShowNotification = simpleDateFormat.format(Date(scheduleNotificationDate))
        val currentDeviceTime = simpleDateFormat.format(Date())

        // Check if schedule notification date is equal to current device time in the format "dd/MM/yyyy"
        // This prevent showing notification in the scenario where user change device time
        return if (dateToShowNotification == currentDeviceTime) {
            val notificationTitle = inputData.getString("NOTIFICATION_TITLE")
            val notificationMessage = inputData.getString("NOTIFICATION_MESSAGE")
            buildNotification(notificationTitle, notificationMessage)
            Result.success()
        } else {
            Result.failure()
        }
    }

    /** Create the NotificationChannel, but only on API 26+ because
     the NotificationChannel class is new and not in the support library **/
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name: CharSequence = applicationContext.getString(R.string.channel_name)
            val description = applicationContext.getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance)
            channel.description = description
            val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(notificationTitle: String?, notificationMessage: String?) {
        if (!notificationTitle.isNullOrBlank() && !notificationMessage.isNullOrBlank()) {
            val notificationBuilder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(R.mipmap.app_logo)
                .setAutoCancel(true)
                .setChannelId(CHANNEL_ID)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setContentTitle(notificationTitle)
                .setStyle(NotificationCompat.BigTextStyle().bigText(notificationMessage))
                .setContentText(notificationMessage)
            showNotification(notificationBuilder.build())
        }
    }

    private fun showNotification(notification: Notification) {
        val notificationManager = NotificationManagerCompat.from(applicationContext)
        notificationManager.notify(Integer.valueOf(CHANNEL_ID), notification)
    }
}
