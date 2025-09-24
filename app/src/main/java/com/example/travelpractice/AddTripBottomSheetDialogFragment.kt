package com.example.travelpractice.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.travelpractice.R
import com.example.travelpractice.model.Trip
import java.text.DateFormat
import java.util.Date
import java.util.Locale

class AddTripBottomSheetDialogFragment : BottomSheetDialogFragment() {

    private var startMillis: Long? = null
    private var endMillis: Long? = null
    private var editingTrip: Trip? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        editingTrip = arguments?.getSerializable(ARG_TRIP) as? Trip
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.bottomsheet_add_trip, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val etTitle       = view.findViewById<EditText>(R.id.etTitle)
        val etDestination = view.findViewById<EditText>(R.id.etDestination)
        val etBudget      = view.findViewById<EditText>(R.id.etBudget)
        val etDates       = view.findViewById<EditText>(R.id.etDates)
        val btnSave       = view.findViewById<MaterialButton>(R.id.btnSave)

        val picker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText("Select trip dates")
            .build()

        etDates.setOnClickListener { picker.show(parentFragmentManager, "date_range") }
        picker.addOnPositiveButtonClickListener { sel ->
            startMillis = sel.first
            endMillis = sel.second
            etDates.setText(formatRange(sel.first ?: 0L, sel.second ?: 0L))
        }

        // Prefill if editing
        editingTrip?.let { t ->
            etTitle.setText(t.title)
            etDestination.setText(t.destination)
            if (t.budget > 0.0) etBudget.setText(t.budget.toString())
            startMillis = t.startDate
            endMillis = t.endDate
            etDates.setText(formatRange(t.startDate, t.endDate))
            btnSave.text = "Update"
        }

        btnSave.setOnClickListener {
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            val title = etTitle.text?.toString()?.trim().orEmpty()
            val destination = etDestination.text?.toString()?.trim().orEmpty()
            val budget = etBudget.text?.toString()?.trim()?.toDoubleOrNull() ?: 0.0
            val s = startMillis; val e = endMillis

            if (uid == null) { Snackbar.make(view, "Not signed in", Snackbar.LENGTH_SHORT).show(); return@setOnClickListener }
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
                    .addOnFailureListener { Snackbar.make(view, "Failed to update trip", Snackbar.LENGTH_SHORT).show() }
                return@setOnClickListener
            }

            // CREATE path
            val doc = trips.document()
            val trip = Trip(
                id = doc.id, userId = uid, title = title,
                destination = destination, budget = budget,
                startDate = s!!, endDate = e!!, createdAt = System.currentTimeMillis()
            )
            doc.set(trip)
                .addOnSuccessListener { dismiss() }
                .addOnFailureListener { Snackbar.make(view, "Failed to save trip", Snackbar.LENGTH_SHORT).show() }
        }
    }

    private fun formatRange(start: Long, end: Long): String {
        val fmt = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault())
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
