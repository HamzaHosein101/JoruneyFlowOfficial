package com.example.travelpractice.utils

import android.util.Log

object IntentDetector {


    enum class Intent {
        EXPENSE_TRACKER,
        ITINERARY,
        CHECKLIST,
        FLIGHT_SEARCH,
        HOTEL_SEARCH,
        GENERAL_CHAT,
        UNKNOWN
    }


    data class DetectedIntent(
        val intent: Intent,
        val confidence: Float,
        val extractedData: Map<String, String> = emptyMap()
    )


    private val expenseKeywords = listOf(
        "expense", "expenses", "spending", "spent", "cost", "costs", "money",
        "budget", "paid", "payment", "price", "bill", "receipt", "transaction",
        "purchase", "bought", "buy"
    )

    private val itineraryKeywords = listOf(
        "itinerary", "schedule", "plan", "plans", "activity", "activities",
        "trip plan", "day plan", "agenda", "visit", "visiting", "going to",
        "reservation", "booking", "appointment", "event"
    )

    private val checklistKeywords = listOf(
        "checklist", "packing", "pack", "to do", "todo", "task", "tasks",
        "bring", "remember", "don't forget", "need to", "list", "items",
        "baggage", "luggage", "suitcase", "bag"
    )


    private val flightKeywords = listOf(
        "flight", "flights", "fly", "flying", "airplane", "plane", "airline", "airlines",
        "ticket", "tickets", "book flight", "aviation", "departure", "arrival",
        "jfk", "lax", "airport", "airports", "ewr", "lga", "lhr", "cdg", "sfo", "ord",
        "from", "to", "round trip", "roundtrip", "one way", "direct", "nonstop", "non-stop"
    )


    private val hotelKeywords = listOf(
        "hotel", "hotels", "accommodation", "stay", "lodging", "room", "rooms",
        "booking", "resort", "inn", "motel", "hostel", "book hotel", "find hotel",
        "place to stay", "where to stay", "accommodations", "check in", "check out"
    )

    private val navigationKeywords = listOf(
        "open", "show", "view", "display", "see", "check", "look at",
        "go to", "navigate", "take me to"
    )


    fun detectIntent(message: String): DetectedIntent {
        val lowerMessage = message.lowercase()

        // Calculate scores for ALL intents
        val expenseScore = calculateScore(lowerMessage, expenseKeywords)
        val itineraryScore = calculateScore(lowerMessage, itineraryKeywords)
        val checklistScore = calculateScore(lowerMessage, checklistKeywords)
        val flightScore = calculateScore(lowerMessage, flightKeywords)
        val hotelScore = calculateScore(lowerMessage, hotelKeywords)

        // Debug logging
        Log.d("IntentDetector", "Message: $message")
        Log.d("IntentDetector", "Scores - Expense: $expenseScore, Itinerary: $itineraryScore, " +
                "Checklist: $checklistScore, Flight: $flightScore, Hotel: $hotelScore")

        // Find the highest scoring intent
        val maxScore = maxOf(expenseScore, itineraryScore, checklistScore, flightScore, hotelScore)
        val threshold = 0.10f  // ✅ LOWERED from 0.15f for better detection

        return when {
            maxScore < threshold -> {
                Log.d("IntentDetector", "Score below threshold ($maxScore < $threshold), returning GENERAL_CHAT")
                DetectedIntent(Intent.GENERAL_CHAT, maxScore)
            }
            hotelScore == maxScore -> {
                Log.d("IntentDetector", "Detected HOTEL_SEARCH with score $hotelScore")
                DetectedIntent(Intent.HOTEL_SEARCH, hotelScore)
            }
            flightScore == maxScore -> {
                Log.d("IntentDetector", "Detected FLIGHT_SEARCH with score $flightScore")
                DetectedIntent(Intent.FLIGHT_SEARCH, flightScore)
            }
            expenseScore == maxScore -> {
                Log.d("IntentDetector", "Detected EXPENSE_TRACKER with score $expenseScore")
                val extractedData = extractExpenseData(lowerMessage)
                DetectedIntent(Intent.EXPENSE_TRACKER, expenseScore, extractedData)
            }
            itineraryScore == maxScore -> {
                Log.d("IntentDetector", "Detected ITINERARY with score $itineraryScore")
                val extractedData = extractItineraryData(lowerMessage)
                DetectedIntent(Intent.ITINERARY, itineraryScore, extractedData)
            }
            checklistScore == maxScore -> {
                Log.d("IntentDetector", "Detected CHECKLIST with score $checklistScore")
                val extractedData = extractChecklistData(lowerMessage)
                DetectedIntent(Intent.CHECKLIST, checklistScore, extractedData)
            }
            else -> {
                Log.d("IntentDetector", "Fallback to UNKNOWN")
                DetectedIntent(Intent.UNKNOWN, 0f)
            }
        }
    }


