package com.example.travelpractice.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.travelpractice.handlers.ChatActionHandler

class ChatViewModelFactory(
    private val geminiApiKey: String,
    private val context: Context
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            val chatActionHandler = ChatActionHandler(context)
            return ChatViewModel(
                geminiApiKey = geminiApiKey,
                chatActionHandler = chatActionHandler
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}