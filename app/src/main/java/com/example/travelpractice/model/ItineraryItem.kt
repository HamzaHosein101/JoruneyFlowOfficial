package com.example.travelpractice.model
import java.io.Serializable

data class ItineraryItem(
    var id: String = "",
    var tripId: String = "",
    var title: String = "",
    var description: String = "",
    var date: Long = 0L,
    var startTime: String = "",
    var endTime: String = "",
    var duration: Int = 60,
    var location: String = "",
    var cost: Double = 0.0,
    var isCompleted: Boolean = false,
    var notes: String = "",
    var type: String = "General",
    var createdAt: Long = System.currentTimeMillis()
) : Serializable