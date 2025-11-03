package com.example.travelpractice.admin

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.travelpractice.LoginActivity
import com.example.travelpractice.R
import com.example.travelpractice.data.Review
import com.example.travelpractice.data.ReviewsRepository
import com.example.travelpractice.admin.ReviewWithReports
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.search.SearchBar
import com.google.android.material.search.SearchView
import com.google.android.material.snackbar.Snackbar
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.google.firebase.firestore.ListenerRegistration

class AdminPanelActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var recyclerReports: RecyclerView
    private lateinit var emptyState: View
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var searchBar: SearchBar
    private lateinit var searchView: SearchView
    private lateinit var chipGroupFilters: ChipGroup
    
    private lateinit var adapter: AdminReportedReviewAdapter
    private val repository = ReviewsRepository(
        FirebaseFirestore.getInstance(),
        FirebaseAuth.getInstance()
    )
    
    private var currentFilter: FilterType = FilterType.REPORTED
    private var searchQuery: String = ""
    private var reviewsListener: ListenerRegistration? = null

    enum class FilterType {
        REPORTED, KEPT, DELETED
    }

    companion object {
        private const val PREF_NAME = "admin_prefs"
        private const val KEY_IS_LOGGED_IN = "is_admin_logged_in"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        
        // Check if admin is logged in
        if (!prefs.getBoolean(KEY_IS_LOGGED_IN, false)) {
            // Redirect to login
            val intent = Intent(this, AdminLoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            return
        }
        
        // User is authenticated (admin login), show admin panel
        // Note: User should also be logged into Firebase for Firestore access
        // But we'll handle permission errors gracefully
        try {
            setContentView(R.layout.activity_admin_panel)
            
            setupViews()
            setupRecyclerView()
            setupSearch()
            setupFilters()
            setupSwipeRefresh()
            loadReviews()
        } catch (e: Exception) {
            android.util.Log.e("AdminPanel", "Failed to initialize admin panel", e)
            android.widget.Toast.makeText(this, "Error loading admin panel: ${e.message}", 
                android.widget.Toast.LENGTH_LONG).show()
            finish()
        }
    }
    
    private fun setupViews() {
        try {
            recyclerReports = findViewById(R.id.recyclerReports)
            emptyState = findViewById(R.id.emptyState)
            swipeRefresh = findViewById(R.id.swipeRefresh)
            searchBar = findViewById(R.id.searchBar)
            searchView = findViewById(R.id.searchView)
            chipGroupFilters = findViewById(R.id.chipGroupFilters)
            
            // Setup toolbar
            val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
            toolbar.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_logout -> {
                        handleLogout()
                        true
                    }
                    else -> false
                }
            }
            
            // Setup toolbar back button - wrap in try-catch in case SearchBar fails
            try {
                searchBar.setNavigationOnClickListener {
                    onBackPressedDispatcher.onBackPressed()
                }
            } catch (e: Exception) {
                android.util.Log.e("AdminPanel", "Failed to setup searchBar navigation", e)
            }
        } catch (e: Exception) {
            android.util.Log.e("AdminPanel", "Failed to setup views", e)
            // Show error and finish
            android.widget.Toast.makeText(this, "Error initializing admin panel", android.widget.Toast.LENGTH_LONG).show()
            finish()
        }
    }
    
    private fun handleLogout() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Log out")
            .setMessage("Are you sure you want to log out of the admin panel?")
            .setPositiveButton("Log out") { _, _ ->
                // Clear admin login state
                prefs.edit().putBoolean(KEY_IS_LOGGED_IN, false).apply()
                
                // Navigate to login screen
                val intent = Intent(this, com.example.travelpractice.LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun setupRecyclerView() {
        try {
            adapter = AdminReportedReviewAdapter(
                onKeep = { review -> handleKeepReview(review) },
                onDelete = { review -> handleDeleteReview(review) }
            )
            recyclerReports.layoutManager = LinearLayoutManager(this)
            recyclerReports.adapter = adapter
        } catch (e: Exception) {
            android.util.Log.e("AdminPanel", "Failed to setup RecyclerView", e)
            throw e // Re-throw to be caught by onCreate's try-catch
        }
    }
    
    private fun setupSearch() {
        try {
            // Try to setup SearchBar and SearchView - if it fails, just hide them
            if (::searchBar.isInitialized && ::searchView.isInitialized) {
                searchView.setupWithSearchBar(searchBar)
                
                searchBar.setOnClickListener {
                    try {
                        searchView.show()
                    } catch (e: Exception) {
                        android.util.Log.e("AdminPanel", "Failed to show search view", e)
                    }
                }
                
                // Setup search text listener
                try {
                    searchView.editText.setOnEditorActionListener { _, _, _ ->
                        searchQuery = searchView.text.toString()
                        loadReviews()
                        try {
                            searchView.hide()
                        } catch (e: Exception) {
                            // Ignore
                        }
                        true
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AdminPanel", "Failed to setup search listener", e)
                    // Hide search if editText not available
                    searchBar.visibility = View.GONE
                    searchView.visibility = View.GONE
                }
            }
        } catch (e: Exception) {
            // SearchBar/SearchView not available or incompatible version
            // Just hide them and continue
            android.util.Log.e("AdminPanel", "Search setup failed completely", e)
            try {
                if (::searchBar.isInitialized) searchBar.visibility = View.GONE
                if (::searchView.isInitialized) searchView.visibility = View.GONE
            } catch (ex: Exception) {
                // Ignore
            }
        }
    }
    
    private fun setupFilters() {
        try {
            findViewById<Chip>(R.id.chipReported)?.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    currentFilter = FilterType.REPORTED
                    loadReviews()
                }
            }
            
            findViewById<Chip>(R.id.chipKept)?.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    currentFilter = FilterType.KEPT
                    loadReviews()
                }
            }
            
            findViewById<Chip>(R.id.chipDeleted)?.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    currentFilter = FilterType.DELETED
                    loadReviews()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AdminPanel", "Failed to setup filters", e)
            // Continue without filters
        }
    }
    
    private fun setupSwipeRefresh() {
        try {
            swipeRefresh.setOnRefreshListener {
                loadReviews()
            }
        } catch (e: Exception) {
            android.util.Log.e("AdminPanel", "Failed to setup swipe refresh", e)
            // Continue without swipe refresh
        }
    }
    
    private fun loadReviews() {
        // Remove existing listener
        reviewsListener?.remove()
        reviewsListener = null
        
        android.util.Log.d("AdminPanel", "Starting to load reviews with real-time listener...")
        
        // Check Firebase auth status
        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser
        android.util.Log.d("AdminPanel", "Current user: ${currentUser?.uid ?: "null"}")
        
        if (::swipeRefresh.isInitialized) {
            swipeRefresh.isRefreshing = true
        }
        
        try {
            val db = FirebaseFirestore.getInstance()
            
            // Use a real-time listener instead of one-time query
            // This ensures reported reviews show up immediately
            reviewsListener = db.collection("reviews")
                .addSnapshotListener { snapshot, error ->
                    lifecycleScope.launch {
                        try {
                            if (error != null) {
                                android.util.Log.e("AdminPanel", "Listener error", error)
                                val errorMsg = when {
                                    error.message?.contains("PERMISSION_DENIED", ignoreCase = true) == true -> {
                                        "Permission denied. Please ensure you're logged in."
                                    }
                                    error.message?.contains("Missing or insufficient permissions", ignoreCase = true) == true -> {
                                        "Permission error. Check Firestore security rules."
                                    }
                                    else -> "Error loading reviews: ${error.message}"
                                }
                                
                                val rootView = findViewById<View>(R.id.root) ?: findViewById<View>(android.R.id.content)
                                if (rootView != null) {
                                    Snackbar.make(rootView, errorMsg, Snackbar.LENGTH_LONG).show()
                                } else {
                                    android.widget.Toast.makeText(this@AdminPanelActivity, errorMsg, android.widget.Toast.LENGTH_LONG).show()
                                }
                                
                                if (::swipeRefresh.isInitialized) {
                                    swipeRefresh.isRefreshing = false
                                }
                                return@launch
                            }
                            
                            if (snapshot == null) {
                                android.util.Log.w("AdminPanel", "Snapshot is null")
                                if (::adapter.isInitialized) {
                                    adapter.submitList(emptyList())
                                    recyclerReports.isVisible = false
                                    emptyState.isVisible = true
                                }
                                if (::swipeRefresh.isInitialized) {
                                    swipeRefresh.isRefreshing = false
                                }
                                return@launch
                            }
                            
                            android.util.Log.d("AdminPanel", "Snapshot received: ${snapshot.documents.size} documents")
                            
                            val reviews = snapshot.documents.mapNotNull { doc ->
                                try {
                                    val reportCount = (doc.getLong("reportCount") ?: 0L).toInt()
                                    Review(
                                        id = doc.id,
                                        userId = doc.getString("userId") ?: "",
                                        username = doc.getString("username") ?: "",
                                        locationName = doc.getString("locationName") ?: "",
                                        tripDate = doc.get("tripDate") as? com.google.firebase.Timestamp ?: com.google.firebase.Timestamp.now(),
                                        rating = (doc.getLong("rating") ?: 0L).toInt(),
                                        comment = doc.getString("comment") ?: "",
                                        photoUrl = doc.getString("photoUrl"),
                                        createdAt = doc.get("createdAt") as? com.google.firebase.Timestamp ?: com.google.firebase.Timestamp.now(),
                                        updatedAt = doc.get("updatedAt") as? com.google.firebase.Timestamp ?: com.google.firebase.Timestamp.now(),
                                        likeCount = (doc.getLong("likeCount") ?: 0L).toInt(),
                                        reportCount = reportCount
                                    )
                                } catch (e: Exception) {
                                    android.util.Log.e("AdminPanel", "Error parsing review ${doc.id}", e)
                                    null
                                }
                            }
                            
                            android.util.Log.d("AdminPanel", "Parsed ${reviews.size} reviews")
                            android.util.Log.d("AdminPanel", "Reviews with reportCount > 0: ${reviews.count { it.reportCount > 0 }}")
                            
                            // Sort by report count (descending) for reported, by createdAt for others
                            val sorted = when (currentFilter) {
                                FilterType.REPORTED -> {
                                    val reported = reviews.sortedByDescending { it.reportCount }
                                    android.util.Log.d("AdminPanel", "REPORTED filter: ${reported.size} reviews")
                                    reported
                                }
                                FilterType.KEPT -> {
                                    val kept = reviews.sortedByDescending { it.createdAt.toDate().time }
                                    android.util.Log.d("AdminPanel", "KEPT filter: ${kept.size} reviews")
                                    kept
                                }
                                FilterType.DELETED -> emptyList()
                            }
                            
                            // Filter by search query
                            val filtered = if (searchQuery.isNotBlank()) {
                                sorted.filter { 
                                    it.locationName.contains(searchQuery, ignoreCase = true) ||
                                    it.username.contains(searchQuery, ignoreCase = true) ||
                                    it.comment.contains(searchQuery, ignoreCase = true)
                                }
                            } else {
                                sorted
                            }
                            
                            // Final filter by status
                            val finalReviewList = when (currentFilter) {
                                FilterType.REPORTED -> {
                                    val reported = filtered.filter { it.reportCount > 0 }
                                    android.util.Log.d("AdminPanel", "Final REPORTED list: ${reported.size} reviews")
                                    reported
                                }
                                FilterType.KEPT -> {
                                    val kept = filtered.filter { it.reportCount == 0 }
                                    android.util.Log.d("AdminPanel", "Final KEPT list: ${kept.size} reviews")
                                    kept
                                }
                                FilterType.DELETED -> emptyList()
                            }
                            
                            android.util.Log.d("AdminPanel", "Final list size: ${finalReviewList.size}")
                            
                            // Fetch report details for reported reviews
                            val reviewsWithReports = finalReviewList.map { review ->
                                if (review.reportCount > 0) {
                                    // Fetch the most recent report for this review
                                    try {
                                        val reportsSnapshot = db.collection("reviews")
                                            .document(review.id)
                                            .collection("reports")
                                            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                                            .limit(1)
                                            .get()
                                            .await()
                                        
                                        if (reportsSnapshot.documents.isNotEmpty()) {
                                            val reportDoc = reportsSnapshot.documents[0]
                                            val reason = reportDoc.getString("reason") ?: ""
                                            val description = reportDoc.getString("description") ?: ""
                                            ReviewWithReports(review, reason, description.takeIf { it.isNotBlank() })
                                        } else {
                                            ReviewWithReports(review, null, null)
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.e("AdminPanel", "Error fetching report for review ${review.id}", e)
                                        ReviewWithReports(review, null, null)
                                    }
                                } else {
                                    ReviewWithReports(review, null, null)
                                }
                            }
                            
                            if (::adapter.isInitialized) {
                                adapter.submitList(reviewsWithReports)
                                recyclerReports.isVisible = reviewsWithReports.isNotEmpty()
                                emptyState.isVisible = reviewsWithReports.isEmpty()
                                android.util.Log.d("AdminPanel", "Adapter updated. RecyclerView visible: ${recyclerReports.isVisible}, EmptyState visible: ${emptyState.isVisible}")
                            }
                            
                        } catch (e: Exception) {
                            android.util.Log.e("AdminPanel", "Error processing snapshot", e)
                            e.printStackTrace()
                        } finally {
                            if (::swipeRefresh.isInitialized) {
                                swipeRefresh.isRefreshing = false
                            }
                        }
                    }
                }
                
        } catch (e: Exception) {
            android.util.Log.e("AdminPanel", "Failed to setup listener", e)
            e.printStackTrace()
            
            val rootView = findViewById<View>(R.id.root) ?: findViewById<View>(android.R.id.content)
            if (rootView != null) {
                Snackbar.make(rootView, "Error loading reviews: ${e.message}", Snackbar.LENGTH_LONG).show()
            } else {
                android.widget.Toast.makeText(this, "Error loading reviews: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
            
            if (::swipeRefresh.isInitialized) {
                swipeRefresh.isRefreshing = false
            }
        }
    }
    
    private fun handleKeepReview(review: Review) {
        // Check authentication
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            Snackbar.make(findViewById(R.id.root), "Please log in to perform this action", Snackbar.LENGTH_LONG).show()
            return
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle("Keep Review")
            .setMessage("Are you sure you want to keep this review? This will clear the reports.")
            .setPositiveButton("Keep") { _, _ ->
                lifecycleScope.launch {
                    try {
                        FirebaseFirestore.getInstance()
                            .collection("reviews")
                            .document(review.id)
                            .update(
                                mapOf(
                                    "reportCount" to 0,
                                    "updatedAt" to com.google.firebase.Timestamp.now()
                                )
                            )
                            .await()
                        Snackbar.make(findViewById(R.id.root), "Review kept", Snackbar.LENGTH_SHORT).show()
                        loadReviews()
                    } catch (e: Exception) {
                        val errorMsg = when {
                            e.message?.contains("PERMISSION_DENIED") == true -> {
                                "Permission denied. You may need to update Firestore security rules."
                            }
                            else -> "Error: ${e.message}"
                        }
                        Snackbar.make(findViewById(R.id.root), errorMsg, Snackbar.LENGTH_LONG).show()
                        android.util.Log.e("AdminPanel", "Error keeping review", e)
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun handleDeleteReview(review: Review) {
        // Check authentication
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            Snackbar.make(findViewById(R.id.root), "Please log in to perform this action", Snackbar.LENGTH_LONG).show()
            return
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Review")
            .setMessage("Are you sure you want to delete this review? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    try {
                        FirebaseFirestore.getInstance()
                            .collection("reviews")
                            .document(review.id)
                            .delete()
                            .await()
                        Snackbar.make(findViewById(R.id.root), "Review deleted", Snackbar.LENGTH_SHORT).show()
                        loadReviews()
                    } catch (e: Exception) {
                        val errorMsg = when {
                            e.message?.contains("PERMISSION_DENIED") == true -> {
                                "Permission denied. You may need to update Firestore security rules."
                            }
                            else -> "Error: ${e.message}"
                        }
                        Snackbar.make(findViewById(R.id.root), errorMsg, Snackbar.LENGTH_LONG).show()
                        android.util.Log.e("AdminPanel", "Error deleting review", e)
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onStop() {
        super.onStop()
        // Remove listener when activity stops to prevent memory leaks
        reviewsListener?.remove()
        reviewsListener = null
    }

    override fun onBackPressed() {
        super.onBackPressed()
        // Optionally clear admin session when leaving
        // Uncomment the line below if you want to require login every time
        // prefs.edit().putBoolean(KEY_IS_LOGGED_IN, false).apply()
    }
}