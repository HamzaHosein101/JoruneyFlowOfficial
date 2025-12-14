package com.example.travelpractice

import android.app.DatePickerDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
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
    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    private var selectedStartHour = 12
    private var selectedStartMinute = 0
    private var selectedEndHour = 13
    private var selectedEndMinute = 0
    private var selectedDate: Calendar = Calendar.getInstance()

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
        val editDate = view.findViewById<TextInputEditText>(R.id.editDate)
        val editStartTime = view.findViewById<TextInputEditText>(R.id.editStartTime)
        val editEndTime = view.findViewById<TextInputEditText>(R.id.editEndTime)
        val autoCompleteType = view.findViewById<AutoCompleteTextView>(R.id.autoCompleteType)
        
        // Debug: Check if date field is found
        if (editDate == null) {
            android.util.Log.e("AddItineraryItemDialog", "Date field not found!")
        }
        val editDescription = view.findViewById<TextInputEditText>(R.id.editDescription)
        val editCost = view.findViewById<TextInputEditText>(R.id.editCost)
        val btnCancel = view.findViewById<Button>(R.id.btnCancel)
        val btnSave = view.findViewById<Button>(R.id.btnSave)

        // Setup type selection dropdown
        val types = arrayOf("General", "Flight", "Meal", "Sightseeing", "Tour", "Beach/Pool Time", "Adventure", "Shopping")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, types)
        autoCompleteType.setAdapter(adapter)

        // Pre-fill fields if editing
        editingItem?.let { item ->
            editTitle.setText(item.title)
            editLocation.setText(item.location)
            editStartTime.setText(item.startTime)
            editDescription.setText(item.description)
            editCost.setText(if (item.cost > 0) item.cost.toString() else "")
            autoCompleteType.setText(item.type)

            // Parse existing date if available
            if (item.date > 0) {
                selectedDate.timeInMillis = item.date
                editDate.setText(dateFormat.format(selectedDate.time))
            }

            // Parse existing start time if available
            parseStartTimeFromString(item.startTime)
            
            // Calculate end time from start time + duration
            val endTime = calculateEndTime(item.startTime, item.duration)
            editEndTime.setText(endTime)
            parseEndTimeFromString(endTime)
        }

        // Set initial date display if not editing
        if (editingItem == null) {
            editDate.setText(dateFormat.format(selectedDate.time))
        }

        // Set up date picker click listener
        editDate.setOnClickListener {
            showDatePicker()
        }

        // Set up start time picker click listener
        editStartTime.setOnClickListener {
            showStartTimePicker()
        }

        // Set up end time picker click listener
        editEndTime.setOnClickListener {
            showEndTimePicker()
        }

        btnCancel.setOnClickListener {
            dismiss()
        }

        btnSave.setOnClickListener {
            val title = editTitle.text?.toString()?.trim() ?: ""
            val location = editLocation.text?.toString()?.trim() ?: ""
            val startTime = getFormattedStartTime()
            val endTime = getFormattedEndTime()
            val description = editDescription.text?.toString()?.trim() ?: ""
            val cost = editCost.text?.toString()?.trim()?.toDoubleOrNull() ?: 0.0
            
            // Calculate duration from start and end times
            val duration = calculateDurationFromTimes(startTime, endTime)

            if (title.isBlank()) {
                Toast.makeText(requireContext(), "Please enter a title", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val item = if (editingItem != null) {
                editingItem!!.copy(
                    title = title,
                    location = location,
                    startTime = startTime,
                    endTime = endTime,
                    duration = duration,
                    description = description,
                    cost = cost,
                    date = selectedDate.timeInMillis,
                    type = autoCompleteType.text.toString()
                )
            } else {
                ItineraryItem(
                    tripId = tripId,
                    title = title,
                    location = location,
                    startTime = startTime,
                    endTime = endTime,
                    duration = duration,
                    description = description,
                    cost = cost,
                    date = selectedDate.timeInMillis,
                    type = autoCompleteType.text.toString()
                )
            }

            onSave?.invoke(item)
            dismiss()
        }

        val dialog = MaterialAlertDialogBuilder(
            requireContext(),
            R.style.ThemeOverlay_JourneyFlow_AlertDialogAnchor
        )
            .setView(view)
            .create()

        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        }

        return dialog


        
        // Size the dialog to show all content including buttons
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.95).toInt(),
            (resources.displayMetrics.heightPixels * 0.9).toInt()
        )
        
        return dialog
    }

    private fun showDatePicker() {
        val datePickerDialog = DatePickerDialog(
            requireContext(),
            R.style.ThemeOverlay_JourneyFlow_DatePicker,
            { _, year, month, dayOfMonth ->
                selectedDate.set(Calendar.YEAR, year)
                selectedDate.set(Calendar.MONTH, month)
                selectedDate.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                updateDateDisplay()
            },
            selectedDate.get(Calendar.YEAR),
            selectedDate.get(Calendar.MONTH),
            selectedDate.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.show()
    }

    private fun showStartTimePicker() {
        val timePickerDialog = TimePickerDialog(
            requireContext(),
            R.style.ThemeOverlay_JourneyFlow_TimePicker,
            { _, hourOfDay, minute ->
                selectedStartHour = hourOfDay
                selectedStartMinute = minute
                updateStartTimeDisplay()
                // Auto-update end time to be 1 hour later
                selectedEndHour = (hourOfDay + 1) % 24
                selectedEndMinute = minute
                updateEndTimeDisplay()
            },
            selectedStartHour,
            selectedStartMinute,
            false // 12-hour format
        )
        timePickerDialog.show()
    }

    private fun showEndTimePicker() {
        val timePickerDialog = TimePickerDialog(
            requireContext(),
            R.style.ThemeOverlay_JourneyFlow_TimePicker,
            { _, hourOfDay, minute ->
                selectedEndHour = hourOfDay
                selectedEndMinute = minute
                updateEndTimeDisplay()
            },
            selectedEndHour,
            selectedEndMinute,
            false // 12-hour format
        )
        timePickerDialog.show()
    }

    private fun updateDateDisplay() {
        val dateString = dateFormat.format(selectedDate.time)
        val dateField = dialog?.findViewById<TextInputEditText>(R.id.editDate)
        dateField?.setText(dateString)
    }

    private fun updateStartTimeDisplay() {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, selectedStartHour)
        calendar.set(Calendar.MINUTE, selectedStartMinute)

        val timeString = timeFormat.format(calendar.time)
        val startTimeField = dialog?.findViewById<TextInputEditText>(R.id.editStartTime)
        startTimeField?.setText(timeString)
    }

    private fun updateEndTimeDisplay() {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, selectedEndHour)
        calendar.set(Calendar.MINUTE, selectedEndMinute)

        val timeString = timeFormat.format(calendar.time)
        val endTimeField = dialog?.findViewById<TextInputEditText>(R.id.editEndTime)
        endTimeField?.setText(timeString)
    }

    private fun getFormattedStartTime(): String {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, selectedStartHour)
        calendar.set(Calendar.MINUTE, selectedStartMinute)
        return timeFormat.format(calendar.time)
    }

    private fun getFormattedEndTime(): String {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, selectedEndHour)
        calendar.set(Calendar.MINUTE, selectedEndMinute)
        return timeFormat.format(calendar.time)
    }

    private fun parseStartTimeFromString(timeString: String) {
        if (timeString.isBlank()) return

        try {
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
                        selectedStartHour = calendar.get(Calendar.HOUR_OF_DAY)
                        selectedStartMinute = calendar.get(Calendar.MINUTE)
                        return
                    }
                } catch (e: Exception) {
                    // Continue to next format
                }
            }
        } catch (e: Exception) {
            // Use default time if parsing fails
            selectedStartHour = 12
            selectedStartMinute = 0
        }
    }

    private fun parseEndTimeFromString(timeString: String) {
        if (timeString.isBlank()) return

        try {
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
                        selectedEndHour = calendar.get(Calendar.HOUR_OF_DAY)
                        selectedEndMinute = calendar.get(Calendar.MINUTE)
                        return
                    }
                } catch (e: Exception) {
                    // Continue to next format
                }
            }
        } catch (e: Exception) {
            // Use default time if parsing fails
            selectedEndHour = 13
            selectedEndMinute = 0
        }
    }

    private fun calculateEndTime(startTime: String, duration: Int): String {
        try {
            val formats = listOf(
                SimpleDateFormat("h:mm a", Locale.getDefault()),
                SimpleDateFormat("HH:mm", Locale.getDefault())
            )

            for (format in formats) {
                try {
                    val date = format.parse(startTime)
                    if (date != null) {
                        val calendar = Calendar.getInstance()
                        calendar.time = date
                        calendar.add(Calendar.MINUTE, duration)
                        return timeFormat.format(calendar.time)
                    }
                } catch (e: Exception) {
                    // Continue to next format
                }
            }
        } catch (e: Exception) {
            // Return default end time
        }
        return "1:00 PM"
    }

    private fun calculateDurationFromTimes(startTime: String, endTime: String): Int {
        try {
            val formats = listOf(
                SimpleDateFormat("h:mm a", Locale.getDefault()),
                SimpleDateFormat("HH:mm", Locale.getDefault())
            )

            for (format in formats) {
                try {
                    val startDate = format.parse(startTime)
                    val endDate = format.parse(endTime)
                    if (startDate != null && endDate != null) {
                        val diff = endDate.time - startDate.time
                        return (diff / (1000 * 60)).toInt() // Convert to minutes
                    }
                } catch (e: Exception) {
                    // Continue to next format
                }
            }
        } catch (e: Exception) {
            // Return default duration
        }
        return 60 // Default 1 hour
    }
}
