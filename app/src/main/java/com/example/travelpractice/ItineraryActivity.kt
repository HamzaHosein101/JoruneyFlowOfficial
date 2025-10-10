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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
                showDeleteConfirmationDialog(item)
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
                }.sortedWith(compareBy({ it.date }, { it.startTime }))

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

        // Calculate total span of days in the itinerary
        val itemsWithDates = items.filter { it.date > 0 }
        val totalDays = if (itemsWithDates.isEmpty()) {
            0
        } else {
            val earliestDate = itemsWithDates.minOf { it.date }
            val latestDate = itemsWithDates.maxOf { it.date }
            val daysDiff = (latestDate - earliestDate) / (1000 * 60 * 60 * 24) // Convert milliseconds to days
            daysDiff + 1 // +1 to include both start and end days
        }
        totalDuration.text = "$totalDays"

        val totalCostValue = items.sumOf { it.cost }
        totalCost.text = "$${String.format("%.0f", totalCostValue)}"
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        recycler.visibility = if (isEmpty) View.GONE else View.VISIBLE
        empty.visibility = if (isEmpty) View.VISIBLE else View.GONE
    }

    private fun showDeleteConfirmationDialog(item: ItineraryItem) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Item")
            .setMessage("Are you sure you want to delete \"${item.title}\"? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                deleteItineraryItem(item)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteItineraryItem(item: ItineraryItem) {
        db.collection("itinerary")
            .document(item.id)
            .delete()
            .addOnSuccessListener {
                Snackbar.make(findViewById(android.R.id.content), "Item deleted successfully", Snackbar.LENGTH_SHORT).show()
                loadItineraryItems() // Refresh the list
            }
            .addOnFailureListener {
                Snackbar.make(findViewById(android.R.id.content), "Failed to delete item", Snackbar.LENGTH_SHORT).show()
            }
    }
}

