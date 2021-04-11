package com.example.communityserviceapp.fcm

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.communityserviceapp.R
import com.example.communityserviceapp.injection.CustomEntryPoint
import com.example.communityserviceapp.ui.adapters.calculateDelay
import com.example.communityserviceapp.ui.adapters.getScheduledDurationBreakdown
import com.example.communityserviceapp.ui.adapters.scheduleNotification
import com.example.communityserviceapp.util.Constants
import com.example.communityserviceapp.util.currentFirebaseUser
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.EntryPointAccessors
import java.util.concurrent.TimeUnit

/**
 * A class that is used to receive Firebase Cloud Messaging (FCM) messages
 *
 * The main purpose of this class is:
 * 1) Receive FCM messages
 * 2) Determine FCM message notification type (reminder or announcement)
 * 3) Determine whether to show the notification (check if user disable notification)
 * 4) Show the notification immediately or schedule the notification (based on notification type and due date)
 *
 * Note: No need to override onNewToken() since this project uses topic subscription
 * See: https://firebase.google.com/docs/cloud-messaging/android/client#sample-register
 */
@SuppressLint("MissingFirebaseInstanceTokenRefresh")
class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    /** Create the NotificationChannel, but only on API 26+ because
     the NotificationChannel class is new and not in the support library **/
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name: CharSequence = getString(R.string.channel_name)
            val description = getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(
                applicationContext.getString(R.string.default_notification_channel_id),
                name,
                importance
            )
            channel.description = description
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        if (remoteMessage.data.isNotEmpty()) {
            val notificationData = remoteMessage.data
            if (notificationData["NOTIFICATION_TYPE"] == "announcement") {
                if (!checkIfUserDisableNotification(Constants.NOTIFICATION_TYPE_ANNOUNCEMENT)) {
                    // if user did not disable announcement notification
                    val title = notificationData["ANNOUNCEMENT_TITLE"]
                    val message = notificationData["ANNOUNCEMENT_MESSAGE"]
                    showNotification(buildNotification(title, message))
                }
            } else if (notificationData["NOTIFICATION_TYPE"] == "reminder" &&
                !checkIfUserDisableNotification(Constants.NOTIFICATION_TYPE_REMINDER)
            ) {
                // if user did not disable reminder notification
                val title = notificationData["REMINDER_TITLE"]
                val message = notificationData["REMINDER_MESSAGE"]
                val dateToComplete = notificationData["REMINDER_DEADLINE_DATE"]!!.toLong()
                checkIfShouldScheduleNotification(title!!, message!!, dateToComplete)
            }
        }
    }

    private fun checkIfShouldScheduleNotification(
        title: String,
        message: String,
        dateToComplete: Long
    ) {
        // get the date to show the reminder (7 days before deadline date)
        val dateToShowReminder = dateToComplete - TimeUnit.DAYS.toMillis(7)
        // check if should show notification or not given that we should show notification 7 days before reminder deadline date
        val delay = calculateDelay(dateToComplete, dateToShowReminder)
        if (delay == 0L) {
            showNotification(buildNotification(title, message))
        } else {
            scheduleNotification(title, message, dateToShowReminder, delay)
            displaySuccessfullyScheduledNotification(title, delay.getScheduledDurationBreakdown())
        }
    }

    private fun displaySuccessfullyScheduledNotification(title: String, timeToShow: String) {
        val notification = buildNotification(
            "Scheduled Reminder",
            "Task \"" + title + "\" is scheduled to be shown in " + timeToShow + "from now"
        )
        showNotification(notification)
    }

    private fun buildNotification(
        notificationTitle: String?,
        notificationMessage: String?
    ): Notification {
        val notificationBuilder = NotificationCompat.Builder(
            applicationContext,
            applicationContext.getString(R.string.default_notification_channel_id)
        ).setSmallIcon(R.mipmap.app_logo)
            .setAutoCancel(true)
            .setChannelId(applicationContext.getString(R.string.default_notification_channel_id))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentTitle(notificationTitle)
            .setContentText(notificationMessage)
        return notificationBuilder.build()
    }

    private fun showNotification(notification: Notification) {
        val notificationManager = NotificationManagerCompat.from(this)
        notificationManager.notify(
            Integer.valueOf(applicationContext.getString(R.string.default_notification_channel_id)),
            notification
        )
    }

    private fun checkIfUserDisableNotification(notificationType: Int): Boolean {
        // Get sharedPreferences dependency first using hilt using custom defined entry point
        // since hilt don't support dependencies injection for FirebaseMessagingService class
        // See: https://dagger.dev/hilt/entry-points.html
        val hiltEntryPoint =
            EntryPointAccessors.fromApplication(applicationContext, CustomEntryPoint::class.java)
        val sharedPreferences = hiltEntryPoint.sharedPreferences()!!

        // Check if user (for already login and haven't login scenario) disable the notification based on notification type
        if (notificationType == Constants.NOTIFICATION_TYPE_REMINDER) {
            // User must login to receive reminder
            currentFirebaseUser?.let {
                return sharedPreferences.getBoolean(
                    currentFirebaseUser!!.uid + getString(R.string.user_disable_reminder_notifications),
                    false
                )
            }
        } else if (notificationType == Constants.NOTIFICATION_TYPE_ANNOUNCEMENT) {
            return if (currentFirebaseUser == null) {
                sharedPreferences.getBoolean(
                    getString(R.string.user_disable_announcement_notifications),
                    false
                )
            } else {
                sharedPreferences.getBoolean(
                    currentFirebaseUser!!.uid + getString(R.string.user_disable_announcement_notifications),
                    false
                )
            }
        }
        return false
    }
}
