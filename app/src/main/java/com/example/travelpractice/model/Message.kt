package com.example.travelpractice.model

import java.util.Date

data class Message(
    val id: Long,
    val text: String,
    val sender: MessageSender,
    val timestamp: Date = Date(),
    val type: MessageType = MessageType.GENERAL
)

enum class MessageSender {
    USER,
    BOT
}

enum class MessageType {
    GREETING,
    DESTINATION,
    BUDGET,
    ITINERARY,
    ACCOMMODATION,
    FLIGHTS,
    DINING,
    DOCUMENTS,
    WEATHER,
    PACKING,
    CURRENCY,
    SPECIFIC_DESTINATION,
    THANKS,
    GENERAL
}