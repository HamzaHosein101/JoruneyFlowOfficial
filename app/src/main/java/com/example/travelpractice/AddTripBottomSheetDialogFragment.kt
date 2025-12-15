package com.example.travelpractice.ui.home

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import androidx.fragment.app.DialogFragment
import com.example.travelpractice.R
import com.example.travelpractice.model.Trip
import com.google.android.material.button.MaterialButton
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import android.graphics.Typeface
import android.widget.TextView
import androidx.core.content.ContextCompat
import android.view.Gravity
import java.text.SimpleDateFormat
import java.util.TimeZone
import java.util.Calendar
import androidx.core.util.Pair


class AddTripBottomSheetDialogFragment : DialogFragment() {

    private var startMillis: Long? = null
    private var endMillis: Long? = null
    private var editingTrip: Trip? = null

    private val HALF_DAY_MS = 12 * 60 * 60 * 1000L

    // Store-safe value (no timezone “previous day” surprises)
    private fun toStoreMillis(utcMidnightMillis: Long): Long = utcMidnightMillis + HALF_DAY_MS

    // Convert any stored millis back to the exact UTC midnight for the same UTC date
    private fun toPickerUtcMidnight(storedMillis: Long): Long {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = storedMillis
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }




    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        editingTrip = arguments?.getSerializable(ARG_TRIP) as? Trip
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // Keep your same layout file
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.bottomsheet_add_trip, null, false)

        val etTitle       = view.findViewById<EditText>(R.id.etTitle)
        val etDestination = view.findViewById<EditText>(R.id.etDestination)
        val etBudget      = view.findViewById<EditText>(R.id.etBudget)
        val etDates       = view.findViewById<EditText>(R.id.etDates)
        val btnSave       = view.findViewById<MaterialButton>(R.id.btnSave)

        val pickerBuilder = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText("Select trip dates")
            .setTheme(R.style.ThemeOverlay_JourneyFlow_DatePicker)

// ✅ If editing, pre-select the saved range (normalized to UTC midnight)
        editingTrip?.let { t ->
            pickerBuilder.setSelection(Pair(toPickerUtcMidnight(t.startDate), toPickerUtcMidnight(t.endDate)))

        }

        val picker = pickerBuilder.build()



        etDates.setOnClickListener { picker.show(parentFragmentManager, "date_range") }

        picker.addOnPositiveButtonClickListener { sel ->
            val s = sel.first ?: return@addOnPositiveButtonClickListener
            val e = sel.second ?: return@addOnPositiveButtonClickListener

            // Picker gives UTC midnight; store as UTC noon
            startMillis = toStoreMillis(s)
            endMillis = toStoreMillis(e)

            // Display using the picker-normalized date (so it prints the correct day)
            etDates.setText(formatRange(toPickerUtcMidnight(startMillis!!), toPickerUtcMidnight(endMillis!!)))
        }



        editingTrip?.let { t ->
            etTitle.setText(t.title)
            etDestination.setText(t.destination)
            if (t.budget > 0.0) etBudget.setText(t.budget.toString())

            startMillis = t.startDate
            endMillis = t.endDate
            etDates.setText(formatRange(toPickerUtcMidnight(startMillis!!), toPickerUtcMidnight(endMillis!!)))

            btnSave.text = "Update"
        }


        btnSave.setOnClickListener {
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            val title = etTitle.text?.toString()?.trim().orEmpty()
            val destination = etDestination.text?.toString()?.trim().orEmpty()
            val budget = etBudget.text?.toString()?.trim()?.toDoubleOrNull() ?: 0.0
            val s = startMillis
            val e = endMillis

            if (uid == null) {
                Snackbar.make(view, "Not signed in", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (title.isEmpty() || destination.isEmpty() || s == null || e == null) {
                Snackbar.make(view, "Please enter name, destination, and dates", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val trips = FirebaseFirestore.getInstance().collection("trips")

            // EDIT path
            editingTrip?.let { existing ->
                val updates = mapOf(
                    "title" to title,
                    "destination" to destination,
                    "budget" to budget,
                    "startDate" to s,
                    "endDate" to e
                )
                trips.document(existing.id).update(updates)
                    .addOnSuccessListener { dismiss() }
                    .addOnFailureListener {
                        Snackbar.make(view, "Failed to update trip", Snackbar.LENGTH_SHORT).show()
                    }
                return@setOnClickListener
            }

            // CREATE path
            val doc = trips.document()
            val trip = Trip(
                id = doc.id, userId = uid, title = title,
                destination = destination, budget = budget,
                startDate = s, endDate = e, createdAt = System.currentTimeMillis()
            )

            doc.set(trip)
                .addOnSuccessListener { dismiss() }
                .addOnFailureListener { Snackbar.make(view, "Failed to save trip", Snackbar.LENGTH_SHORT).show() }
        }

        val titleView = TextView(requireContext()).apply {
            text = if (editingTrip == null) "Add Trip" else "Edit Trip"
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(48, 40, 48, 5)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.black))
        }



        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setCustomTitle(titleView)
            .setView(view)
            .create()

        dialog.window?.setBackgroundDrawableResource(R.drawable.dialog_rounded_white)

        // Helps keyboard not cover fields
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        return dialog
    }




    private fun formatRange(start: Long, end: Long): String {
        val fmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return "${fmt.format(Date(start))} — ${fmt.format(Date(end))}"
    }

    companion object {
        private const val ARG_TRIP = "arg_trip"

        fun newInstance(): AddTripBottomSheetDialogFragment =
            AddTripBottomSheetDialogFragment()

        fun forEdit(trip: Trip): AddTripBottomSheetDialogFragment =
            AddTripBottomSheetDialogFragment().apply {
                arguments = Bundle().apply { putSerializable(ARG_TRIP, trip) }
            }
    }
}
