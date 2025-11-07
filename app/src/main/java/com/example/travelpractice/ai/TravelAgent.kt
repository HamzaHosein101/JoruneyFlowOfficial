package com.example.travelpractice.ai

import com.example.travelpractice.model.Message
import com.example.travelpractice.model.MessageSender
import com.example.travelpractice.model.MessageType
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date

class TravelAgent(private val apiKey: String) {

    private val model = GenerativeModel(
        modelName = "gemini-2.5-flash",
        apiKey = apiKey,

    )

    private val conversationHistory = mutableListOf<com.google.ai.client.generativeai.type.Content>()
    private val weatherTool = WeatherTool()
    private val currencyTool = CurrencyTool()

    suspend fun processMessage(userMessage: String): Message {
        return withContext(Dispatchers.IO) {
            try {
                val toolResponse = checkForToolUsage(userMessage)
                if (toolResponse != null) {
                    return@withContext Message(
                        id = System.currentTimeMillis(),
                        text = toolResponse,
                        sender = MessageSender.BOT,
                        timestamp = Date(),
                        type = detectMessageType(userMessage)
                    )
                }

                conversationHistory.add(content {
                    role = "user"
                    text(userMessage)
                })

                val chat = model.startChat(conversationHistory)
                val response = chat.sendMessage(userMessage)
                val responseText = response.text ?: "I apologize, I couldn't process that. Could you rephrase?"

                conversationHistory.add(content {
                    role = "model"
                    text(responseText)
                })

                Message(
                    id = System.currentTimeMillis(),
                    text = responseText,
                    sender = MessageSender.BOT,
                    timestamp = Date(),
                    type = detectMessageType(userMessage)
                )
            } catch (e: Exception) {
                Message(
                    id = System.currentTimeMillis(),
                    text = "I'm having trouble connecting right now. Error: ${e.message}. Please check your internet connection and try again.",
                    sender = MessageSender.BOT,
                    timestamp = Date(),
                    type = MessageType.GENERAL
                )
            }
        }
    }

    private suspend fun checkForToolUsage(message: String): String? {
        val lowerMessage = message.lowercase()

        if (lowerMessage.contains("weather") || lowerMessage.contains("temperature")) {
            val city = extractCityName(message)
            if (city != null) {
                return weatherTool.getWeather(city)
            }
        }

        if (lowerMessage.contains("convert") || lowerMessage.contains("currency") || lowerMessage.contains("exchange")) {
            return currencyTool.convertCurrency(message)
        }

        return null
    }

    private fun extractCityName(message: String): String? {
        val words = message.split(" ")
        val weatherIndex = words.indexOfFirst { it.lowercase().contains("weather") }
        if (weatherIndex != -1 && weatherIndex + 1 < words.size) {
            return words.getOrNull(weatherIndex + 1)?.replace("?", "")?.replace(",", "")
        }

        val inIndex = words.indexOfFirst { it.lowercase() == "in" }
        if (inIndex != -1 && inIndex + 1 < words.size) {
            return words.getOrNull(inIndex + 1)?.replace("?", "")?.replace(",", "")
        }

        return null
    }

    private fun detectMessageType(message: String): MessageType {
        val lowerMessage = message.lowercase()
        return when {
            lowerMessage.contains("weather") -> MessageType.WEATHER
            lowerMessage.contains("currency") || lowerMessage.contains("convert") -> MessageType.CURRENCY
            lowerMessage.contains("destination") -> MessageType.DESTINATION
            lowerMessage.contains("budget") -> MessageType.BUDGET
            lowerMessage.contains("itinerary") -> MessageType.ITINERARY
            lowerMessage.contains("hotel") || lowerMessage.contains("accommodation") -> MessageType.ACCOMMODATION
            lowerMessage.contains("flight") -> MessageType.FLIGHTS
            lowerMessage.contains("food") || lowerMessage.contains("restaurant") -> MessageType.DINING
            else -> MessageType.GENERAL
        }
    }

    fun clearHistory() {
        conversationHistory.clear()
    }
}