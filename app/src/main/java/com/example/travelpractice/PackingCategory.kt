package com.example.travelpractice.models

data class PackingCategory(
    val id: String = "",          // Firestore doc id (we'll set after fetch)
    val title: String = "",       // e.g., "Toiletries", "Clothes"
    val uid: String = "",         // owner user id
    val listId: String = "default", // trip/list scope; keep "default" for now
    val expanded: Boolean = true  // UI preference; ok to store or keep local
)


