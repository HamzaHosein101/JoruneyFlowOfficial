package com.example.travelpractice

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.example.travelpractice.model.ItineraryItem
import com.example.travelpractice.model.Trip
import java.util.*

class ItineraryNotificationManager(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val notificationManager = NotificationManagerCompat.from(context)

    companion object {
        private const val REMINDER_TIME_MINUTES = 30 // Remind 30 minutes before
        private const val REMINDER_TIME_HOURS = 1 // Or 1 hour before for early morning items
    }

    fun scheduleNotification(itineraryItem: ItineraryItem, trip: Trip) {
        val reminderTime = calculateReminderTime(itineraryItem)
        if (reminderTime > System.currentTimeMillis()) {
            val intent = createNotificationIntent(itineraryItem, trip)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                itineraryItem.id.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        reminderTime,
                        pendingIntent
                    )
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        reminderTime,
                        pendingIntent
                    )
                }
            } catch (e: SecurityException) {
                // Handle case where exact alarm permission is not granted
                // Fall back to inexact alarm
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    reminderTime,
                    pendingIntent
                )
            }
        }
    }

    fun cancelNotification(itineraryItem: ItineraryItem) {
        val intent = createNotificationIntent(itineraryItem, Trip())
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            itineraryItem.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    fun scheduleAllNotifications(trip: Trip, itineraryItems: List<ItineraryItem>) {
        // Cancel all existing notifications for this trip
        cancelAllNotificationsForTrip(trip.id)
        
        // Schedule new notifications
        itineraryItems.forEach { item ->
            if (!item.isCompleted && item.date > 0) {
                scheduleNotification(item, trip)
            }
        }
    }

    fun cancelAllNotificationsForTrip(tripId: String) {
        // This is a simplified approach - in a real app, you'd want to track
        // which notifications are scheduled for each trip
        // For now, we'll rely on the individual item cancellation
    }

    private fun calculateReminderTime(itineraryItem: ItineraryItem): Long {
        val itemDateTime = getItemDateTime(itineraryItem)
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = itemDateTime
        
        // Determine reminder time based on the time of day
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val reminderMinutes = if (hour < 8) {
            // For early morning items (before 8 AM), remind 1 hour before
            REMINDER_TIME_HOURS * 60
        } else {
            // For regular items, remind 15 minutes before
            REMINDER_TIME_MINUTES
        }
        
        return itemDateTime - (reminderMinutes * 60 * 1000)
    }

    private fun getItemDateTime(itineraryItem: ItineraryItem): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = itineraryItem.date
        
        // Parse start time (format: "09:00 AM" or "21:30")
        val timeParts = itineraryItem.startTime.split(" ")
        val timeStr = if (timeParts.size == 2) {
            // Format: "09:00 AM"
            val time = timeParts[0]
            val ampm = timeParts[1]
            val (hour, minute) = time.split(":").map { it.toInt() }
            val hour24 = if (ampm.uppercase() == "PM" && hour != 12) hour + 12
                       else if (ampm.uppercase() == "AM" && hour == 12) 0
                       else hour
            "$hour24:$minute"
        } else {
            // Format: "21:30" (24-hour format)
            itineraryItem.startTime
        }
        
        val (hour, minute) = timeStr.split(":").map { it.toInt() }
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        
        return calendar.timeInMillis
    }

    private fun createNotificationIntent(itineraryItem: ItineraryItem, trip: Trip): Intent {
        return Intent(context, ItineraryNotificationReceiver::class.java).apply {
            putExtra(ItineraryNotificationReceiver.EXTRA_ITINERARY_ITEM, itineraryItem)
            putExtra(ItineraryNotificationReceiver.EXTRA_TRIP, trip)
        }
    }

    fun areNotificationsEnabled(): Boolean {
        return notificationManager.areNotificationsEnabled()
    }
}
