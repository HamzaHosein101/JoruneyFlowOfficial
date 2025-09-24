package com.example.travelpractice

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.travelpractice.model.ItineraryItem
import com.example.travelpractice.model.Trip
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class ItineraryActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var empty: TextView
    private lateinit var adapter: ItineraryAdapter
    private lateinit var trip: Trip
    private lateinit var selectedDate: TextView
    private lateinit var selectedDay: TextView
    private lateinit var completedCount: TextView
    private lateinit var totalDuration: TextView
    private lateinit var totalCost: TextView

    private val db by lazy { FirebaseFirestore.getInstance() }
    private val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
    private val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_itinerary)

        trip = intent.getSerializableExtra("extra_trip") as? Trip
            ?: run {
                finish()
                return
            }

        setupToolbar()
        setupViews()
        setupRecyclerView()
        setupFab()
        loadItineraryItems()
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.topAppBar)
        toolbar.title = "Travel Itinerary"
        toolbar.setNavigationIcon(android.R.drawable.ic_menu_revert)
        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupViews() {
        selectedDate = findViewById(R.id.selectedDate)
        selectedDay = findViewById(R.id.selectedDay)
        completedCount = findViewById(R.id.completedCount)
        totalDuration = findViewById(R.id.totalDuration)
        totalCost = findViewById(R.id.totalCost)

        // Set current date
        val now = Date()
        selectedDate.text = dateFormat.format(now)
        selectedDay.text = dayFormat.format(now).uppercase()
    }

    private fun setupRecyclerView() {
        recycler = findViewById(R.id.recyclerItinerary)
        empty = findViewById(R.id.emptyState)

        recycler.layoutManager = LinearLayoutManager(this)
        adapter = ItineraryAdapter(
            onEdit = { item ->
                AddItineraryItemDialogFragment.forEdit(item, trip.id)
                    .apply {
                        setOnSaveListener { updatedItem ->
                            saveItineraryItem(updatedItem)
                        }
                    }
                    .show(supportFragmentManager, "edit_itinerary_item")
            },
            onDelete = { item ->
                // For now, we'll just show a snackbar
                // In a real implementation, you'd delete from Firestore
                Snackbar.make(findViewById(android.R.id.content), "Delete functionality coming soon", Snackbar.LENGTH_SHORT).show()
            },
            onToggleComplete = { item ->
                saveItineraryItem(item)
            }
        )
        recycler.adapter = adapter
    }

    private fun setupFab() {
        findViewById<View>(R.id.fabAddItem).setOnClickListener {
            AddItineraryItemDialogFragment.newInstance(trip.id)
                .apply {
                    setOnSaveListener { item ->
                        saveItineraryItem(item)
                    }
                }
                .show(supportFragmentManager, "add_itinerary_item")
        }
    }

    private fun saveItineraryItem(item: ItineraryItem) {
        val itemData = if (item.id.isEmpty()) {
            // New item
            item.copy(id = db.collection("itinerary").document().id)
        } else {
            item
        }

        db.collection("itinerary")
            .document(itemData.id)
            .set(itemData)
            .addOnSuccessListener {
                Snackbar.make(findViewById(android.R.id.content), "Item saved", Snackbar.LENGTH_SHORT).show()
                loadItineraryItems()
            }
            .addOnFailureListener {
                Snackbar.make(findViewById(android.R.id.content), "Failed to save item", Snackbar.LENGTH_SHORT).show()
            }
    }

    private fun loadItineraryItems() {
        db.collection("itinerary")
            .whereEqualTo("tripId", trip.id)
            .get()
            .addOnSuccessListener { documents ->
                val items = documents.mapNotNull { document ->
                    document.toObject(ItineraryItem::class.java).apply {
                        id = document.id
                    }
                }.sortedBy { it.startTime }

                adapter.submitList(items)
                updateEmptyState(items.isEmpty())
                updateDaySummary(items)
            }
            .addOnFailureListener {
                Snackbar.make(findViewById(android.R.id.content), "Failed to load items", Snackbar.LENGTH_SHORT).show()
            }
    }

    private fun updateDaySummary(items: List<ItineraryItem>) {
        val completed = items.count { it.isCompleted }
        val total = items.size
        completedCount.text = "$completed/$total"

        val totalMinutes = items.sumOf { it.duration }
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        totalDuration.text = "${hours}h ${minutes}m"

        val totalCostValue = items.sumOf { it.cost }
        totalCost.text = "$${String.format("%.0f", totalCostValue)}"
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        recycler.visibility = if (isEmpty) View.GONE else View.VISIBLE
        empty.visibility = if (isEmpty) View.VISIBLE else View.GONE
    }
}
