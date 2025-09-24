package com.example.travelpractice

import android.app.Dialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.example.travelpractice.model.ItineraryItem
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import java.util.*

class AddItineraryItemDialogFragment : DialogFragment() {

    private var editingItem: ItineraryItem? = null
    private var tripId: String = ""
    private var onSave: ((ItineraryItem) -> Unit)? = null
    private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    private var selectedHour = 12
    private var selectedMinute = 0

    companion object {
        fun newInstance(tripId: String): AddItineraryItemDialogFragment {
            return AddItineraryItemDialogFragment().apply {
                arguments = Bundle().apply {
                    putString("trip_id", tripId)
                }
            }
        }

        fun forEdit(item: ItineraryItem, tripId: String): AddItineraryItemDialogFragment {
            return AddItineraryItemDialogFragment().apply {
                arguments = Bundle().apply {
                    putSerializable("editing_item", item)
                    putString("trip_id", tripId)
                }
            }
        }
    }

    fun setOnSaveListener(listener: (ItineraryItem) -> Unit) {
        onSave = listener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            editingItem = it.getSerializable("editing_item") as? ItineraryItem
            tripId = it.getString("trip_id") ?: ""
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = layoutInflater.inflate(R.layout.dialog_add_itinerary_item, null)
        
        val editTitle = view.findViewById<TextInputEditText>(R.id.editTitle)
        val editLocation = view.findViewById<TextInputEditText>(R.id.editLocation)
        val editStartTime = view.findViewById<TextInputEditText>(R.id.editStartTime)
        val editDuration = view.findViewById<TextInputEditText>(R.id.editDuration)
        val editDescription = view.findViewById<TextInputEditText>(R.id.editDescription)
        val editCost = view.findViewById<TextInputEditText>(R.id.editCost)
        val btnCancel = view.findViewById<Button>(R.id.btnCancel)
        val btnSave = view.findViewById<Button>(R.id.btnSave)

        // Pre-fill fields if editing
        editingItem?.let { item ->
            editTitle.setText(item.title)
            editLocation.setText(item.location)
            editStartTime.setText(item.startTime)
            editDuration.setText(item.duration.toString())
            editDescription.setText(item.description)
            editCost.setText(if (item.cost > 0) item.cost.toString() else "")
            
            // Parse existing time if available
            parseTimeFromString(item.startTime)
        }

        // Set up time picker click listener
        editStartTime.setOnClickListener {
            showTimePicker()
        }

        btnCancel.setOnClickListener {
            dismiss()
        }

        btnSave.setOnClickListener {
            val title = editTitle.text?.toString()?.trim() ?: ""
            val location = editLocation.text?.toString()?.trim() ?: ""
            val startTime = getFormattedTime()
            val duration = editDuration.text?.toString()?.trim()?.toIntOrNull() ?: 60
            val description = editDescription.text?.toString()?.trim() ?: ""
            val cost = editCost.text?.toString()?.trim()?.toDoubleOrNull() ?: 0.0

            if (title.isBlank()) {
                Toast.makeText(requireContext(), "Please enter a title", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val item = if (editingItem != null) {
                editingItem!!.copy(
                    title = title,
                    location = location,
                    startTime = startTime,
                    duration = duration,
                    description = description,
                    cost = cost
                )
            } else {
                ItineraryItem(
                    tripId = tripId,
                    title = title,
                    location = location,
                    startTime = startTime,
                    duration = duration,
                    description = description,
                    cost = cost,
                    date = System.currentTimeMillis() // For now, use current time. In a real app, you'd have a date picker
                )
            }

            onSave?.invoke(item)
            dismiss()
        }

        return MaterialAlertDialogBuilder(requireContext())
            .setView(view)
            .create()
    }

    private fun showTimePicker() {
        val timePickerDialog = TimePickerDialog(
            requireContext(),
            { _, hourOfDay, minute ->
                selectedHour = hourOfDay
                selectedMinute = minute
                updateTimeDisplay()
            },
            selectedHour,
            selectedMinute,
            false // 12-hour format
        )
        timePickerDialog.show()
    }

    private fun updateTimeDisplay() {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, selectedHour)
        calendar.set(Calendar.MINUTE, selectedMinute)
        
        val timeString = timeFormat.format(calendar.time)
        view?.findViewById<TextInputEditText>(R.id.editStartTime)?.setText(timeString)
    }

    private fun getFormattedTime(): String {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, selectedHour)
        calendar.set(Calendar.MINUTE, selectedMinute)
        return timeFormat.format(calendar.time)
    }

    private fun parseTimeFromString(timeString: String) {
        if (timeString.isBlank()) return
        
        try {
            // Try to parse various time formats
            val formats = listOf(
                SimpleDateFormat("h:mm a", Locale.getDefault()),
                SimpleDateFormat("HH:mm", Locale.getDefault()),
                SimpleDateFormat("h:mm", Locale.getDefault())
            )
            
            for (format in formats) {
                try {
                    val date = format.parse(timeString)
                    if (date != null) {
                        val calendar = Calendar.getInstance()
                        calendar.time = date
                        selectedHour = calendar.get(Calendar.HOUR_OF_DAY)
                        selectedMinute = calendar.get(Calendar.MINUTE)
                        return
                    }
                } catch (e: Exception) {
                    // Continue to next format
                }
            }
        } catch (e: Exception) {
            // Use default time if parsing fails
            selectedHour = 12
            selectedMinute = 0
        }
    }
}
