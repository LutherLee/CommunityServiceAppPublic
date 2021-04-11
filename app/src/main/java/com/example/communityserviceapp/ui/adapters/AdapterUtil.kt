package com.example.communityserviceapp.ui.adapters

import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.communityserviceapp.ui.MyApplication.Companion.appContext
import com.example.communityserviceapp.worker.NotificationWorker
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

fun Long.getDurationBreakdown(): String {
    val currentDeviceTime = Date().time
    var difference = currentDeviceTime - this
    val stringBuilder = StringBuilder(10)
    if (difference <= 0) {
        stringBuilder.append("Now")
    } else {
        val days = TimeUnit.MILLISECONDS.toDays(difference)
        if (days > 0) {
            if (days <= 7) {
                stringBuilder.append(days.toString() + "d ago")
            } else {
                stringBuilder.append(SimpleDateFormat("dd/MM/yyyy", Locale.ROOT).format(this))
            }
        } else {
            difference -= TimeUnit.DAYS.toMillis(days)
            val hours = TimeUnit.MILLISECONDS.toHours(difference)
            if (hours > 0) {
                stringBuilder.append(hours.toString() + "h ago")
            } else {
                difference -= TimeUnit.HOURS.toMillis(hours)
                val minutes = TimeUnit.MILLISECONDS.toMinutes(difference)
                if (minutes > 0) {
                    stringBuilder.append(minutes.toString() + "m ago")
                } else {
                    stringBuilder.append("Now")
                }
            }
        }
    }
    return stringBuilder.toString()
}

fun Long.getScheduledDurationBreakdown(): String {
    var millis = this
    require(millis >= 0) { "Duration must be greater than zero!" }
    val days = TimeUnit.MILLISECONDS.toDays(millis)
    millis -= TimeUnit.DAYS.toMillis(days)
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    millis -= TimeUnit.HOURS.toMillis(hours)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
    val sb = StringBuilder(64)
    if (days > 0) {
        sb.append("$days Days ")
    }
    if (hours > 0) {
        sb.append("$hours Hours ")
    }
    if (minutes > 0) {
        sb.append("$minutes Minutes ")
    }
    return sb.toString()
}

fun calculateDelay(reminderDeadlineDate: Long, dateToShowReminder: Long): Long {
    val currentDeviceTime = Date().time
    var delay: Long = 0

    // If current device time is before the deadline date and the date to show reminder
    if (reminderDeadlineDate > currentDeviceTime && dateToShowReminder > currentDeviceTime) {
        // current device time is before the date to show the reminder
        // so calculate the difference to know when to show notification
        delay = dateToShowReminder - currentDeviceTime
    }
    return delay
}

fun scheduleNotification(title: String, message: String, dateToShowReminder: Long, delay: Long) {
    val data = workDataOf(
        "NOTIFICATION_TITLE" to title,
        "NOTIFICATION_MESSAGE" to message,
        "DATE_TO_SHOW_NOTIFICATION" to dateToShowReminder.toString()
    )
    val notificationWork = OneTimeWorkRequestBuilder<NotificationWorker>()
        .setInitialDelay(delay, TimeUnit.MILLISECONDS)
        .setInputData(data)
        .addTag("scheduledNotification")
        .build()
    WorkManager.getInstance(appContext).enqueue(notificationWork)
}
