package com.example.travelpractice.ui.home

import android.app.Dialog
import android.graphics.Typeface
import android.location.Geocoder
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.util.Pair
import androidx.fragment.app.DialogFragment
import com.example.travelpractice.HomeActivity
import com.example.travelpractice.R
import com.example.travelpractice.model.Trip
import com.google.android.material.button.MaterialButton
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class AddTripBottomSheetDialogFragment : DialogFragment() {

    private var startMillis: Long? = null
    private var endMillis: Long? = null
    private var editingTrip: Trip? = null

    private val HALF_DAY_MS = 12 * 60 * 60 * 1000L


    private fun toStoreMillis(utcMidnightMillis: Long): Long = utcMidnightMillis + HALF_DAY_MS


    private fun toPickerUtcMidnight(storedMillis: Long): Long {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = storedMillis
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun geocodeDestination(
        destination: String,
        onResult: (lat: Double?, lng: Double?) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val geocoder = Geocoder(requireContext(), Locale.getDefault())

                @Suppress("DEPRECATION")
                val results = geocoder.getFromLocationName(destination, 1)

                val loc = results?.firstOrNull()
                withContext(Dispatchers.Main) {
                    onResult(loc?.latitude, loc?.longitude)
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) { onResult(null, null) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onResult(null, null) }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        editingTrip = arguments?.getSerializable(ARG_TRIP) as? Trip
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.bottomsheet_add_trip, null, false)

        val etTitle = view.findViewById<EditText>(R.id.etTitle)
        val etDestination = view.findViewById<EditText>(R.id.etDestination)
        val etBudget = view.findViewById<EditText>(R.id.etBudget)
        val etDates = view.findViewById<EditText>(R.id.etDates)
        val btnSave = view.findViewById<MaterialButton>(R.id.btnSave)

        val pickerBuilder = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText("Select trip dates")
            .setTheme(R.style.ThemeOverlay_JourneyFlow_DatePicker)

        // If editing, pre-select the saved range (normalized to UTC midnight)
        editingTrip?.let { t ->
            pickerBuilder.setSelection(
                Pair(
                    toPickerUtcMidnight(t.startDate),
                    toPickerUtcMidnight(t.endDate)
                )
            )
        }

        val picker = pickerBuilder.build()

        etDates.setOnClickListener { picker.show(parentFragmentManager, "date_range") }

        picker.addOnPositiveButtonClickListener { sel ->
            val s = sel.first ?: return@addOnPositiveButtonClickListener
            val e = sel.second ?: return@addOnPositiveButtonClickListener


            startMillis = toStoreMillis(s)
            endMillis = toStoreMillis(e)

            etDates.setText(
                formatRange(
                    toPickerUtcMidnight(startMillis!!),
                    toPickerUtcMidnight(endMillis!!)
                )
            )
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


            editingTrip?.let { existing ->
                val destinationChanged = destination != existing.destination

                val doUpdate: (Double?, Double?) -> Unit = { newLat, newLng ->
                    val updates = mutableMapOf<String, Any>(
                        "title" to title,
                        "destination" to destination,
                        "budget" to budget,
                        "startDate" to s,
                        "endDate" to e
                    )

                    if (destinationChanged) {

                        updates["lat"] = newLat as Any
                        updates["lng"] = newLng as Any
                    }

                    trips.document(existing.id).update(updates)
                        .addOnSuccessListener {
                            dismiss()

                            (activity as? HomeActivity)?.showSnackbar("Trip updated successfully")
                        }
                        .addOnFailureListener {
                            Snackbar.make(view, "Failed to update trip", Snackbar.LENGTH_SHORT).show()
                        }
                }

                if (destinationChanged) {
                    geocodeDestination(destination) { lat, lng ->

                        doUpdate(lat, lng)
                    }
                } else {
                    doUpdate(existing.lat, existing.lng)
                }

                return@setOnClickListener
            }


            geocodeDestination(destination) { lat, lng ->
                val doc = trips.document()
                val trip = Trip(
                    id = doc.id,
                    userId = uid,
                    title = title,
                    destination = destination,
                    budget = budget,
                    startDate = s,
                    endDate = e,
                    createdAt = System.currentTimeMillis(),
                    lat = lat,
                    lng = lng
                )

                doc.set(trip)
                    .addOnSuccessListener {
                        dismiss()

                        (activity as? HomeActivity)?.showSnackbar("Trip created successfully")
                    }
                    .addOnFailureListener {
                        Snackbar.make(view, "Failed to save trip", Snackbar.LENGTH_SHORT).show()
                    }
            }
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

        fun newInstance(): AddTripBottomSheetDialogFragment = AddTripBottomSheetDialogFragment()

        fun forEdit(trip: Trip): AddTripBottomSheetDialogFragment =
            AddTripBottomSheetDialogFragment().apply {
                arguments = Bundle().apply { putSerializable(ARG_TRIP, trip) }
            }
    }
}