package com.example.travelpractice.model

import com.example.travelpractice.handlers.ActionOption
import java.util.Date

data class Message(
    val id: Long,
    val text: String,
    val sender: MessageSender,
    val timestamp: Date,
    val type: MessageType = MessageType.GENERAL,
    val actionOptions: List<ActionOption>? = null
) {
    /**
     * Check if message has action buttons
     */
    fun hasActions(): Boolean = !actionOptions.isNullOrEmpty()
}

enum class MessageSender {
    USER,
    BOT
}

enum class MessageType {
    GENERAL,
    GREETING,
    BUDGET,
    ITINERARY,
    PACKING,
    WEATHER,        // Weather-related messages
    CURRENCY,       // Currency conversion messages
    DESTINATION,    // Destination info messages
    ACCOMMODATION,  // Hotel/lodging messages
    FLIGHTS,        // Flight-related messages
    DINING          // Restaurant/food messages
}