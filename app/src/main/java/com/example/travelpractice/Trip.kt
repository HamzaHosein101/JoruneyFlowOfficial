package com.example.travelpractice.model
import java.io.Serializable

data class Trip(
    var id: String = "",
    var userId: String = "",
    var title: String = "",
    var destination: String = "",
    var budget: Double = 0.0,
    val remaining: Double = 0.0,
    val spent: Double = 0.0,
    var startDate: Long = 0L,
    var endDate: Long = 0L,
    var createdAt: Long = System.currentTimeMillis(),
    var lat: Double? = null,
    var lng: Double? = null
) : Serializable


