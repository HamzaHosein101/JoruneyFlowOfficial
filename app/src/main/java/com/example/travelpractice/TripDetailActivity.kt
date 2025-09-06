package com.example.travelpractice
import android.content.Intent
import com.example.travelpractice.ui.checklist.BagChecklistActivity
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.travelpractice.model.Trip
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import java.text.DateFormat
import java.util.*

class TripDetailActivity : AppCompatActivity() {

    private var singleToast: Toast? = null
    private fun toast(msg: String) {
        singleToast?.cancel()
        singleToast = Toast.makeText(this, msg, Toast.LENGTH_SHORT).also { it.show() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trip_detail)

        val trip = intent.getSerializableExtra("extra_trip") as? Trip
        if (trip == null) { toast("Trip not found"); finish(); return }

        // Toolbar
        val bar = findViewById<MaterialToolbar>(R.id.topAppBar)
        bar.title = trip.title.ifBlank { "Trip" }
        bar.setNavigationIcon(android.R.drawable.ic_media_previous)
        bar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        // Header values
        val dfMed = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault())
        findViewById<TextView>(R.id.txtHeaderDestination).text =
            trip.destination.ifBlank { "Destination TBD" }
        findViewById<TextView>(R.id.txtHeaderDates).text =
            "${dfMed.format(Date(trip.startDate))} — ${dfMed.format(Date(trip.endDate))}"
        findViewById<TextView>(R.id.txtHeaderBudget).apply {
            if (trip.budget > 0.0) {
                text = java.text.NumberFormat.getCurrencyInstance().format(trip.budget)
                alpha = 1f
            } else {
                text = "No budget set"
                alpha = 0.7f
            }
        }

        // Feature clicks (toasts for now)
        findViewById<MaterialCardView>(R.id.cardChatbot).setOnClickListener { toast("AI Chatbot") }
        findViewById<MaterialCardView>(R.id.cardItinerary).setOnClickListener { toast("Itinerary") }
        findViewById<MaterialCardView>(R.id.cardChecklist).setOnClickListener {
            val i = Intent(this, BagChecklistActivity::class.java).apply {
                putExtra("extra_trip_id", trip.id)          // if Trip has an id
                putExtra("extra_trip_title", trip.title)    // nice for toolbar title
            }
            startActivity(i)
        }

        findViewById<MaterialCardView>(R.id.cardExpenses).setOnClickListener { toast("Expense tracker") }
        findViewById<MaterialCardView>(R.id.cardReviews).setOnClickListener { toast("Reviews") }
    }
}

