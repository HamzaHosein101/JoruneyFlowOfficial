package com.example.travelpractice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
    private lateinit var notificationManager: ItineraryNotificationManager

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            scheduleNotificationsForCurrentItems()
        } else {
            Snackbar.make(findViewById(android.R.id.content), 
                "Notifications disabled. You can enable them in Settings.", 
                Snackbar.LENGTH_LONG).show()
        }
    }

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
        setupNotificationManager()
        loadItineraryItems()
        
        // Handle notification tap
        handleNotificationIntent()
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.topAppBar)
        toolbar.title = "Travel Itinerary"
        toolbar.setNavigationIcon(android.R.drawable.ic_menu_revert)
        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        setSupportActionBar(toolbar)
        
        // Enable the back button
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
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

    private fun setupNotificationManager() {
        notificationManager = ItineraryNotificationManager(this)
        checkNotificationPermission()
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED -> {
                    // Permission already granted
                }
                else -> {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    private fun saveItineraryItem(item: ItineraryItem) {
        val isNew = item.id.isEmpty()

        val itemData = if (isNew) {
            item.copy(id = db.collection("itinerary").document().id)
        } else {
            item
        }

        db.collection("itinerary")
            .document(itemData.id)
            .set(itemData)
            .addOnSuccessListener {
                // Schedule / cancel notifications
                if (!itemData.isCompleted && itemData.date > 0) {
                    notificationManager.scheduleNotification(itemData, trip)
                } else if (itemData.isCompleted) {
                    notificationManager.cancelNotification(itemData)
                }

                //Snackbar here (only after success)
                Snackbar.make(
                    findViewById(android.R.id.content),
                    if (isNew) "Itinerary item added" else "Itinerary item updated",
                    Snackbar.LENGTH_SHORT
                ).show()

                loadItineraryItems()
            }
            .addOnFailureListener {
                Snackbar.make(
                    findViewById(android.R.id.content),
                    "Failed to save item",
                    Snackbar.LENGTH_SHORT
                ).show()
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
                scheduleNotificationsForCurrentItems(items)
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
        val dialog = MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_JourneyFlow_AlertDialogAnchor)
            .setTitle("Delete Item")
            .setMessage(
                "Are you sure you want to delete \"${item.title}\"? This action cannot be undone."
            )
            .setPositiveButton("Delete") { _, _ ->
                deleteItineraryItem(item)
            }
            .setNegativeButton("Cancel", null)
            .show()

        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
            .setTextColor(getColor(R.color.delete_red))
    }




    private fun deleteItineraryItem(item: ItineraryItem) {
        // Cancel notification before deleting
        notificationManager.cancelNotification(item)
        
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

    private fun scheduleNotificationsForCurrentItems(items: List<ItineraryItem> = emptyList()) {
        if (notificationManager.areNotificationsEnabled()) {
            val itemsToSchedule = if (items.isEmpty()) {
                // If no items provided, get current adapter items
                adapter.currentList
            } else {
                items
            }
            notificationManager.scheduleAllNotifications(trip, itemsToSchedule)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_itinerary, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                // Handle back button press
                onBackPressedDispatcher.onBackPressed()
                true
            }
            R.id.action_notification_settings -> {
                showNotificationSettingsDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showNotificationSettingsDialog() {
        val isEnabled = notificationManager.areNotificationsEnabled()
        
        MaterialAlertDialogBuilder(this)
            .setTitle("Notification Settings")
            .setMessage(if (isEnabled) {
                "Notifications are currently enabled. You'll receive reminders for upcoming itinerary items."
            } else {
                "Notifications are currently disabled. Enable them to receive reminders for upcoming itinerary items."
            })
            .setPositiveButton(if (isEnabled) "Disable" else "Enable") { _, _ ->
                if (isEnabled) {
                    // Disable notifications by opening app settings
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", packageName, null)
                    }
                    startActivity(intent)
                } else {
                    // Request notification permission
                    checkNotificationPermission()
                }
            }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Test Notification") { _, _ ->
                if (isEnabled) {
                    showTestNotification()
                } else {
                    Snackbar.make(findViewById(android.R.id.content), 
                        "Please enable notifications first", 
                        Snackbar.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun showTestNotification() {
        val testItem = ItineraryItem(
            id = "test_${System.currentTimeMillis()}",
            title = "Test Notification",
            description = "This is a test notification for itinerary reminders",
            startTime = "Now",
            location = "Test Location",
            date = System.currentTimeMillis() + 5000 // 5 seconds from now
        )
        notificationManager.scheduleNotification(testItem, trip)
        Snackbar.make(findViewById(android.R.id.content), 
            "Test notification scheduled for 5 seconds from now", 
            Snackbar.LENGTH_SHORT).show()
    }

    private fun handleNotificationIntent() {
        // Check if the activity was opened from a notification
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0) {
            // Show a welcome message if opened from notification
            Snackbar.make(findViewById(android.R.id.content), 
                "Welcome to your itinerary! Check your upcoming items below.", 
                Snackbar.LENGTH_LONG).show()
        }
    }
}

