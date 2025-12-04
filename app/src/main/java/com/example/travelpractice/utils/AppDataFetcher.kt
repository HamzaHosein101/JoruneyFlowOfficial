package com.example.travelpractice.utils

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class AppDataFetcher {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    /**
     * Get list of user's trips
     */
    suspend fun getUserTrips(): List<Trip> {
        return try {
            val userId = auth.currentUser?.uid ?: return emptyList()

            val tripsSnapshot = firestore.collection("trips")
                .whereEqualTo("userId", userId)
                .get()
                .await()

            tripsSnapshot.documents.mapNotNull { doc ->
                Trip(
                    id = doc.id,
                    name = doc.getString("destination") ?: doc.getString("name") ?: "Unnamed Trip",
                    destination = doc.getString("destination") ?: "",
                    startDate = doc.getLong("startDate") ?: 0L,
                    endDate = doc.getLong("endDate") ?: 0L
                )
            }
        } catch (e: Exception) {
            Log.e("AppDataFetcher", "Error fetching trips", e)
            emptyList()
        }
    }

    /**
     * Get formatted trip list message
     */
    suspend fun getTripsListMessage(): String {
        val trips = getUserTrips()

        return if (trips.isEmpty()) {
            "You don't have any trips yet. Create a trip first to start tracking!"
        } else if (trips.size == 1) {
            // If only one trip, use it automatically
            "📍 Current Trip: ${trips[0].name}"
        } else {
            buildString {
                append("🌍 Your Trips:\n\n")
                trips.forEachIndexed { index, trip ->
                    append("${index + 1}. ${trip.name}\n")
                }
                append("\nWhich trip would you like to see?")
            }
        }
    }

    /**
     * Fetch expense summary for a specific trip
     */
    suspend fun getExpenseSummary(tripId: String): String {
        return try {
            val userId = auth.currentUser?.uid ?: return "You're not logged in."

            val expenses = firestore.collection("expenses")
                .whereEqualTo("tripId", tripId)
                .whereEqualTo("userId", userId)
                .get()
                .await()

            if (expenses.isEmpty) {
                "You don't have any expenses for this trip yet."
            } else {
                val totalAmount = expenses.documents.sumOf {
                    it.getDouble("amount") ?: 0.0
                }
                val expenseCount = expenses.size()

                val byCategory = expenses.documents.groupBy {
                    it.getString("category") ?: "Other"
                }

                buildString {
                    append("💰 Expense Summary:\n\n")
                    append("📊 Total: $${String.format("%.2f", totalAmount)}\n")
                    append("📝 Count: $expenseCount\n\n")

                    append("By Category:\n")
                    byCategory.forEach { (category, items) ->
                        val catTotal = items.sumOf { it.getDouble("amount") ?: 0.0 }
                        append("• $category: $${String.format("%.2f", catTotal)}\n")
                    }

                    append("\nRecent:\n")
                    expenses.documents
                        .sortedByDescending { it.getLong("timestamp") ?: 0L }
                        .take(5)
                        .forEach { doc ->
                            val desc = doc.getString("description") ?: "Unknown"
                            val amt = doc.getDouble("amount") ?: 0.0
                            append("• $desc: $${String.format("%.2f", amt)}\n")
                        }
                }
            }
        } catch (e: Exception) {
            Log.e("AppDataFetcher", "Error fetching expenses", e)
            "I couldn't fetch expenses. Error: ${e.message}"
        }
    }

    /**
     * Fetch itinerary summary for a specific trip
     */
    suspend fun getItinerarySummary(tripId: String): String {
        return try {
            val itineraryItems = firestore.collection("itinerary")
                .whereEqualTo("tripId", tripId)
                .get()
                .await()

            if (itineraryItems.isEmpty) {
                "You don't have any activities planned for this trip yet."
            } else {
                val itemCount = itineraryItems.size()
                val completedCount = itineraryItems.documents.count {
                    it.getBoolean("completed") == true
                }

                buildString {
                    append("🗓️ Itinerary:\n\n")
                    append("📍 Activities: $itemCount\n")
                    append("✅ Completed: $completedCount\n")
                    append("⏳ Upcoming: ${itemCount - completedCount}\n\n")

                    val upcoming = itineraryItems.documents
                        .filter { it.getBoolean("completed") != true }
                        .sortedBy { it.getLong("date") ?: 0L }
                        .take(5)

                    if (upcoming.isNotEmpty()) {
                        append("Next Activities:\n")
                        upcoming.forEach { doc ->
                            val title = doc.getString("title") ?: "Activity"
                            val location = doc.getString("location") ?: ""
                            val time = doc.getString("startTime") ?: ""

                            append("• $title")
                            if (location.isNotEmpty()) append(" at $location")
                            if (time.isNotEmpty()) append(" - $time")
                            append("\n")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AppDataFetcher", "Error fetching itinerary", e)
            "I couldn't fetch itinerary. Error: ${e.message}"
        }
    }

    /**
     * Fetch checklist summary for a specific trip
     */
    suspend fun getChecklistSummary(tripId: String): String {
        return try {
            val userId = auth.currentUser?.uid ?: return "You're not logged in."

            val trip = firestore.collection("trips")
                .document(tripId)
                .get()
                .await()

            val checklistId = trip.getString("checklistId")

            if (checklistId == null) {
                return "This trip doesn't have a checklist yet."
            }

            val items = firestore.collection("users")
                .document(userId)
                .collection("checklists")
                .document(checklistId)
                .collection("items")
                .get()
                .await()

            if (items.isEmpty) {
                "Your checklist is empty."
            } else {
                val totalItems = items.size()
                val packedItems = items.documents.count {
                    it.getBoolean("isPacked") == true
                }
                val progress = if (totalItems > 0) (packedItems * 100) / totalItems else 0

                buildString {
                    append("✅ Packing Checklist:\n\n")
                    append("📦 Total: $totalItems\n")
                    append("✔️ Packed: $packedItems\n")
                    append("📊 Progress: $progress%\n\n")

                    if (packedItems < totalItems) {
                        append("To Pack:\n")
                        items.documents
                            .filter { it.getBoolean("isPacked") != true }
                            .take(8)
                            .forEach { doc ->
                                val name = doc.getString("name") ?: "Item"
                                append("☐ $name\n")
                            }
                    } else {
                        append("🎉 All packed!")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AppDataFetcher", "Error fetching checklist", e)
            "I couldn't fetch checklist. Error: ${e.message}"
        }
    }

    /**
     * Get the most recent trip ID
     */
    suspend fun getMostRecentTripId(): String? {
        return try {
            val userId = auth.currentUser?.uid ?: return null

            val trips = firestore.collection("trips")
                .whereEqualTo("userId", userId)
                .orderBy("startDate", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .await()

            trips.documents.firstOrNull()?.id
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Simple Trip data class
 */
data class Trip(
    val id: String,
    val name: String,
    val destination: String,
    val startDate: Long,
    val endDate: Long
)