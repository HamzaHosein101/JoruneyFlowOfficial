package com.example.travelpractice.admin

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.ViewFlipper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.travelpractice.LoginActivity
import com.example.travelpractice.R
import com.example.travelpractice.data.Review
import com.example.travelpractice.data.UserProfile
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.search.SearchBar
import com.google.android.material.search.SearchView
import com.google.android.material.snackbar.Snackbar
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.google.firebase.firestore.ListenerRegistration
import java.util.Date

class AdminPanelActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var recyclerReports: RecyclerView
    private lateinit var emptyState: View
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var searchBar: SearchBar
    private lateinit var searchView: SearchView
    private lateinit var chipGroupFilters: ChipGroup
    private lateinit var toggleSections: MaterialButtonToggleGroup
    private lateinit var viewFlipper: ViewFlipper
    private lateinit var recyclerUsers: RecyclerView
    private lateinit var usersEmptyState: View
    private lateinit var swipeUsers: SwipeRefreshLayout
    
    private lateinit var adapter: AdminReportedReviewAdapter
    private lateinit var usersAdapter: AdminUserAdapter
    
    private var currentFilter: FilterType = FilterType.REPORTED
    private var searchQuery: String = ""
    private var reviewsListener: ListenerRegistration? = null
    private var usersListener: ListenerRegistration? = null
    private var currentSection: Section = Section.REVIEWS

    enum class FilterType {
        REPORTED, KEPT, DELETED
    }

    enum class Section {
        REVIEWS, USERS
    }

    companion object {
        private const val PREF_NAME = "admin_prefs"
        private const val KEY_IS_LOGGED_IN = "is_admin_logged_in"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_panel_loading)
        
        prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        
        // Check if admin is logged in
        if (!prefs.getBoolean(KEY_IS_LOGGED_IN, false)) {
            redirectToLogin()
            return
        }
        val firebaseUser = FirebaseAuth.getInstance().currentUser
        if (firebaseUser == null) {
            redirectToLogin()
            return
        }

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(firebaseUser.uid)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists() && snapshot.getString("role") == "admin") {
                    initializePanel()
                } else {
                    handleAccessDenied()
                }
            }
            .addOnFailureListener { e ->
                android.util.Log.e("AdminPanel", "Failed to verify admin access", e)
                android.widget.Toast.makeText(
                    this,
                    "Unable to verify admin access: ${e.localizedMessage ?: e.message}",
                    android.widget.Toast.LENGTH_LONG
                ).show()
                handleAccessDenied()
            }
    }

    private fun initializePanel() {
        try {
            setContentView(R.layout.activity_admin_panel)

            setupViews()
            setupRecyclerView()
            setupUsersRecyclerView()
            setupSearch()
            setupFilters()
            setupSectionToggle()
            setupSwipeRefresh()
            showSection(Section.REVIEWS, shouldResetSearch = false)
        } catch (e: Exception) {
            android.util.Log.e("AdminPanel", "Failed to initialize admin panel", e)
            android.widget.Toast.makeText(
                this,
                "Error loading admin panel: ${e.message}",
                android.widget.Toast.LENGTH_LONG
            ).show()
            finish()
        }
    }

    private fun handleAccessDenied() {
        prefs.edit().putBoolean(KEY_IS_LOGGED_IN, false).apply()
        FirebaseAuth.getInstance().signOut()
        redirectToLogin()
    }

    private fun redirectToLogin() {
        val intent = Intent(this, AdminLoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
    
    private fun setupViews() {
        try {
            recyclerReports = findViewById(R.id.recyclerReports)
            emptyState = findViewById(R.id.emptyState)
            swipeRefresh = findViewById(R.id.swipeRefresh)
            searchBar = findViewById(R.id.searchBar)
            searchView = findViewById(R.id.searchView)
            chipGroupFilters = findViewById(R.id.chipGroupFilters)
            toggleSections = findViewById(R.id.toggleSections)
            viewFlipper = findViewById(R.id.viewFlipper)
            recyclerUsers = findViewById(R.id.recyclerUsers)
            usersEmptyState = findViewById(R.id.usersEmptyState)
            swipeUsers = findViewById(R.id.swipeUsers)
            
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

                FirebaseAuth.getInstance().signOut()
                
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
            throw e
        }
    }

    private fun setupUsersRecyclerView() {
        try {
            usersAdapter = AdminUserAdapter(
                onResetPassword = { user -> handleResetPassword(user) },
                onToggleAdmin = { user -> handleToggleAdmin(user) }
            )
            recyclerUsers.layoutManager = LinearLayoutManager(this)
            recyclerUsers.adapter = usersAdapter
        } catch (e: Exception) {
            android.util.Log.e("AdminPanel", "Failed to setup users RecyclerView", e)
            throw e
        }
    }
    
    private fun setupSearch() {
        try {

            if (::searchBar.isInitialized && ::searchView.isInitialized) {
                searchView.setupWithSearchBar(searchBar)
                
                searchBar.setOnClickListener {
                    try {
                        searchView.show()
                    } catch (e: Exception) {
                        android.util.Log.e("AdminPanel", "Failed to show search view", e)
                    }
                }
                

                try {
                    searchView.editText.setOnEditorActionListener { _, _, _ ->
                        searchQuery = searchView.text.toString()
                        loadCurrentSection()
                        try {
                            searchView.hide()
                        } catch (e: Exception) {
                            // Ignore
                        }
                        true
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AdminPanel", "Failed to setup search listener", e)

                    searchBar.visibility = View.GONE
                    searchView.visibility = View.GONE
                }
            }
        } catch (e: Exception) {

            android.util.Log.e("AdminPanel", "Search setup failed completely", e)
            try {
                if (::searchBar.isInitialized) searchBar.visibility = View.GONE
                if (::searchView.isInitialized) searchView.visibility = View.GONE
            } catch (ex: Exception) {
                // Ignore
            }
        }
    }

    private fun setupSectionToggle() {
        try {
            toggleSections.addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (!isChecked) return@addOnButtonCheckedListener
                val targetSection = when (checkedId) {
                    R.id.btnSectionUsers -> Section.USERS
                    else -> Section.REVIEWS
                }
                showSection(targetSection)
            }
        } catch (e: Exception) {
            android.util.Log.e("AdminPanel", "Failed to setup section toggle", e)
        }
    }
    
    private fun setupFilters() {
        try {
            // Setup ChipGroup listener
            chipGroupFilters.setOnCheckedStateChangeListener { group, checkedIds ->
                if (checkedIds.isEmpty()) {
                    // If nothing is checked, keep current filter
                    return@setOnCheckedStateChangeListener
                }
                
                val checkedId = checkedIds[0]
                val newFilter = when (checkedId) {
                    R.id.chipReported -> FilterType.REPORTED
                    R.id.chipKept -> FilterType.KEPT
                    R.id.chipDeleted -> FilterType.DELETED
                    else -> {
                        android.util.Log.w("AdminPanel", "Unknown chip checked: $checkedId")
                        return@setOnCheckedStateChangeListener
                    }
                }
                
                if (newFilter != currentFilter) {
                    currentFilter = newFilter
                    android.util.Log.d("AdminPanel", "Filter changed to: $currentFilter")
                    loadReviews()
                }
            }
            

            findViewById<Chip>(R.id.chipReported)?.setOnClickListener {
                android.util.Log.d("AdminPanel", "chipReported clicked")
                if (currentFilter != FilterType.REPORTED) {
                    currentFilter = FilterType.REPORTED
                    chipGroupFilters.check(R.id.chipReported)
                    loadReviews()
                }
            }
            
            findViewById<Chip>(R.id.chipKept)?.setOnClickListener {
                android.util.Log.d("AdminPanel", "chipKept clicked")
                if (currentFilter != FilterType.KEPT) {
                    currentFilter = FilterType.KEPT
                    chipGroupFilters.check(R.id.chipKept)
                    loadReviews()
                }
            }
            
            findViewById<Chip>(R.id.chipDeleted)?.setOnClickListener {
                android.util.Log.d("AdminPanel", "chipDeleted clicked")
                if (currentFilter != FilterType.DELETED) {
                    currentFilter = FilterType.DELETED
                    chipGroupFilters.check(R.id.chipDeleted)
                    loadReviews()
                }
            }
            
            android.util.Log.d("AdminPanel", "Filters setup complete")
        } catch (e: Exception) {
            android.util.Log.e("AdminPanel", "Failed to setup filters", e)
            e.printStackTrace()

        }
    }
    
    private fun setupSwipeRefresh() {
        try {
            swipeRefresh.setOnRefreshListener {
                loadReviews()
            }
            swipeUsers.setOnRefreshListener {
                loadUsers()
            }
        } catch (e: Exception) {
            android.util.Log.e("AdminPanel", "Failed to setup swipe refresh", e)
            // Continue without swipe refresh
        }
    }

    private fun showSection(section: Section, shouldResetSearch: Boolean = true) {
        currentSection = section

        if (shouldResetSearch) {
            searchQuery = ""
            try {
                searchBar.setText("")
                searchView.editText?.setText("")
            } catch (e: Exception) {
                android.util.Log.w("AdminPanel", "Failed to reset search text", e)
            }
        }

        try {
            viewFlipper.displayedChild = if (section == Section.REVIEWS) 0 else 1
        } catch (e: Exception) {
            android.util.Log.w("AdminPanel", "Unable to switch view flipper", e)
        }

        try {
            chipGroupFilters.isVisible = section == Section.REVIEWS
        } catch (_: Exception) { }

        try {
            searchBar.hint = when (section) {
                Section.REVIEWS -> getString(R.string.admin_search_reviews_hint)
                Section.USERS -> getString(R.string.admin_search_users_hint)
            }
        } catch (_: Exception) { }

        loadCurrentSection()
    }

    private fun loadCurrentSection() {
        when (currentSection) {
            Section.REVIEWS -> loadReviews()
            Section.USERS -> loadUsers()
        }
    }
    
    private fun loadReviews() {

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
                                        reportCount = reportCount,
                                        adminStatus = doc.getString("adminStatus")
                                    )
                                } catch (e: Exception) {
                                    android.util.Log.e("AdminPanel", "Error parsing review ${doc.id}", e)
                                    null
                                }
                            }
                            
                            android.util.Log.d("AdminPanel", "Parsed ${reviews.size} reviews")
                            android.util.Log.d("AdminPanel", "Reviews with reportCount > 0: ${reviews.count { it.reportCount > 0 }}")
                            android.util.Log.d("AdminPanel", "Reviews with adminStatus='kept': ${reviews.count { it.adminStatus == "kept" }}")
                            android.util.Log.d("AdminPanel", "Reviews with adminStatus='deleted': ${reviews.count { it.adminStatus == "deleted" }}")
                            android.util.Log.d("AdminPanel", "Reviews with adminStatus=null: ${reviews.count { it.adminStatus == null }}")
                            android.util.Log.d("AdminPanel", "Current filter: $currentFilter")
                            
                            // Filter by admin status first
                            val filteredByStatus = when (currentFilter) {
                                FilterType.REPORTED -> {
                                    // Show reviews that have reports and haven't been handled yet
                                    reviews.filter { it.reportCount > 0 && it.adminStatus == null }
                                }
                                FilterType.KEPT -> {
                                    // Show reviews that were kept by admin
                                    val keptReviews = reviews.filter { it.adminStatus == "kept" }
                                    android.util.Log.d("AdminPanel", "KEPT filter found ${keptReviews.size} kept reviews")
                                    keptReviews
                                }
                                FilterType.DELETED -> {
                                    // Show reviews that were deleted by admin
                                    val deletedReviews = reviews.filter { it.adminStatus == "deleted" }
                                    android.util.Log.d("AdminPanel", "DELETED filter found ${deletedReviews.size} deleted reviews")
                                    deletedReviews
                                }
                            }
                            

                            val sorted = when (currentFilter) {
                                FilterType.REPORTED -> {
                                    filteredByStatus.sortedByDescending { it.reportCount }
                                }
                                FilterType.KEPT, FilterType.DELETED -> {
                                    filteredByStatus.sortedByDescending { it.updatedAt.toDate().time }
                                }
                            }
                            
                            android.util.Log.d("AdminPanel", "${currentFilter} filter: ${sorted.size} reviews")
                            

                            val finalReviewList = if (searchQuery.isNotBlank()) {
                                sorted.filter { 
                                    it.locationName.contains(searchQuery, ignoreCase = true) ||
                                    it.username.contains(searchQuery, ignoreCase = true) ||
                                    it.comment.contains(searchQuery, ignoreCase = true)
                                }
                            } else {
                                sorted
                            }
                            
                            android.util.Log.d("AdminPanel", "Final ${currentFilter} list after search: ${finalReviewList.size} reviews")
                            
                            android.util.Log.d("AdminPanel", "Final list size: ${finalReviewList.size}")
                            

                            val reviewsWithReports = finalReviewList.map { review ->
                                if (review.reportCount > 0) {

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

    private fun loadUsers() {
        usersListener?.remove()
        usersListener = null

        if (::swipeUsers.isInitialized) {
            swipeUsers.isRefreshing = true
        }

        try {
            val db = FirebaseFirestore.getInstance()
            usersListener = db.collection("users")
                .addSnapshotListener { snapshot, error ->
                    lifecycleScope.launch {
                        try {
                            if (error != null) {
                                android.util.Log.e("AdminPanel", "Users listener error", error)
                                val message = error.message ?: "Error loading users"
                                val rootView = findViewById<View>(R.id.root) ?: findViewById(R.id.recyclerUsers)
                                if (rootView != null) {
                                    Snackbar.make(rootView, message, Snackbar.LENGTH_LONG).show()
                                }
                                swipeUsers.isRefreshing = false
                                return@launch
                            }

                            if (snapshot == null) {
                                usersAdapter.submitList(emptyList())
                                recyclerUsers.isVisible = false
                                usersEmptyState.isVisible = true
                                swipeUsers.isRefreshing = false
                                return@launch
                            }

                            val users = snapshot.documents.mapNotNull { doc ->
                                try {
                                    val profile = doc.toObject(UserProfile::class.java)
                                    profile?.let {
                                        val uid = if (it.uid.isBlank()) doc.id else it.uid
                                        val role = doc.getString("role")
                                        // Explicitly extract emailVerified from document
                                        // Use getBoolean() which properly handles the field type
                                        val emailVerified = try {
                                            doc.getBoolean("emailVerified") ?: it.emailVerified
                                        } catch (e: Exception) {
                                            // If getBoolean fails, try getting as Any and converting
                                            try {
                                                when (val value = doc.get("emailVerified")) {
                                                    is Boolean -> value
                                                    is String -> value.toBoolean()
                                                    else -> it.emailVerified
                                                }
                                            } catch (e2: Exception) {
                                                android.util.Log.w("AdminPanel", "Failed to parse emailVerified for ${doc.id}", e2)
                                                it.emailVerified
                                            }
                                        }
                                        it.copy(uid = uid, role = role, emailVerified = emailVerified)
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("AdminPanel", "Failed to parse user ${doc.id}", e)
                                    null
                                }
                            }

                            val sorted = users.sortedWith(
                                compareByDescending<UserProfile> { it.createdAt?.toDate() }
                                    .thenBy { it.email.lowercase() }
                            )

                            val filtered = if (searchQuery.isNotBlank()) {
                                val queryLower = searchQuery.lowercase()
                                sorted.filter { user ->
                                    val nameMatch = user.displayName?.lowercase()?.contains(queryLower) == true
                                    val emailMatch = user.email.lowercase().contains(queryLower)
                                    val providerMatch = user.providers.joinToString().lowercase().contains(queryLower)
                                    val uidMatch = user.uid.lowercase().contains(queryLower)
                                    nameMatch || emailMatch || providerMatch || uidMatch
                                }
                            } else {
                                sorted
                            }

                            usersAdapter.submitList(filtered)
                            recyclerUsers.isVisible = filtered.isNotEmpty()
                            usersEmptyState.isVisible = filtered.isEmpty()
                        } catch (e: Exception) {
                            android.util.Log.e("AdminPanel", "Error processing users snapshot", e)
                        } finally {
                            swipeUsers.isRefreshing = false
                        }
                    }
                }
        } catch (e: Exception) {
            android.util.Log.e("AdminPanel", "Failed to load users", e)
            val rootView = findViewById<View>(R.id.root) ?: findViewById<View>(android.R.id.content)
            if (rootView != null) {
                Snackbar.make(rootView, "Error loading users: ${e.message}", Snackbar.LENGTH_LONG).show()
            } else {
                android.widget.Toast.makeText(this, "Error loading users: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
            swipeUsers.isRefreshing = false
        }
    }

    private fun handleResetPassword(user: UserProfile) {
        if (user.email.isBlank()) {
            val root = findViewById<View>(R.id.root) ?: findViewById<View>(android.R.id.content)
            if (root != null) {
                Snackbar.make(root, getString(R.string.admin_reset_password_no_email), Snackbar.LENGTH_LONG).show()
            }
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.admin_reset_password_title)
            .setMessage(getString(R.string.admin_reset_password_message, user.email))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                FirebaseAuth.getInstance()
                    .sendPasswordResetEmail(user.email)
                    .addOnSuccessListener {
                        val root = findViewById<View>(R.id.root) ?: findViewById<View>(android.R.id.content)
                        if (root != null) {
                            Snackbar.make(root, getString(R.string.admin_reset_password_success, user.email), Snackbar.LENGTH_LONG).show()
                        }
                    }
                    .addOnFailureListener { e ->
                        val root = findViewById<View>(R.id.root) ?: findViewById<View>(android.R.id.content)
                        if (root != null) {
                            Snackbar.make(root, getString(R.string.admin_reset_password_failure, e.localizedMessage ?: e.message ?: "Unknown error"), Snackbar.LENGTH_LONG).show()
                        }
                        android.util.Log.e("AdminPanel", "Failed to send reset email for ${user.uid}", e)
                    }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun handleToggleAdmin(user: UserProfile) {
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            Snackbar.make(findViewById(R.id.root), "Please log in to perform this action", Snackbar.LENGTH_LONG).show()
            return
        }

        val isCurrentlyAdmin = user.role == "admin"
        val action = if (isCurrentlyAdmin) "remove admin privileges from" else "make"
        val newRole = if (isCurrentlyAdmin) null else "admin"

        MaterialAlertDialogBuilder(this)
            .setTitle(if (isCurrentlyAdmin) "Remove Admin" else "Make Admin")
            .setMessage("Are you sure you want to $action ${user.email}?")
            .setPositiveButton(if (isCurrentlyAdmin) "Remove Admin" else "Make Admin") { _, _ ->
                lifecycleScope.launch {
                    try {
                        val updateData = if (newRole == null) {
                            mapOf("role" to FieldValue.delete())
                        } else {
                            mapOf("role" to newRole)
                        }
                        
                        FirebaseFirestore.getInstance()
                            .collection("users")
                            .document(user.uid)
                            .update(updateData)
                            .await()
                        
                        val message = if (isCurrentlyAdmin) {
                            "Admin privileges removed from ${user.email}"
                        } else {
                            "Admin privileges granted to ${user.email}"
                        }
                        Snackbar.make(findViewById(R.id.root), message, Snackbar.LENGTH_SHORT).show()
                        // The listener will automatically update the UI
                    } catch (e: Exception) {
                        val errorMsg = when {
                            e.message?.contains("PERMISSION_DENIED") == true -> {
                                "Permission denied. You may need to update Firestore security rules."
                            }
                            else -> "Error: ${e.message}"
                        }
                        Snackbar.make(findViewById(R.id.root), errorMsg, Snackbar.LENGTH_LONG).show()
                        android.util.Log.e("AdminPanel", "Error toggling admin role", e)
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun handleKeepReview(review: Review) {

        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            Snackbar.make(findViewById(R.id.root), "Please log in to perform this action", Snackbar.LENGTH_LONG).show()
            return
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle("Keep Review")
            .setMessage("Are you sure you want to keep this review? This will clear the reports and mark it as kept.")
            .setPositiveButton("Keep") { _, _ ->
                lifecycleScope.launch {
                    try {
                        FirebaseFirestore.getInstance()
                            .collection("reviews")
                            .document(review.id)
                            .update(
                                mapOf(
                                    "reportCount" to 0,
                                    "adminStatus" to "kept",
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

        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            Snackbar.make(findViewById(R.id.root), "Please log in to perform this action", Snackbar.LENGTH_LONG).show()
            return
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Review")
            .setMessage("Are you sure you want to delete this review? It will be marked as deleted and hidden from users.")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    try {
                        // Soft delete: mark as deleted instead of permanently deleting
                        FirebaseFirestore.getInstance()
                            .collection("reviews")
                            .document(review.id)
                            .update(
                                mapOf(
                                    "adminStatus" to "deleted",
                                    "updatedAt" to com.google.firebase.Timestamp.now()
                                )
                            )
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

        reviewsListener?.remove()
        reviewsListener = null
        usersListener?.remove()
        usersListener = null
    }

    override fun onBackPressed() {
        super.onBackPressed()

    }
}