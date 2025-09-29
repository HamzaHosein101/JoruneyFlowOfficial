package com.example.travelpractice.models

data class TodoTask(
    val id: String = "",          // Firestore doc id (we'll set after fetch)
    val name: String = "",        // e.g., "Renew passport"
    val checked: Boolean = false, // done or not
    val uid: String = "",
    val listId: String = "default"
)


