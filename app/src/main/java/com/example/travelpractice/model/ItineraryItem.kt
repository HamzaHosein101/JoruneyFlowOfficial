package com.example.travelpractice.model
import java.io.Serializable

data class ItineraryItem(
    var id: String = "",
    var tripId: String = "",
    var title: String = "",
    var description: String = "",
    var date: Long = 0L, // timestamp for the day
    var startTime: String = "", // e.g., "09:00"
    var endTime: String = "", // e.g., "11:00"
    var duration: Int = 60, // duration in minutes
    var location: String = "",
    var cost: Double = 0.0, // cost in dollars
    var isCompleted: Boolean = false,
    var notes: String = "",
    var createdAt: Long = System.currentTimeMillis()
) : Serializable