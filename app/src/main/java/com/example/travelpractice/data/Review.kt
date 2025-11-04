package com.example.travelpractice.data
import com.google.firebase.Timestamp

data class Review(
    val id: String = "",
    val userId: String = "",
    val username: String = "",
    val locationName: String = "",
    val tripDate: Timestamp = Timestamp.now(),
    val rating: Int = 0,               // 1..5
    val comment: String = "",
    val photoUrl: String? = null,
    val createdAt: Timestamp = Timestamp.now(),
    val updatedAt: Timestamp = Timestamp.now(),
    val likeCount: Int = 0,
    val reportCount: Int = 0
)