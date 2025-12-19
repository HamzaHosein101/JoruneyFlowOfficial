package com.example.travelpractice.data

import com.google.firebase.Timestamp

data class UserProfile(
    val uid: String = "",
    val email: String = "",
    val displayName: String? = null,
    val photoUrl: String? = null,
    val createdAt: Timestamp? = null,
    val lastLoginAt: Timestamp? = null,
    val providers: List<String> = emptyList(),
    val emailVerified: Boolean = false,
    val role: String? = null
)


