package com.example.travelpractice

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.travelpractice.model.ItineraryItem
import com.example.travelpractice.model.Trip

class ItineraryNotificationReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "itinerary_reminders"
        const val NOTIFICATION_ID = 1001
        const val EXTRA_ITINERARY_ITEM = "extra_itinerary_item"
        const val EXTRA_TRIP = "extra_trip"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val itineraryItem = intent.getSerializableExtra(EXTRA_ITINERARY_ITEM) as? ItineraryItem
        val trip = intent.getSerializableExtra(EXTRA_TRIP) as? Trip

        if (itineraryItem != null && trip != null) {
            createNotificationChannel(context)
            showNotification(context, itineraryItem, trip)
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Itinerary Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for upcoming itinerary items"
                enableVibration(true)
                setShowBadge(true)
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showNotification(context: Context, itineraryItem: ItineraryItem, trip: Trip) {
        val intent = Intent(context, ItineraryActivity::class.java).apply {
            putExtra("extra_trip", trip)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            itineraryItem.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_star_24)
            .setContentTitle("Upcoming: ${itineraryItem.title}")
            .setContentText("${itineraryItem.startTime} - ${itineraryItem.location}")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("${itineraryItem.description}\n\nLocation: ${itineraryItem.location}\nTime: ${itineraryItem.startTime}"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(itineraryItem.id.hashCode(), notification)
    }
}

