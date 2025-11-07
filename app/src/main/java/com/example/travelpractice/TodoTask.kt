package com.example.travelpractice.model

data class TodoTask(
    val id: String = "",
    val name: String = "",
    val checked: Boolean = false,
    val uid: String = "",
    val listId: String = "default"
)