    private fun calculateScore(message: String, keywords: List<String>): Float {
        var matchedKeywords = 0
        var totalWeight = 0f

        for (keyword in keywords) {
            if (message.contains(keyword)) {
                matchedKeywords++
                // Give more weight to longer, more specific keywords
                totalWeight += when {
                    keyword.length >= 7 -> 2.0f
                    keyword.length >= 5 -> 1.5f
                    else -> 1.0f
                }
            }
        }


        return if (matchedKeywords > 0) {
            // Base score from weight
            val baseScore = totalWeight / keywords.size.toFloat()
            // Boost for multiple matches (up to 1.5x)
            val boost = (matchedKeywords.toFloat() / 3).coerceAtMost(1.5f)
            // Final score capped at 1.0
            (baseScore * boost).coerceIn(0f, 1f)
        } else {
            0f
        }
    }


    private fun extractExpenseData(message: String): Map<String, String> {
        val data = mutableMapOf<String, String>()


        val amountRegex = Regex("""\$?(\d+\.?\d*)\s*(dollars?|usd|eur|gbp)?""")
        amountRegex.find(message)?.let {
            data["amount"] = it.groupValues[1]
        }

        // Extract category
        val categories = mapOf(
            "food" to listOf("food", "meal", "restaurant", "dinner", "lunch", "breakfast", "eat", "dining"),
            "hotel" to listOf("hotel", "accommodation", "lodging", "stay", "airbnb"),
            "transport" to listOf("transport", "taxi", "uber", "flight", "train", "bus", "car", "gas", "fuel"),
            "activities" to listOf("activity", "activities", "ticket", "museum", "tour", "attraction"),
            "shopping" to listOf("shopping", "shop", "bought", "purchase", "store")
        )

        for ((category, keywords) in categories) {
            if (keywords.any { message.contains(it) }) {
                data["category"] = category.replaceFirstChar { it.uppercase() }
                break
            }
        }

        return data
    }


    private fun extractItineraryData(message: String): Map<String, String> {
        val data = mutableMapOf<String, String>()


        val timeKeywords = mapOf(
            "today" to "today",
            "tomorrow" to "tomorrow",
            "tonight" to "tonight",
            "morning" to "morning",
            "afternoon" to "afternoon",
            "evening" to "evening",
            "this week" to "this week",
            "next week" to "next week"
        )

        for ((keyword, value) in timeKeywords) {
            if (message.contains(keyword)) {
                data["when"] = value
                break
            }
        }

        // Extract activity type
        val activityTypes = mapOf(
            "sightseeing" to listOf("sightseeing", "visit", "see", "tour"),
            "dining" to listOf("dinner", "lunch", "breakfast", "restaurant", "eat"),
            "accommodation" to listOf("hotel", "check in", "check out", "stay")
        )

        for ((type, keywords) in activityTypes) {
            if (keywords.any { message.contains(it) }) {
                data["type"] = type
                break
            }
        }

        return data
    }


    private fun extractChecklistData(message: String): Map<String, String> {
        val data = mutableMapOf<String, String>()


        val categories = mapOf(
            "clothing" to listOf("clothes", "clothing", "shirt", "pants", "dress", "shoes"),
            "toiletries" to listOf("toiletries", "toothbrush", "shampoo", "soap", "hygiene"),
            "documents" to listOf("passport", "documents", "visa", "ticket", "id", "license"),
            "electronics" to listOf("phone", "charger", "laptop", "camera", "electronics", "adapter")
        )

        for ((category, keywords) in categories) {
            if (keywords.any { message.contains(it) }) {
                data["category"] = category.replaceFirstChar { it.uppercase() }
                break
            }
        }

        return data
    }


    fun isNavigationRequest(message: String): Boolean {
        val lowerMessage = message.lowercase()
        return navigationKeywords.any { lowerMessage.contains(it) }
    }
}
