package com.example.travelpractice.model

data class PackingItem(
    val id: String = "",          // Firestore doc id (we'll set after fetch)
    val name: String = "",        // e.g., "Toothbrush"
    val checked: Boolean = false, // packed or not
    val uid: String = "",         // owner user id
    val listId: String = "default",
    val categoryId: String = ""   // parent category doc id
)


