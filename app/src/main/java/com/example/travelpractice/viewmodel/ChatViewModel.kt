package com.example.travelpractice.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.travelpractice.ai.TravelAgent
import com.example.travelpractice.model.Message
import com.example.travelpractice.model.MessageSender
import com.example.travelpractice.model.MessageType
import kotlinx.coroutines.launch
import java.util.Date

class ChatViewModel(private val geminiApiKey: String) : ViewModel() {

    private val travelAgent = TravelAgent(geminiApiKey)

    private val _messages = MutableLiveData<List<Message>>(emptyList())
    val messages: LiveData<List<Message>> = _messages

    private val _isTyping = MutableLiveData<Boolean>(false)
    val isTyping: LiveData<Boolean> = _isTyping

    init {
        addMessage(Message(
            id = System.currentTimeMillis(),
            text = "Hi! 👋 I'm your AI Travel Assistant!\n\n" +
                    "✈️ Trip planning & destinations\n" +
                    "🌤️ Real-time weather\n" +
                    "💱 Currency conversion\n" +
                    "🏨 Hotels, flights & activities\n\n" +
                    "What is your question?",
            sender = MessageSender.BOT,
            timestamp = Date(),
            type = MessageType.GREETING
        ))
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        val userMessage = Message(
            id = System.currentTimeMillis(),
            text = text.trim(),
            sender = MessageSender.USER,
            timestamp = Date()
        )
        addMessage(userMessage)

        _isTyping.value = true
        viewModelScope.launch {
            try {
                val botResponse = travelAgent.processMessage(text)
                addMessage(botResponse)
            } catch (e: Exception) {
                addMessage(Message(
                    id = System.currentTimeMillis(),
                    text = "I apologize, but I'm having trouble processing your request. " +
                            "Error: ${e.message}\n\n" +
                            "Please check:\n" +
                            "1. Internet connection\n" +
                            "2. Gemini API key is correct",
                    sender = MessageSender.BOT,
                    timestamp = Date(),
                    type = MessageType.GENERAL
                ))
            } finally {
                _isTyping.value = false
            }
        }
    }

    private fun addMessage(message: Message) {
        val currentMessages = _messages.value ?: emptyList()
        _messages.value = currentMessages + message
    }

    fun clearChat() {
        _messages.value = listOf(_messages.value?.first() ?: return)
        travelAgent.clearHistory()
    }
}