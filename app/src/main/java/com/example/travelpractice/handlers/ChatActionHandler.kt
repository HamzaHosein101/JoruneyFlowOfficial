package com.example.travelpractice.handlers

import android.content.Context
import android.content.Intent
import com.example.travelpractice.utils.IntentDetector

/**
 * Handles actions triggered from chat messages
 */
class ChatActionHandler(private val context: Context) {

    /**
     * Process detected intent and generate appropriate response with actions
     */
    fun handleIntent(detectedIntent: IntentDetector.DetectedIntent): ChatAction {
        return when (detectedIntent.intent) {
            IntentDetector.Intent.EXPENSE_TRACKER -> handleExpenseIntent(detectedIntent)
            IntentDetector.Intent.ITINERARY -> handleItineraryIntent(detectedIntent)
            IntentDetector.Intent.CHECKLIST -> handleChecklistIntent(detectedIntent)
            else -> ChatAction.None
        }
    }

    private fun handleExpenseIntent(detected: IntentDetector.DetectedIntent): ChatAction {
        val response = buildString {
            append("I can help you with expense tracking! 💰\n\n")

            if (detected.extractedData.isNotEmpty()) {
                append("I noticed you mentioned:\n")
                detected.extractedData["amount"]?.let { append("• Amount: $$it\n") }
                detected.extractedData["category"]?.let { append("• Category: $it\n") }
                append("\n")
            }

            append("Would you like me to:")
        }

        return ChatAction.ShowOptions(
            message = response,
            options = listOf(
                ActionOption("Open Expense Tracker", ActionType.OPEN_EXPENSE_TRACKER),
                ActionOption("Add New Expense", ActionType.ADD_EXPENSE),
                ActionOption("View Expense Summary", ActionType.VIEW_EXPENSE_SUMMARY)
            )
        )
    }

    private fun handleItineraryIntent(detected: IntentDetector.DetectedIntent): ChatAction {
        val response = buildString {
            append("Let me help you with your itinerary! 🗓️\n\n")

            if (detected.extractedData.isNotEmpty()) {
                append("I noticed:\n")
                detected.extractedData["when"]?.let { append("• Time: $it\n") }
                detected.extractedData["type"]?.let { append("• Activity type: $it\n") }
                append("\n")
            }

            append("What would you like to do?")
        }

        return ChatAction.ShowOptions(
            message = response,
            options = listOf(
                ActionOption("View Itinerary", ActionType.OPEN_ITINERARY),
                ActionOption("Add Activity", ActionType.ADD_ITINERARY_ITEM),
                ActionOption("See Today's Plan", ActionType.VIEW_TODAY_ITINERARY)
            )
        )
    }

    private fun handleChecklistIntent(detected: IntentDetector.DetectedIntent): ChatAction {
        val response = buildString {
            append("I'll help you with your packing checklist! ✅\n\n")

            if (detected.extractedData.isNotEmpty()) {
                append("Looks like you're thinking about:\n")
                detected.extractedData["category"]?.let { append("• $it items\n") }
                append("\n")
            }

            append("Choose an option:")
        }

        return ChatAction.ShowOptions(
            message = response,
            options = listOf(
                ActionOption("Open Checklist", ActionType.OPEN_CHECKLIST),
                ActionOption("Add Items", ActionType.ADD_CHECKLIST_ITEM),
                ActionOption("View Packing Progress", ActionType.VIEW_PACKING_PROGRESS)
            )
        )
    }

    /**
     * Execute the selected action
     */
    fun executeAction(actionType: ActionType, data: Map<String, String> = emptyMap()) {
        when (actionType) {
            ActionType.OPEN_EXPENSE_TRACKER -> openExpenseTracker()
            ActionType.ADD_EXPENSE -> openExpenseTracker(addNew = true)
            ActionType.VIEW_EXPENSE_SUMMARY -> openExpenseTracker(showSummary = true)

            ActionType.OPEN_ITINERARY -> openItinerary()
            ActionType.ADD_ITINERARY_ITEM -> openItinerary(addNew = true)
            ActionType.VIEW_TODAY_ITINERARY -> openItinerary(filterToday = true)

            ActionType.OPEN_CHECKLIST -> openChecklist()
            ActionType.ADD_CHECKLIST_ITEM -> openChecklist(addNew = true)
            ActionType.VIEW_PACKING_PROGRESS -> openChecklist(showProgress = true)
        }
    }

    private fun openExpenseTracker(addNew: Boolean = false, showSummary: Boolean = false) {
        try {
            val intent = Intent(context, Class.forName("com.example.travelpractice.ExpenseTrackerActivity"))
            intent.putExtra("ADD_NEW", addNew)
            intent.putExtra("SHOW_SUMMARY", showSummary)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: ClassNotFoundException) {
            // Activity not found - handle gracefully
        }
    }

    private fun openItinerary(addNew: Boolean = false, filterToday: Boolean = false) {
        try {
            val intent = Intent(context, Class.forName("com.example.travelpractice.ItineraryActivity"))
            intent.putExtra("ADD_NEW", addNew)
            intent.putExtra("FILTER_TODAY", filterToday)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: ClassNotFoundException) {
            // Activity not found - handle gracefully
        }
    }

    private fun openChecklist(addNew: Boolean = false, showProgress: Boolean = false) {
        try {
            val intent = Intent(context, Class.forName("com.example.travelpractice.ChecklistActivity"))
            intent.putExtra("ADD_NEW", addNew)
            intent.putExtra("SHOW_PROGRESS", showProgress)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: ClassNotFoundException) {
            // Activity not found - handle gracefully
        }
    }
}

/**
 * Sealed class representing different chat actions
 */
sealed class ChatAction {
    object None : ChatAction()
    data class ShowOptions(
        val message: String,
        val options: List<ActionOption>
    ) : ChatAction()
    data class Navigate(val destination: String, val extras: Map<String, String>) : ChatAction()
}

/**
 * Represents an action option that can be presented to the user
 */
data class ActionOption(
    val label: String,
    val actionType: ActionType
)

/**
 * Types of actions that can be executed
 */
enum class ActionType {
    // Expense Tracker Actions
    OPEN_EXPENSE_TRACKER,
    ADD_EXPENSE,
    VIEW_EXPENSE_SUMMARY,

    // Itinerary Actions
    OPEN_ITINERARY,
    ADD_ITINERARY_ITEM,
    VIEW_TODAY_ITINERARY,

    // Checklist Actions
    OPEN_CHECKLIST,
    ADD_CHECKLIST_ITEM,
    VIEW_PACKING_PROGRESS
}